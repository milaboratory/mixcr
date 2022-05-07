package com.milaboratory.mixcr.trees

import com.milaboratory.mixcr.trees.Tree.NodeLink
import java.math.BigDecimal
import java.util.*
import java.util.function.BiFunction
import java.util.function.Function
import java.util.stream.Collectors
import java.util.stream.Stream

class TreeBuilderByAncestors<T, E, M> private constructor(
    val distance: BiFunction<E, M, BigDecimal>,
    /**
     * parent, observed -> reconstructed
     */
    private val asAncestor: Function<T, E>,
    private val mutationsBetween: BiFunction<E, E, M>,
    private val mutate: BiFunction<E, M, E>,
    private val findCommonMutations: BiFunction<M, M, M>,
    private val postprocessDescendants: BiFunction<E, E, E>,
    val tree: Tree<ObservedOrReconstructed<T, E>>,
    private val countOfNodesToProbe: Int,
    private var counter: Int
) {
    constructor(
        root: E,
        distance: BiFunction<E, M, BigDecimal>,
        mutationsBetween: BiFunction<E, E, M>,
        mutate: BiFunction<E, M, E>,
        asAncestor: Function<T, E>,
        findCommonMutations: BiFunction<M, M, M>,
        postprocessDescendants: BiFunction<E, E, E>,
        countOfNodesToProbe: Int
    ) : this(
        distance,
        asAncestor,
        mutationsBetween,
        mutate,
        findCommonMutations,
        postprocessDescendants,
        Tree<ObservedOrReconstructed<T, E>>(
            Tree.Node<ObservedOrReconstructed<T, E>>(
                Reconstructed<T, E>(
                    root,
                    BigDecimal.ZERO,
                    0
                )
            )
        ),
        countOfNodesToProbe,
        1
    ) {
    }

    fun copy(): TreeBuilderByAncestors<T, E, M> {
        return TreeBuilderByAncestors(
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
    }

    fun addNode(toAdd: T): TreeBuilderByAncestors<T, E, M> {
        val bestAction = bestActionForObserved(toAdd)
        bestAction.apply()
        return this
    }

    fun bestActionForObserved(toAdd: T): Action {
        val addedAsAncestor = asAncestor.apply(toAdd)
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
    ): Action {
        val nearestNodes = tree.allNodes()
            .filter { it.node.content is Reconstructed<*, *> }
            .sorted(Comparator.comparing { compareWith ->
                val reconstructed = compareWith.node.content as Reconstructed<T, E>
                val nodeContent = reconstructed.content
                distance.apply(
                    nodeContent,
                    mutationsBetween.apply(nodeContent, addedAsAncestor)
                ).add(reconstructed.minDistanceFromObserved)
            })
            .limit(countOfNodesToProbe.toLong())
            .collect(Collectors.toList())
        val possibleActions =
            nearestNodes.stream().flatMap { nodeWithParent ->
                val chosenNode = nodeWithParent.node
                //search for siblings with common mutations with the added node
                val siblingToMergeWith: Optional<Replace?> = chosenNode.links.stream()
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
                    .max(
                        Comparator.comparing<Replace, BigDecimal> { it.distanceFromParentToCommon }
                    )
                if (siblingToMergeWith.isPresent) {
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
                    return@flatMap siblingToMergeWith.stream()
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
                        Stream.of(
                            replaceChild(
                                nodeWithParent.parent,
                                chosenNode,
                                nodeWithParent.distance!!,
                                addedAsAncestor,
                                nodeGenerator
                            )
                        ).filter { Objects.nonNull(it) }
                    } else {
                        Stream.empty()
                    }
                    return@flatMap Stream.concat(
                        Stream.of(insertAsDirectDescendant(chosenNode, addedAsAncestor, nodeGenerator)),
                        insertAsParent
                    )
                }
            }
        //optimize sum of distances from observed nodes
        val temp = possibleActions.collect(Collectors.toList())
        return temp.stream().min(ACTION_COMPARATOR).orElseThrow { IllegalArgumentException() }!!
    }

    fun distanceFromRootToObserved(node: T): BigDecimal {
        val rootContent = (tree.root.content as Reconstructed<T, E>).content
        return distance.apply(
            rootContent,
            mutationsBetween.apply(rootContent, asAncestor.apply(node))
        )
    }

    private fun insertAsDirectDescendant(
        parent: Tree.Node<ObservedOrReconstructed<T, E>>,
        toAdd: E,
        nodeGenerator: (BigDecimal) -> Tree.Node<ObservedOrReconstructed<T, E>>
    ): Insert {
        val contentOfParent = (parent.content as Reconstructed<T, E>).content
        val distanceBetweenParentAndAdded =
            distance.apply(contentOfParent, mutationsBetween.apply(contentOfParent, toAdd))
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
        val mutationsToAdded = mutationsBetween.apply(parentContent, addedAsAncestor)
        val mutationsToChild = mutationsBetween.apply(parentContent, childContent)
        val commonMutations = findCommonMutations.apply(mutationsToAdded, mutationsToChild)
        val distanceFromParentToCommon = distance.apply(parentContent, commonMutations)
        //if distance is zero than there is no common mutations
        if (distanceFromParentToCommon.compareTo(BigDecimal.ZERO) == 0) {
            return null
        }
        val commonAncestor = mutate.apply(parentContent, commonMutations)
        val fromCommonToChild = mutationsBetween.apply(commonAncestor, childContent)
        val distanceFromCommonAncestorToChild = distance.apply(commonAncestor, fromCommonToChild)
        //if distance is zero than result of replacement equals to insertion
        if (distanceFromCommonAncestorToChild.compareTo(BigDecimal.ZERO) == 0) {
            return null
        }
        val distanceFromCommonToAdded =
            distance.apply(commonAncestor, mutationsBetween.apply(commonAncestor, addedAsAncestor))
        val minDistanceFromObserved = Stream.of(
            distanceFromCommonToAdded,
            distanceFromCommonAncestorToChild,
            parentAsReconstructed.minDistanceFromObserved.add(distanceFromParentToCommon)
        ).min { obj: BigDecimal?, `val`: BigDecimal? -> obj!!.compareTo(`val`) }.get()
        val replacement = Tree.Node<ObservedOrReconstructed<T, E>>(
            Reconstructed(
                commonAncestor,
                minDistanceFromObserved,
                ++counter
            )
        )
        val rebuiltChild = Tree.Node<ObservedOrReconstructed<T, E>>(
            Reconstructed(
                mutate.apply(commonAncestor, fromCommonToChild),
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

    abstract class Action {
        abstract fun changeOfDistance(): BigDecimal
        abstract fun distanceFromObserved(): BigDecimal
        abstract fun apply()
    }

    private inner class Insert(
        private val parent: Tree.Node<ObservedOrReconstructed<T, E>>,
        private val distanceFromParentAndInsertion: BigDecimal,
        private val nodeToInsert: Tree.Node<ObservedOrReconstructed<T, E>>
    ) : Action() {
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
    }

    private inner class Replace(
        private val parent: Tree.Node<ObservedOrReconstructed<T, E>>,
        private val replaceWhat: Tree.Node<ObservedOrReconstructed<T, E>>,
        private val replacement: Tree.Node<ObservedOrReconstructed<T, E>>,
        val distanceFromParentToCommon: BigDecimal,
        private val sumOfDistancesFromAncestor: BigDecimal,
        private val distanceFromParentToReplaceWhat: BigDecimal
    ) : Action() {
        override fun changeOfDistance(): BigDecimal {
            return distanceFromParentToCommon.subtract(distanceFromParentToReplaceWhat).add(sumOfDistancesFromAncestor)
        }

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

        private fun postprocess(parentToPostprocess: Tree.Node<ObservedOrReconstructed<T, E>>): Tree.Node<ObservedOrReconstructed<T, E>> {
            return Tree.Node(
                parentToPostprocess.content,
                postprocess(
                    (parentToPostprocess.content as Reconstructed<T, E>).content,
                    parentToPostprocess.links
                )
            )
        }

        private fun postprocess(
            parentContent: E, links: List<NodeLink<ObservedOrReconstructed<T, E>>>
        ): MutableList<NodeLink<ObservedOrReconstructed<T, E>>> {
            return links.stream()
                .map { link ->
                    val child = link.node
                    if (child.content is Reconstructed<*, *>) {
                        val childContent = child.content as Reconstructed<T, E>
                        val mapped = postprocessDescendants.apply(parentContent, childContent.content)
                        return@map NodeLink(
                            Tree.Node(
                                Reconstructed(
                                    mapped,  //TODO recalculate minDistanceFromObserved
                                    childContent.minDistanceFromObserved,
                                    ++counter
                                ),
                                postprocess(mapped, child.links)
                            ),
                            distance.apply(parentContent, mutationsBetween.apply(parentContent, mapped))
                        )
                    } else {
                        return@map link
                    }
                }
                .collect(Collectors.toList())
        }
    }

    sealed class ObservedOrReconstructed<T, E> constructor(val id: Int) {
        fun <R1, R2> map(
            mapObserved: Function<T, R1>,
            mapReconstructed: Function<E, R2>
        ): ObservedOrReconstructed<R1, R2> {
            return when (this) {
                is Observed -> Observed(mapObserved.apply(content), id)
                is Reconstructed -> Reconstructed(mapReconstructed.apply(content), minDistanceFromObserved, id)
            }
        }

        fun <R> convert(mapObserved: (T) -> R, mapReconstructed: (E) -> R): R = when (this) {
            is Observed -> mapObserved(this.content)
            is Reconstructed -> mapReconstructed(this.content)
        }
    }

    internal class Observed<T, E>(val content: T, id: Int) : ObservedOrReconstructed<T, E>(id)
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
        var minDistanceFromObserved: BigDecimal, id: Int
    ) : ObservedOrReconstructed<T, E>(id)

    companion object {
        val ACTION_COMPARATOR = Comparator
            .comparing { action: Action ->
                action.changeOfDistance()
                    .add(action.distanceFromObserved())
            }
            .thenComparing { obj: Action -> obj.changeOfDistance() }
            .thenComparing { action: Action -> if (action is TreeBuilderByAncestors<*, *, *>.Insert) 1 else -1 }
    }
}
