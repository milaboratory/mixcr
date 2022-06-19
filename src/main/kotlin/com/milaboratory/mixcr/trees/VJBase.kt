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
import io.repseq.core.GeneType
import io.repseq.core.VDJCGeneId

@Serializable(by = VJBaseSerializer::class)
data class VJBase(
    val VGeneId: VDJCGeneId,
    val JGeneId: VDJCGeneId,
    val CDR3length: Int
) {
    fun getGeneId(geneType: GeneType): VDJCGeneId = when (geneType) {
        GeneType.Variable -> VGeneId
        GeneType.Joining -> JGeneId
        else -> throw IllegalArgumentException()
    }

    companion object {
        val comparator: Comparator<VJBase> = Comparator.comparing<VJBase, VDJCGeneId> { it.VGeneId }
            .thenComparing<VDJCGeneId> { it.JGeneId }
            .thenComparingInt { it.CDR3length }
    }
}

class VJBaseSerializer : Serializer<VJBase> {
    override fun write(output: PrimitivO, obj: VJBase) {
        output.writeObject(obj.VGeneId)
        output.writeObject(obj.JGeneId)
        output.writeInt(obj.CDR3length)
    }

    override fun read(input: PrimitivI): VJBase = VJBase(
        input.readObjectRequired(),
        input.readObjectRequired(),
        input.readInt()
    )

    override fun isReference(): Boolean = false

    override fun handlesReference(): Boolean = false
}
