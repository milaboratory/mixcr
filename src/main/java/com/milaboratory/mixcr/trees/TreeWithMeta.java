package com.milaboratory.mixcr.trees;

public class TreeWithMeta {
    private final Tree<TreeBuilderByAncestors.ObservedOrReconstructed<CloneWrapper, AncestorInfo>> tree;
    private final RootInfo rootInfo;
    private final TreeWithMetaBuilder.TreeId treeId;

    public TreeWithMeta(
            Tree<TreeBuilderByAncestors.ObservedOrReconstructed<CloneWrapper, AncestorInfo>> tree,
            RootInfo rootInfo,
            TreeWithMetaBuilder.TreeId treeId
    ) {
        this.tree = tree;
        this.rootInfo = rootInfo;
        this.treeId = treeId;
    }

    public TreeWithMetaBuilder.TreeId getTreeId() {
        return treeId;
    }

    public Tree<TreeBuilderByAncestors.ObservedOrReconstructed<CloneWrapper, AncestorInfo>> getTree() {
        return tree;
    }

    public RootInfo getRootInfo() {
        return rootInfo;
    }
}
