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
@file:Suppress("LocalVariableName", "FunctionName", "PrivatePropertyName", "PropertyName")

package com.milaboratory.mixcr.trees

import cc.redberry.pipe.OutputPort
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NSequenceWithQuality
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mitool.pattern.search.BasicSerializer
import com.milaboratory.mixcr.basictypes.GeneFeatures
import com.milaboratory.mixcr.basictypes.VDJCHit
import com.milaboratory.mixcr.trees.BuildSHMTreeStep.AttachClonesByDistanceChange
import com.milaboratory.mixcr.trees.BuildSHMTreeStep.AttachClonesByNDN
import com.milaboratory.mixcr.trees.BuildSHMTreeStep.BuildingInitialTrees
import com.milaboratory.mixcr.trees.BuildSHMTreeStep.CombineTrees
import com.milaboratory.mixcr.trees.Tree.NodeWithParent
import com.milaboratory.mixcr.trees.TreeBuilderByAncestors.ObservedOrReconstructed
import com.milaboratory.mixcr.trees.TreeBuilderByAncestors.Reconstructed
import com.milaboratory.mixcr.trees.TreeWithMetaBuilder.MetricDecisionInfo
import com.milaboratory.mixcr.trees.TreeWithMetaBuilder.ZeroStepDecisionInfo
import com.milaboratory.mixcr.util.VJPair
import com.milaboratory.mixcr.util.XSV
import com.milaboratory.primitivio.GroupingCriteria
import com.milaboratory.primitivio.PrimitivI
import com.milaboratory.primitivio.PrimitivIOStateBuilder
import com.milaboratory.primitivio.PrimitivO
import com.milaboratory.primitivio.annotations.Serializable
import com.milaboratory.primitivio.cached
import com.milaboratory.primitivio.count
import com.milaboratory.primitivio.filter
import com.milaboratory.primitivio.flatMap
import com.milaboratory.primitivio.flatten
import com.milaboratory.primitivio.forEachInParallel
import com.milaboratory.primitivio.groupBy
import com.milaboratory.primitivio.map
import com.milaboratory.primitivio.mapInParallelOrdered
import com.milaboratory.primitivio.port
import com.milaboratory.primitivio.readList
import com.milaboratory.primitivio.readObjectRequired
import com.milaboratory.primitivio.withProgress
import com.milaboratory.primitivio.writeCollection
import com.milaboratory.util.ProgressAndStage
import com.milaboratory.util.TempFileDest
import io.repseq.core.GeneFeature
import io.repseq.core.GeneType.Joining
import io.repseq.core.GeneType.Variable
import io.repseq.core.VDJCGeneId
import java.io.PrintStream
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier
import kotlin.math.max
import kotlin.math.min

private val groupingCriteria: GroupingCriteria<CloneWrapper> = object : GroupingCriteria<CloneWrapper> {
    override fun hashCodeForGroup(entity: CloneWrapper): Int = entity.VJBase.hashCode()

    override val comparator: Comparator<CloneWrapper> = Comparator.comparing({ c -> c.VJBase }, VJBase.comparator)
}

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
 * @see SHMTreeBuilderBySteps.applyStep
 * @see SHMTreeBuilderBySteps.makeDecisions
 * @see SHMTreeBuilder.distance
 * @see MutationsUtils.concreteNDNChild
 * @see MutationsUtils.findNDNCommonAncestor
 * @see MutationsUtils.NDNScoring
 */
