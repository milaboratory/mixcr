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
@file:Suppress("PrivatePropertyName", "LocalVariableName", "FunctionName")

package com.milaboratory.mixcr.trees

import cc.redberry.pipe.OutputPort
import cc.redberry.pipe.OutputPortCloseable
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.mutations.Mutations.EMPTY_NUCLEOTIDE_MUTATIONS
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mitool.pattern.search.BasicSerializer
import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.basictypes.CloneReader
import com.milaboratory.mixcr.util.VJPair
import com.milaboratory.mixcr.util.XSV
import com.milaboratory.primitivio.PrimitivI
import com.milaboratory.primitivio.PrimitivO
import com.milaboratory.primitivio.annotations.Serializable
import com.milaboratory.primitivio.count
import com.milaboratory.primitivio.filter
import com.milaboratory.primitivio.flatMap
import com.milaboratory.primitivio.flatten
import com.milaboratory.primitivio.map
import com.milaboratory.primitivio.readObjectRequired
import com.milaboratory.util.TempFileDest
import io.repseq.core.GeneFeature
import io.repseq.core.GeneFeature.CDR3
import io.repseq.core.GeneType.Joining
import io.repseq.core.GeneType.Variable
import io.repseq.core.ReferencePoint.CDR3Begin
import io.repseq.core.ReferencePoint.CDR3End
import io.repseq.core.ReferencePoint.FR4End
import io.repseq.core.ReferencePoint.UTR5Begin
import io.repseq.core.VDJCGene
import io.repseq.core.VDJCGeneId
import java.io.PrintStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Algorithm has several steps.
 * For each step process all VJ clusters. Zero-step produce trees, others - add clones to trees or combine them.
 *
 * Zero-step will form initial trees from mutated clones.
 * Initially we can't trust position of VEndTrimmed and JEndTrimmed because of mutations near VEndTrimmed and JEndTrimmed
 * change border of alignment in both ways.
 * For mutated clones we have more information in VJ segments so algorithm will less prone to uncertainty in coordinates of VEndTrimmed and JEndTrimmed.
 *
 * After forming initial trees we can calculate coordinates of VEndTrimmed and JEndTrimmed with high precision,
 * so next steps may use NDN for comparing nodes and clones.
 *
 * After each step we got more information about tree and their MRCA. It affects result of next steps.
 *
 * On every step clone may be chosen for several trees. So after each step there must be call of makeDecisions that left every clone only in one tree.
 *
 * Trees build by maximum parsimony with distances calculated by ClusterProcessor.distance.
 * On every added clone in the tree there is a recalculation of NDN of part of nodes by MutationsUtils.findNDNCommonAncestor and MutationsUtils.concreteNDNChild
 *
 * For NDN used modified score because it must work with wildcards properly. See MutationsUtils.NDNScoring
 *
 * Thoughts:
 * - Maybe we need to repeat steps until they not yield any results
 * - Try to combine trees and clones with different CDR3length at the end
 *
 * @see BuildSHMTreeStep
 * @see SHMTreeBuilderOrchestrator.zeroStep
 * @see SHMTreeBuilderOrchestrator.applyStep
 * @see SHMTreeBuilderOrchestrator.makeDecisions
 * @see SHMTreeBuilder.distance
 * @see MutationsUtils.concreteNDNChild
 * @see MutationsUtils.findNDNCommonAncestor
 * @see MutationsUtils.NDNScoring
 */
