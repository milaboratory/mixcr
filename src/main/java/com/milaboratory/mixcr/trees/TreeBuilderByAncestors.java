package com.milaboratory.mixcr.trees;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Comparator.comparing;

public class TreeBuilderByAncestors<T, E, M> {
    public static final Comparator<Action> ACTION_COMPARATOR = Comparator
            .<Action, BigDecimal>comparing(action -> action.changeOfDistance().add(action.distanceFromObserved()))
            .thenComparing(Action::changeOfDistance)
            .thenComparing(action -> action instanceof TreeBuilderByAncestors.Insert ? 1 : -1);

    private final BiFunction<E, M, BigDecimal> distance;
    /**
     * parent, observed -> reconstructed
     */
    private final Function<T, E> asAncestor;
    private final BiFunction<E, E, M> mutationsBetween;
    private final BiFunction<E, M, E> mutate;
    private final BiFunction<M, M, M> findCommonMutations;
    private final BiFunction<E, E, E> postprocessDescendants;

    private final Tree<ObservedOrReconstructed<T, E>> tree;
    private final int countOfNodesToProbe;
    private int counter;

    public TreeBuilderByAncestors(
            E root,
            BiFunction<E, M, BigDecimal> distance,
            BiFunction<E, E, M> mutationsBetween,
            BiFunction<E, M, E> mutate,
            Function<T, E> asAncestor,
            BiFunction<M, M, M> findCommonMutations,
            BiFunction<E, E, E> postprocessDescendants,
            int countOfNodesToProbe
    ) {
        this(
                distance,
                asAncestor,
                mutationsBetween,
                mutate,
                findCommonMutations,
                postprocessDescendants,
                new Tree<>(new Tree.Node<>(new Reconstructed<>(root, BigDecimal.ZERO, 0))),
                countOfNodesToProbe,
                1
        );
    }

    private TreeBuilderByAncestors(
            BiFunction<E, M, BigDecimal> distance,
            Function<T, E> asAncestor,
            BiFunction<E, E, M> mutationsBetween,
            BiFunction<E, M, E> mutate,
            BiFunction<M, M, M> findCommonMutations,
            BiFunction<E, E, E> postprocessDescendants,
            Tree<ObservedOrReconstructed<T, E>> tree,
            int countOfNodesToProbe,
            int counter
    ) {
        this.distance = distance;
        this.asAncestor = asAncestor;
        this.mutationsBetween = mutationsBetween;
        this.mutate = mutate;
        this.findCommonMutations = findCommonMutations;
        this.postprocessDescendants = postprocessDescendants;
        this.tree = tree;
        this.countOfNodesToProbe = countOfNodesToProbe;
        this.counter = counter;
    }

    public TreeBuilderByAncestors<T, E, M> copy() {
        return new TreeBuilderByAncestors<>(
                distance,
                asAncestor,
                mutationsBetween,
                mutate,
                findCommonMutations,
                postprocessDescendants,
                tree.copy(),
                countOfNodesToProbe,
                counter
        );
    }

    public TreeBuilderByAncestors<T, E, M> addNode(T toAdd) {
        Action bestAction = bestActionForObserved(toAdd);
        bestAction.apply();
        return this;
    }

