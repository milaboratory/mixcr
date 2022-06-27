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

import com.milaboratory.mixcr.trees.Tree.NodeLink
import java.math.BigDecimal
import java.util.*

class TreeBuilderByAncestors<T, E, M> private constructor(
    private val distance: (E, M) -> BigDecimal,
    private val asAncestor: (T) -> E,
    private val mutationsBetween: (E, E) -> M,
    private val mutate: (E, M) -> E,
    private val findCommonMutations: (M, M) -> M,
    private val postprocessDescendants: (E, E) -> E,
    val tree: Tree<ObservedOrReconstructed<T, E>>,
    private val countOfNodesToProbe: Int,
    private var counter: Int
) {
    constructor(
        root: E,
        distance: (E, M) -> BigDecimal,
        mutationsBetween: (E, E) -> M,
        mutate: (E, M) -> E,
        asAncestor: (T) -> E,
        findCommonMutations: (M, M) -> M,
        postprocessDescendants: (E, E) -> E,
        countOfNodesToProbe: Int
    ) : this(
        distance,
        asAncestor,
        mutationsBetween,
        mutate,
        findCommonMutations,
        postprocessDescendants,
        Tree(Tree.Node(Reconstructed(root, BigDecimal.ZERO, 0))),
        countOfNodesToProbe,
        1
    )

    fun copy(): TreeBuilderByAncestors<T, E, M> = TreeBuilderByAncestors(
        distance,
        asAncestor,
        mutationsBetween,
        mutate,
        findCommonMutations,
        postprocessDescendants,
        tree.copy(),
        countOfNodesToProbe,
        counter
    )

    fun addNode(toAdd: T): TreeBuilderByAncestors<T, E, M> {
        val bestAction = bestActionForObserved(toAdd)
        bestAction.apply()
        return this
    }

    fun bestActionForObserved(toAdd: T): Action<E> {
        val addedAsAncestor = asAncestor(toAdd)
        return bestAction(addedAsAncestor) { distanceFromParent: BigDecimal ->
            val nodeToAdd: Tree.Node<ObservedOrReconstructed<T, E>>
            if (distanceFromParent.compareTo(BigDecimal.ZERO) == 0) {
                nodeToAdd = Tree.Node(Observed(toAdd, ++counter))
            } else {
                nodeToAdd = Tree.Node(Reconstructed(addedAsAncestor, BigDecimal.ZERO, ++counter))
                nodeToAdd.addChild(Tree.Node(Observed(toAdd, ++counter)), BigDecimal.ZERO)
            }
            nodeToAdd
        }
    }

    /**
     * Assumptions:
     * 1. Nodes must be added in order by distance from the root
     * 2. findCommonAncestor is constructing ancestor based on root (left common mutations on nodes from root)
     *
     *
     *
     *
     * Invariants of the tree:
     * 1. Root node is always reconstructed. Root is not changing
     * 2. Leaves are always observed.
     * 3. Distance between observed and reconstructed could be zero (they are identical).
     * 4. Distance between observed can't be zero.
     * 5. Siblings have no common ancestors.
     */
    private fun bestAction(
        addedAsAncestor: E,
        nodeGenerator: (BigDecimal) -> Tree.Node<ObservedOrReconstructed<T, E>>
    ): Action<E> {
        val nearestNodes = tree.allNodes()
            .filter { it.node.content is Reconstructed<*, *> }
            .sortedWith(Comparator.comparing { compareWith ->
                val reconstructed = compareWith.node.content as Reconstructed<T, E>
                val nodeContent = reconstructed.content
                distance(
                    nodeContent,
                    mutationsBetween(nodeContent, addedAsAncestor)
                ).add(reconstructed.minDistanceFromObserved)
            })
            .take(countOfNodesToProbe)
            .toList()
        val possibleActions = nearestNodes.flatMap { nodeWithParent ->
            val chosenNode = nodeWithParent.node
            //search for siblings with common mutations with the added node
            val siblingToMergeWith: Replace? = chosenNode.links
                .filter { it.node.content is Reconstructed<*, *> }
                .map { link ->
                    replaceChild(
                        chosenNode,
                        link.node,
                        link.distance,
                        addedAsAncestor,
                        nodeGenerator
                    )
                }
                .filter { Objects.nonNull(it) } //choose a sibling with max score of common mutations
                .maxWithOrNull(
                    Comparator.comparing<Replace, BigDecimal> { it.distanceFromParentToCommon }
                )
            if (siblingToMergeWith != null) {
                //the added node (A), the chosen node (B), the sibling (D) has common ancestor (C) with A
                //
                //       P                   P
                //       |                   |
                //     --*--               --*--
                //     |   |               |   |
                //     K   B      ==>      K   B
                //         |                   |
                //       --*--               --*--
                //       |   |               |   |
                //       R   D               R   C
                //                               |
                //                             --*--
                //                             |   |
                //                             A   D
                //
                // R and C has no common ancestors because R and D haven't one
                return@flatMap listOf(siblingToMergeWith)
            } else {
                //the added node (A), the chosen node (B), no siblings with common ancestors
                //
                //       P                   P
                //       |                   |
                //     --*--               --*--
                //     |   |               |   |
                //     K   B      ==>      K   B
                //         |                   |
                //       --*--               --*-----
                //       |   |               |   |  |
                //       R   T               R   T  A
                val insertAsParent = if (nodeWithParent.parent != null) {
                    replaceChild(
                        nodeWithParent.parent,
                        chosenNode,
                        nodeWithParent.distance!!,
                        addedAsAncestor,
                        nodeGenerator
                    )
                } else {
                    null
                }
                return@flatMap listOfNotNull(
                    insertAsDirectDescendant(chosenNode, addedAsAncestor, nodeGenerator),
                    insertAsParent
                )
            }
        }
        //optimize sum of distances from observed nodes
        return possibleActions.minWithOrNull(ACTION_COMPARATOR)!!
    }

    fun distanceFromRootToObserved(node: T): BigDecimal {
        val rootContent = (tree.root.content as Reconstructed<T, E>).content
        return distance(
            rootContent,
            mutationsBetween(rootContent, asAncestor(node))
        )
    }

    private fun insertAsDirectDescendant(
        parent: Tree.Node<ObservedOrReconstructed<T, E>>,
        toAdd: E,
        nodeGenerator: (BigDecimal) -> Tree.Node<ObservedOrReconstructed<T, E>>
    ): Insert {
        val contentOfParent = (parent.content as Reconstructed<T, E>).content
        val distanceBetweenParentAndAdded =
            distance(contentOfParent, mutationsBetween(contentOfParent, toAdd))
        val nodeToInsert = nodeGenerator(distanceBetweenParentAndAdded)
        return Insert(
            parent,
            distanceBetweenParentAndAdded,
            nodeToInsert
        )
    }

    /**
     * Replacing node with subtree with common ancestor and leaves as added node and node that was here before
     *
     * <pre>
     * P                   P
     * |                   |
     * --*--               --*--
     * |   |    ==>        |   |
     * R   B               R   C
     * |
     * --*--
     * |   |
     * A   B
    </pre> *
     * P - parent
     * A - toAdd
     * B - child
     * C - commonAncestor
     */
    private fun replaceChild(
        parent: Tree.Node<ObservedOrReconstructed<T, E>>,
        child: Tree.Node<ObservedOrReconstructed<T, E>>,
        distanceFromParentToChild: BigDecimal,
        addedAsAncestor: E,
        nodeGenerator: (BigDecimal) -> Tree.Node<ObservedOrReconstructed<T, E>>
    ): Replace? {
        val parentAsReconstructed = parent.content as Reconstructed<T, E>
        val parentContent = parentAsReconstructed.content
        val childContent = (child.content as Reconstructed<T, E>).content
        val mutationsToAdded = mutationsBetween(parentContent, addedAsAncestor)
        val mutationsToChild = mutationsBetween(parentContent, childContent)
        val commonMutations = findCommonMutations(mutationsToAdded, mutationsToChild)
        val distanceFromParentToCommon = distance(parentContent, commonMutations)
        //if distance is zero than there is no common mutations
        if (distanceFromParentToCommon.compareTo(BigDecimal.ZERO) == 0) {
            return null
        }
        val commonAncestor = mutate(parentContent, commonMutations)
        val fromCommonToChild = mutationsBetween(commonAncestor, childContent)
        val distanceFromCommonAncestorToChild = distance(commonAncestor, fromCommonToChild)
        //if distance is zero than result of replacement equals to insertion
        if (distanceFromCommonAncestorToChild.compareTo(BigDecimal.ZERO) == 0) {
            return null
        }
        val distanceFromCommonToAdded = distance(commonAncestor, mutationsBetween(commonAncestor, addedAsAncestor))
        val minDistanceFromObserved = minOf(
            distanceFromCommonToAdded,
            distanceFromCommonAncestorToChild,
            parentAsReconstructed.minDistanceFromObserved.add(distanceFromParentToCommon)
        )
        val replacement = Tree.Node<ObservedOrReconstructed<T, E>>(
            Reconstructed(
                commonAncestor,
                minDistanceFromObserved,
                ++counter
            )
        )
        val rebuiltChild = Tree.Node<ObservedOrReconstructed<T, E>>(
            Reconstructed(
                mutate(commonAncestor, fromCommonToChild),
                child.content.minDistanceFromObserved,
                ++counter
            )
        )
        child.links.forEach { link ->
            rebuiltChild.addChild(
                link.node,
                link.distance
            )
        }
        replacement.addChild(rebuiltChild, distanceFromCommonAncestorToChild)
        replacement.addChild(nodeGenerator(distanceFromCommonToAdded), distanceFromCommonToAdded)
        return Replace(
            parent,
            child,
            replacement,
            distanceFromParentToCommon,
            distanceFromCommonAncestorToChild.add(distanceFromCommonToAdded),
            distanceFromParentToChild
        )
    }

    abstract class Action<E> {
        abstract fun changeOfDistance(): BigDecimal
        abstract fun distanceFromObserved(): BigDecimal
        abstract fun apply()

        abstract fun parentContent(): E
    }

    private inner class Insert(
        private val parent: Tree.Node<ObservedOrReconstructed<T, E>>,
        private val distanceFromParentAndInsertion: BigDecimal,
        private val nodeToInsert: Tree.Node<ObservedOrReconstructed<T, E>>
    ) : Action<E>() {
        override fun changeOfDistance(): BigDecimal = distanceFromParentAndInsertion

        override fun distanceFromObserved(): BigDecimal =
            (parent.content as Reconstructed<T, E>).minDistanceFromObserved

        override fun apply() {
            val parentAsReconstructed = parent.content as Reconstructed<T, E>
            if (distanceFromParentAndInsertion.compareTo(BigDecimal.ZERO) == 0) {
                parentAsReconstructed.minDistanceFromObserved = BigDecimal.ZERO
            }
            parent.addChild(nodeToInsert, distanceFromParentAndInsertion)
        }

        override fun parentContent(): E = (parent.content as Reconstructed<T, E>).content
    }

    private inner class Replace(
        private val parent: Tree.Node<ObservedOrReconstructed<T, E>>,
        private val replaceWhat: Tree.Node<ObservedOrReconstructed<T, E>>,
        private val replacement: Tree.Node<ObservedOrReconstructed<T, E>>,
        val distanceFromParentToCommon: BigDecimal,
        private val sumOfDistancesFromAncestor: BigDecimal,
        private val distanceFromParentToReplaceWhat: BigDecimal
    ) : Action<E>() {
        override fun changeOfDistance(): BigDecimal =
            distanceFromParentToCommon - distanceFromParentToReplaceWhat + sumOfDistancesFromAncestor

        override fun distanceFromObserved(): BigDecimal {
            return (parent.content as Reconstructed<T, E>).minDistanceFromObserved
        }

        override fun apply() {
            parent.replaceChild(
                replaceWhat,
                postprocess(replacement),
                distanceFromParentToCommon
            )
        }

        override fun parentContent(): E = (parent.content as Reconstructed<T, E>).content

        private fun postprocess(parentToPostprocess: Tree.Node<ObservedOrReconstructed<T, E>>): Tree.Node<ObservedOrReconstructed<T, E>> =
            Tree.Node(
                parentToPostprocess.content,
                postprocess(
                    (parentToPostprocess.content as Reconstructed<T, E>).content,
                    parentToPostprocess.links
                )
            )

        private fun postprocess(
            parentContent: E, links: List<NodeLink<ObservedOrReconstructed<T, E>>>
        ): List<NodeLink<ObservedOrReconstructed<T, E>>> = links
            .flatMap { link ->
                val child = link.node
                when (child.content) {
                    is Reconstructed<*, *> -> {
                        val childContent = child.content as Reconstructed<T, E>
                        val mapped = postprocessDescendants(parentContent, childContent.content)
                        val newDistance = distance(parentContent, mutationsBetween(parentContent, mapped))
                        if (newDistance.compareTo(BigDecimal.ZERO) == 0) {
                            //actually parent equals mapped result.
                            //in this case we should remove this node from tree by skipping it and inserting its children directly
                            postprocess(parentContent, child.links)
                        } else {
                            val result = NodeLink(
                                Tree.Node(
                                    Reconstructed(
                                        mapped,  //TODO recalculate minDistanceFromObserved
                                        childContent.minDistanceFromObserved,
                                        ++counter
                                    ),
                                    postprocess(mapped, child.links)
                                ),
                                newDistance
                            )
                            listOf(result)
                        }
                    }
                    else -> listOf(link)
                }
            }
    }

    sealed class ObservedOrReconstructed<T, E> constructor(val id: Int) {
        fun <R1, R2> map(
            mapObserved: (T) -> R1,
            mapReconstructed: (E) -> R2
        ): ObservedOrReconstructed<R1, R2> = when (this) {
            is Observed -> Observed(mapObserved(content), id)
            is Reconstructed -> Reconstructed(mapReconstructed(content), minDistanceFromObserved, id)
        }

        fun <R> convert(mapObserved: (T) -> R, mapReconstructed: (E) -> R): R = when (this) {
            is Observed -> mapObserved(content)
            is Reconstructed -> mapReconstructed(content)
        }
    }

    internal class Observed<T, E>(
        val content: T,
        id: Int
    ) : ObservedOrReconstructed<T, E>(id)

    internal class Reconstructed<T, E>(
        val content: E,
        /**
         * This number represents distance that will be added after removing of all reconstructed nodes from the tree.
         *
         *
         * zero for root
         * zero if had an observed child with distance zero
         * otherwise it is a distance from the nearest parent with minDistanceFromObserved equals zero
         */
        var minDistanceFromObserved: BigDecimal,
        id: Int
    ) : ObservedOrReconstructed<T, E>(id)

    companion object {
        val ACTION_COMPARATOR: Comparator<Action<*>> = Comparator
            .comparing<Action<*>, BigDecimal> { action ->
                action.changeOfDistance()
                    .add(action.distanceFromObserved())
            }
            .thenComparing { action -> action.changeOfDistance() }
            .thenComparing { action -> if (action is TreeBuilderByAncestors<*, *, *>.Insert) 1 else -1 }
    }
}
