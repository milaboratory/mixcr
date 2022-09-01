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

import com.milaboratory.core.Range
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mitool.pattern.search.BasicSerializer
import com.milaboratory.mixcr.util.VJPair
import com.milaboratory.primitivio.PrimitivI
import com.milaboratory.primitivio.PrimitivO
import com.milaboratory.primitivio.annotations.Serializable
import com.milaboratory.primitivio.readObjectRequired
import io.repseq.core.ReferencePoints

/**
 * Common information about germline of the tree
 */
@Serializable(by = RootInfo.SerializerImpl::class)
data class RootInfo(
    /**
     * V and J germline for VJBase.VGeneId and VJBase.JGeneId
     */
    val sequence1: VJPair<NucleotideSequence>,
    val partitioning: VJPair<ReferencePoints>,
    /**
     * CDR3Part ranges in V and J coordinates
     */
    val rangeInCDR3: VJPair<Range>,
    /**
     * What is used as NDN in root.
     * It may be just sequence of N or reconstructed before NDN.
     * Has length VJBase.CDR3length
     */
    val reconstructedNDN: NucleotideSequence,
    val VJBase: VJBase
) {
    class SerializerImpl : BasicSerializer<RootInfo>() {
        override fun write(output: PrimitivO, obj: RootInfo) {
            output.writeObject(obj.sequence1.V)
            output.writeObject(obj.sequence1.J)
            output.writeObject(obj.partitioning.V)
            output.writeObject(obj.partitioning.J)
            output.writeObject(obj.rangeInCDR3.V)
            output.writeObject(obj.rangeInCDR3.J)
            output.writeObject(obj.reconstructedNDN)
            output.writeObject(obj.VJBase)
        }

        override fun read(input: PrimitivI): RootInfo = RootInfo(
            VJPair(
                input.readObjectRequired(),
                input.readObjectRequired()
            ),
            VJPair(
                input.readObjectRequired(),
                input.readObjectRequired()
            ),
            VJPair(
                input.readObjectRequired(),
                input.readObjectRequired()
            ),
            input.readObjectRequired(),
            input.readObjectRequired()
        )
    }
}
