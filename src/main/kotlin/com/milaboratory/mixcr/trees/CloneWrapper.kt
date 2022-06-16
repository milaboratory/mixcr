package com.milaboratory.mixcr.trees

import com.google.common.collect.Sets
import com.milaboratory.core.sequence.NSequenceWithQuality
import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.basictypes.VDJCHit
import com.milaboratory.mixcr.basictypes.VDJCPartitionedSequence
import com.milaboratory.primitivio.PrimitivI
import com.milaboratory.primitivio.PrimitivO
import com.milaboratory.primitivio.Serializer
import com.milaboratory.primitivio.annotations.Serializable
import com.milaboratory.primitivio.readObjectRequired
import io.repseq.core.GeneFeature
import io.repseq.core.GeneType
import io.repseq.core.ReferencePoint
import io.repseq.core.VDJCGene
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
    val datasetId: Int,
    val VJBase: VJBase
) {
    fun getHit(geneType: GeneType): VDJCHit {
        val geneId = VJBase.getGeneId(geneType)
        return clone.getHits(geneType).first { it.gene.id == geneId }
    }

    fun getFeature(geneFeature: GeneFeature): NSequenceWithQuality? {
        val topHits = EnumMap<GeneType, VDJCGene>(GeneType::class.java)
        for (geneType in Sets.newHashSet(GeneType.Joining, GeneType.Variable)) {
            topHits[geneType] = getHit(geneType).gene
        }
        for (geneType in Sets.newHashSet(GeneType.Constant, GeneType.Diversity)) {
            val hits = clone.hits[geneType]
            if (hits != null && hits.isNotEmpty()) topHits[geneType] = hits[0].gene
        }
        val partitionedTargets = Array<VDJCPartitionedSequence>(clone.targets.size) { i ->
            clone.getPartitionedTarget(i, topHits)
        }
        val tcf = getTargetContainingFeature(partitionedTargets, geneFeature)
        return if (tcf == -1) null else partitionedTargets[tcf].getFeature(geneFeature)
    }

    private fun getTargetContainingFeature(
        partitionedTargets: Array<VDJCPartitionedSequence>,
        feature: GeneFeature
    ): Int {
        var tmp: NSequenceWithQuality?
        var targetIndex = -1
        val quality = -1
        for (i in clone.targets.indices) {
            tmp = partitionedTargets[i].getFeature(feature)
            if (tmp != null && quality < tmp.quality.minValue()) targetIndex = i
        }
        return targetIndex
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as CloneWrapper
        return datasetId == that.datasetId && clone == that.clone
    }

    override fun hashCode(): Int = Objects.hash(clone, datasetId)

    fun getRelativePosition(geneType: GeneType, referencePoint: ReferencePoint): Int {
        val hit = getHit(geneType)
        return hit.gene.partitioning
            .getRelativePosition(hit.alignedFeature, referencePoint)
    }
}

class SerializerImpl : Serializer<CloneWrapper> {
    override fun write(output: PrimitivO, `object`: CloneWrapper) {
        output.writeObject(`object`.clone)
        output.writeInt(`object`.datasetId)
        output.writeObject(`object`.VJBase.VGeneId)
        output.writeObject(`object`.VJBase.JGeneId)
        output.writeInt(`object`.VJBase.CDR3length)
    }

    override fun read(input: PrimitivI): CloneWrapper = CloneWrapper(
        input.readObjectRequired(),
        input.readInt(),
        VJBase(input.readObjectRequired(), input.readObjectRequired(), input.readInt())
    )

    override fun isReference(): Boolean = true

    override fun handlesReference(): Boolean = false
}
