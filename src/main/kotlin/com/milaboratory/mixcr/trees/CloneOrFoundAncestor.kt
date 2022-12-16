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
import com.milaboratory.primitivio.readList
import com.milaboratory.primitivio.readObjectRequired
import com.milaboratory.primitivio.writeCollection

@Serializable(by = CloneOrFoundAncestor.SerializerImpl::class)
class CloneOrFoundAncestor(
    val id: Int,
    val clones: List<CloneWithDatasetId>,
    val mutationsSet: MutationsSet
) {
    class SerializerImpl : Serializer<CloneOrFoundAncestor> {
        override fun write(output: PrimitivO, obj: CloneOrFoundAncestor) {
            output.writeInt(obj.id)
            output.writeCollection(obj.clones, PrimitivO::writeObject)
            output.writeObject(obj.mutationsSet)
        }

        override fun read(input: PrimitivI): CloneOrFoundAncestor {
            val id = input.readInt()
            val clones = input.readList<CloneWithDatasetId> { readObjectRequired() }
            val mutationsSet = input.readObjectRequired<MutationsSet>()
            return CloneOrFoundAncestor(
                id,
                clones,
                mutationsSet
            )
        }
    }
}
