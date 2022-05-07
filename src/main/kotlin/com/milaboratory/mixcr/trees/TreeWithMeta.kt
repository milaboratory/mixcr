package com.milaboratory.mixcr.trees;

public class TreeWithMeta {
    private final Tree<CloneOrFoundAncestor> tree;
    private final RootInfo rootInfo;
    private final TreeWithMetaBuilder.TreeId treeId;

    public TreeWithMeta(
            Tree<CloneOrFoundAncestor> tree,
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

    public Tree<CloneOrFoundAncestor> getTree() {
        return tree;
    }

    public RootInfo getRootInfo() {
        return rootInfo;
    }
}
