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

import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.primitivio.PrimitivI
import com.milaboratory.primitivio.PrimitivO
import com.milaboratory.primitivio.Serializer
import com.milaboratory.primitivio.annotations.Serializable
import com.milaboratory.primitivio.readObjectOptional
import com.milaboratory.primitivio.readObjectRequired

@Serializable(by = CloneOrFoundAncestorSerializer::class)
class CloneOrFoundAncestor(
    val id: Int,
    val clone: Clone?,
    val datasetId: Int?,
    val mutationsSet: MutationsSet
)

class CloneOrFoundAncestorSerializer : Serializer<CloneOrFoundAncestor> {
    override fun write(output: PrimitivO, obj: CloneOrFoundAncestor) {
        output.writeInt(obj.id)
        output.writeObject(obj.clone)
        output.writeInt(obj.datasetId ?: -1)
        output.writeObject(obj.mutationsSet)
    }

    override fun read(input: PrimitivI): CloneOrFoundAncestor = CloneOrFoundAncestor(
        input.readInt(),
        input.readObjectOptional(),
        input.readInt().let { if (it == -1) null else it },
        input.readObjectRequired()
    )

    override fun isReference(): Boolean = false

    override fun handlesReference(): Boolean = false

}
