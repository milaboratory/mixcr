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

@Serializable(by = SHMTreeSerializer::class)
class SHMTreeResult(
    val tree: Tree<CloneOrFoundAncestor>,
    val rootInfo: RootInfo,
    val treeId: Int
) {
    val mostRecentCommonAncestor: CloneOrFoundAncestor
        get() = tree.root
            .links
            .map { it.node.content }
            .first { it.clone == null }
}

class SHMTreeSerializer : Serializer<SHMTreeResult> {
    override fun write(output: PrimitivO, obj: SHMTreeResult) {
        TreeSerializer.writeTree(output, obj.tree)
        output.writeObject(obj.rootInfo)
        output.writeInt(obj.treeId)
    }

    override fun read(input: PrimitivI): SHMTreeResult = SHMTreeResult(
        TreeSerializer.readTree(input),
        input.readObjectRequired(),
        input.readInt()
    )

    override fun isReference(): Boolean = false

    override fun handlesReference(): Boolean = false
}