internal class SHMTreeBuilderBySteps(
    private val steps: List<BuildSHMTreeStep>,
    private val scoringSet: ScoringSet,
    private val assemblingFeatures: GeneFeatures,
    private val shmTreeBuilder: SHMTreeBuilder,
    private val clonesFilter: SHMTreeBuilderOrchestrator.ClonesFilter,
    private val relatedAllelesMutations: Map<VDJCGeneId, List<Mutations<NucleotideSequence>>>,
    private val clonesCount: Long,
    private val stateBuilder: PrimitivIOStateBuilder,
    private val tempDest: TempFileDest
) {

    private val initialStep: BuildingInitialTrees = steps.first() as BuildingInitialTrees

    /**
     * For every clone store in what tree it was added and with what score
     */
    private var decisions = ConcurrentHashMap<CloneWrapper.ID, Map<VJBase, TreeWithMetaBuilder.DecisionInfo>>()

    /**
     * For storing full structure of the tree there is not enough memory.
     * So between steps we store only minimum information about clones in every tree.
     */
    private var currentTrees = ConcurrentHashMap<VJBase, List<TreeWithMetaBuilder.Snapshot>>()

    private val allClonesInTress
        get() = currentTrees.asSequence()
            .flatMap { it.value }
            .flatMap { it.clonesAdditionHistory }
            .toSet()

    fun buildTrees(
        clones: OutputPort<CloneWithDatasetId>,
        progressAndStage: ProgressAndStage,
        threads: Int,
        debugs: List<SHMTreeBuilderOrchestrator.Debug>,
        resultWriter: (OutputPort<TreeWithMetaBuilder>) -> Unit
    ) {
        clones
            .groupByTheSameTargets(progressAndStage)
            .groupByTheSameVJBase(progressAndStage)
            .cached(
                tempDest.addSuffix("tree.builder.grouping.by.the.same.VJ.CDR3Length"),
                stateBuilder,
                blockSize = 100,
                concurrencyToRead = max(1, threads / 2),
                concurrencyToWrite = max(1, threads / 2)
            ) { clustersCache ->
                val cloneWrappersCount = clustersCache().count().toLong()

                steps.forEachIndexed { i, step ->
                    val previousStepDebug = if (i != 0) debugs[i - 1] else null
                    val currentStepDebug = debugs[i]
                    val allClonesInTress = allClonesInTress
                    clustersCache().withProgress(
                        cloneWrappersCount,
                        progressAndStage,
                        "Step ${i + 1}/${steps.size}, ${step.forPrint}"
                    ) { clusters ->
                        clusters.forEachInParallel(threads) { cluster ->
                            applyStep(
                                cluster.clones,
                                step,
                                allClonesInTress,
                                previousStepDebug?.treesAfterDecisionsWriter,
                                currentStepDebug.treesBeforeDecisionsWriter
                            )
                        }
                    }
                    makeDecisions()
                }
                clustersCache().withProgress(
                    cloneWrappersCount,
                    progressAndStage,
                    "Building results"
                ) { clusters ->
                    val result = clusters
                        .mapInParallelOrdered(threads) { cluster ->
                            getResult(cluster.clones, debugs.last().treesAfterDecisionsWriter)
                        }
                        .flatten()

                    resultWriter(result)
                }
            }
    }

    /**
     * Clone may be selected for several trees with different VJ.
     * Need to choose it what tree suppose to leave the clone.
     *
     * @return total count of clones that was added on this step
     */
    private fun makeDecisions() {
        val clonesToRemove = mutableMapOf<VJBase, MutableSet<CloneWrapper.ID>>()
        decisions.forEach { (cloneId, decisions) ->
            val chosenDecision: VJBase = makeDecision(decisions)
            (decisions.keys - chosenDecision).forEach { VJBase ->
                clonesToRemove.computeIfAbsent(VJBase) { mutableSetOf() } += cloneId
            }
        }
        currentTrees = currentTrees.mapValuesTo(ConcurrentHashMap()) { (key, value) ->
            value
                .map { snapshot -> snapshot.excludeClones(clonesToRemove[key] ?: emptySet()) }
                .filter { snapshot -> snapshot.clonesAdditionHistory.size > 1 }
        }
        decisions = ConcurrentHashMap()
    }

    private fun OutputPort<List<CloneWithDatasetId>>.groupByTheSameVJBase(progressAndStage: ProgressAndStage): OutputPort<Cluster> =
        withProgress(
            clonesCount,
            progressAndStage,
            "Group clones by the same V, J and CDR3Length"
        ) { groupedClones ->
            groupedClones
                .flatMap { clones -> clones.asCloneWrappers().port }
                //filter by user defined parameters
                .filter { c -> clonesFilter.match(c) }
                .groupBy(
                    stateBuilder,
                    tempDest.addSuffix("tree.builder.grouping.by.the.same.VJ.CDR3Length"),
                    groupingCriteria
                )
                .map { Cluster(it) }
        }

    private fun List<CloneWithDatasetId>.asCloneWrappers(): List<CloneWrapper> {
        val mainClone = CloneWrapper.chooseMainClone(map { it.clone })
        val VGeneIds = mainClone.getHits(Variable).map { VHit -> VHit.gene.id }
        val JGeneIds = mainClone.getHits(Joining).map { JHit -> JHit.gene.id }
        val candidateVJBases = VGeneIds
            //create copy of clone for every pair of V and J hits in it
            .flatMap { VGeneId ->
                JGeneIds.map { JGeneId ->
                    VJBase(VJPair(VGeneId, JGeneId), mainClone.ntLengthOf(GeneFeature.CDR3, VGeneId, JGeneId))
                }
            }
            .filter { VJBase ->
                clonesFilter.matchForProductive(mainClone, VJBase)
            }
            //filter compositions that not overlap with each another
            .filter { VJBase ->
                mainClone.formsAllRefPointsInCDR3(VJBase)
            }
        return candidateVJBases.map { VJBase ->
            CloneWrapper(this, VJBase, candidateVJBases)
        }
    }


    private fun OutputPort<CloneWithDatasetId>.groupByTheSameTargets(progressAndStage: ProgressAndStage): OutputPort<List<CloneWithDatasetId>> =
        withProgress(
            clonesCount,
            progressAndStage,
            "Search for clones with the same targets"
        ) { allClones ->
            //group efficiently the same clones
            allClones.groupBy(
                stateBuilder,
                tempDest.addSuffix("tree.builder.grouping.clones.with.the.same.targets"),
                GroupingCriteria.groupBy { it.clone.targets.reduce(NSequenceWithQuality::concatenate) }
            )
        }

    /**
     * Run one of possible steps to add clones or combine trees.
     */
    private fun applyStep(
        clusterBySameVAndJ: List<CloneWrapper>,
        step: BuildSHMTreeStep,
        allClonesInTress: Set<CloneWrapper.ID>,
        debugOfPreviousStep: PrintStream?,
        debugOfCurrentStep: PrintStream
    ) {
        val VJBase = clusterBySameVAndJ.first().VJBase
        try {
            val currentTrees = currentTrees.getOrDefault(VJBase, emptyList())
                .map { snapshot -> restore(snapshot, clusterBySameVAndJ) }
            if (debugOfPreviousStep != null) {
                val debugInfos = debugInfos(currentTrees)
                XSV.writeXSVBody(debugOfPreviousStep, debugInfos, DebugInfo.COLUMNS_FOR_XSV, ";")
            }
            val result = applyStep(step, currentTrees, allClonesInTress, clusterBySameVAndJ)
            this.currentTrees[VJBase] = result.snapshots
            result.decisions.forEach { (cloneId, decision) ->
                decisions.merge(cloneId, mapOf(VJBase to decision)) { a, b -> a + b }
            }
            XSV.writeXSVBody(debugOfCurrentStep, result.nodesDebugInfo, DebugInfo.COLUMNS_FOR_XSV, ";")
        } catch (e: Exception) {
            throw RuntimeException("can't apply step $step on $VJBase", e)
        }
    }

    private fun getResult(
        clusterBySameVAndJ: List<CloneWrapper>,
        previousStepDebug: PrintStream
    ): List<TreeWithMetaBuilder> {
        val VJBase = clusterBySameVAndJ.first().VJBase
        if (!clonesFilter.match(VJBase)) {
            return emptyList()
        }
        try {
            val currentTrees = currentTrees.getOrDefault(VJBase, emptyList())
                .map { snapshot -> restore(snapshot, clusterBySameVAndJ) }
            if (currentTrees.isEmpty()) return emptyList()
            val debugInfos = debugInfos(currentTrees)
            XSV.writeXSVBody(previousStepDebug, debugInfos, DebugInfo.COLUMNS_FOR_XSV, ";")
            return currentTrees
        } catch (e: Exception) {
            throw RuntimeException("can't build result for $VJBase", e)
        }
    }

    private fun applyStep(
        stepName: BuildSHMTreeStep,
        currentTrees: List<TreeWithMetaBuilder>,
        allClonesInTress: Set<CloneWrapper.ID>,
        clones: List<CloneWrapper>
    ): StepResult = stepByName(stepName).next(currentTrees) {
        clones.asSequence()
            .filter { it.id !in allClonesInTress }
            .map { cloneWrapper -> cloneWrapper.rebaseFromGermline(assemblingFeatures) }
    }

    private fun stepByName(stepParameters: BuildSHMTreeStep): Step = when (stepParameters) {
        is AttachClonesByDistanceChange -> object : Step {
            override fun next(
                originalTrees: List<TreeWithMetaBuilder>,
                clonesNotInClusters: () -> Sequence<CloneWithMutationsFromVJGermline>
            ): StepResult = attachClonesByDistanceChange(
                originalTrees,
                clonesNotInClusters,
                stepParameters
            )
        }
        is CombineTrees -> object : Step {
            override fun next(
                originalTrees: List<TreeWithMetaBuilder>,
                clonesNotInClusters: () -> Sequence<CloneWithMutationsFromVJGermline>
            ): StepResult = combineTrees(originalTrees, stepParameters)
        }
        is AttachClonesByNDN -> object : Step {
            override fun next(
                originalTrees: List<TreeWithMetaBuilder>,
                clonesNotInClusters: () -> Sequence<CloneWithMutationsFromVJGermline>
            ): StepResult = attachClonesByNDN(
                originalTrees,
                clonesNotInClusters,
                stepParameters
            )
        }
        is BuildingInitialTrees -> object : Step {
            override fun next(
                originalTrees: List<TreeWithMetaBuilder>,
                clonesNotInClusters: () -> Sequence<CloneWithMutationsFromVJGermline>
            ): StepResult = buildTreeTopParts(clonesNotInClusters())
        }
    }

    /**
     * Build clusters of clones that definitely in one clonal group.
     * Then build trees from clusters.
     *
     * See actual algorithm for forming clusters in link
     * @see ClustersBuilder.buildClusters
     */
    private fun buildTreeTopParts(clones: Sequence<CloneWithMutationsFromVJGermline>): StepResult {
        val clusterizationAlgorithm = initialStep.algorithm
        //use only clones that are at long distance from any germline
        val rebasedClones = clones.toList()
            .filterNot {
                hasVJPairThatCloseToGermline(it.cloneWrapper, clusterizationAlgorithm.commonMutationsCountForClustering)
            }
            .filter { it.mutations.VJMutationsCount >= clusterizationAlgorithm.commonMutationsCountForClustering }
            .sortedWith(CloneWithMutationsFromVJGermline.comparatorByMutationsCount.reversed())
            .toList()
        if (rebasedClones.isEmpty()) return StepResult.empty
        val clusterPredictor = ClustersBuilder.ClusterPredictorForOneChain(
            clusterizationAlgorithm.commonMutationsCountForClustering,
            clusterizationAlgorithm.maxNDNDistanceForClustering,
            scoringSet,
            rebasedClones,
            rebasedClones.first().cloneWrapper.VJBase,
            relatedAllelesMutations
        )
        val clustersBuilder = when (clusterizationAlgorithm) {
            is SHMTreeBuilderParameters.ClusterizationAlgorithm.BronKerbosch ->
                ClustersBuilder.BronKerbosch(clusterPredictor)
            is SHMTreeBuilderParameters.ClusterizationAlgorithm.SingleLinkage ->
                ClustersBuilder.SingleLinkage(clusterPredictor)
        }
        val clusters = clustersBuilder.buildClusters(rebasedClones)
        val result = clusters
            .asSequence()
            .filter { it.size > 1 }
            .map { cluster ->
                val treeWithMetaBuilder = shmTreeBuilder.buildATreeFromRoot(cluster)
                val decisionsInfo = cluster.map {
                    val effectiveParent = treeWithMetaBuilder.getEffectiveParent(it.cloneWrapper.id)
                    val VHit = it.cloneWrapper.getHit(Variable)
                    val JHit = it.cloneWrapper.getHit(Joining)
                    it.cloneWrapper.id to ZeroStepDecisionInfo(
                        effectiveParent.fromRootToThis.mutations.V.mutationsCount() +
                                effectiveParent.fromRootToThis.mutations.J.mutationsCount(),
                        VHit.gene.geneName,
                        JHit.gene.geneName,
                        VHit.score,
                        JHit.score
                    )
                }
                decisionsInfo to treeWithMetaBuilder
            }
            .toList()
        return buildStepResult(
            result.flatMap { it.first }.toMap(),
            result.map { it.second }.toList()
        )
    }

    private fun hasVJPairThatCloseToGermline(cloneWrapper: CloneWrapper, threshold: Int): Boolean =
        cloneWrapper.candidateVJBases
            .map { VJBase ->
                cloneWrapper.mainClone.getHit(VJBase, Variable).mutationsCount() +
                        cloneWrapper.mainClone.getHit(VJBase, Joining).mutationsCount()
            }
            .any { it < threshold }

    private fun VDJCHit.mutationsCount(): Int = alignments.sumOf { it.absoluteMutations.size() }

    private fun restore(snapshot: TreeWithMetaBuilder.Snapshot, clones: List<CloneWrapper>): TreeWithMetaBuilder {
        val clonesInTrees = snapshot.clonesAdditionHistory.toSet()
        val rebasedClonesFromGermline = clones.asSequence()
            .filter { it.id in clonesInTrees }
            .map { cloneWrapper -> cloneWrapper.rebaseFromGermline(assemblingFeatures) }
            .toList()
        return shmTreeBuilder.buildATreeFromRoot(rebasedClonesFromGermline)
    }

    /**
     * Try to compare NDN of MRCA of different trees to decide if we need to collide trees.
     *
     * If distance between NDN of MRCA less than parameters.thresholdForCombineTrees, then combine this trees.
     *
     * parameters.thresholdForCombineTrees must be very strict because NDN of MRCA contains wildcards.
     *
     * Thoughts:
     * - Use information about VJ of MRCA
     * - Try to calculate probabilities of resolving wildcards to specific letter. It is possible by using
     * topology of branches and possibility of mutations in VJ.
     * - Maybe we can calculate possibility of combined tree and use it as a metric.
     * - see NDNDistance
     */
    private fun combineTrees(originalTrees: List<TreeWithMetaBuilder>, parameters: CombineTrees): StepResult {
        if (originalTrees.isEmpty()) return StepResult.empty
        val result = mutableListOf<TreeWithMetaBuilder>()
        val originalTreesCopy = originalTrees
            .sortedByDescending { it.clonesCount() }
            .toMutableList()
        //TODO sort by entropy in NDN sequence (or calculate it by pairs with broad NDN and sort pairs)
        //trying to grow the biggest trees first, because we know more about NDN sequence
        while (originalTreesCopy.isNotEmpty()) {
            var treeToGrow = originalTreesCopy[0]
            originalTreesCopy.removeAt(0)

            //trying to add the smallest trees first
            for (i in originalTreesCopy.indices.reversed()) {
                val treeToAttach = originalTreesCopy[i]
                //try to calculate distances in both ways, it may differ
                val distance_1 = shmTreeBuilder.distanceBetweenTrees(treeToAttach, treeToGrow)
                val distance_2 = shmTreeBuilder.distanceBetweenTrees(treeToGrow, treeToAttach)
                val metric = min(distance_1, distance_2)
                if (metric <= parameters.maxNDNDistanceBetweenRoots) {
                    treeToGrow = shmTreeBuilder.buildATreeFromRoot(
                        listOf(treeToGrow, treeToAttach).flatMap { treeWithMetaBuilder ->
                            treeWithMetaBuilder.allNodes()
                                .asSequence()
                                .map { it.node }
                                .map { node -> node.content.convert({ it }) { null } }
                                .filterNotNull()
                                .map {
                                    CloneWithMutationsFromVJGermline(
                                        it.mutationsFromVJGermline,
                                        it.clone
                                    )
                                }
                        }
                    )
                    originalTreesCopy.removeAt(i)
                }
            }
            result.add(treeToGrow)
        }
        return buildStepResult(HashMap(), result)
    }

    /**
     * Work only with not so mutated clones
     *
     * Try to compare NDN of clone and NDN of MRCA of tree.
     * If distance less than parameters.thresholdForCombineByNDN than add this clone to the tree.
     *
     * If clone will attach to different tress (with different VJBase or not), then we will choose tree that closet to the clone by NDN of MRCA.
     *
     * parameters.thresholdForCombineByNDN must be strict because NDN of MRCA contains wildcards
     *
     * Thoughts:
     * - We may use information about VJ
     * - Try to calculate probabilities of resolving wildcards to specific letter. It is possible by using
     * topology of branches and possibility of mutations in VJ.
     * - see NDNDistance
     */
    private fun attachClonesByNDN(
        originalTrees: List<TreeWithMetaBuilder>,
        clonesNotInClusters: Supplier<Sequence<CloneWithMutationsFromVJGermline>>,
        parameters: AttachClonesByNDN
    ): StepResult {
        if (originalTrees.isEmpty()) return StepResult.empty
        val decisions = mutableMapOf<CloneWrapper.ID, TreeWithMetaBuilder.DecisionInfo>()
        val resultTrees = originalTrees.map { it.copy() }
        clonesNotInClusters.get()
            .filter { clone -> clone.mutations.VJMutationsCount < initialStep.algorithm.commonMutationsCountForClustering }
            .forEach { clone ->
                //find a tree that closed to the clone by NDN of MRCA
                val bestTreeToAttach = resultTrees
                    .map { tree ->
                        val rebasedClone = tree.rebaseClone(clone)
                        //TODO check that VJMutationsCount wasn't get more than parameters.commonMutationsCountForClustering after rabse
                        val oldestAncestorOfTreeToGrow = tree.mostRecentCommonAncestor()
                        //TODO check also every clone with minimum mutations from germline (align with oldest and use diff)
                        val NDNOfTreeToGrow =
                            oldestAncestorOfTreeToGrow.fromRootToThis.NDNMutations.buildSequence(tree.rootInfo)
                        val NDNOfNodeToAttach = rebasedClone.mutationsSet.NDNMutations.buildSequence(tree.rootInfo)
                        //try to calculate distances in both ways, it may differ
                        val metric_1 = scoringSet.NDNDistance(NDNOfTreeToGrow, NDNOfNodeToAttach)
                        val metric_2 = scoringSet.NDNDistance(NDNOfNodeToAttach, NDNOfTreeToGrow)
                        min(metric_1, metric_2) to { tree.addClone(rebasedClone) }
                    }
                    .minByOrNull { it.first }
                if (bestTreeToAttach != null) {
                    val metric = bestTreeToAttach.first
                    if (metric <= parameters.maxNDNDistance) {
                        bestTreeToAttach.second()
                        decisions[clone.cloneWrapper.id] = MetricDecisionInfo(metric)
                    }
                }
            }
        return buildStepResult(decisions, resultTrees)
    }

    /**
     * Working only with mutated clones.
     *
     * Try to insert every clone in a tree. Compare how far it from germline and how far it will be from branch.
     * So clone will be attached only if tree will not be too wide after attaching.
     *
     * Apart from that algorithm checks that NDN is not so different.
     *
     * If clone will attach to different tress (with different VJBase or not), then we will choose tree that most suitable for the clone by topology.
     *
     * Thoughts:
     * - Maybe this step will not yield anything after rework of buildTreeTopParts()
     * - Threshold of NDN distance may be function of distanceFromRoot and changeOfDistance.
     * So we allow to change NDN if we sure about information in VJ.
     * - Maybe we should to calculate information about VJ separately to make decision
     */
    private fun attachClonesByDistanceChange(
        originalTrees: List<TreeWithMetaBuilder>,
        clonesNotInClustersSupplier: () -> Sequence<CloneWithMutationsFromVJGermline>,
        parameters: AttachClonesByDistanceChange
    ): StepResult {
        if (originalTrees.isEmpty()) return StepResult.empty
        val result = originalTrees.map { it.copy() }
        val decisions = mutableMapOf<CloneWrapper.ID, TreeWithMetaBuilder.DecisionInfo>()
        val clonesNotInClusters = clonesNotInClustersSupplier()
            .sortedByDescending { it.mutations.VJMutationsCount }
        //try to add clones as nodes, more mutated first, so we build a tree from top to bottom
        clonesNotInClusters.forEach { clone ->
            if (clone.mutations.VJMutationsCount >= initialStep.algorithm.commonMutationsCountForClustering) {
                //choose tree that most suitable for the clone by topology
                val bestActionAndDistanceFromRoot = result
                    .mapNotNull { treeWithMeta ->
                        val rebasedClone = treeWithMeta.rebaseClone(clone)
                        val bestAction = treeWithMeta.bestAction(rebasedClone)
                        val NDNOfPossibleParent =
                            bestAction.parentContent().fromRootToThis.NDNMutations.buildSequence(treeWithMeta.rootInfo)
                        val NDNOfRebasedClone =
                            rebasedClone.mutationsSet.NDNMutations.buildSequence(treeWithMeta.rootInfo)
                        val NDNDistance = scoringSet.NDNDistance(NDNOfPossibleParent, NDNOfRebasedClone)
                        //filter trees with too different NDN from the clone
                        if (NDNDistance <= parameters.maxNDNDistance) {
                            val distanceFromRoot = treeWithMeta.distanceFromRootToClone(rebasedClone)
                            bestAction to bestAction.changeOfDistance / distanceFromRoot
                        } else {
                            null
                        }
                    }
                    .minByOrNull { (_, metric) -> metric }
                if (bestActionAndDistanceFromRoot != null) {
                    val (bestAction, metric) = bestActionAndDistanceFromRoot
                    if (metric <= parameters.threshold) {
                        decisions[clone.cloneWrapper.id] = MetricDecisionInfo(metric)
                        bestAction.apply()
                    }
                }
            }
        }
        return buildStepResult(decisions, result)
    }

    private fun buildStepResult(
        decisions: Map<CloneWrapper.ID, TreeWithMetaBuilder.DecisionInfo>,
        trees: List<TreeWithMetaBuilder>
    ): StepResult = StepResult(
        decisions,
        trees.map { it.snapshot() },
        trees.flatMap { tree ->
            tree.allNodes()
                .asSequence()
                .filter { it.node.content is Reconstructed }
                .map { nodeWithParent -> buildDebugInfo(decisions, tree, nodeWithParent) }
        }
    )

    private fun buildDebugInfo(
        decisions: Map<CloneWrapper.ID, TreeWithMetaBuilder.DecisionInfo>,
        tree: TreeWithMetaBuilder,
        nodeWithParent: NodeWithParent<ObservedOrReconstructed<CloneWithMutationsFromReconstructedRoot, SyntheticNode>>
    ): DebugInfo {
        val nodeContent = nodeWithParent.node
            .content
            .convert({ null }, { it })!!
        val cloneId = nodeWithParent.node.links
            .map { it.node }
            .firstNotNullOfOrNull { child -> child.content.convert({ it.clone.id }, { null }) }
        val metric: Double? = when (cloneId) {
            null -> null
            else -> when (val decision = decisions[cloneId]) {
                is MetricDecisionInfo -> decision.metric
                is ZeroStepDecisionInfo -> decision.commonMutationsCount.toDouble()
                null -> null
            }
        }
        val parentMutations = nodeWithParent.parent
            ?.content?.convert({ null }, { it })
            ?.fromRootToThis
        return DebugInfo(
            tree.treeId,
            tree.rootInfo,
            nodeContent.fromRootToThis.mutations.V.mutationsOutsideOfCDR3.keys.map {
                tree.rootInfo.partitioning.V.getRange(it)
            },
            nodeContent.fromRootToThis.mutations.J.mutationsOutsideOfCDR3.keys.map {
                tree.rootInfo.partitioning.J.getRange(it)
            },
            cloneId,
            nodeWithParent.node.content.id,
            nodeWithParent.parent?.content?.id,
            nodeContent.fromRootToThis.NDNMutations.buildSequence(tree.rootInfo),
            nodeContent.fromRootToThis,
            parentMutations,
            metric
        )
    }

    private fun debugInfos(currentTrees: List<TreeWithMetaBuilder>): List<DebugInfo> = currentTrees.flatMap { tree ->
        tree.allNodes()
            .asSequence()
            .filter { it.node.content is Reconstructed }
            .map { nodeWithParent -> buildDebugInfo(emptyMap(), tree, nodeWithParent) }
    }

    @Suppress("UNCHECKED_CAST")
    fun makeDecision(chooses: Map<VJBase, TreeWithMetaBuilder.DecisionInfo>): VJBase {
        if (chooses.size == 1) {
            return chooses.keys.first()
        }
        val anyElement = chooses.values.first()
        //all values are from the same class
        require(chooses.values.all { anyElement.javaClass.isInstance(it) })
        return when (anyElement) {
            is ZeroStepDecisionInfo -> ZeroStepDecisionInfo.makeDecision(chooses as Map<VJBase, ZeroStepDecisionInfo>)
            is MetricDecisionInfo -> MetricDecisionInfo.makeDecision(chooses as Map<VJBase, MetricDecisionInfo>)
        }
    }

    private interface Step {
        fun next(
            originalTrees: List<TreeWithMetaBuilder>,
            clonesNotInClusters: () -> Sequence<CloneWithMutationsFromVJGermline>
        ): StepResult
    }

    class StepResult internal constructor(
        val decisions: Map<CloneWrapper.ID, TreeWithMetaBuilder.DecisionInfo>,
        val snapshots: List<TreeWithMetaBuilder.Snapshot>,
        val nodesDebugInfo: List<DebugInfo>
    ) {
        companion object {
            val empty = StepResult(emptyMap(), emptyList(), emptyList())
        }
    }

    @Serializable(by = Cluster.SerializerImpl::class)
    private class Cluster(
        val clones: List<CloneWrapper>
    ) {
        class SerializerImpl : BasicSerializer<Cluster>() {
            override fun write(output: PrimitivO, obj: Cluster) {
                output.writeCollection(obj.clones, PrimitivO::writeObject)
            }

            override fun read(input: PrimitivI): Cluster {
                val clones = input.readList<CloneWrapper>(PrimitivI::readObjectRequired)
                return Cluster(clones)
            }
        }
    }
}

