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
