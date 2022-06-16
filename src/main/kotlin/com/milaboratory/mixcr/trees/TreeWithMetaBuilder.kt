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
import io.repseq.core.GeneType
import java.math.BigDecimal
import java.util.*

class TreeWithMetaBuilder(
    private val treeBuilder: TreeBuilderByAncestors<CloneWithMutationsFromReconstructedRoot, SyntheticNode, MutationsDescription>,
    val rootInfo: RootInfo,
    private val clonesRebase: ClonesRebase,
    val clonesAdditionHistory: LinkedList<CloneWrapper.ID>,
    val treeId: TreeId
) {
    fun copy(): TreeWithMetaBuilder {
        return TreeWithMetaBuilder(treeBuilder.copy(), rootInfo, clonesRebase, clonesAdditionHistory, treeId)
    }

    fun clonesCount(): Int {
        return clonesAdditionHistory.size
    }

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
        val oldestReconstructedAncestor = treeBuilder.tree.root
            .links[0]
            .node
            .content as Reconstructed
        return oldestReconstructedAncestor.content
    }

    fun buildResult(): Tree<CloneOrFoundAncestor> {
        val mostRecentCommonAncestor = mostRecentCommonAncestor()
        val rootAsNode = (treeBuilder.tree.root.content as Reconstructed).content
        return treeBuilder.tree
            .map { parent, node ->
                val mutationsSet = node.convert({ it.mutationsSet }, { it.fromRootToThis })
                val cloneWrapper = node.convert({ it.clone }) { null }

                val nodeAsMutationsFromGermline =
                    node.convert({ SyntheticNode.createFromMutations(it.mutationsSet) }) { it }
                val distanceFromReconstructedRootToNode = when (parent) {
                    null -> null
                    else -> treeBuilder.distance(
                        mostRecentCommonAncestor,
                        treeBuilder.mutationsBetween(
                            mostRecentCommonAncestor,
                            nodeAsMutationsFromGermline
                        )
                    )
                }
                val distanceFromGermlineToNode = treeBuilder.distance(
                    rootAsNode,
                    treeBuilder.mutationsBetween(
                        rootAsNode,
                        nodeAsMutationsFromGermline
                    )
                )

                CloneOrFoundAncestor(
                    node.id,
                    cloneWrapper?.clone,
                    cloneWrapper?.id?.datasetId,
                    mutationsSet,
                    distanceFromGermlineToNode,
                    distanceFromReconstructedRootToNode
                )
            }
    }

    fun allNodes(): Sequence<NodeWithParent<ObservedOrReconstructed<CloneWithMutationsFromReconstructedRoot, SyntheticNode>>> =
        treeBuilder.tree.allNodes()

    fun bestAction(rebasedClone: CloneWithMutationsFromReconstructedRoot): TreeBuilderByAncestors.Action {
        val bestAction = treeBuilder.bestActionForObserved(rebasedClone)
        return object : TreeBuilderByAncestors.Action() {
            override fun changeOfDistance(): BigDecimal = bestAction.changeOfDistance()

            override fun distanceFromObserved(): BigDecimal = bestAction.distanceFromObserved()

            override fun apply() {
                bestAction.apply()
                clonesAdditionHistory.add(rebasedClone.clone.id)
            }
        }
    }

    fun distanceFromRootToClone(rebasedClone: CloneWithMutationsFromReconstructedRoot): Double =
        treeBuilder.distanceFromRootToObserved(rebasedClone).toDouble()

    fun snapshot(): Snapshot = Snapshot(
        clonesAdditionHistory,
        rootInfo,
        treeId,
        mostRecentCommonAncestorNDN()
    )

    fun mostRecentCommonAncestorNDN() =
        mostRecentCommonAncestor().fromRootToThis.NDNMutations.mutations.mutate(rootInfo.reconstructedNDN)

    data class TreeId(
        val id: Int,
        private val VJBase: VJBase
    ) {
        fun encode(): String = "${VJBase.VGeneId.name}-${VJBase.CDR3length}-${VJBase.JGeneId.name}-${id}"
    }

    data class Snapshot(//TODO save position and action description to skip recalculation
        val clonesAdditionHistory: List<CloneWrapper.ID>,
        val rootInfo: RootInfo,
        val treeId: TreeId,
        val lastFoundNDN: NucleotideSequence,
        val dirty: Boolean = false
    ) {

        fun excludeClones(toExclude: Set<CloneWrapper.ID>): Snapshot {
            val newHistory = clonesAdditionHistory.filter { it !in toExclude }
            return copy(
                clonesAdditionHistory = newHistory,
                dirty = clonesAdditionHistory.size != newHistory.size
            )
        }
    }

    internal sealed interface DecisionInfo

    internal class ZeroStepDecisionInfo(
        val commonMutationsCount: Int,
        private val VGeneName: String,
        private val JGeneName: String,
        private val VHitScore: Float,
        private val JHitScore: Float
    ) : DecisionInfo {
        fun getGeneName(geneType: GeneType?): String {
            return when (geneType) {
                GeneType.Variable -> VGeneName
                GeneType.Joining -> JGeneName
                else -> throw IllegalArgumentException()
            }
        }

        fun getScore(geneType: GeneType?): Float {
            return when (geneType) {
                GeneType.Variable -> VHitScore
                GeneType.Joining -> JHitScore
                else -> throw IllegalArgumentException()
            }
        }
    }

    internal class MetricDecisionInfo(val metric: Double) : DecisionInfo
}
