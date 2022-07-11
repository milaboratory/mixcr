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

import com.google.common.collect.Sets
import com.milaboratory.core.sequence.NSequenceWithQuality
import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.basictypes.VDJCHit
import com.milaboratory.mixcr.basictypes.VDJCPartitionedSequence
import com.milaboratory.primitivio.*
import com.milaboratory.primitivio.Serializer
import com.milaboratory.primitivio.annotations.Serializable
import io.repseq.core.*
import io.repseq.core.GeneType.*
import java.util.*

/**
 *
 */
@Serializable(by = SerializerImpl::class)
class CloneWrapper(
    /**
     * Original clonotype
     */
    val clone: Clone,
    /**
     * Dataset serial number
     */
    datasetId: Int,
    /**
     * Within this VJ pair and CDR3 length this clone will be viewed. There maybe several copies of one clone with different VJ
     */
    val VJBase: VJBase,
    val candidateVJBases: List<VJBase>
) {
    val id = ID(clone.id, datasetId)
    fun getHit(geneType: GeneType): VDJCHit = clone.getHit(geneType, VJBase)

    fun getFeature(geneFeature: GeneFeature): NSequenceWithQuality? = clone.getFeature(geneFeature, VJBase)

    fun getRelativePosition(geneType: GeneType, referencePoint: ReferencePoint): Int {
        val hit = getHit(geneType)
        return hit.gene.partitioning
            .getRelativePosition(hit.alignedFeature, referencePoint)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CloneWrapper

        if (VJBase != other.VJBase) return false
        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        var result = VJBase.hashCode()
        result = 31 * result + id.hashCode()
        return result
    }

    data class ID(
        val cloneId: Int,
        val datasetId: Int
    )
}

fun Clone.getHit(geneType: GeneType, VJBase: VJBase): VDJCHit =
    getHit(geneType, VJBase.getGeneId(geneType))

private fun Clone.getHit(geneType: GeneType, geneId: VDJCGeneId): VDJCHit =
    getHits(geneType).first { it.gene.id == geneId }

fun Clone.getFeature(geneFeature: GeneFeature, VJBase: VJBase): NSequenceWithQuality? =
    getFeature(geneFeature, VJBase.VGeneId, VJBase.JGeneId)

fun Clone.ntLengthOf(geneFeature: GeneFeature, VGeneId: VDJCGeneId, JGeneId: VDJCGeneId): Int =
    getFeature(geneFeature, VGeneId, JGeneId)?.size() ?: -1

private fun Clone.getFeature(
    geneFeature: GeneFeature,
    VGeneId: VDJCGeneId,
    JGeneId: VDJCGeneId
): NSequenceWithQuality? {
    val topHits = EnumMap<GeneType, VDJCGene>(GeneType::class.java)
    topHits[Variable] = getHit(Variable, VGeneId).gene
    topHits[Joining] = getHit(Joining, JGeneId).gene
    for (geneType in Sets.newHashSet(Constant, Diversity)) {
        val hit = getBestHit(geneType)
        if (hit != null) topHits[geneType] = hit.gene
    }
    val partitionedTargets = Array<VDJCPartitionedSequence>(targets.size) { i ->
        getPartitionedTarget(i, topHits)
    }
    val tcf = getTargetContainingFeature(partitionedTargets, geneFeature)
    return if (tcf == -1) null else partitionedTargets[tcf].getFeature(geneFeature)
}

private fun Clone.getTargetContainingFeature(
    partitionedTargets: Array<VDJCPartitionedSequence>,
    feature: GeneFeature
): Int {
    var tmp: NSequenceWithQuality?
    var targetIndex = -1
    val quality = -1
    for (i in targets.indices) {
        tmp = partitionedTargets[i].getFeature(feature)
        if (tmp != null && quality < tmp.quality.minValue()) targetIndex = i
    }
    return targetIndex
}

class SerializerImpl : Serializer<CloneWrapper> {
    override fun write(output: PrimitivO, `object`: CloneWrapper) {
        output.writeObject(`object`.clone)
        output.writeInt(`object`.id.datasetId)
        output.writeObject(`object`.VJBase)
        output.writeList(`object`.candidateVJBases)
    }

    override fun read(input: PrimitivI): CloneWrapper = CloneWrapper(
        input.readObjectRequired(),
        input.readInt(),
        input.readObjectRequired(),
        input.readList()
    )

    override fun isReference(): Boolean = true

    override fun handlesReference(): Boolean = false
}
