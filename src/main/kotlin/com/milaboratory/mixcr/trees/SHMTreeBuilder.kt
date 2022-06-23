/*
 * Copyright (c) 2014-2022, MiLaboratories Inc. All Rights Reserved
 *
 * Before downloading or accessing the software, please read carefully the
 * License Agreement available at:
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 *
 * By downloading or accessing the software, you accept and agree to be bound
 * by the terms of the License Agreement. If you do not want to agree to the terms
 * of the Licensing Agreement, you must not download or access the software.
 */
@file:Suppress("PrivatePropertyName", "LocalVariableName")

package com.milaboratory.mixcr.trees

import cc.redberry.pipe.CUtils
import cc.redberry.pipe.OutputPort
import cc.redberry.pipe.OutputPortCloseable
import cc.redberry.pipe.blocks.Buffer
import com.milaboratory.core.alignment.AlignmentScoring
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.mutations.Mutations.EMPTY_NUCLEOTIDE_MUTATIONS
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.basictypes.CloneReader
import com.milaboratory.mixcr.basictypes.IOUtil
import com.milaboratory.mixcr.cli.BuildSHMTreeStep
import com.milaboratory.mixcr.util.Cluster
import com.milaboratory.mixcr.util.XSV
import com.milaboratory.mixcr.util.buildClusters
import com.milaboratory.primitivio.PrimitivI
import com.milaboratory.primitivio.PrimitivIOStateBuilder
import com.milaboratory.primitivio.PrimitivO
import com.milaboratory.primitivio.Serializer
import com.milaboratory.primitivio.annotations.Serializable
import com.milaboratory.primitivio.count
import com.milaboratory.primitivio.filter
import com.milaboratory.primitivio.flatMap
import com.milaboratory.primitivio.flatten
import com.milaboratory.primitivio.map
import com.milaboratory.primitivio.mapNotNull
import com.milaboratory.primitivio.readObjectRequired
import com.milaboratory.util.TempFileDest
import com.milaboratory.util.sorting.HashSorter
import io.repseq.core.GeneFeature.CDR3
import io.repseq.core.GeneFeature.VJJunction
import io.repseq.core.GeneType
import io.repseq.core.GeneType.Joining
import io.repseq.core.GeneType.Variable
import io.repseq.core.VDJCGene
import io.repseq.core.VDJCGeneId
import java.io.PrintStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 *
 */