class SHMTreeBuilderOrchestrator(
    parameters: SHMTreeBuilderParameters,
    private val scoringSet: ScoringSet,
    val datasets: List<CloneReader>,
    private val assemblingFeatures: Array<GeneFeature>,
    private val tempDest: TempFileDest,
    private val threads: Int,
    VGenesToSearch: Set<String>,
    JGenesToSearch: Set<String>,
    CDR3LengthToSearch: Set<Int>,
    minCountForClone: Int?
) {
    private val clonesFilter = ClonesFilter(
        VGenesToSearch.ifEmpty { null },
        JGenesToSearch.ifEmpty { null },
        CDR3LengthToSearch.ifEmpty { null },
        minCountForClone,
        parameters.productiveOnly
    )

    /**
     * For every clone store in what tree it was added and with what score
     */
    private var decisions = ConcurrentHashMap<CloneWrapper.ID, Map<VJBase, TreeWithMetaBuilder.DecisionInfo>>()

    /**
     * For storing full structure of the tree there is not enough memory.
     * So between steps we store only minimum information about clones in every tree.
     */
    private var currentTrees = ConcurrentHashMap<VJBase, List<TreeWithMetaBuilder.Snapshot>>()

    private val SHMTreeBuilder = SHMTreeBuilder(
        parameters,
        scoringSet
    )

    private val SHMTreeBuilderBySteps = SHMTreeBuilderBySteps(
        parameters,
        scoringSet,
        assemblingFeatures,
        SHMTreeBuilder
    )

    private val geneFeatureToMatch: VJPair<GeneFeature> = assemblingFeatures
        .reduce(GeneFeature::append)
        .let { geneFeatureToSearch ->
            VJPair(
                V = GeneFeature.intersection(geneFeatureToSearch, GeneFeature(UTR5Begin, CDR3Begin)),
                J = GeneFeature.intersection(geneFeatureToSearch, GeneFeature(CDR3End, FR4End)),
            )
        }

    fun cloneWrappersCount(): Int = unsortedClonotypes().count()

    fun allClonesInTress() = currentTrees.asSequence()
        .flatMap { it.value }
        .flatMap { it.clonesAdditionHistory }
        .toSet()

    fun unsortedClonotypes(): OutputPortCloseable<CloneWrapper> = readClonesWithDatasetIds()
        .flatMap { (clone, datasetId) ->
            val VGeneIds = clone.getHits(Variable).map { VHit -> VHit.gene.id }
            val JGeneIds = clone.getHits(Joining).map { JHit -> JHit.gene.id }
            val candidateVJBases = VGeneIds
                //create copy of clone for every pair of V and J hits in it
                .flatMap { VGeneId ->
                    JGeneIds.map { JGeneId ->
                        VJBase(VJPair(VGeneId, JGeneId), clone.ntLengthOf(CDR3, VGeneId, JGeneId))
                    }
                }
                .filter { VJBase -> clone.coversFeature(geneFeatureToMatch, VJBase) }
                .filter { VJBase ->
                    clonesFilter.matchForProductive(clone, VJBase)
                }
                //filter compositions that not overlap with each another
                .filter { VJBase ->
                    clone.formsAllRefPointsInCDR3(VJBase)
                }
            candidateVJBases.map { VJBase ->
                CloneWrapper(clone, datasetId, VJBase, candidateVJBases)
            }
        }
        //filter by user defined parameters
        .filter { c -> clonesFilter.match(c) }

    /**
     * @param userInput (datasetId:cloneId) = treeId
     */
    fun buildByUserData(userInput: Map<CloneWrapper.ID, Int>): OutputPort<TreeWithMetaBuilder> =
        TreeBuilderByUserData(
            tempDest,
            datasets.constructStateBuilder(),
            geneFeatureToMatch,
            threads,
            assemblingFeatures,
            SHMTreeBuilder
        ).buildByUserData(userInput, readClonesWithDatasetIds())


    fun buildTreesByCellTags(
        outputTreesPath: String,
        singleCellParams: SHMTreeBuilderParameters.SingleCell.SimpleClustering
    ): OutputPort<TreeWithMetaBuilder> {
        check(datasets.map { it.tagsInfo }.distinct().size == 1) {
            "tagsInfo must be the same for all files"
        }
        val tagsInfo = datasets.first().tagsInfo
        val stateBuilder = datasets.constructStateBuilder()

        return SingleCellTreeBuilder(
            stateBuilder,
            tempDest,
            clonesFilter,
            scoringSet,
            assemblingFeatures,
            threads,
            SHMTreeBuilder
        )
            .buildTrees(
                readClonesWithDatasetIds(),
                outputTreesPath,
                tagsInfo,
                singleCellParams
            )
    }

    private fun readClonesWithDatasetIds(): OutputPortCloseable<CloneWithDatasetId> = datasets
        .withIndex()
        .map { (datasetId, dataset) ->
            dataset.readClones().map { clone -> CloneWithDatasetId(clone, datasetId) }
        }
        .flatten()

    /**
     * Clone may be selected for several trees with different VJ.
     * Need to choose it what tree suppose to leave the clone.
     *
     * @return total count of clones that was added on this step
     */
    fun makeDecisions(): Int {
        val clonesToRemove = mutableMapOf<VJBase, MutableSet<CloneWrapper.ID>>()
        decisions.forEach { (cloneId, decisions) ->
            val chosenDecision: VJBase = SHMTreeBuilderBySteps.makeDecision(decisions)
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
        clusterBySameVAndJ: List<CloneWrapper>,
        debug: PrintStream,
        relatedAllelesMutations: Map<VDJCGeneId, List<Mutations<NucleotideSequence>>>
    ) {
        val VJBase = clusterBySameVAndJ.first().VJBase
        try {
            val clusterProcessor = buildClusterProcessor(clusterBySameVAndJ)
            val result = clusterProcessor.buildTreeTopParts(relatedAllelesMutations, clusterBySameVAndJ)
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
        clusterBySameVAndJ: List<CloneWrapper>,
        step: BuildSHMTreeStep,
        allClonesInTress: Set<CloneWrapper.ID>,
        debugOfPreviousStep: PrintStream,
        debugOfCurrentStep: PrintStream
    ) {
        val VJBase = clusterBySameVAndJ.first().VJBase
        try {
            val clusterProcessor = buildClusterProcessor(clusterBySameVAndJ)
            val currentTrees = currentTrees.getOrDefault(VJBase, emptyList())
                .map { snapshot -> clusterProcessor.restore(snapshot, clusterBySameVAndJ) }
            if (currentTrees.isEmpty()) return
            val debugInfos = clusterProcessor.debugInfos(currentTrees)
            XSV.writeXSVBody(debugOfPreviousStep, debugInfos, DebugInfo.COLUMNS_FOR_XSV, ";")
            val result = clusterProcessor.applyStep(step, currentTrees, allClonesInTress, clusterBySameVAndJ)
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
        clusterBySameVAndJ: List<CloneWrapper>,
        previousStepDebug: PrintStream
    ): List<TreeWithMetaBuilder> {
        val VJBase = clusterBySameVAndJ.first().VJBase
        if (!clonesFilter.match(VJBase)) {
            return emptyList()
        }
        try {
            val clusterProcessor = buildClusterProcessor(clusterBySameVAndJ)
            val currentTrees = currentTrees.getOrDefault(VJBase, emptyList())
                .map { snapshot -> clusterProcessor.restore(snapshot, clusterBySameVAndJ) }
            if (currentTrees.isEmpty()) return emptyList()
            val debugInfos = clusterProcessor.debugInfos(currentTrees)
            XSV.writeXSVBody(previousStepDebug, debugInfos, DebugInfo.COLUMNS_FOR_XSV, ";")
            return currentTrees
        } catch (e: Exception) {
            throw RuntimeException("can't build result for $VJBase", e)
        }
    }

    private fun buildClusterProcessor(clusterBySameVAndJ: List<CloneWrapper>): SHMTreeBuilderBySteps {
        require(clusterBySameVAndJ.isNotEmpty())
        return SHMTreeBuilderBySteps
    }

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

    private fun alleleMutations(gene: VDJCGene): Mutations<NucleotideSequence> =
        gene.data.baseSequence.mutations ?: EMPTY_NUCLEOTIDE_MUTATIONS

    class ClonesFilter(
        private val VGenesToSearch: Set<String>?,
        private val JGenesToSearch: Set<String>?,
        private val CDR3LengthToSearch: Set<Int>?,
        private val minCountForClone: Int?,
        private val productiveOnly: Boolean
    ) {
        // filter non-productive clonotypes
        // todo only CDR3?
        fun matchForProductive(clone: Clone) = !productiveOnly ||
                (!clone.containsStops(CDR3) && !clone.isOutOfFrame(CDR3))

        // filter non-productive clonotypes
        // todo only CDR3?
        fun matchForProductive(clone: Clone, VJBase: VJBase) = !productiveOnly ||
                (!clone.containsStops(CDR3, VJBase) && !clone.isOutOfFrame(CDR3, VJBase))

        fun match(VJBase: VJBase): Boolean =
            (VGenesToSearch?.contains(VJBase.geneIds.V.name) ?: true) &&
                    (JGenesToSearch?.contains(VJBase.geneIds.J.name) ?: true) &&
                    CDR3LengthMatches(VJBase)

        fun match(cloneWrapper: CloneWrapper): Boolean =
            VGeneMatches(cloneWrapper) && JGeneMatches(cloneWrapper) && CDR3LengthMatches(cloneWrapper.VJBase)
                    && countMatches(cloneWrapper.clone)

        fun countMatches(clone: Clone): Boolean {
            if (minCountForClone == null) return true
            return clone.count >= minCountForClone
        }

        private fun JGeneMatches(cloneWrapper: CloneWrapper): Boolean {
            if (JGenesToSearch == null) return true
            return cloneWrapper.candidateVJBases.any { it.geneIds.J.name in JGenesToSearch }
        }

        private fun VGeneMatches(cloneWrapper: CloneWrapper): Boolean {
            if (VGenesToSearch == null) return true
            return cloneWrapper.candidateVJBases.any { it.geneIds.V.name in VGenesToSearch }
        }

        private fun CDR3LengthMatches(VJBase: VJBase) =
            (CDR3LengthToSearch?.contains(VJBase.CDR3length) ?: true)
    }
}

data class CloneWithDatasetId(
    val clone: Clone,
    val datasetId: Int
)

@Serializable(by = CloneFromUserInput.SerializerImpl::class)
class CloneFromUserInput(
    val clone: Clone,
    val datasetId: Int,
    val treeId: Int
) {
    class SerializerImpl : BasicSerializer<CloneFromUserInput>() {
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
    }
}
