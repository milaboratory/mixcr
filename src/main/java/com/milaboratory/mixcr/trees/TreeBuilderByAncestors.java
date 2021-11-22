package com.milaboratory.mixcr.trees;

import org.apache.commons.math3.util.Pair;

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
     * 2. Leaves are always observed. Distance between observed and reconstructed may be zero (they are identical).
     * 3. Siblings have no common ancestors.
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
            E contentOfChosen = ((Reconstructed<T, E>) chosenNode.getContent()).getContent();
            E commonAncestor;
            if (chosenNode.getParent() == null) {
                commonAncestor = contentOfChosen;
            } else {
                E contentOfParent = ((Reconstructed<T, E>) chosenNode.getParent().getContent()).getContent();
                M mutationsToAdded = mutationsBetween.apply(contentOfParent, contentAsAncestor);
                M mutationsToChosen = mutationsBetween.apply(contentOfParent, contentOfChosen);

                commonAncestor = mutate.apply(contentOfParent, findCommonMutations.apply(mutationsToAdded, mutationsToChosen));
            }
            BigDecimal distanceBetweenCommonAndChosen = distance.apply(mutationsBetween.apply(commonAncestor, contentOfChosen));
            //all nodes are assumed as direct descendant of the root
            if (chosenNode.getParent() == null || distanceBetweenCommonAndChosen.compareTo(BigDecimal.ZERO) == 0) {
                //the added node is direct descendant of the chosen node (the added node has no mutations that don't exist in the chosen node)

                //search for siblings with common mutations with the added node
                Optional<Action> siblingToMergeWith = chosenNode.getLinks().stream()
                        .map(Tree.NodeLink::getNode)
                        .filter(it -> it.getContent() instanceof Reconstructed<?, ?>)
                        .map(sibling -> {
                            M mutationsToAdded = mutationsBetween.apply(contentOfChosen, contentAsAncestor);
                            M mutationsToSibling = mutationsBetween.apply(contentOfChosen, ((Reconstructed<T, E>) sibling.getContent()).getContent());

                            M commonMutations = findCommonMutations.apply(mutationsToAdded, mutationsToSibling);
                            E commonAncestorWithSibling = mutate.apply(contentOfChosen, commonMutations);
                            return Pair.create(
                                    replaceNode(sibling, toAdd, commonAncestorWithSibling),
                                    distance.apply(commonMutations)
                            );
                        })
                        //if distance is zero than there is no common mutations
                        .filter(it -> it.getSecond().compareTo(BigDecimal.ZERO) != 0)
                        //choose a sibling with max count of common mutations
                        .max(comparing(Pair::getSecond))
                        .map(Pair::getFirst);
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
                    BigDecimal distanceBetweenCommonAndAdded = distance.apply(mutationsBetween.apply(commonAncestor, contentAsAncestor));
                    Tree.Node<ObservedOrReconstructed<T, E>> nodeToInsert;
                    if (distanceBetweenCommonAndAdded.compareTo(BigDecimal.ZERO) == 0) {
                        //case with inserting a node equal to reconstructed root
                        nodeToInsert = new Tree.Node<>(new Observed<>(toAdd));
                    } else {
                        nodeToInsert = new Tree.Node<>(new Reconstructed<>(asAncestor.apply(toAdd), BigDecimal.ZERO));
                        nodeToInsert.addChild(new Tree.Node<>(new Observed<>(toAdd)), BigDecimal.ZERO);
                    }
                    return new Insert(
                            chosenNode,
                            nodeToInsert,
                            distanceBetweenCommonAndAdded
                    );
                }
            } else {
                //the added node (A) and the chosen node (B) has common ancestor (C)
                //
                //       P                   P
                //       |                   |
                //     --*--               --*--
                //     |   |               |   |
                //     K   B      ==>      K   C
                //         |                   |
                //       --*--               --*--
                //       |   |               |   |
                //       R   T               A   B
                //                               |
                //                             --*--
                //                             |   |
                //                             R   T
                return replaceNode(chosenNode, toAdd, commonAncestor);
            }
        });
        //optimize sum of distances in the tree. If there is no difference, optimize by distances without reconstructed nodes
        Optional<Action> bestAction = possibleActions.min(comparing(Action::changeOfDistance).thenComparing(Action::distanceFromObserved));
        bestAction.orElseThrow(IllegalArgumentException::new).apply();
        return this;
    }

    private Action replaceNode(Tree.Node<ObservedOrReconstructed<T, E>> replacedNode, T with, E commonAncestor) {
        //can't replace the root
        if (replacedNode.getParent() == null) {
            throw new IllegalArgumentException();
        }
        Reconstructed<T, E> parentContent = (Reconstructed<T, E>) replacedNode.getParent().getContent();
        BigDecimal minDistanceFromObserved = parentContent.minDistanceFromObserved
                .add(replacedNode.getDistanceFromParent());
        Tree.Node<ObservedOrReconstructed<T, E>> replacement = new Tree.Node<>(new Reconstructed<>(commonAncestor, minDistanceFromObserved));
        replacement.addChild(replacedNode.copy(), distance.apply(mutationsBetween.apply(commonAncestor, ((Reconstructed<T, E>) replacedNode.getContent()).getContent())));
        E contentAsAncestor = asAncestor.apply(with);
        Tree.Node<ObservedOrReconstructed<T, E>> ancestorNode = new Tree.Node<>(new Reconstructed<>(contentAsAncestor, BigDecimal.ZERO));
        ancestorNode.addChild(new Tree.Node<>(new Observed<>(with)), BigDecimal.ZERO);
        replacement.addChild(ancestorNode, distance.apply(mutationsBetween.apply(commonAncestor, contentAsAncestor)));
        BigDecimal distance = this.distance.apply(mutationsBetween.apply(parentContent.getContent(), commonAncestor));
        return new Replace(replacedNode, replacement, distance);
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
        private final BigDecimal newDistance;
        private final BigDecimal sumOfDistancesInReplacement;

        public Replace(Tree.Node<ObservedOrReconstructed<T, E>> replaceWhat, Tree.Node<ObservedOrReconstructed<T, E>> replacement, BigDecimal newDistance) {
            this.replaceWhat = replaceWhat;
            this.replacement = replacement;
            this.newDistance = newDistance;
            this.sumOfDistancesInReplacement = replacement.sumOfDistancesToDescendants();
        }

        @Override
        BigDecimal changeOfDistance() {
            BigDecimal distanceFromParent = replaceWhat.getDistanceFromParent() != null ? replaceWhat.getDistanceFromParent() : BigDecimal.ZERO;
            return newDistance.subtract(distanceFromParent).add(sumOfDistancesInReplacement);
        }

        @Override
        BigDecimal distanceFromObserved() {
            if (replaceWhat.getParent() == null) {
                return BigDecimal.ZERO;
            } else {
                return ((Reconstructed<T, E>) replaceWhat.getParent().getContent()).minDistanceFromObserved.add(replaceWhat.getDistanceFromParent());
            }
        }

        @Override
        void apply() {
            if (replaceWhat.getParent() == null) throw new IllegalArgumentException();
            replaceWhat.getParent().replaceChild(replaceWhat, replacement, newDistance);
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
