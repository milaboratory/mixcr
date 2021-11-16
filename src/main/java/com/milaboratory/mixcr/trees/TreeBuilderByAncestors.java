package com.milaboratory.mixcr.trees;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.function.BiFunction;
import java.util.function.Function;

public class TreeBuilderByAncestors<T, E> {
    private final BiFunction<E, E, BigDecimal> distanceBetween;
    private final BiFunction<E, E, E> findCommonAncestor;
    private final Function<T, E> asAncestor;

    private final Tree<T, E> tree;

    public TreeBuilderByAncestors(
            T root,
            BiFunction<E, E, BigDecimal> distanceBetween,
            BiFunction<E, E, E> findCommonAncestor,
            Function<T, E> asAncestor
    ) {
        this.distanceBetween = distanceBetween;
        this.findCommonAncestor = findCommonAncestor;
        this.asAncestor = asAncestor;
        Tree.Node.Synthetic<T, E> rootNode = new Tree.Node.Synthetic<>(asAncestor.apply(root));
        tree = new Tree<>(rootNode);
        rootNode.addChild(new Tree.Node.Real<>(root), BigDecimal.ZERO);
    }

    public TreeBuilderByAncestors<T, E> addNode(T toAdd) {
        E contentAsAncestor = asAncestor.apply(toAdd);
        Tree.Node.Synthetic<T, E> nearestNode = tree.allNodes()
                .filter(it -> it instanceof Tree.Node.Synthetic<?, ?>)
                .map(it -> (Tree.Node.Synthetic<T, E>) it)
                .min(Comparator.comparing(compareWith -> {
                    BigDecimal distance = distanceBetween.apply(contentAsAncestor, compareWith.getContent());
                    if (distance.compareTo(BigDecimal.ZERO) == 0) {
                        throw new IllegalArgumentException("can't add the same node");
                    }
                    return distance;
                }))
                .orElseThrow(IllegalArgumentException::new);
        E commonAncestor = findCommonAncestor.apply(nearestNode.getContent(), contentAsAncestor);

        BigDecimal fromCommonToAdded = distanceBetween.apply(commonAncestor, contentAsAncestor);
        BigDecimal fromCommonToChanged = distanceBetween.apply(commonAncestor, nearestNode.getContent());

        if (fromCommonToChanged.equals(BigDecimal.ZERO)) {
            nearestNode.addChild(nodePairWithSynthetic(toAdd), fromCommonToAdded);
        } else {
            Tree.Node<T, E> parentBefore = nearestNode.getParent();

            Tree.Node.Synthetic<T, E> replaceWith = new Tree.Node.Synthetic<>(commonAncestor);
            replaceWith.addChild(nearestNode, fromCommonToChanged);
            replaceWith.addChild(nodePairWithSynthetic(toAdd), fromCommonToAdded);
            if (parentBefore == null) {
                tree.setRoot(replaceWith);
            } else {
                BigDecimal distance = distanceBetween.apply(((Tree.Node.Synthetic<T, E>) parentBefore).getContent(), commonAncestor);
                parentBefore.replaceChild(nearestNode, replaceWith, distance);
            }
        }
        return this;
    }

    private Tree.Node.Synthetic<T, E> nodePairWithSynthetic(T toAdd) {
        Tree.Node.Synthetic<T, E> ancestorNode = new Tree.Node.Synthetic<>(asAncestor.apply(toAdd));
        ancestorNode.addChild(new Tree.Node.Real<>(toAdd), BigDecimal.ZERO);
        return ancestorNode;
    }

    public Tree<T, E> getTree() {
        return tree;
    }
}
