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
package com.milaboratory.mixcr.trees

import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.trees.Tree.NodeWithParent
import com.milaboratory.mixcr.trees.TreeBuilderByAncestors.ObservedOrReconstructed
import com.milaboratory.mixcr.trees.TreeBuilderByAncestors.Reconstructed
import com.milaboratory.mixcr.util.plus
import io.repseq.core.GeneFeature
import io.repseq.core.GeneFeature.CDR3
import io.repseq.core.ReferencePoint.*
import java.math.BigDecimal
import java.util.*

class TreeWithMetaBuilder(
    private val treeBuilder: TreeBuilderByAncestors<CloneWithMutationsFromReconstructedRoot, SyntheticNode, NodeMutationsDescription>,
    val rootInfo: RootInfo,
    private val clonesRebase: ClonesRebase,
    private val clonesAdditionHistory: LinkedList<CloneWrapper.ID>,
    val treeId: TreeId
) {
    fun copy(): TreeWithMetaBuilder =
        TreeWithMetaBuilder(treeBuilder.copy(), rootInfo, clonesRebase, clonesAdditionHistory, treeId)

    fun clonesCount(): Int = clonesAdditionHistory.size

    /**
     * first parent is copy of clone, we need grandparent
     */
    fun getEffectiveParent(toSearch: CloneWrapper.ID): SyntheticNode = treeBuilder.tree.allNodes()
        .filter { nodeWithParent ->
            nodeWithParent.node.links
                .mapNotNull { link ->
                    link.node.content.convert(
                        { it.clone.id },
                        { null })
                }
                .any { cloneWrapperId -> toSearch == cloneWrapperId }
        }
        .map { it.parent }
        .filterNotNull()
        .first()
        .content.convert({ null }, { it })!!

    fun rebaseClone(clone: CloneWithMutationsFromVJGermline): CloneWithMutationsFromReconstructedRoot =
        clonesRebase.rebaseClone(rootInfo, clone.mutations, clone.cloneWrapper)

    fun addClone(rebasedClone: CloneWithMutationsFromReconstructedRoot) {
        treeBuilder.addNode(rebasedClone)
        clonesAdditionHistory.add(rebasedClone.clone.id)
    }

    fun mostRecentCommonAncestor(): SyntheticNode {
        //TODO check that there is only one direct child of the root
        return (treeBuilder.tree.root
            .links
            .map { it.node.content }
            .first { it is Reconstructed } as Reconstructed).content
    }

    fun buildResult(): Tree<CloneOrFoundAncestor> = treeBuilder.tree
        .map { _, node ->
            val mutationsSet = node.convert({ it.mutationsSet }, { it.fromRootToThis })
            val cloneWrapper = node.convert({ it.clone }) { null }

            if (cloneWrapper != null) {
                check(GeneFeature(FR1Begin, CDR3Begin) in mutationsSet.VMutations.mutations)
                check(
                    cloneWrapper.clone.getNFeature(GeneFeature(FR1Begin, CDR3Begin)) == MutationsUtils.buildSequence(
                        rootInfo.VSequence,
                        mutationsSet.VMutations.mutations[GeneFeature(FR1Begin, CDR3Begin)]!!,
                        rootInfo.VPartitioning.getRange(GeneFeature(FR1Begin, CDR3Begin))
                    )
                )
                val actualCDR3 = MutationsUtils.buildSequence(
                    rootInfo.VSequence,
                    mutationsSet.VMutations.partInCDR3.mutations,
                    mutationsSet.VMutations.partInCDR3.range
                ) + mutationsSet.NDNMutations.mutations.mutate(rootInfo.reconstructedNDN) +
                        MutationsUtils.buildSequence(
                            rootInfo.JSequence,
                            mutationsSet.JMutations.partInCDR3.mutations,
                            mutationsSet.JMutations.partInCDR3.range
                        )
                check(cloneWrapper.clone.getNFeature(CDR3) == actualCDR3)
                check(GeneFeature(CDR3End, FR4End) in mutationsSet.JMutations.mutations)
                check(
                    cloneWrapper.clone.getNFeature(GeneFeature(CDR3End, FR4End)) == MutationsUtils.buildSequence(
                        rootInfo.JSequence,
                        mutationsSet.JMutations.mutations[GeneFeature(CDR3End, FR4End)]!!,
                        rootInfo.JPartitioning.getRange(GeneFeature(CDR3End, FR4End))
                    )
                )
            }

            CloneOrFoundAncestor(
                node.id,
                cloneWrapper?.clone,
                cloneWrapper?.id?.datasetId,
                mutationsSet
            )
        }

    fun allNodes(): Sequence<NodeWithParent<ObservedOrReconstructed<CloneWithMutationsFromReconstructedRoot, SyntheticNode>>> =
        treeBuilder.tree.allNodes()

    fun bestAction(rebasedClone: CloneWithMutationsFromReconstructedRoot): TreeBuilderByAncestors.Action<SyntheticNode> {
        val bestAction = treeBuilder.bestActionForObserved(rebasedClone)
        return object : TreeBuilderByAncestors.Action<SyntheticNode>() {
            override fun changeOfDistance(): BigDecimal = bestAction.changeOfDistance()

            override fun distanceFromObserved(): BigDecimal = bestAction.distanceFromObserved()

            override fun apply() {
                bestAction.apply()
                clonesAdditionHistory.add(rebasedClone.clone.id)
            }

            override fun parentContent(): SyntheticNode = bestAction.parentContent()
        }
    }

    fun distanceFromRootToClone(rebasedClone: CloneWithMutationsFromReconstructedRoot): Double =
        treeBuilder.distanceFromRootToObserved(rebasedClone).toDouble()

    fun snapshot(): Snapshot = Snapshot(
        clonesAdditionHistory,
        rootInfo,
        treeId
    )

    fun mostRecentCommonAncestorNDN(): NucleotideSequence =
        mostRecentCommonAncestor().fromRootToThis.NDNMutations.mutations.mutate(rootInfo.reconstructedNDN)

    data class Snapshot(
        /**
         * Ids of clones that was added
         */
        val clonesAdditionHistory: List<CloneWrapper.ID>,
        val rootInfo: RootInfo,
        val treeId: TreeId
    ) {

        fun excludeClones(toExclude: Set<CloneWrapper.ID>): Snapshot {
            val newHistory = clonesAdditionHistory - toExclude
            return copy(
                clonesAdditionHistory = newHistory
            )
        }
    }

    internal sealed interface DecisionInfo

    internal class ZeroStepDecisionInfo(
        val commonMutationsCount: Int,
        /**
         * The same name for all alleles of this V gene
         */
        private val VGeneName: String,
        /**
         * The same name for all alleles of this J gene
         */
        private val JGeneName: String,
        private val VHitScore: Float,
        private val JHitScore: Float
    ) : DecisionInfo {
        companion object {
            fun makeDecision(chooses: Map<VJBase, ZeroStepDecisionInfo>): VJBase {
                //group by the same origin VJ pair - group decisions by related alleles
                //and choose VJ pair that close to germline
                val filteredByAlleles =
                    chooses.entries
                        .groupBy { (_, value) ->
                            value.VGeneName to value.JGeneName
                        }
                        .values
                        .mapNotNull { withTheSameGeneBase ->
                            withTheSameGeneBase.minByOrNull { (_, value) -> value.commonMutationsCount }
                        }
                        .map { (key, _) -> key }
                        .toSet()
                //for every VJ pair (already without allele variants) choose best score
                return filteredByAlleles
                    .maxByOrNull {
                        chooses[it]!!.VHitScore + chooses[it]!!.JHitScore
                    }!!
            }
        }
    }

    internal class MetricDecisionInfo(val metric: Double) : DecisionInfo {
        companion object {
            fun makeDecision(chooses: Map<VJBase, MetricDecisionInfo>): VJBase = chooses.entries
                .minByOrNull { (_, value): Map.Entry<VJBase, MetricDecisionInfo> -> value.metric }!!
                .key

        }
    }
}
