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

import com.google.common.collect.Sets
import com.milaboratory.core.sequence.AminoAcidSequence
import com.milaboratory.core.sequence.NSequenceWithQuality
import com.milaboratory.mitool.pattern.search.BasicSerializer
import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.basictypes.VDJCHit
import com.milaboratory.mixcr.basictypes.VDJCPartitionedSequence
import com.milaboratory.primitivio.PrimitivI
import com.milaboratory.primitivio.PrimitivO
import com.milaboratory.primitivio.annotations.Serializable
import com.milaboratory.primitivio.readList
import com.milaboratory.primitivio.readObjectRequired
import com.milaboratory.primitivio.writeCollection
import io.repseq.core.GeneFeature
import io.repseq.core.GeneType
import io.repseq.core.GeneType.Constant
import io.repseq.core.GeneType.Diversity
import io.repseq.core.GeneType.Joining
import io.repseq.core.GeneType.Variable
import io.repseq.core.ReferencePoints
import io.repseq.core.VDJCGene
import io.repseq.core.VDJCGeneId
import java.util.*
import kotlin.collections.set

@Serializable(by = CloneWrapper.SerializerImpl::class)
class CloneWrapper(
    /**
     * Original clones with the same targets, but from different files or with different C gene
     */
    val clones: List<CloneWithDatasetId>,
    /**
     * Within this VJ pair and CDR3 length this clone will be viewed. There maybe several copies of one clone with different VJ
     */
    val VJBase: VJBase,
    val candidateVJBases: List<VJBase> = emptyList()
) {
    val id = ID(clones.map { it.id })
    val mainClone = chooseMainClone(clones.map { it.clone })

    fun getHit(geneType: GeneType): VDJCHit = mainClone.getHit(VJBase, geneType)

    fun getFeature(geneFeature: GeneFeature): NSequenceWithQuality? =
        mainClone.getFeature(geneFeature, VJBase)

    fun getPartitioning(geneType: GeneType): ReferencePoints =
        getHit(geneType).gene.partitioning.getRelativeReferencePoints(getHit(geneType).alignedFeature)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CloneWrapper

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    data class ID(
        val ids: List<CloneWithDatasetId.ID>
    )

    class SerializerImpl : BasicSerializer<CloneWrapper>() {
        override fun write(output: PrimitivO, obj: CloneWrapper) {
            output.writeCollection(obj.clones, PrimitivO::writeObject)
            output.writeObject(obj.VJBase)
            output.writeCollection(obj.candidateVJBases, PrimitivO::writeObject)
        }

        override fun read(input: PrimitivI): CloneWrapper {
            val clones = input.readList<CloneWithDatasetId>(PrimitivI::readObjectRequired)
            val VJBase = input.readObjectRequired<VJBase>()
            val candidateVJBases = input.readList<VJBase>(PrimitivI::readObjectRequired)
            return CloneWrapper(
                clones,
                VJBase,
                candidateVJBases
            )
        }
    }

    companion object {
        /**
         * Algorithm can't distinct evolution of clones that differs outside assemble feature.
         * But we need to choose one base for all clones.
         */
        fun chooseMainClone(clones: List<Clone>): Clone =
            clones.maxWithOrNull(
                Comparator.comparingDouble<Clone> { (it.getBestHit(Variable).score + it.getBestHit(Joining).score).toDouble() }
                    .thenComparingDouble { it.count }
            )!!

        val comparatorMaxFractionFirst: Comparator<CloneWrapper> = Comparator
            .comparingDouble { cloneWrapper: CloneWrapper -> cloneWrapper.clones.maxOf { it.clone.fraction } }
            .reversed()
            //just for reproducible order
            .thenComparingInt { cloneWrapper: CloneWrapper ->
                cloneWrapper.clones.map { it.clone.id }.reduce { acc, i -> acc * i }
            }
    }
}

@Serializable(by = CloneWithDatasetId.SerializerImpl::class)
data class CloneWithDatasetId(
    val clone: Clone,
    val datasetId: Int
) {
    val id
        get() = ID(
            clone.id,
            datasetId
        )

    data class ID(
        val cloneId: Int,
        val datasetId: Int
    ) {
        fun encode() = "$datasetId:$cloneId"

        companion object {
            fun decode(text: String): ID {
                val (datasetId, cloneId) = text.split(":")
                return ID(
                    datasetId.toInt(),
                    cloneId.toInt()
                )
            }
        }
    }

    class SerializerImpl : BasicSerializer<CloneWithDatasetId>() {
        override fun write(output: PrimitivO, obj: CloneWithDatasetId) {
            output.writeObject(obj.clone)
            output.writeInt(obj.datasetId)
        }

        override fun read(input: PrimitivI): CloneWithDatasetId {
            val clone = input.readObjectRequired<Clone>()
            val datasetId = input.readInt()
            return CloneWithDatasetId(
                clone,
                datasetId
            )
        }
    }
}

fun Clone.containsStopsOrAbsent(feature: GeneFeature, VJBase: VJBase): Boolean {
    val codingFeature = GeneFeature.getCodingGeneFeature(feature) ?: return true
    val partitionedTargets = getPartitionedTargets(VJBase.geneIds.V, VJBase.geneIds.J)
    for (i in partitionedTargets.indices) {
        val codingSeq = partitionedTargets[i].getFeature(codingFeature) ?: continue
        val tr = partitionedTargets[i].partitioning.getTranslationParameters(codingFeature) ?: return true
        if (AminoAcidSequence.translate(codingSeq.sequence, tr).containStops()) return true
    }
    return false
}

fun Clone.isOutOfFrameOrAbsent(feature: GeneFeature, VJBase: VJBase): Boolean {
    val nt = getFeature(feature, VJBase)
    return nt == null || nt.size() % 3 != 0
}

fun Clone.getHit(VJBase: VJBase, geneType: GeneType): VDJCHit =
    getHit(geneType, VJBase.geneIds[geneType])

private fun Clone.getHit(geneType: GeneType, geneId: VDJCGeneId): VDJCHit =
    getHits(geneType).first { it.gene.id == geneId }

fun Clone.getFeature(geneFeature: GeneFeature, VJBase: VJBase): NSequenceWithQuality? =
    getFeature(geneFeature, VJBase.geneIds.V, VJBase.geneIds.J)

fun Clone.ntLengthOf(geneFeature: GeneFeature, VGeneId: VDJCGeneId, JGeneId: VDJCGeneId): Int =
    getFeature(geneFeature, VGeneId, JGeneId)?.size() ?: -1

private fun Clone.getFeature(
    geneFeature: GeneFeature,
    VGeneId: VDJCGeneId,
    JGeneId: VDJCGeneId
): NSequenceWithQuality? {
    val partitionedTargets = getPartitionedTargets(VGeneId, JGeneId)
    val tcf = getTargetContainingFeature(partitionedTargets, geneFeature)
    return if (tcf == -1) null else partitionedTargets[tcf].getFeature(geneFeature)
}

private fun Clone.getPartitionedTargets(
    VGeneId: VDJCGeneId,
    JGeneId: VDJCGeneId
): Array<VDJCPartitionedSequence> {
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
    return partitionedTargets
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
