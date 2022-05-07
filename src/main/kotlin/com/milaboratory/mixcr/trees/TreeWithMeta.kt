package com.milaboratory.mixcr.trees

import com.milaboratory.mixcr.trees.TreeWithMetaBuilder.TreeId

class TreeWithMeta(
    val tree: Tree<CloneOrFoundAncestor>,
    val rootInfo: RootInfo,
    val treeId: TreeId
)
