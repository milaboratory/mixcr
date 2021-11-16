package com.milaboratory.mixcr.trees;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.function.BiFunction;
import java.util.function.Function;

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
        Tree.Node<RealOrSynthetic<T, E>> nearestNode = tree.allNodes()
                .filter(it -> it.getContent() instanceof Synthetic<?, ?>)
                .min(Comparator.comparing(compareWith -> {
                    BigDecimal distance = distanceBetween.apply(contentAsAncestor, ((Synthetic<T, E>) compareWith.getContent()).getContent());
                    if (distance.compareTo(BigDecimal.ZERO) == 0) {
                        throw new IllegalArgumentException("can't add the same node");
                    }
                    return distance;
                }))
                .orElseThrow(IllegalArgumentException::new);
        E contentOfNearest = ((Synthetic<T, E>) nearestNode.getContent()).getContent();
        E commonAncestor = findCommonAncestor.apply(contentOfNearest, contentAsAncestor);

        BigDecimal fromCommonToAdded = distanceBetween.apply(commonAncestor, contentAsAncestor);
        BigDecimal fromCommonToChanged = distanceBetween.apply(commonAncestor, contentOfNearest);

        if (fromCommonToChanged.equals(BigDecimal.ZERO)) {
            nearestNode.addChild(nodePairWithSynthetic(toAdd), fromCommonToAdded);
        } else {
            Tree.Node<RealOrSynthetic<T, E>> parentBefore = nearestNode.getParent();

            Tree.Node<RealOrSynthetic<T, E>> replaceWith = new Tree.Node<>(new Synthetic<>(commonAncestor));
            replaceWith.addChild(nearestNode, fromCommonToChanged);
            replaceWith.addChild(nodePairWithSynthetic(toAdd), fromCommonToAdded);
            if (parentBefore == null) {
                tree.setRoot(replaceWith);
            } else {
                BigDecimal distance = distanceBetween.apply(((Synthetic<T, E>) parentBefore.getContent()).getContent(), commonAncestor);
                parentBefore.replaceChild(nearestNode, replaceWith, distance);
            }
        }
        return this;
    }

    private Tree.Node<RealOrSynthetic<T, E>> nodePairWithSynthetic(T toAdd) {
        Tree.Node<RealOrSynthetic<T, E>> ancestorNode = new Tree.Node<>(new Synthetic<>(asAncestor.apply(toAdd)));
        ancestorNode.addChild(new Tree.Node<>(new Real<>(toAdd)), BigDecimal.ZERO);
        return ancestorNode;
    }

    public Tree<RealOrSynthetic<T, E>> getTree() {
        return tree;
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
