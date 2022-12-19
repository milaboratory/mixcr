/*
 * Copyright (c) 2014-2022, MiLaboratories Inc. All Rights Reserved
 *
 * Before downloading or accessing the software, please read carefully the
 * License Agreement available at:
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 *
 * By downloading or accessing the software, you accept and agree to be bound
 * by the terms of the License Agreement. If you do not want to agree to the terms
 * of the Licensing Agreement, you must not download or access the software.
 */
package com.milaboratory.mixcr.trees

import com.milaboratory.primitivio.PrimitivI
import com.milaboratory.primitivio.PrimitivO
import com.milaboratory.primitivio.Serializer
import com.milaboratory.primitivio.annotations.Serializable
import com.milaboratory.primitivio.readObjectRequired

@Serializable(by = SHMTreeResult.SerializerImpl::class)
class SHMTreeResult(
    val tree: Tree<CloneOrFoundAncestor>,
    val rootInfo: RootInfo,
    val treeId: Int
) {
    val mostRecentCommonAncestor: CloneOrFoundAncestor
        get() = when (tree.root.links.size) {
            1 -> tree.root.links.first().node
            else -> tree.root
        }.content

    class SerializerImpl : Serializer<SHMTreeResult> {
        override fun write(output: PrimitivO, obj: SHMTreeResult) {
            TreeSerializer.writeTree(output, obj.tree)
            output.writeObject(obj.rootInfo)
            output.writeInt(obj.treeId)
        }

        override fun read(input: PrimitivI): SHMTreeResult {
            val tree = TreeSerializer.readTree<CloneOrFoundAncestor>(input)
            val rootInfo = input.readObjectRequired<RootInfo>()
            val treeId = input.readInt()
            return SHMTreeResult(
                tree,
                rootInfo,
                treeId
            )
        }
    }
}



