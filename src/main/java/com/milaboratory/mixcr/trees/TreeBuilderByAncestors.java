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

    private final Tree<RealOrSynthetic<T, E>> tree;

    public TreeBuilderByAncestors(
            T root,
            BiFunction<E, E, BigDecimal> distanceBetween,
            BiFunction<E, E, E> findCommonAncestor,
            Function<T, E> asAncestor
    ) {
        this.distanceBetween = distanceBetween;
        this.findCommonAncestor = findCommonAncestor;
        this.asAncestor = asAncestor;
        Tree.Node<RealOrSynthetic<T, E>> rootNode = new Tree.Node<>(new Synthetic<>(asAncestor.apply(root)));
        tree = new Tree<>(rootNode);
        rootNode.addChild(new Tree.Node<>(new Real<>(root)), BigDecimal.ZERO);
    }

    public TreeBuilderByAncestors<T, E> addNode(T toAdd) {
        E contentAsAncestor = asAncestor.apply(toAdd);
        tree.allNodes()
                .filter(it -> it.getContent() instanceof Synthetic<?, ?>)
                .collect(Collectors.groupingBy(compareWith -> {
                    BigDecimal distance = distanceBetween.apply(((Synthetic<T, E>) compareWith.getContent()).getContent(), contentAsAncestor);
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
                .map(nearestNode -> {
                    E contentOfNearest = ((Synthetic<T, E>) nearestNode.getContent()).getContent();
                    E commonAncestor = findCommonAncestor.apply(contentOfNearest, contentAsAncestor);

                    BigDecimal fromCommonToNearest = distanceBetween.apply(commonAncestor, contentOfNearest);

                    if (fromCommonToNearest.compareTo(BigDecimal.ZERO) == 0) {
                        Optional<Pair<Tree.Node<RealOrSynthetic<T, E>>, E>> mergeWith = nearestNode.getLinks().stream()
                                .map(Tree.NodeLink::getNode)
                                .filter(it -> it.getContent() instanceof TreeBuilderByAncestors.Synthetic<?, ?>)
                                .map(it -> {
                                    E commonAncestor2 = findCommonAncestor.apply(contentAsAncestor, ((Synthetic<T, E>) it.getContent()).getContent());
                                    return Pair.create(it, Pair.create(commonAncestor2, distanceBetween.apply(contentOfNearest, commonAncestor2)));
                                })
                                .filter(it -> it.getSecond().getSecond().compareTo(BigDecimal.ZERO) != 0)
                                .max(Comparator.comparing(it -> it.getSecond().getSecond()))
                                .map(it -> Pair.create(it.getFirst(), it.getSecond().getFirst()));
                        if (mergeWith.isPresent()) {
                            E content = ((Synthetic<T, E>) mergeWith.get().getFirst().getContent()).getContent();

                            BigDecimal fromCommonToNearest2 = distanceBetween.apply(mergeWith.get().getSecond(), content);

                            if (fromCommonToNearest2.compareTo(BigDecimal.ZERO) != 0) {
                                return replaceNode(mergeWith.get().getFirst(), toAdd, mergeWith.get().getSecond());
                            }
                        }


                        Tree.Node<RealOrSynthetic<T, E>> added = nodePairWithSynthetic(toAdd);
                        return new Insert(
                                nearestNode,
                                added,
                                distanceBetween.apply(commonAncestor, contentAsAncestor)
                        );
                    } else {
                        return replaceNode(nearestNode, toAdd, commonAncestor);
                    }
                })
                .min(Comparator.comparing(Action::changeOfDistance))
                .orElseThrow(IllegalArgumentException::new)
                .apply();
        return this;
    }

    private Action replaceNode(Tree.Node<RealOrSynthetic<T, E>> replacedNode, T with, E commonAncestor) {
        Tree.Node<RealOrSynthetic<T, E>> replacement = new Tree.Node<>(new Synthetic<>(commonAncestor));
        replacement.addChild(replacedNode.copy(), distanceBetween.apply(commonAncestor, ((Synthetic<T, E>) replacedNode.getContent()).getContent()));
        replacement.addChild(nodePairWithSynthetic(with), distanceBetween.apply(commonAncestor, asAncestor.apply(with)));
        if (replacedNode.getParent() == null) {
            return new Replace(replacedNode, replacement, BigDecimal.ZERO);
        } else {
            BigDecimal distance = distanceBetween.apply(((Synthetic<T, E>) replacedNode.getParent().getContent()).getContent(), commonAncestor);
            return new Replace(replacedNode, replacement, distance);
        }
    }

    private Tree.Node<RealOrSynthetic<T, E>> nodePairWithSynthetic(T toAdd) {
        Tree.Node<RealOrSynthetic<T, E>> ancestorNode = new Tree.Node<>(new Synthetic<>(asAncestor.apply(toAdd)));
        ancestorNode.addChild(new Tree.Node<>(new Real<>(toAdd)), BigDecimal.ZERO);
        return ancestorNode;
    }

    public Tree<RealOrSynthetic<T, E>> getTree() {
        return tree;
    }

    private static abstract class Action {
        abstract BigDecimal changeOfDistance();

        abstract void apply();
    }

    private class Insert extends Action {
        private final Tree.Node<RealOrSynthetic<T, E>> addTo;
        private final Tree.Node<RealOrSynthetic<T, E>> insertion;
        private final BigDecimal distance;

        public Insert(Tree.Node<RealOrSynthetic<T, E>> addTo, Tree.Node<RealOrSynthetic<T, E>> insertion, BigDecimal distance) {
            this.addTo = addTo;
            this.insertion = insertion;
            this.distance = distance;
        }

        @Override
        BigDecimal changeOfDistance() {
            return distance;
        }

        @Override
        void apply() {
            addTo.addChild(insertion, distance);
        }
    }

    private class Replace extends Action {
        private final Tree.Node<RealOrSynthetic<T, E>> replaceWhat;
        private final Tree.Node<RealOrSynthetic<T, E>> replacement;
        private final BigDecimal newDistance;
        private final BigDecimal sumOfDistancesInReplacement;

        public Replace(Tree.Node<RealOrSynthetic<T, E>> replaceWhat, Tree.Node<RealOrSynthetic<T, E>> replacement, BigDecimal newDistance) {
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
        void apply() {
            if (replaceWhat.getParent() == null) {
                tree.setRoot(replacement);
            } else {
                replaceWhat.getParent().replaceChild(replaceWhat, replacement, newDistance);
            }
        }
    }

    public static abstract class RealOrSynthetic<T, E> {
        public <R1, R2> RealOrSynthetic<R1, R2> map(Function<T, R1> mapReal, Function<E, R2> mapSynthetic) {
            if (this instanceof Real<?, ?>) {
                return new Real<>(mapReal.apply(((Real<T, E>) this).getContent()));
            } else if (this instanceof Synthetic<?, ?>) {
                return new Synthetic<>(mapSynthetic.apply(((Synthetic<T, E>) this).getContent()));
            } else {
                throw new IllegalArgumentException();
            }
        }

        public <R> R convert(Function<T, R> mapReal, Function<E, R> mapSynthetic) {
            if (this instanceof Real<?, ?>) {
                return mapReal.apply(((Real<T, E>) this).getContent());
            } else if (this instanceof Synthetic<?, ?>) {
                return mapSynthetic.apply(((Synthetic<T, E>) this).getContent());
            } else {
                throw new IllegalArgumentException();
            }
        }
    }

    public static class Real<T, E> extends RealOrSynthetic<T, E> {
        private final T content;

        public Real(T content) {
            this.content = content;
        }

        public T getContent() {
            return content;
        }
    }

    public static class Synthetic<T, E> extends RealOrSynthetic<T, E> {
        private final E content;

        public Synthetic(E content) {
            this.content = content;
        }

        public E getContent() {
            return content;
        }
    }
}
