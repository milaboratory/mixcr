package com.milaboratory.mixcr.trees

import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.trees.CloneOrFoundAncestor.CloneInfo
import com.milaboratory.mixcr.trees.Tree.NodeWithParent
import com.milaboratory.mixcr.trees.TreeBuilderByAncestors.ObservedOrReconstructed
import com.milaboratory.mixcr.trees.TreeBuilderByAncestors.Reconstructed
import io.repseq.core.GeneType
import java.math.BigDecimal
import java.util.*
import java.util.stream.Collectors
import java.util.stream.Stream

class TreeWithMetaBuilder(
    private val treeBuilder: TreeBuilderByAncestors<CloneWithMutationsFromReconstructedRoot, SyntheticNode, MutationsDescription>,
    val rootInfo: RootInfo,
    private val clonesRebase: ClonesRebase,
    val clonesAdditionHistory: LinkedList<Int>,
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
    fun getEffectiveParent(clone: Clone): SyntheticNode = treeBuilder.tree.allNodes()
        .filter { nodeWithParent ->
            nodeWithParent.node.links.stream()
                .map { link ->
                    link.node.content.convert(
                        { Optional.of(it.clone.clone.id) },
                        { Optional.empty() })
                }
                .flatMap { obj: Optional<Int> -> obj.stream() }
                .anyMatch { cloneId: Int -> clone.id == cloneId }
        }
        .map { it.parent }
        .findAny()
        .orElseThrow { IllegalStateException("clone not found in the tree") }!!
        .content.convert(
            { Optional.empty() },
            { Optional.of(it) })
        .orElseThrow { IllegalStateException() }

    fun rebaseClone(clone: CloneWithMutationsFromVJGermline): CloneWithMutationsFromReconstructedRoot {
        return clonesRebase.rebaseClone(rootInfo, clone.mutations, clone.cloneWrapper)
    }

    fun addClone(rebasedClone: CloneWithMutationsFromReconstructedRoot) {
        treeBuilder.addNode(rebasedClone)
        clonesAdditionHistory.add(rebasedClone.clone.clone.id)
    }

    fun oldestReconstructedAncestor(): SyntheticNode {
        //TODO check that there is only one direct child of the root
        val oldestReconstructedAncestor = treeBuilder.tree.root
            .links[0]
            .node
            .content as Reconstructed
        return oldestReconstructedAncestor.content
    }

    fun buildResult(): Tree<CloneOrFoundAncestor> {
        val reconstructedRoot = oldestReconstructedAncestor()
        val fromGermlineToReconstructedRoot = reconstructedRoot.fromRootToThis
        return treeBuilder.tree
            .map { parent, node ->
                val fromGermlineToParent = parent?.let { asMutations(it) }
                val nodeAsMutationsFromGermline = asMutations(node)
                val distanceFromReconstructedRootToNode = if (parent != null) {
                    treeBuilder.distance.apply(
                        reconstructedRoot,
                        MutationsUtils.mutationsBetween(
                            reconstructedRoot.fromRootToThis,
                            nodeAsMutationsFromGermline
                        )
                    )
                } else {
                    null
                }
                val rootAsNode = treeBuilder.tree.root.content
                    .convert(
                        { Optional.empty() },
                        { Optional.of(it) })
                    .orElseThrow()
                val distanceFromGermlineToNode = treeBuilder.distance.apply(rootAsNode, nodeAsMutationsFromGermline)
                node.convert(
                    { c ->
                        CloneInfo(
                            c.clone,
                            node.id,
                            nodeAsMutationsFromGermline,
                            fromGermlineToReconstructedRoot,
                            fromGermlineToParent,
                            distanceFromReconstructedRootToNode,
                            distanceFromGermlineToNode
                        )
                    }
                ) {
                    CloneOrFoundAncestor.AncestorInfo(
                        node.id,
                        nodeAsMutationsFromGermline,
                        fromGermlineToReconstructedRoot,
                        fromGermlineToParent,
                        distanceFromReconstructedRootToNode,
                        distanceFromGermlineToNode
                    )
                }
            }
    }

    private fun asMutations(parent: ObservedOrReconstructed<CloneWithMutationsFromReconstructedRoot, SyntheticNode>): MutationsDescription {
        return parent.convert({ it.mutationsFromRoot }) { it.fromRootToThis }
    }

    fun allNodes(): Stream<NodeWithParent<ObservedOrReconstructed<CloneWithMutationsFromReconstructedRoot, SyntheticNode>>> {
        return treeBuilder.tree.allNodes()
    }

    fun bestAction(rebasedClone: CloneWithMutationsFromReconstructedRoot): TreeBuilderByAncestors.Action {
        val bestAction = treeBuilder.bestActionForObserved(rebasedClone)
        return object : TreeBuilderByAncestors.Action() {
            override fun changeOfDistance(): BigDecimal = bestAction.changeOfDistance()

            override fun distanceFromObserved(): BigDecimal = bestAction.distanceFromObserved()

            override fun apply() {
                bestAction.apply()
                clonesAdditionHistory.add(rebasedClone.clone.clone.id)
            }
        }
    }

    fun distanceFromRootToClone(rebasedClone: CloneWithMutationsFromReconstructedRoot): Double =
        treeBuilder.distanceFromRootToObserved(rebasedClone).toDouble()

    fun snapshot(): Snapshot = Snapshot(clonesAdditionHistory, rootInfo, treeId)

    data class TreeId(
        val id: Int,
        private val VJBase: VJBase
    ) {
        fun encode(): String {
            val result = StringBuilder()
                .append(VJBase.VGeneName)
            if (VJBase.CDR3length != null) {
                result.append("-").append(VJBase.CDR3length)
            }
            result.append("-").append(VJBase.JGeneName)
                .append("-").append(id)
            return result.toString()
        }
    }

    class Snapshot(//TODO save position and action description to skip recalculation
        val clonesAdditionHistory: List<Int>,
        val rootInfo: RootInfo,
        val treeId: TreeId
    ) {

        fun excludeClones(toExclude: Set<Int?>): Snapshot {
            return Snapshot(
                clonesAdditionHistory.stream()
                    .filter { !toExclude.contains(it) }
                    .collect(
                        Collectors.toCollection { LinkedList() }
                    ),
                rootInfo,
                treeId
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
