package com.milaboratory.mixcr.trees;

public class TreeWithMeta {
    private final Tree<TreeBuilderByAncestors.ObservedOrReconstructed<CloneWrapper, AncestorInfo>> tree;
    private final RootInfo rootInfo;

    public TreeWithMeta(
            Tree<TreeBuilderByAncestors.ObservedOrReconstructed<CloneWrapper, AncestorInfo>> tree,
            RootInfo rootInfo
    ) {
        this.tree = tree;
        this.rootInfo = rootInfo;
    }

    public Tree<TreeBuilderByAncestors.ObservedOrReconstructed<CloneWrapper, AncestorInfo>> getTree() {
        return tree;
    }

    public RootInfo getRootInfo() {
        return rootInfo;
    }
}
