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

import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NucleotideSequence
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
import io.repseq.core.GeneFeature
import io.repseq.core.GeneType.Joining
import io.repseq.core.GeneType.Variable
import io.repseq.core.VDJCGeneId
import java.util.*
import java.util.function.Supplier
import kotlin.math.min

internal class SHMTreeBuilderBySteps(
    private val parameters: SHMTreeBuilderParameters,
    private val scoringSet: ScoringSet,
    private val assemblingFeatures: Array<GeneFeature>,
    private val shmTreeBuilder: SHMTreeBuilder
) {
    fun applyStep(
        stepName: BuildSHMTreeStep,
        currentTrees: List<TreeWithMetaBuilder>,
        allClonesInTress: Set<CloneWrapper.ID>,
        clones: List<CloneWrapper>
    ): StepResult = stepByName(stepName).next(currentTrees) {
        clones.asSequence()
            .filter { it.id !in allClonesInTress }
            .map { cloneWrapper -> cloneWrapper.rebaseFromGermline(assemblingFeatures) }
    }

    private fun stepByName(stepName: BuildSHMTreeStep): Step = when (stepName) {
        AttachClonesByDistanceChange -> object : Step {
            override fun next(
                originalTrees: List<TreeWithMetaBuilder>,
                clonesNotInClusters: () -> Sequence<CloneWithMutationsFromVJGermline>
            ): StepResult = attachClonesByDistanceChange(
                originalTrees,
                clonesNotInClusters
            )
        }
        CombineTrees -> object : Step {
            override fun next(
                originalTrees: List<TreeWithMetaBuilder>,
                clonesNotInClusters: () -> Sequence<CloneWithMutationsFromVJGermline>
            ): StepResult = combineTrees(originalTrees)
        }
        AttachClonesByNDN -> object : Step {
            override fun next(
                originalTrees: List<TreeWithMetaBuilder>,
                clonesNotInClusters: () -> Sequence<CloneWithMutationsFromVJGermline>
            ): StepResult = attachClonesByNDN(
                originalTrees,
                clonesNotInClusters
            )
        }
        BuildingInitialTrees -> throw IllegalArgumentException()
    }

    /**
     * Build clusters of clones that definitely in one clonal group.
     * Then build trees from clusters.
     *
     * See actual algorithm for forming clusters in link
     * @see ClustersBuilder.buildClusters
     */
    fun buildTreeTopParts(
        relatedAllelesMutations: Map<VDJCGeneId, List<Mutations<NucleotideSequence>>>,
        clones: List<CloneWrapper>
    ): StepResult {
        val clusterizationAlgorithm = parameters.initialClusterization
        //use only clones that are at long distance from any germline
        val clonesThatNotCloseToAnyGermline = clones.asSequence()
            .filter { !hasVJPairThatCloseToGermline(it, clusterizationAlgorithm.commonMutationsCountForClustering) }
        val rebasedClones = clonesThatNotCloseToAnyGermline
            .map { cloneWrapper -> cloneWrapper.rebaseFromGermline(assemblingFeatures) }
            .filter { it.mutations.VJMutationsCount >= clusterizationAlgorithm.commonMutationsCountForClustering }
            .toList()
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
            is SHMTreeBuilderParameters.ClusterizationAlgorithm.Hierarchical ->
                ClustersBuilder.Hierarchical(clusterPredictor) { cloneWrapper.id.datasetId }
        }
        val clusters = clustersBuilder.buildClusters(rebasedClones)
        val result = clusters
            .asSequence()
            .filter { it.size > 1 }
            .filter {
                //skip cluster if it formed by the same clones but with different C or from different samples
                val toCompare = it.first()
                it.subList(1, it.size).any { clone ->
                    !Arrays.equals(clone.cloneWrapper.clone.targets, toCompare.cloneWrapper.clone.targets)
                }
            }
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

    private fun hasVJPairThatCloseToGermline(cloneWrapper: CloneWrapper, threashold: Int): Boolean =
        cloneWrapper.candidateVJBases
            .map { VJBase ->
                cloneWrapper.clone.getHit(VJBase, Variable).mutationsCount() +
                        cloneWrapper.clone.getHit(VJBase, Joining).mutationsCount()
            }
            .any { it < threashold }

    private fun VDJCHit.mutationsCount(): Int = alignments.sumOf { it.absoluteMutations.size() }

    fun restore(snapshot: TreeWithMetaBuilder.Snapshot, clones: List<CloneWrapper>): TreeWithMetaBuilder {
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
    private fun combineTrees(originalTrees: List<TreeWithMetaBuilder>): StepResult {
        val result = mutableListOf<TreeWithMetaBuilder>()
        val originalTreesCopy = originalTrees
            .sortedByDescending { it.clonesCount() }
            .toMutableList()
        //trying to grow the biggest trees first
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
                if (metric <= parameters.thresholdForCombineTrees) {
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
        clonesNotInClusters: Supplier<Sequence<CloneWithMutationsFromVJGermline>>
    ): StepResult {
        val decisions = mutableMapOf<CloneWrapper.ID, TreeWithMetaBuilder.DecisionInfo>()
        val resultTrees = originalTrees.map { it.copy() }
        clonesNotInClusters.get()
            .filter { clone -> clone.mutations.VJMutationsCount < parameters.initialClusterization.commonMutationsCountForClustering }
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
                    if (metric <= parameters.thresholdForCombineByNDN) {
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
        clonesNotInClustersSupplier: Supplier<Sequence<CloneWithMutationsFromVJGermline>>
    ): StepResult {
        val result = originalTrees.map { it.copy() }
        val decisions = mutableMapOf<CloneWrapper.ID, TreeWithMetaBuilder.DecisionInfo>()
        val clonesNotInClusters = clonesNotInClustersSupplier.get()
            .sortedByDescending { it.mutations.VJMutationsCount }
        //try to add clones as nodes, more mutated first, so we build a tree from top to bottom
        clonesNotInClusters.forEach { clone ->
            if (clone.mutations.VJMutationsCount >= parameters.initialClusterization.commonMutationsCountForClustering) {
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
                        when {
                            //filter trees with too different NDN from the clone
                            NDNDistance > parameters.maxNDNDistanceForFreeClones -> null
                            else -> bestAction to treeWithMeta.distanceFromRootToClone(rebasedClone)
                        }
                    }
                    .minByOrNull { p -> p.first.changeOfDistance() }
                if (bestActionAndDistanceFromRoot != null) {
                    val bestAction = bestActionAndDistanceFromRoot.first
                    val distanceFromRoot = bestActionAndDistanceFromRoot.second
                    val metric = bestAction.changeOfDistance() / distanceFromRoot
                    if (metric <= parameters.thresholdForFreeClones) {
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
        trees.flatMap { tree: TreeWithMetaBuilder ->
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
        var metric: Double? = null
        if (cloneId != null) {
            val decision = decisions[cloneId]
            if (decision is MetricDecisionInfo) {
                metric = decision.metric
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
            metric,
            isPublic(tree.rootInfo)
        )
    }

    private fun isPublic(rootInfo: RootInfo): Boolean =
        rootInfo.reconstructedNDN.size() <= parameters.NDNSizeLimitForPublicClones

    fun debugInfos(currentTrees: List<TreeWithMetaBuilder>): List<DebugInfo> = currentTrees.flatMap { tree ->
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
    )
}

