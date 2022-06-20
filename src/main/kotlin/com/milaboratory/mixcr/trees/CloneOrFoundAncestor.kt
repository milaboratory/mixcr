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
import java.math.BigDecimal

@Serializable(by = CloneOrFoundAncestorSerializer::class)
class CloneOrFoundAncestor(
    val id: Int,
    val clone: Clone?,
    val datasetId: Int?,
    val mutationsSet: MutationsSet,
    val distanceFromRoot: BigDecimal,
    val distanceFromMostRecentAncestor: BigDecimal?
)

class CloneOrFoundAncestorSerializer : Serializer<CloneOrFoundAncestor> {
    override fun write(output: PrimitivO, obj: CloneOrFoundAncestor) {
        output.writeInt(obj.id)
        output.writeObject(obj.clone)
        output.writeInt(obj.datasetId ?: -1)
        output.writeObject(obj.mutationsSet)
        output.writeDouble(obj.distanceFromRoot.toDouble())
        output.writeDouble(obj.distanceFromMostRecentAncestor?.toDouble() ?: Double.NaN)
    }

    override fun read(input: PrimitivI): CloneOrFoundAncestor = CloneOrFoundAncestor(
        input.readInt(),
        input.readObjectOptional(),
        input.readInt().let { if (it == -1) null else it },
        input.readObjectRequired(),
        BigDecimal.valueOf(input.readDouble()),
        input.readDouble().let { if (it.isNaN()) null else BigDecimal.valueOf(it) }
    )

    override fun isReference(): Boolean = false

    override fun handlesReference(): Boolean = false

}