    public Action bestActionForObserved(T toAdd) {
        E addedAsAncestor = asAncestor.apply(toAdd);
        return bestAction(addedAsAncestor, distanceFromParent -> {
            Tree.Node<ObservedOrReconstructed<T, E>> nodeToAdd;
            if (distanceFromParent.compareTo(BigDecimal.ZERO) == 0) {
                nodeToAdd = new Tree.Node<>(new Observed<>(toAdd, ++counter));
            } else {
                nodeToAdd = new Tree.Node<>(new Reconstructed<>(addedAsAncestor, BigDecimal.ZERO, ++counter));
                nodeToAdd.addChild(new Tree.Node<>(new Observed<>(toAdd, ++counter)), BigDecimal.ZERO);
            }
            return nodeToAdd;
        });
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
    private Action bestAction(E addedAsAncestor, Function<BigDecimal, Tree.Node<ObservedOrReconstructed<T, E>>> nodeGenerator) {
        List<Tree.NodeWithParent<ObservedOrReconstructed<T, E>>> nearestNodes = tree.allNodes()
                .filter(it -> it.getNode().getContent() instanceof Reconstructed<?, ?>)
                .sorted(Comparator.comparing(compareWith -> {
                    Reconstructed<T, E> reconstructed = (Reconstructed<T, E>) compareWith.getNode().getContent();
                    E nodeContent = reconstructed.getContent();
                    return distance.apply(
                            nodeContent,
                            mutationsBetween.apply(nodeContent, addedAsAncestor)
                    ).add(reconstructed.getMinDistanceFromObserved());
                }))
                .limit(countOfNodesToProbe)
                .collect(Collectors.toList());
        Stream<Action> possibleActions = nearestNodes.stream().flatMap(nodeWithParent -> {
            Tree.Node<ObservedOrReconstructed<T, E>> chosenNode = nodeWithParent.getNode();
            //search for siblings with common mutations with the added node
            Optional<Replace> siblingToMergeWith = chosenNode.getLinks().stream()
                    .filter(it -> it.getNode().getContent() instanceof Reconstructed<?, ?>)
                    .map(link -> replaceChild(chosenNode, link.getNode(), link.getDistance(), addedAsAncestor, nodeGenerator))
                    .filter(Objects::nonNull)
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
                return siblingToMergeWith.stream();
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
                Stream<Replace> insertAsParent;
                if (nodeWithParent.getParent() != null) {
                    insertAsParent = Stream.of(replaceChild(
                            nodeWithParent.getParent(),
                            chosenNode,
                            nodeWithParent.getDistance(),
                            addedAsAncestor,
                            nodeGenerator
                    )).filter(Objects::nonNull);
                } else {
                    insertAsParent = Stream.empty();
                }
                return Stream.concat(
                        Stream.of(insertAsDirectDescendant(chosenNode, addedAsAncestor, nodeGenerator)),
                        insertAsParent
                );
            }
        });
        //optimize sum of distances from observed nodes
        List<Action> temp = possibleActions.collect(Collectors.toList());
        return temp.stream().min(ACTION_COMPARATOR).orElseThrow(IllegalArgumentException::new);
    }

    public BigDecimal distanceFromRootToObserved(T node) {
        E rootContent = ((Reconstructed<T, E>) tree.getRoot().getContent()).getContent();
        return distance.apply(
                rootContent,
                mutationsBetween.apply(rootContent, asAncestor.apply(node))
        );
    }

