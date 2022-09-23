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
@file:Suppress("LocalVariableName")

package com.milaboratory.mixcr.trees

import com.milaboratory.mitool.pattern.search.BasicSerializer
import com.milaboratory.mixcr.util.VJPair
import com.milaboratory.primitivio.PrimitivI
import com.milaboratory.primitivio.PrimitivO
import com.milaboratory.primitivio.annotations.Serializable
import com.milaboratory.primitivio.readObjectRequired
import io.repseq.core.VDJCGeneId

/**
 * On this VJ and CDR3length based a cluster or a tree.
 * I.e. all clones will be aligned on this V and J, and all clones have this CDR3length
 */
@Serializable(by = VJBase.SerializerImpl::class)
data class VJBase(
    val geneIds: VJPair<VDJCGeneId>,
    val CDR3length: Int
) {
    constructor(
        VGeneId: VDJCGeneId,
        JGeneId: VDJCGeneId,
        CDR3length: Int
    ) : this(VJPair(VGeneId, JGeneId), CDR3length)


    companion object {
        val comparator: Comparator<VJBase> = Comparator.comparing { VJBase: VJBase -> VJBase.geneIds.V }
            .thenComparing { VJBase: VJBase -> VJBase.geneIds.J }
            .thenComparingInt { it.CDR3length }
    }

    class SerializerImpl : BasicSerializer<VJBase>() {
        override fun write(output: PrimitivO, obj: VJBase) {
            output.writeObject(obj.geneIds.V)
            output.writeObject(obj.geneIds.J)
            output.writeInt(obj.CDR3length)
        }

        override fun read(input: PrimitivI): VJBase {
            val VGeneId = input.readObjectRequired<VDJCGeneId>()
            val JGeneId = input.readObjectRequired<VDJCGeneId>()
            val CDR3length = input.readInt()
            return VJBase(
                VGeneId,
                JGeneId,
                CDR3length
            )
        }
    }

    override fun toString(): String = "VJBase(V=${geneIds.V.name}, J=${geneIds.J.name}, CDR3length=$CDR3length)"
}
