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
package com.milaboratory.mixcr.basictypes

import com.milaboratory.core.Range
import com.milaboratory.core.alignment.AffineGapAlignmentScoring
import com.milaboratory.core.alignment.Aligner
import com.milaboratory.core.sequence.NSequenceWithQuality
import com.milaboratory.core.sequence.NucleotideSequence
import io.repseq.core.GeneType

class VDJCAlignmentsFormatter(
    private val addReads: Boolean
) {
    companion object {
        @JvmStatic
        fun getTargetAsMultiAlignment(vdjcObject: VDJCObject, targetId: Int): MultiAlignmentHelper<NucleotideSequence> =
            VDJCAlignmentsFormatter(
                addReads = vdjcObject is VDJCAlignments && vdjcObject.getOriginalReads() != null
            ).formatMultiAlignments(vdjcObject, targetId)
    }

    fun formatMultiAlignments(vdjcObject: VDJCObject, targetId: Int): MultiAlignmentHelper<NucleotideSequence> {
        require(!(addReads && vdjcObject !is VDJCAlignments)) { "Read alignments supported only for VDJCAlignments." }
        val target = vdjcObject.getTarget(targetId)
        val partitioning = vdjcObject.getPartitionedTarget(targetId).partitioning
        val alignmentsInputs = mutableListOf<MultiAlignmentHelper.Input<NucleotideSequence>>()
        alignmentsInputs += inputsFromAlignments(vdjcObject, targetId)

        // Adding read information
        if (addReads) {
            alignmentsInputs += inputsFromReads(vdjcObject, targetId, target)
        }
        return MultiAlignmentHelper.build(
            MultiAlignmentHelper.DEFAULT_SETTINGS,
            Range(0, target.size()),
            name = "Target$targetId",
            target.sequence,
            alignmentsInputs,
            listOf(
                MultiAlignmentHelper.ReferencePointsInput(partitioning),
                MultiAlignmentHelper.AminoAcidInput(partitioning),
                MultiAlignmentHelper.QualityInput(target.quality)
            )
        )
    }

    private fun inputsFromReads(
        vdjcObject: VDJCObject,
        targetId: Int,
        target: NSequenceWithQuality
    ): List<MultiAlignmentHelper.Input<NucleotideSequence>> {
        val vdjcAlignments = vdjcObject as VDJCAlignments
        val history = vdjcAlignments.getHistory(targetId)
        val reads = history.rawReads()
        val map = reads.map { read ->
            val seq = vdjcAlignments.getOriginalSequence(read.index).sequence
            val offset = history.offset(read.index)
            val alignment = Aligner.alignOnlySubstitutions(
                target.sequence, seq, offset, seq.size(), 0, seq.size(),
                AffineGapAlignmentScoring.IGBLAST_NUCLEOTIDE_SCORING
            )
            MultiAlignmentHelper.ReadInput(
                read.index.toString(),
                alignment
            )
        }
        return map
    }

    private fun inputsFromAlignments(
        vdjcObject: VDJCObject,
        targetId: Int
    ): List<MultiAlignmentHelper.Input<NucleotideSequence>> {
        val target = vdjcObject.getTarget(targetId)
        return GeneType.values().flatMap { gt ->
            vdjcObject.getHits(gt).mapNotNull { hit ->
                val alignment = hit.getAlignment(targetId) ?: return@mapNotNull null
                MultiAlignmentHelper.AlignmentInput(
                    hit.gene.name,
                    alignment.invert(target.sequence),
                    hit.getAlignment(targetId).score.toInt(),
                    hit.score.toInt()
                )
            }
        }
    }
}
