package com.milaboratory.mixcr.trees;

public class TreeWithMeta {
    private final Tree<TreeBuilderByAncestors.ObservedOrReconstructed<CloneWrapper, AncestorInfo>> tree;
    private final RootInfo rootInfo;
    private final VJBase VJBase;

    public TreeWithMeta(
            Tree<TreeBuilderByAncestors.ObservedOrReconstructed<CloneWrapper, AncestorInfo>> tree,
            RootInfo rootInfo,
            VJBase vjBase) {
        this.tree = tree;
        this.rootInfo = rootInfo;
        VJBase = vjBase;
    }

    public VJBase getVJBase() {
        return VJBase;
    }

    public Tree<TreeBuilderByAncestors.ObservedOrReconstructed<CloneWrapper, AncestorInfo>> getTree() {
        return tree;
    }

    public RootInfo getRootInfo() {
        return rootInfo;
    }
}
