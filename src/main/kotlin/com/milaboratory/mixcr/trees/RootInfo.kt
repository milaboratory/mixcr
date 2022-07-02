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
import com.milaboratory.primitivio.*
import com.milaboratory.primitivio.annotations.Serializable
import io.repseq.core.GeneFeature
import io.repseq.core.GeneType
import io.repseq.core.GeneType.Joining
import io.repseq.core.GeneType.Variable
import io.repseq.core.ReferencePoints

/**
 * Common information about germline of the tree
 */
@Serializable(by = RootInfoSerializer::class)
data class RootInfo(
    /**
     * V germline for VJBase.VGeneId
     */
    val VSequence: NucleotideSequence,
    val VPartitioning: ReferencePoints,
    val VAlignFeatures: List<GeneFeature>,
    /**
     * VCDR3Part range in V coordinates
     */
    val VRangeInCDR3: Range,
    /**
     * What is used as NDN in root.
     * It may be just sequence of N or reconstructed before NDN.
     * Has length VJBase.CDR3length
     */
    val reconstructedNDN: NucleotideSequence,
    /**
     * J germline for VJBase.JGeneId
     */
    val JSequence: NucleotideSequence,
    val JPartitioning: ReferencePoints,
    val JAlignFeatures: List<GeneFeature>,
    /**
     * JCDR3Part range in J coordinates
     */
    val JRangeInCDR3: Range,
    val VJBase: VJBase
) {
    fun getSequence1(geneType: GeneType) = when (geneType) {
        Joining -> JSequence
        Variable -> VSequence
        else -> throw IllegalArgumentException()
    }

    fun getPartitioning(geneType: GeneType) = when (geneType) {
        Joining -> JPartitioning
        Variable -> VPartitioning
        else -> throw IllegalArgumentException()
    }
}

class RootInfoSerializer : Serializer<RootInfo> {
    override fun write(output: PrimitivO, obj: RootInfo) {
        output.writeObject(obj.VSequence)
        output.writeObject(obj.VPartitioning)
        output.writeList(obj.VAlignFeatures)
        output.writeObject(obj.VRangeInCDR3)
        output.writeObject(obj.reconstructedNDN)
        output.writeObject(obj.JSequence)
        output.writeObject(obj.JPartitioning)
        output.writeList(obj.JAlignFeatures)
        output.writeObject(obj.JRangeInCDR3)
        output.writeObject(obj.VJBase)
    }

    override fun read(input: PrimitivI): RootInfo = RootInfo(
        input.readObjectRequired(),
        input.readObjectRequired(),
        input.readList(),
        input.readObjectRequired(),
        input.readObjectRequired(),
        input.readObjectRequired(),
        input.readObjectRequired(),
        input.readList(),
        input.readObjectRequired(),
        input.readObjectRequired()
    )

    override fun isReference(): Boolean = false

    override fun handlesReference(): Boolean = false
}
