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
import cc.redberry.pipe.blocks.FilteringPort
import cc.redberry.pipe.util.FlatteningOutputPort
import com.milaboratory.core.alignment.AlignmentScoring
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.mutations.Mutations.EMPTY_NUCLEOTIDE_MUTATIONS
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.basictypes.CloneReader
import com.milaboratory.mixcr.basictypes.IOUtil
import com.milaboratory.mixcr.cli.BuildSHMTreeStep
import com.milaboratory.mixcr.trees.ClusterProcessor.CalculatedClusterInfo
import com.milaboratory.mixcr.util.Cluster
import com.milaboratory.mixcr.util.XSV
import com.milaboratory.primitivio.PrimitivIOStateBuilder
import com.milaboratory.util.TempFileDest
import com.milaboratory.util.sorting.HashSorter
import io.repseq.core.GeneFeature
import io.repseq.core.GeneFeature.CDR3
import io.repseq.core.GeneType.Joining
import io.repseq.core.GeneType.Variable
import io.repseq.core.ReferencePoint.JBeginTrimmed
import io.repseq.core.ReferencePoint.VEndTrimmed
import io.repseq.core.VDJCGene
import io.repseq.core.VDJCGeneId
import java.io.IOException
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
    vGenesToSearch: Set<String>,
    jGenesToSearch: Set<String>,
    CDR3LengthToSearch: Set<Int>
) {
    private val filter = Filter(
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
    private val calculatedClustersInfo = ConcurrentHashMap<VJBase, CalculatedClusterInfo>()
    private val counter = AtomicInteger(0)

    fun cloneWrappersCount(): Int = CUtils.it(unsortedClonotypes()).count()

    @Throws(IOException::class)
    private fun createSorter(): HashSorter<CloneWrapper> {
        // todo pre-build state, fill with references if possible
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


        // todo check memory budget
        // HDD-offloading collator of alignments
        // Collate solely by cloneId (no sorting by mapping type, etc.);
        // less fields to sort by -> faster the procedure
        val memoryBudget = if (Runtime.getRuntime().maxMemory() > 10000000000L /* -Xmx10g */) Runtime.getRuntime()
            .maxMemory() / 4L /* 1 Gb */ else 1 shl 28 /* 256 Mb */

        // todo move constants to parameters
        // creating sorter instance
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

    private fun unsortedClonotypes(): OutputPort<CloneWrapper> {
        val wrapped: MutableList<OutputPort<CloneWrapper>> = ArrayList()
        for (i in datasets.indices) {
            // filter non-productive clonotypes
            val port = when {
                // todo CDR3?
                parameters.productiveOnly -> FilteringPort(datasets[i].readClones()) { clone ->
                    !clone.containsStops(CDR3) && !clone.isOutOfFrame(CDR3)
                }
                else -> datasets[i].readClones()
            }
            val wrap = CUtils.wrap(port) { clone ->
                val VGeneNames = clone.getHits(Variable).map { VHit -> VHit.gene.id }
                val JGeneNames = clone.getHits(Joining).map { JHit -> JHit.gene.id }
                CUtils.asOutputPort(
                    VGeneNames
                        .flatMap { VGeneId ->
                            JGeneNames.map { JGeneId ->
                                CloneWrapper(
                                    clone,
                                    i,
                                    VJBase(VGeneId, JGeneId, clone.getNFeature(CDR3).size())
                                )
                            }
                        }
                        .filter { it.getFeature(CDR3) != null }
                        .filter { it.getFeature(GeneFeature(VEndTrimmed, JBeginTrimmed)) != null }
                        .filter { c -> filter.match(c) }
                )
            }
            wrapped.add(FlatteningOutputPort(wrap))
        }
        return FlatteningOutputPort(CUtils.asOutputPort(wrapped))
    }

    fun buildClusters(sortedClones: OutputPortCloseable<CloneWrapper>): OutputPort<Cluster<CloneWrapper>> {
        // todo do not copy cluster
        val cluster = mutableListOf<CloneWrapper>()

        // group by similar V/J genes
        return CUtils.makeSynchronized(object : OutputPortCloseable<Cluster<CloneWrapper>> {
            override fun close() {
                sortedClones.close()
            }

            override fun take(): Cluster<CloneWrapper>? {
                while (true) {
                    val clone = sortedClones.take()
                    if (clone == null) {
                        if (cluster.isNotEmpty()) {
                            val copy = ArrayList(cluster)

                            // new cluster
                            cluster.clear()
                            return Cluster(copy)
                        }
                        return null
                    }
                    if (cluster.isEmpty()) {
                        cluster.add(clone)
                        continue
                    }
                    val lastAdded = cluster[cluster.size - 1]
                    if (clusteringCriteria.clusteringComparator().compare(lastAdded, clone) == 0) {
                        // new cluster
                        cluster.add(clone)
                    } else {
                        val copy = ArrayList(cluster)

                        // new cluster
                        cluster.clear()
                        cluster.add(clone)
                        return Cluster(copy)
                    }
                }
            }
        })
    }

    fun sortedClones(): OutputPortCloseable<CloneWrapper> = createSorter().port(unsortedClonotypes())

    fun makeDecisions(): Int {
        val clonesToRemove: MutableMap<VJBase, MutableSet<CloneWrapper.ID>> = HashMap()
        decisions.forEach { (cloneId, decisions) ->
            val chosenDecision: VJBase = ClusterProcessor.makeDecision(decisions)
            decisions.keys
                .filter { it != chosenDecision }
                .forEach { VJBase ->
                    clonesToRemove.computeIfAbsent(VJBase) { HashSet() }.add(cloneId)
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

    fun zeroStep(
        clusterBySameVAndJ: Cluster<CloneWrapper>,
        debug: PrintStream,
        relatedAllelesMutations: Map<VDJCGeneId, List<Mutations<NucleotideSequence>>>
    ) {
        val VJBase = clusterVJBase(clusterBySameVAndJ)
        val clusterProcessor = buildClusterProcessor(clusterBySameVAndJ, VJBase)
        val result = clusterProcessor.buildTreeTopParts(relatedAllelesMutations)
        currentTrees[VJBase] = result.snapshots
        result.decisions.forEach { (cloneId, decision) ->
            decisions.merge(cloneId, mapOf(VJBase to decision)) { a, b -> a + b }
        }
        XSV.writeXSVBody(debug, result.nodesDebugInfo, DebugInfo.COLUMNS_FOR_XSV, ";")
    }

    fun applyStep(
        clusterBySameVAndJ: Cluster<CloneWrapper>,
        step: BuildSHMTreeStep,
        debugOfPreviousStep: PrintStream,
        debugOfCurrentStep: PrintStream
    ) {
        val VJBase = clusterVJBase(clusterBySameVAndJ)
        val clusterProcessor = buildClusterProcessor(clusterBySameVAndJ, VJBase)
        val currentTrees = currentTrees[VJBase]!!.map { snapshot -> clusterProcessor.restore(snapshot) }
        val debugInfos = clusterProcessor.debugInfos(currentTrees)
        XSV.writeXSVBody(debugOfPreviousStep, debugInfos, DebugInfo.COLUMNS_FOR_XSV, ";")
        val result = clusterProcessor.applyStep(step, currentTrees)
        this.currentTrees[VJBase] = result.snapshots
        result.decisions.forEach { (cloneId, decision) ->
            decisions.merge(cloneId, mapOf(VJBase to decision)) { a, b -> a + b }
        }
        XSV.writeXSVBody(debugOfCurrentStep, result.nodesDebugInfo, DebugInfo.COLUMNS_FOR_XSV, ";")
    }

    fun getResult(clusterBySameVAndJ: Cluster<CloneWrapper>, previousStepDebug: PrintStream): List<SHMTreeResult> {
        val VJBase = clusterVJBase(clusterBySameVAndJ)
        val clusterProcessor = buildClusterProcessor(clusterBySameVAndJ, VJBase)
        val currentTrees = currentTrees[VJBase]!!
            .map { snapshot ->
                if (!snapshot.dirty) {
                    val reconstructedNDN = snapshot.lastFoundNDN
                    clusterProcessor.restore(
                        snapshot.copy(
                            rootInfo = snapshot.rootInfo.copy(
                                reconstructedNDN = reconstructedNDN
                            )
                        )
                    )
                } else {
                    val currentVersionOfTheTree = clusterProcessor.restore(snapshot)
                    val reconstructedNDN = currentVersionOfTheTree.mostRecentCommonAncestorNDN()
                    clusterProcessor.restore(
                        currentVersionOfTheTree.snapshot().copy(
                            rootInfo = snapshot.rootInfo.copy(
                                reconstructedNDN = reconstructedNDN
                            )
                        )
                    )
                }
            }
        val debugInfos = clusterProcessor.debugInfos(currentTrees)
        XSV.writeXSVBody(previousStepDebug, debugInfos, DebugInfo.COLUMNS_FOR_XSV, ";")
        return currentTrees.asSequence()
            .filter { treeWithMetaBuilder -> treeWithMetaBuilder.clonesCount() >= parameters.hideTreesLessThanSize }
            .map { treeWithMetaBuilder ->
                SHMTreeResult(
                    treeWithMetaBuilder.buildResult(),
                    treeWithMetaBuilder.rootInfo,
                    treeWithMetaBuilder.treeId
                )
            }
            .toList()
    }

    private fun buildClusterProcessor(clusterBySameVAndJ: Cluster<CloneWrapper>, VJBase: VJBase): ClusterProcessor {
        return ClusterProcessor.build(
            parameters,
            VScoring,
            JScoring,
            clusterBySameVAndJ,
            getOrCalculateClusterInfo(VJBase, clusterBySameVAndJ),
            idGenerators.computeIfAbsent(VJBase) { IdGenerator() },
            VJBase
        )
    }

    private fun clusterVJBase(clusterBySameVAndJ: Cluster<CloneWrapper>): VJBase = clusterBySameVAndJ.cluster[0].VJBase

    private fun getOrCalculateClusterInfo(
        VJBase: VJBase,
        clusterBySameVAndJ: Cluster<CloneWrapper>
    ): CalculatedClusterInfo = calculatedClustersInfo.computeIfAbsent(VJBase) {
        ClusterProcessor.calculateClusterInfo(
            clusterBySameVAndJ,
            parameters.minPortionOfClonesForCommonAlignmentRanges
        )
    }

    fun relatedAllelesMutations(): Map<VDJCGeneId, List<Mutations<NucleotideSequence>>> = datasets
        .flatMap { it.usedGenes }
        .groupBy { it.geneName }
        .values
        .flatMap { genes ->
            when (genes.size) {
                1 -> emptyList()
                else -> genes.map { gene ->
                    val currentAlleleMutations = alleleMutations(gene)
                    gene.id to (genes - gene)
                        .map {
                            currentAlleleMutations.invert().combineWith(alleleMutations(it))
                        } - EMPTY_NUCLEOTIDE_MUTATIONS
                }
            }
        }
        .toMap()

    class Filter(
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