class SHMTreeBuilder(
    private val parameters: SHMTreeBuilderParameters,
    private val clusteringCriteria: ClusteringCriteria,
    private val datasets: List<CloneReader>,
    private val tempDest: TempFileDest,
    private val threads: Int,
    vGenesToSearch: Set<String>,
    jGenesToSearch: Set<String>,
    CDR3LengthToSearch: Set<Int>
) {
    private val cloneWrappersFilter = CloneWrappersFilter(
        vGenesToSearch.ifEmpty { null },
        jGenesToSearch.ifEmpty { null },
        CDR3LengthToSearch.ifEmpty { null }
    )
    private val VScoring: AlignmentScoring<NucleotideSequence> =
        datasets[0].assemblerParameters.cloneFactoryParameters.vParameters.scoring

    private val JScoring: AlignmentScoring<NucleotideSequence> =
        datasets[0].assemblerParameters.cloneFactoryParameters.jParameters.scoring
    private var decisions = ConcurrentHashMap<CloneWrapper.ID, Map<VJBase, TreeWithMetaBuilder.DecisionInfo>>()
    private var currentTrees = ConcurrentHashMap<VJBase, List<TreeWithMetaBuilder.Snapshot>>()
    private val idGenerators = ConcurrentHashMap<VJBase, IdGenerator>()
    private val counter = AtomicInteger(0)

    fun cloneWrappersCount(): Int = unsortedClonotypes().count()

    fun allClonesInTress() = currentTrees.asSequence()
        .flatMap { it.value }
        .flatMap { it.clonesAdditionHistory }
        .toSet()

    private fun createCloneWrapperSorter(): HashSorter<CloneWrapper> {
        val stateBuilder = stateBuilderForSorter()

        // todo check memory budget
        val memoryBudget = memoryBudgetForSorter()

        // todo move constants to parameters
        return HashSorter(
            CloneWrapper::class.java,
            clusteringCriteria.clusteringHashCode(),
            clusteringCriteria.clusteringComparatorWithNumberOfMutations(VScoring, JScoring),
            5,
            tempDest.addSuffix("tree.builder").addSuffix("_" + counter.incrementAndGet()),
            8,
            8,
            stateBuilder.oState,
            stateBuilder.iState,
            memoryBudget,
            (1 shl 18 /* 256 Kb */).toLong()
        )
    }

    private fun createUserInputSorter(): HashSorter<CloneFromUserInput> {
        val stateBuilder = stateBuilderForSorter()

        // todo check memory budget
        val memoryBudget = memoryBudgetForSorter()

        // todo move constants to parameters
        return HashSorter(
            CloneFromUserInput::class.java,
            { it.treeId },
            Comparator.comparing { it.treeId },
            5,
            tempDest.addSuffix("tree.builder").addSuffix("_" + counter.incrementAndGet()),
            8,
            8,
            stateBuilder.oState,
            stateBuilder.iState,
            memoryBudget,
            (1 shl 18 /* 256 Kb */).toLong()
        )
    }

    /* 256 Mb */
    private fun memoryBudgetForSorter(): Long =
        if (Runtime.getRuntime().maxMemory() > 10000000000L /* -Xmx10g */) Runtime.getRuntime()
            .maxMemory() / 4L /* 1 Gb */ else 1 shl 28

    private fun stateBuilderForSorter(): PrimitivIOStateBuilder {
        val stateBuilder = PrimitivIOStateBuilder()
        val registeredGenes = mutableSetOf<String>()
        datasets.forEach { dataset ->
            IOUtil.registerGeneReferences(
                stateBuilder,
                dataset.usedGenes.filter { it.name !in registeredGenes },
                dataset.alignerParameters
            )
            registeredGenes += dataset.usedGenes.map { it.name }
        }
        return stateBuilder
    }

    private fun unsortedClonotypes(): OutputPort<CloneWrapper> = readClonesWithDatasetIds()
        .filter { (_, c) ->
            // filter non-productive clonotypes
            // todo CDR3?
            !parameters.productiveOnly || (!c.containsStops(CDR3) && !c.isOutOfFrame(CDR3))
        }
        .flatMap { (datasetId, clone) ->
            val VGeneNames = clone.getHits(Variable).map { VHit -> VHit.gene.id }
            val JGeneNames = clone.getHits(Joining).map { JHit -> JHit.gene.id }
            val candidateVJBases = VGeneNames
                //create copy of clone for every pair of V and J hits in it
                .flatMap { VGeneId ->
                    JGeneNames.map { JGeneId ->
                        VJBase(VGeneId, JGeneId, clone.getNFeature(CDR3).size())
                    }
                }
                //filter compositions that not overlap with each another
                .filter { VJBase ->
                    clone.formsAllRefPointsInCDR3(VJBase)
                }
            candidateVJBases
                .map { VJBase ->
                    CloneWrapper(
                        clone,
                        datasetId,
                        VJBase,
                        candidateVJBases
                    )
                }
        }
        //filter by user defined parameters
        .filter { c -> cloneWrappersFilter.match(c) }

    private fun Clone.formsAllRefPointsInCDR3(VJBase: VJBase): Boolean {
        try {
            return getFeature(CDR3, VJBase) != null && getFeature(VJJunction, VJBase) != null
        } catch (e: Exception) {
            throw e
        }
    }

    /**
     * @param userInput (datasetId:cloneId) = treeId
     */
    fun buildByUserData(userInput: Map<CloneWrapper.ID, Int>): OutputPort<SHMTreeResult> {
        val userInputPort = readClonesWithDatasetIds()
            .mapNotNull { (datasetId, clone) ->
                val datasetIdWithCloneId = CloneWrapper.ID(datasetId = datasetId, cloneId = clone.id)
                val treeId = userInput[datasetIdWithCloneId] ?: return@mapNotNull null
                CloneFromUserInput(
                    clone = clone,
                    treeId = treeId,
                    datasetId = datasetId
                )
            }

        val sortedByTreeId = createUserInputSorter().port(userInputPort)
        val clusters = sortedByTreeId.buildClusters(Comparator.comparing { it.treeId })

        return CUtils.orderedParallelProcessor(
            clusters,
            { cluster ->
                val treeId = cluster.cluster.first().treeId

                val CDR3lengths = cluster.cluster.map { it.clone.getFeature(CDR3).size() }
                    .groupingBy { it }.eachCount()
                if (CDR3lengths.size > 1) {
                    println("WARN: in $treeId not all clones have the same length of CDR3")
                }
                val VJBase = VJBase(
                    VGeneId = cluster.cluster.map { it.clone }.bestGeneForClones(Variable),
                    JGeneId = cluster.cluster.map { it.clone }.bestGeneForClones(Joining),
                    CDR3length = CDR3lengths.maxByOrNull { it.value }!!.key
                )

                val cloneWrappers = cluster.cluster
                    .filter { it.clone.getFeature(CDR3).size() == VJBase.CDR3length }
                    //filter compositions that not overlap with each another
                    .filter { it.clone.formsAllRefPointsInCDR3(VJBase) }
                    .map { CloneWrapper(it.clone, it.datasetId, VJBase, listOf(VJBase)) }

                val clusterProcessor = buildClusterProcessor(Cluster(cloneWrappers), VJBase)
                clusterProcessor.buildTreeFromAllClones(treeId)
            },
            Buffer.DEFAULT_SIZE,
            threads
        )
    }

    private fun List<Clone>.bestGeneForClones(geneType: GeneType): VDJCGeneId =
        flatMap { clone -> clone.getHits(geneType).map { it.gene.id } }
            .groupingBy { it }.eachCount()
            .maxByOrNull { it.value }!!
            .key

    private fun readClonesWithDatasetIds(): OutputPort<Pair<Int, Clone>> = datasets
        .withIndex()
        .map { (datasetId, dataset) ->
            dataset.readClones().map { clone -> datasetId to clone }
        }
        .flatten()

    fun buildClusters(sortedClones: OutputPortCloseable<CloneWrapper>): OutputPort<Cluster<CloneWrapper>> =
        sortedClones.buildClusters(clusteringCriteria.clusteringComparator())

    fun sortedClones(): OutputPortCloseable<CloneWrapper> = createCloneWrapperSorter().port(unsortedClonotypes())

    /**
     * Clone may be selected for several trees with different VJ.
     * Need to choose it what tree suppose to leave the clone.
     *
     * @return total count of clones that was added on this step
     */
    fun makeDecisions(): Int {
        val clonesToRemove = mutableMapOf<VJBase, MutableSet<CloneWrapper.ID>>()
        decisions.forEach { (cloneId, decisions) ->
            val chosenDecision: VJBase = ClusterProcessor.makeDecision(decisions)
            (decisions.keys - chosenDecision).forEach { VJBase ->
                clonesToRemove.computeIfAbsent(VJBase) { mutableSetOf() } += cloneId
            }
        }
        currentTrees = currentTrees.mapValuesTo(ConcurrentHashMap()) { (key, value) ->
            value
                .map { snapshot -> snapshot.excludeClones(clonesToRemove[key] ?: emptySet()) }
                .filter { snapshot -> snapshot.clonesAdditionHistory.size > 1 }
        }
        val clonesWasAdded = decisions.size
        decisions = ConcurrentHashMap()
        return clonesWasAdded
    }

    fun treesCount(): Int = currentTrees.values.sumOf { it.size }

    /**
     * Build initial trees.
     */
    fun zeroStep(
        clusterBySameVAndJ: Cluster<CloneWrapper>,
        debug: PrintStream,
        relatedAllelesMutations: Map<VDJCGeneId, List<Mutations<NucleotideSequence>>>
    ) {
        val VJBase = clusterVJBase(clusterBySameVAndJ)
        try {
            val clusterProcessor = buildClusterProcessor(clusterBySameVAndJ, VJBase)
            val result = clusterProcessor.buildTreeTopParts(relatedAllelesMutations)
            currentTrees[VJBase] = result.snapshots
            result.decisions.forEach { (cloneId, decision) ->
                decisions.merge(cloneId, mapOf(VJBase to decision)) { a, b -> a + b }
            }
            XSV.writeXSVBody(debug, result.nodesDebugInfo, DebugInfo.COLUMNS_FOR_XSV, ";")
        } catch (e: Exception) {
            throw RuntimeException("can't apply zero step for $VJBase", e)
        }
    }

    /**
     * Run one of possible steps to add clones or combine trees.
     */
    fun applyStep(
        clusterBySameVAndJ: Cluster<CloneWrapper>,
        step: BuildSHMTreeStep,
        allClonesInTress: Set<CloneWrapper.ID>,
        debugOfPreviousStep: PrintStream,
        debugOfCurrentStep: PrintStream
    ) {
        val VJBase = clusterVJBase(clusterBySameVAndJ)
        try {
            val clusterProcessor = buildClusterProcessor(clusterBySameVAndJ, VJBase)
            val currentTrees = currentTrees[VJBase]!!.map { snapshot -> clusterProcessor.restore(snapshot) }
            val debugInfos = clusterProcessor.debugInfos(currentTrees)
            XSV.writeXSVBody(debugOfPreviousStep, debugInfos, DebugInfo.COLUMNS_FOR_XSV, ";")
            val result = clusterProcessor.applyStep(step, currentTrees, allClonesInTress)
            this.currentTrees[VJBase] = result.snapshots
            result.decisions.forEach { (cloneId, decision) ->
                decisions.merge(cloneId, mapOf(VJBase to decision)) { a, b -> a + b }
            }
            XSV.writeXSVBody(debugOfCurrentStep, result.nodesDebugInfo, DebugInfo.COLUMNS_FOR_XSV, ";")
        } catch (e: Exception) {
            throw RuntimeException("can't apply step $step on $VJBase", e)
        }
    }

    fun getResult(
        clusterBySameVAndJ: Cluster<CloneWrapper>,
        previousStepDebug: PrintStream,
        recalculatedTreeIds: Map<TreeId, Int>
    ): List<SHMTreeResult> {
        val VJBase = clusterVJBase(clusterBySameVAndJ)
        try {
            val clusterProcessor = buildClusterProcessor(clusterBySameVAndJ, VJBase)
            val currentTrees = currentTrees[VJBase]!!
                //base tree on NDN that was found before instead of sequence of N
                .map { snapshot ->
                    val reconstructedNDN = when {
                        !snapshot.dirty -> snapshot.lastFoundNDN
                        else -> clusterProcessor.restore(snapshot).mostRecentCommonAncestorNDN()
                    }
                    clusterProcessor.restore(
                        snapshot.copy(
                            rootInfo = snapshot.rootInfo.copy(
                                reconstructedNDN = reconstructedNDN
                            ),
                            dirty = true
                        )
                    )
                }
            val debugInfos = clusterProcessor.debugInfos(currentTrees)
            XSV.writeXSVBody(previousStepDebug, debugInfos, DebugInfo.COLUMNS_FOR_XSV, ";")
            return currentTrees.asSequence()
                .filter { treeWithMetaBuilder -> treeWithMetaBuilder.clonesCount() >= parameters.hideTreesLessThanSize }
                .map { treeWithMetaBuilder ->
                    SHMTreeResult(
                        treeWithMetaBuilder.buildResult(),
                        treeWithMetaBuilder.rootInfo,
                        recalculatedTreeIds[treeWithMetaBuilder.treeId]!!
                    )
                }
                .toList()
        } catch (e: Exception) {
            throw RuntimeException("can't build result for $VJBase", e)
        }
    }

    private fun buildClusterProcessor(clusterBySameVAndJ: Cluster<CloneWrapper>, VJBase: VJBase): ClusterProcessor =
        ClusterProcessor.build(
            parameters,
            VScoring,
            JScoring,
            clusterBySameVAndJ,
            ClusterProcessor.calculateClusterInfo(
                clusterBySameVAndJ,
                parameters.minPortionOfClonesForCommonAlignmentRanges
            ),
            idGenerators.computeIfAbsent(VJBase) { IdGenerator() },
            VJBase
        )

    private fun clusterVJBase(clusterBySameVAndJ: Cluster<CloneWrapper>): VJBase = clusterBySameVAndJ.cluster[0].VJBase

    fun calculateUniqGlobalTreeIds(): Map<TreeId, Int> = currentTrees
        .flatMap { it.value.map { snapshot -> snapshot.treeId } }
        .sortedWith(TreeId.comparator)
        .withIndex()
        .associate { it.value to it.index }

    /**
     * For every gene make a list of mutations to alleles of the gene.
     * Empty list if no alleles for the gene.
     */
    fun relatedAllelesMutations(): Map<VDJCGeneId, List<Mutations<NucleotideSequence>>> = datasets
        .flatMap { it.usedGenes }
        .groupBy { it.geneName }
        .values
        .flatMap { allelesGenes ->
            when (allelesGenes.size) {
                1 -> emptyList()
                else -> allelesGenes.map { gene ->
                    val currentAlleleMutations = alleleMutations(gene)
                    gene.id to (allelesGenes - gene)
                        .map { currentAlleleMutations.invert().combineWith(alleleMutations(it)) }
                }
            }
        }
        .toMap()

    private class CloneWrappersFilter(
        private val vGenesToSearch: Set<String>?,
        private val jGenesToSearch: Set<String>?,
        private val CDR3LengthToSearch: Set<Int>?
    ) {
        fun match(cloneWrapper: CloneWrapper): Boolean =
            (vGenesToSearch?.contains(cloneWrapper.VJBase.VGeneId.name) ?: true) &&
                (jGenesToSearch?.contains(cloneWrapper.VJBase.JGeneId.name) ?: true) &&
                (CDR3LengthToSearch?.contains(cloneWrapper.VJBase.CDR3length) ?: true)
    }

    private fun alleleMutations(gene: VDJCGene): Mutations<NucleotideSequence> =
        gene.data.baseSequence.mutations ?: EMPTY_NUCLEOTIDE_MUTATIONS
}

@Serializable(by = CloneFromUserInputSerializer::class)
class CloneFromUserInput(
    val clone: Clone,
    val datasetId: Int,
    val treeId: Int
)

class CloneFromUserInputSerializer : Serializer<CloneFromUserInput> {
    override fun write(output: PrimitivO, obj: CloneFromUserInput) {
        output.writeObject(obj.clone)
        output.writeInt(obj.datasetId)
        output.writeInt(obj.treeId)
    }

    override fun read(input: PrimitivI): CloneFromUserInput = CloneFromUserInput(
        input.readObjectRequired(),
        input.readInt(),
        input.readInt()
    )

    override fun isReference(): Boolean = false

    override fun handlesReference(): Boolean = false
}