    private Insert insertAsDirectDescendant(Tree.Node<ObservedOrReconstructed<T, E>> parent, E toAdd, Function<BigDecimal, Tree.Node<ObservedOrReconstructed<T, E>>> nodeGenerator) {
        E contentOfParent = ((Reconstructed<T, E>) parent.getContent()).getContent();
        BigDecimal distanceBetweenParentAndAdded = distance.apply(contentOfParent, mutationsBetween.apply(contentOfParent, toAdd));
        Tree.Node<ObservedOrReconstructed<T, E>> nodeToInsert = nodeGenerator.apply(distanceBetweenParentAndAdded);
        return new Insert(
                parent,
                distanceBetweenParentAndAdded,
                nodeToInsert
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
     * B - child
     * C - commonAncestor
     */
    private Replace replaceChild(
            Tree.Node<ObservedOrReconstructed<T, E>> parent,
            Tree.Node<ObservedOrReconstructed<T, E>> child,
            BigDecimal distanceFromParentToChild,
            E addedAsAncestor,
            Function<BigDecimal, Tree.Node<ObservedOrReconstructed<T, E>>> nodeGenerator
    ) {
        Reconstructed<T, E> parentAsReconstructed = (Reconstructed<T, E>) parent.getContent();
        E parentContent = parentAsReconstructed.getContent();
        E childContent = ((Reconstructed<T, E>) child.getContent()).getContent();

        M mutationsToAdded = mutationsBetween.apply(parentContent, addedAsAncestor);
        M mutationsToChild = mutationsBetween.apply(parentContent, childContent);

        M commonMutations = findCommonMutations.apply(mutationsToAdded, mutationsToChild);
        BigDecimal distanceFromParentToCommon = distance.apply(parentContent, commonMutations);
        //if distance is zero than there is no common mutations
        if (distanceFromParentToCommon.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        E commonAncestor = mutate.apply(parentContent, commonMutations);
        M fromCommonToChild = mutationsBetween.apply(commonAncestor, childContent);
        BigDecimal distanceFromCommonAncestorToChild = distance.apply(commonAncestor, fromCommonToChild);
        //if distance is zero than result of replacement equals to insertion
        if (distanceFromCommonAncestorToChild.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        BigDecimal distanceFromCommonToAdded = distance.apply(commonAncestor, mutationsBetween.apply(commonAncestor, addedAsAncestor));

        BigDecimal minDistanceFromObserved = Stream.of(
                distanceFromCommonToAdded,
                distanceFromCommonAncestorToChild,
                parentAsReconstructed.getMinDistanceFromObserved().add(distanceFromParentToCommon)
        ).min(BigDecimal::compareTo).get();
        Tree.Node<ObservedOrReconstructed<T, E>> replacement = new Tree.Node<>(new Reconstructed<>(
                commonAncestor,
                minDistanceFromObserved,
                ++counter
        ));
        Tree.Node<ObservedOrReconstructed<T, E>> rebuiltChild = new Tree.Node<>(new Reconstructed<>(
                mutate.apply(commonAncestor, fromCommonToChild),
                ((Reconstructed<T, E>) child.getContent()).getMinDistanceFromObserved(),
                ++counter
        ));
        child.getLinks().forEach(link -> rebuiltChild.addChild(link.getNode(), link.getDistance()));
        replacement.addChild(rebuiltChild, distanceFromCommonAncestorToChild);

        replacement.addChild(nodeGenerator.apply(distanceFromCommonToAdded), distanceFromCommonToAdded);

        return new Replace(
                parent,
                child,
                replacement,
                distanceFromParentToCommon,
                distanceFromCommonAncestorToChild.add(distanceFromCommonToAdded),
                distanceFromParentToChild
        );
    }

    public Tree<ObservedOrReconstructed<T, E>> getTree() {
        return tree;
    }

    public static abstract class Action {
        protected abstract BigDecimal changeOfDistance();

        protected abstract BigDecimal distanceFromObserved();

        protected abstract void apply();
    }

    private class Insert extends Action {
        private final Tree.Node<ObservedOrReconstructed<T, E>> parent;
        private final BigDecimal distanceFromParentAndInsertion;
        private final Tree.Node<ObservedOrReconstructed<T, E>> nodeToInsert;

        public Insert(Tree.Node<ObservedOrReconstructed<T, E>> parent, BigDecimal distanceFromParentAndInsertion, Tree.Node<ObservedOrReconstructed<T, E>> nodeToInsert) {
            this.parent = parent;
            this.distanceFromParentAndInsertion = distanceFromParentAndInsertion;
            this.nodeToInsert = nodeToInsert;
        }

        @Override
        protected BigDecimal changeOfDistance() {
            return distanceFromParentAndInsertion;
        }

        @Override
        protected BigDecimal distanceFromObserved() {
            return ((Reconstructed<T, E>) parent.getContent()).getMinDistanceFromObserved();
        }

        @Override
        protected void apply() {
            Reconstructed<T, E> parentAsReconstructed = (Reconstructed<T, E>) parent.getContent();
            if (distanceFromParentAndInsertion.compareTo(BigDecimal.ZERO) == 0) {
                parentAsReconstructed.setMinDistanceFromObserved(BigDecimal.ZERO);
            }
            parent.addChild(nodeToInsert, distanceFromParentAndInsertion);
        }
    }

    private class Replace extends Action {
        private final Tree.Node<ObservedOrReconstructed<T, E>> parent;
        private final Tree.Node<ObservedOrReconstructed<T, E>> replaceWhat;
        private final Tree.Node<ObservedOrReconstructed<T, E>> replacement;
        private final BigDecimal distanceFromParentToCommon;
        private final BigDecimal sumOfDistancesFromAncestor;
        private final BigDecimal distanceFromParentToReplaceWhat;

        public Replace(Tree.Node<ObservedOrReconstructed<T, E>> parent,
                       Tree.Node<ObservedOrReconstructed<T, E>> replaceWhat,
                       Tree.Node<ObservedOrReconstructed<T, E>> replacement,
                       BigDecimal distanceFromParentToCommon,
                       BigDecimal sumOfDistancesFromAncestor,
                       BigDecimal distanceFromParentToReplaceWhat
        ) {
            this.parent = parent;
            this.replaceWhat = replaceWhat;
            this.replacement = replacement;
            this.distanceFromParentToCommon = distanceFromParentToCommon;
            this.sumOfDistancesFromAncestor = sumOfDistancesFromAncestor;
            this.distanceFromParentToReplaceWhat = distanceFromParentToReplaceWhat;
        }

        BigDecimal getDistanceFromParentToCommon() {
            return distanceFromParentToCommon;
        }

        @Override
        protected BigDecimal changeOfDistance() {
            return distanceFromParentToCommon.subtract(distanceFromParentToReplaceWhat).add(sumOfDistancesFromAncestor);
        }

        @Override
        protected BigDecimal distanceFromObserved() {
            return ((Reconstructed<T, E>) parent.getContent()).getMinDistanceFromObserved();
        }

        @Override
        protected void apply() {
            parent.replaceChild(
                    replaceWhat,
                    postprocess(replacement),
                    distanceFromParentToCommon
            );
        }

        private Tree.Node<ObservedOrReconstructed<T, E>> postprocess(Tree.Node<ObservedOrReconstructed<T, E>> parentToPostprocess) {
            return new Tree.Node<>(
                    parentToPostprocess.getContent(),
                    postprocess(
                            ((Reconstructed<T, E>) parentToPostprocess.getContent()).getContent(),
                            parentToPostprocess.getLinks()
                    )
            );
        }

        private List<Tree.NodeLink<ObservedOrReconstructed<T, E>>> postprocess(
                E parentContent, List<Tree.NodeLink<ObservedOrReconstructed<T, E>>> links
        ) {
            return links.stream()
                    .map(link -> {
                        Tree.Node<ObservedOrReconstructed<T, E>> child = link.getNode();
                        if (child.getContent() instanceof Reconstructed<?, ?>) {
                            Reconstructed<T, E> childContent = (Reconstructed<T, E>) child.getContent();
                            E mapped = postprocessDescendants.apply(parentContent, childContent.getContent());
                            return new Tree.NodeLink<>(
                                    new Tree.Node<>(
                                            new Reconstructed<>(
                                                    mapped,
                                                    //TODO recalculate minDistanceFromObserved
                                                    childContent.getMinDistanceFromObserved(),
                                                    ++counter
                                            ),
                                            postprocess(mapped, child.getLinks())
                                    ),
                                    distance.apply(parentContent, mutationsBetween.apply(parentContent, mapped))
                            );
                        } else {
                            return link;
                        }
                    })
                    .collect(Collectors.toList());
        }
    }

    public static abstract class ObservedOrReconstructed<T, E> {
        private final int id;

        protected ObservedOrReconstructed(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        public <R1, R2> ObservedOrReconstructed<R1, R2> map(Function<T, R1> mapObserved, Function<E, R2> mapReconstructed) {
            if (this instanceof Observed<?, ?>) {
                return new Observed<>(mapObserved.apply(((Observed<T, E>) this).getContent()), id);
            } else if (this instanceof Reconstructed<?, ?>) {
                Reconstructed<T, E> casted = (Reconstructed<T, E>) this;
                return new Reconstructed<>(mapReconstructed.apply(casted.getContent()), casted.getMinDistanceFromObserved(), id);
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

        private Observed(T content, int id) {
            super(id);
            this.content = content;
        }

        public T getContent() {
            return content;
        }
    }

    static class Reconstructed<T, E> extends ObservedOrReconstructed<T, E> {
        private final E content;
        /**
         * This number represents distance that will be added after removing of all reconstructed nodes from the tree.
         * <p>
         * zero for root
         * zero if had an observed child with distance zero
         * otherwise it is a distance from the nearest parent with minDistanceFromObserved equals zero
         */
        private BigDecimal minDistanceFromObserved;

        private Reconstructed(E content, BigDecimal minDistanceFromObserved, int id) {
            super(id);
            this.content = content;
            this.minDistanceFromObserved = minDistanceFromObserved;
        }

        private void setMinDistanceFromObserved(BigDecimal minDistanceFromObserved) {
            this.minDistanceFromObserved = minDistanceFromObserved;
        }

        BigDecimal getMinDistanceFromObserved() {
            return minDistanceFromObserved;
        }

        public E getContent() {
            return content;
        }
    }
}
