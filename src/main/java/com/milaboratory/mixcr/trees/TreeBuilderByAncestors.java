package com.milaboratory.mixcr.trees;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Comparator.comparing;

public class TreeBuilderByAncestors<T, E, M> {
    private final Function<M, BigDecimal> distance;
    private final Function<T, E> asAncestor;
    private final BiFunction<E, E, M> mutationsBetween;
    private final BiFunction<E, M, E> mutate;
    private final BiFunction<M, M, M> findCommonMutations;

    private final Tree<ObservedOrReconstructed<T, E>> tree;

    public TreeBuilderByAncestors(
            E root,
            Function<M, BigDecimal> distance,
            BiFunction<E, E, M> mutationsBetween,
            BiFunction<E, M, E> mutate,
            Function<T, E> asAncestor,
            BiFunction<M, M, M> findCommonMutations
    ) {
        this.distance = distance;
        this.mutationsBetween = mutationsBetween;
        this.mutate = mutate;
        this.asAncestor = asAncestor;
        this.findCommonMutations = findCommonMutations;
        tree = new Tree<>(new Tree.Node<>(new Reconstructed<>(root, BigDecimal.ZERO)));
    }

    /**
     * Assumptions:
     * 1. Nodes must be added in order by distance from the root
     * 2. findCommonAncestor is constructing ancestor based on root (left common mutations on nodes from root)
     * <p>
     * <p>
     * Invariants of the tree:
     * 1. Root node is always reconstructed. Root is not changing
     * 2. Leaves are always observed.
     * 3. Distance between observed and reconstructed could be zero (they are identical).
     * 4. Distance between observed can't be zero.
     * 5. Siblings have no common ancestors.
     */
    public TreeBuilderByAncestors<T, E, M> addNode(T toAdd) {
        E contentAsAncestor = asAncestor.apply(toAdd);
        List<Tree.Node<ObservedOrReconstructed<T, E>>> nearestNodes = tree.allNodes()
                .filter(it -> it.getContent() instanceof Reconstructed<?, ?>)
                .collect(Collectors.groupingBy(compareWith ->
                        distance.apply(mutationsBetween.apply(((Reconstructed<T, E>) compareWith.getContent()).getContent(), contentAsAncestor))
                ))
                .entrySet().stream()
                .min(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .orElseThrow(IllegalArgumentException::new);
        Stream<Action> possibleActions = nearestNodes.stream().map(chosenNode -> {
            //search for siblings with common mutations with the added node
            Optional<Replace> siblingToMergeWith = chosenNode.getLinks().stream()
                    .map(Tree.NodeLink::getNode)
                    .filter(it -> it.getContent() instanceof Reconstructed<?, ?>)
                    .map(sibling -> replaceSibling(toAdd, chosenNode, sibling))
                    //if distance is zero than there is no common mutations
                    .filter(it -> it.getDistanceFromParentToCommon().compareTo(BigDecimal.ZERO) != 0)
                    //choose a sibling with max score of common mutations
                    .max(comparing(Replace::getDistanceFromParentToCommon));
            if (siblingToMergeWith.isPresent()) {
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
                return siblingToMergeWith.get();
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
                return insertAsDirectDescendant(toAdd, chosenNode);
            }
        });
        //optimize sum of distances from observed nodes
        Optional<Action> bestAction = possibleActions.min(comparing(action -> action.changeOfDistance().add(action.distanceFromObserved())));
        bestAction.orElseThrow(IllegalArgumentException::new).apply();
        return this;
    }

    private Insert insertAsDirectDescendant(T toAdd, Tree.Node<ObservedOrReconstructed<T, E>> parent) {
        E contentAsAncestor = asAncestor.apply(toAdd);
        E contentOfParent = ((Reconstructed<T, E>) parent.getContent()).getContent();
        BigDecimal distanceBetweenParentAndAdded = distance.apply(mutationsBetween.apply(contentOfParent, contentAsAncestor));
        Tree.Node<ObservedOrReconstructed<T, E>> nodeToInsert;
        if (distanceBetweenParentAndAdded.compareTo(BigDecimal.ZERO) == 0) {
            nodeToInsert = new Tree.Node<>(new Observed<>(toAdd));
        } else {
            nodeToInsert = new Tree.Node<>(new Reconstructed<>(asAncestor.apply(toAdd), BigDecimal.ZERO));
            nodeToInsert.addChild(new Tree.Node<>(new Observed<>(toAdd)), BigDecimal.ZERO);
        }
        return new Insert(
                parent,
                nodeToInsert,
                distanceBetweenParentAndAdded
        );
    }

    /**
     * Replacing node with subtree with common ancestor and leaves as added node and node that was here before
     *
     * <pre>
     *              P                   P
     *              |                   |
     *            --*--               --*--
     *            |   |    ==>        |   |
     *            R   B               R   C
     *                                    |
     *                                  --*--
     *                                  |   |
     *                                  A   B
     * </pre>
     * P - parent
     * A - toAdd
     * B - sibling
     * C - commonAncestor
     */
    private Replace replaceSibling(T toAdd, Tree.Node<ObservedOrReconstructed<T, E>> parent, Tree.Node<ObservedOrReconstructed<T, E>> sibling) {
        E contentOfAdded = asAncestor.apply(toAdd);
        Reconstructed<T, E> parentContent = (Reconstructed<T, E>) parent.getContent();

        M mutationsToAdded = mutationsBetween.apply(parentContent.getContent(), contentOfAdded);
        M mutationsToSibling = mutationsBetween.apply(parentContent.getContent(), ((Reconstructed<T, E>) sibling.getContent()).getContent());

        M commonMutations = findCommonMutations.apply(mutationsToAdded, mutationsToSibling);
        E commonAncestor = mutate.apply(parentContent.getContent(), commonMutations);
        Tree.Node<ObservedOrReconstructed<T, E>> replacement = new Tree.Node<>(new Reconstructed<>(
                commonAncestor,
                parentContent.minDistanceFromObserved.add(sibling.getDistanceFromParent())
        ));
        replacement.addChild(sibling.copy(), distance.apply(mutationsBetween.apply(commonAncestor, ((Reconstructed<T, E>) sibling.getContent()).getContent())));

        BigDecimal distanceBetweenCommonAndAdded = distance.apply(mutationsBetween.apply(commonAncestor, contentOfAdded));
        Tree.Node<ObservedOrReconstructed<T, E>> nodeToAdd;
        if (distanceBetweenCommonAndAdded.compareTo(BigDecimal.ZERO) != 0) {
            nodeToAdd = new Tree.Node<>(new Reconstructed<>(contentOfAdded, BigDecimal.ZERO));
            nodeToAdd.addChild(new Tree.Node<>(new Observed<>(toAdd)), BigDecimal.ZERO);
        } else {
            nodeToAdd = new Tree.Node<>(new Observed<>(toAdd));
        }
        replacement.addChild(nodeToAdd, distanceBetweenCommonAndAdded);

        BigDecimal distanceFromParentToCommon = distance.apply(commonMutations);
        return new Replace(sibling, replacement, distanceFromParentToCommon, parentContent.minDistanceFromObserved);
    }

    public Tree<ObservedOrReconstructed<T, E>> getTree() {
        return tree;
    }

    private static abstract class Action {
        abstract BigDecimal changeOfDistance();

        abstract BigDecimal distanceFromObserved();

        abstract void apply();
    }

    private class Insert extends Action {
        private final Tree.Node<ObservedOrReconstructed<T, E>> addTo;
        private final Tree.Node<ObservedOrReconstructed<T, E>> insertion;
        private final BigDecimal distance;

        public Insert(Tree.Node<ObservedOrReconstructed<T, E>> addTo, Tree.Node<ObservedOrReconstructed<T, E>> insertion, BigDecimal distance) {
            this.addTo = addTo;
            this.insertion = insertion;
            this.distance = distance;
        }

        @Override
        BigDecimal changeOfDistance() {
            return distance;
        }

        @Override
        BigDecimal distanceFromObserved() {
            return ((Reconstructed<T, E>) addTo.getContent()).minDistanceFromObserved;
        }

        @Override
        void apply() {
            addTo.addChild(insertion, distance);
        }
    }

    private class Replace extends Action {
        private final Tree.Node<ObservedOrReconstructed<T, E>> replaceWhat;
        private final Tree.Node<ObservedOrReconstructed<T, E>> replacement;
        private final BigDecimal distanceFromParentToCommon;
        private final BigDecimal sumOfDistancesInReplacement;
        private final BigDecimal minDistanceFromObserved;

        public Replace(Tree.Node<ObservedOrReconstructed<T, E>> replaceWhat,
                       Tree.Node<ObservedOrReconstructed<T, E>> replacement,
                       BigDecimal distanceFromParentToCommon,
                       BigDecimal minDistanceFromObserved) {
            this.replaceWhat = replaceWhat;
            this.replacement = replacement;
            this.distanceFromParentToCommon = distanceFromParentToCommon;
            this.sumOfDistancesInReplacement = replacement.sumOfDistancesToDescendants();
            this.minDistanceFromObserved = minDistanceFromObserved;
        }

        BigDecimal getDistanceFromParentToCommon() {
            return distanceFromParentToCommon;
        }

        @Override
        BigDecimal changeOfDistance() {
            BigDecimal distanceFromParent = replaceWhat.getDistanceFromParent() != null ? replaceWhat.getDistanceFromParent() : BigDecimal.ZERO;
            return distanceFromParentToCommon.subtract(distanceFromParent).add(sumOfDistancesInReplacement);
        }

        @Override
        BigDecimal distanceFromObserved() {
            return minDistanceFromObserved;
        }

        @Override
        void apply() {
            if (replaceWhat.getParent() == null) throw new IllegalArgumentException();
            replaceWhat.getParent().replaceChild(replaceWhat, replacement, distanceFromParentToCommon);
        }
    }

    public static abstract class ObservedOrReconstructed<T, E> {
        public <R1, R2> ObservedOrReconstructed<R1, R2> map(Function<T, R1> mapObserved, Function<E, R2> mapReconstructed) {
            if (this instanceof Observed<?, ?>) {
                return new Observed<>(mapObserved.apply(((Observed<T, E>) this).getContent()));
            } else if (this instanceof Reconstructed<?, ?>) {
                Reconstructed<T, E> casted = (Reconstructed<T, E>) this;
                return new Reconstructed<>(mapReconstructed.apply(casted.getContent()), casted.minDistanceFromObserved);
            } else {
                throw new IllegalArgumentException();
            }
        }

        public <R> R convert(Function<T, R> mapObserved, Function<E, R> mapReconstructed) {
            if (this instanceof Observed<?, ?>) {
                return mapObserved.apply(((Observed<T, E>) this).getContent());
            } else if (this instanceof Reconstructed<?, ?>) {
                return mapReconstructed.apply(((Reconstructed<T, E>) this).getContent());
            } else {
                throw new IllegalArgumentException();
            }
        }
    }

    static class Observed<T, E> extends ObservedOrReconstructed<T, E> {
        private final T content;

        private Observed(T content) {
            this.content = content;
        }

        public T getContent() {
            return content;
        }
    }

    static class Reconstructed<T, E> extends ObservedOrReconstructed<T, E> {
        private final E content;
        private final BigDecimal minDistanceFromObserved;

        private Reconstructed(E content, BigDecimal minDistanceFromObserved) {
            this.content = content;
            this.minDistanceFromObserved = minDistanceFromObserved;
        }

        public E getContent() {
            return content;
        }
    }
}
