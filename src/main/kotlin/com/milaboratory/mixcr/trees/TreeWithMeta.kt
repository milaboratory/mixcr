package com.milaboratory.mixcr.trees

import com.milaboratory.mixcr.trees.TreeWithMetaBuilder.TreeId

class TreeWithMeta(
    val tree: Tree<CloneOrFoundAncestorOld>,
    val rootInfo: RootInfo,
    val treeId: TreeId
)
