package com.milaboratory.mixcr.trees

import com.google.common.collect.Sets
import com.milaboratory.core.sequence.NSequenceWithQuality
import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.basictypes.TargetPartitioning
import com.milaboratory.mixcr.basictypes.VDJCHit
import com.milaboratory.mixcr.basictypes.VDJCPartitionedSequence
import com.milaboratory.primitivio.PrimitivI
import com.milaboratory.primitivio.PrimitivO
import com.milaboratory.primitivio.Serializer
import com.milaboratory.primitivio.annotations.Serializable
import io.repseq.core.GeneFeature
import io.repseq.core.GeneType
import io.repseq.core.ReferencePoint
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
        val geneName = VJBase.getGeneName(geneType)
        return Arrays.stream(clone.getHits(geneType))
            .filter { it.gene.name == geneName }
            .findAny()
            .orElseThrow { IllegalArgumentException() }
    }

    fun getFeature(geneFeature: GeneFeature): NSequenceWithQuality? {
        val partitionedTargets = arrayOfNulls<VDJCPartitionedSequence>(clone.targets.size)
        val topHits = EnumMap<GeneType, VDJCHit>(GeneType::class.java)
        for (geneType in Sets.newHashSet(GeneType.Joining, GeneType.Variable)) {
            topHits[geneType] = getHit(geneType)
        }
        for (geneType in Sets.newHashSet(GeneType.Constant, GeneType.Diversity)) {
            val hits = clone.hits[geneType]
            if (hits != null && hits.size > 0) topHits[geneType] = hits[0]
        }
        for (i in clone.targets.indices) partitionedTargets[i] =
            VDJCPartitionedSequence(clone.getTarget(i), TargetPartitioning(i, topHits))
        val tcf = getTargetContainingFeature(partitionedTargets, geneFeature)
        return if (tcf == -1) null else partitionedTargets[tcf]!!.getFeature(geneFeature)
    }

    private fun getTargetContainingFeature(
        partitionedTargets: Array<VDJCPartitionedSequence?>,
        feature: GeneFeature
    ): Int {
        var tmp: NSequenceWithQuality?
        var targetIndex = -1
        val quality = -1
        for (i in clone.targets.indices) {
            tmp = partitionedTargets[i]!!.getFeature(feature)
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
        output.writeUTF(`object`.VJBase.VGeneName)
        output.writeUTF(`object`.VJBase.JGeneName)
        output.writeInt(`object`.VJBase.CDR3length)
    }

    override fun read(input: PrimitivI): CloneWrapper = CloneWrapper(
        input.readObject(Clone::class.java),
        input.readInt(),
        VJBase(input.readUTF(), input.readUTF(), input.readInt())
    )

    override fun isReference(): Boolean = true

    override fun handlesReference(): Boolean = false
}
