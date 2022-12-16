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

import com.milaboratory.core.Range
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.util.VJPair
import com.milaboratory.mixcr.util.readPair
import com.milaboratory.mixcr.util.writePair
import com.milaboratory.primitivio.PrimitivI
import com.milaboratory.primitivio.PrimitivO
import com.milaboratory.primitivio.Serializer
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
    class SerializerImpl : Serializer<RootInfo> {
        override fun write(output: PrimitivO, obj: RootInfo) {
            output.writePair(obj.sequence1)
            output.writePair(obj.partitioning)
            output.writePair(obj.rangeInCDR3)
            output.writeObject(obj.reconstructedNDN)
            output.writeObject(obj.VJBase)
        }

        override fun read(input: PrimitivI): RootInfo {
            val sequence1 = input.readPair<NucleotideSequence>()
            val partitioning = input.readPair<ReferencePoints>()
            val rangeInCDR3 = input.readPair<Range>()
            val reconstructedNDN = input.readObjectRequired<NucleotideSequence>()
            val VJBase = input.readObjectRequired<VJBase>()
            return RootInfo(
                sequence1,
                partitioning,
                rangeInCDR3,
                reconstructedNDN,
                VJBase
            )
        }
    }
}
