package com.milaboratory.mixcr.trees;

import org.apache.commons.math3.util.Pair;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TreeBuilderByAncestors<T, E> {
    private final BiFunction<E, E, BigDecimal> distanceBetween;
    private final BiFunction<E, E, E> findCommonAncestor;
    private final Function<T, E> asAncestor;

    private final Tree<ObservedOrReconstructed<T, E>> tree;

    public TreeBuilderByAncestors(
            T root,
            BiFunction<E, E, BigDecimal> distanceBetween,
            BiFunction<E, E, E> findCommonAncestor,
            Function<T, E> asAncestor
    ) {
        this.distanceBetween = distanceBetween;
        this.findCommonAncestor = findCommonAncestor;
        this.asAncestor = asAncestor;
        Tree.Node<ObservedOrReconstructed<T, E>> rootNode = new Tree.Node<>(new Reconstructed<>(asAncestor.apply(root), BigDecimal.ZERO));
        tree = new Tree<>(rootNode);
        rootNode.addChild(new Tree.Node<>(new Observed<>(root)), BigDecimal.ZERO);
    }

    /**
     * Assumptions:
     * 1. Nodes must be added in order by distance from the root
     * 2. findCommonAncestor is constructing ancestor based on root (left common mutations on nodes from root)
     * <p>
     * Invariants of the tree:
     * 1. Root node is always reconstructed.
     * 2. Leaves are always observed. Distance between observed and reconstructed may be zero (they are identical).
     * 3. Siblings have no common ancestors.
     */
    public TreeBuilderByAncestors<T, E> addNode(T toAdd) {
        E contentAsAncestor = asAncestor.apply(toAdd);
        tree.allNodes()
                .filter(it -> it.getContent() instanceof Reconstructed<?, ?>)
                .collect(Collectors.groupingBy(compareWith -> {
                    BigDecimal distance = distanceBetween.apply(((Reconstructed<T, E>) compareWith.getContent()).getContent(), contentAsAncestor);
                    if (distance.compareTo(BigDecimal.ZERO) == 0) {
                        throw new IllegalArgumentException("can't add the same node");
                    }
                    return distance;
                }))
                .entrySet().stream()
                .min(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .orElseThrow(IllegalArgumentException::new)
                .stream()
                .map(chosenNode -> {
                    E contentOfChosen = ((Reconstructed<T, E>) chosenNode.getContent()).getContent();
                    E commonAncestor = findCommonAncestor.apply(contentOfChosen, contentAsAncestor);

                    BigDecimal fromCommonToChosen = distanceBetween.apply(commonAncestor, contentOfChosen);

                    if (fromCommonToChosen.compareTo(BigDecimal.ZERO) == 0) {
                        //the added node is direct descendant of the chosen node (the added node has no mutations that don't exist in the chosen node)

                        //search for siblings with common mutations with the added node
                        Optional<Action> siblingToMergeWith = chosenNode.getLinks().stream()
                                .map(Tree.NodeLink::getNode)
                                .filter(it -> it.getContent() instanceof Reconstructed<?, ?>)
                                .map(it -> {
                                    E commonAncestorWithSibling = findCommonAncestor.apply(contentAsAncestor, ((Reconstructed<T, E>) it.getContent()).getContent());
                                    return Pair.create(
                                            replaceNode(it, toAdd, commonAncestorWithSibling),
                                            distanceBetween.apply(contentOfChosen, commonAncestorWithSibling)
                                    );
                                })
                                //if distance is zero than there is no common mutations
                                .filter(it -> it.getSecond().compareTo(BigDecimal.ZERO) != 0)
                                //choose a sibling with max count of common mutations
                                .max(Comparator.comparing(Pair::getSecond))
                                .map(Pair::getFirst);
                        //noinspection OptionalIsPresent
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
                            return new Insert(
                                    chosenNode,
                                    nodePairWithReconstructed(toAdd),
                                    distanceBetween.apply(commonAncestor, contentAsAncestor)
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
                })
                //optimize sum of distances in the tree. If there is no difference, optimize by distances without reconstructed nodes
                .min(Comparator.comparing(Action::changeOfDistance).thenComparing(Action::distanceFromObserved))
                .orElseThrow(IllegalArgumentException::new)
                .apply();
        return this;
    }

    private Action replaceNode(Tree.Node<ObservedOrReconstructed<T, E>> replacedNode, T with, E commonAncestor) {
        BigDecimal minDistanceFromObserved;
        if (replacedNode.getParent() != null) {
            minDistanceFromObserved = ((Reconstructed<T, E>) replacedNode.getParent().getContent()).minDistanceFromObserved.add(replacedNode.getDistanceFromParent());
        } else {
            minDistanceFromObserved = BigDecimal.ZERO;
        }
        Tree.Node<ObservedOrReconstructed<T, E>> replacement = new Tree.Node<>(new Reconstructed<>(commonAncestor, minDistanceFromObserved));
        replacement.addChild(replacedNode.copy(), distanceBetween.apply(commonAncestor, ((Reconstructed<T, E>) replacedNode.getContent()).getContent()));
        replacement.addChild(nodePairWithReconstructed(with), distanceBetween.apply(commonAncestor, asAncestor.apply(with)));
        if (replacedNode.getParent() == null) {
            return new Replace(replacedNode, replacement, BigDecimal.ZERO);
        } else {
            BigDecimal distance = distanceBetween.apply(((Reconstructed<T, E>) replacedNode.getParent().getContent()).getContent(), commonAncestor);
            return new Replace(replacedNode, replacement, distance);
        }
    }

    private Tree.Node<ObservedOrReconstructed<T, E>> nodePairWithReconstructed(T toAdd) {
        Tree.Node<ObservedOrReconstructed<T, E>> ancestorNode = new Tree.Node<>(new Reconstructed<>(asAncestor.apply(toAdd), BigDecimal.ZERO));
        ancestorNode.addChild(new Tree.Node<>(new Observed<>(toAdd)), BigDecimal.ZERO);
        return ancestorNode;
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
            return ((Reconstructed<T, E>) replaceWhat.getContent()).minDistanceFromObserved;
        }

        @Override
        void apply() {
            if (replaceWhat.getParent() == null) {
                tree.setRoot(replacement);
            } else {
                replaceWhat.getParent().replaceChild(replaceWhat, replacement, newDistance);
            }
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
