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
import com.milaboratory.core.sequence.AminoAcidAlphabet.INCOMPLETE_CODON
import com.milaboratory.core.sequence.AminoAcidSequence
import com.milaboratory.core.sequence.NSequenceWithQuality
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.core.sequence.SequenceQuality
import com.milaboratory.mixcr.basictypes.MultiAlignmentFormatter.POINTS_FOR_REARRANGED
import com.milaboratory.mixcr.basictypes.MultiAlignmentFormatter.PointToDraw
import com.milaboratory.mixcr.basictypes.MultiAlignmentHelper.AnnotationLine
import io.repseq.core.GeneType
import io.repseq.core.SequencePartitioning
import java.util.*

class VDJCAlignmentsFormatter(
    private val addReads: Boolean
) {
    companion object {
        @JvmStatic
        fun getTargetAsMultiAlignment(vdjcObject: VDJCObject, targetId: Int): MultiAlignmentHelper<NucleotideSequence> =
            VDJCAlignmentsFormatter(
                addReads = vdjcObject is VDJCAlignments && vdjcObject.getOriginalReads() != null
            ).formatMultiAlignments(vdjcObject, targetId)

        fun MultiAlignmentHelper<*>.makeQualityLine(quality: SequenceQuality): AnnotationLine {
            val chars = CharArray(size())
            for (i in 0 until size()) chars[i] = when {
                subject.positions[i] < 0 -> ' '
                else -> simplifiedQuality(quality.value(subject.positions[i]).toInt())
            }
            return MultiAlignmentHelper.QualityLine(content = String(chars))
        }

        private fun simplifiedQuality(value: Int): Char {
            var result = value
            result /= 5
            if (result > 9) result = 9
            return result.toString()[0]
        }

        fun MultiAlignmentHelper<*>.makeAALine(
            partitioning: SequencePartitioning,
            target: NucleotideSequence
        ): AnnotationLine {
            val trParams = partitioning.getTranslationParameters(target.size())
            val line = CharArray(size())
            Arrays.fill(line, ' ')
            for (trParam in trParams) {
                val mainSequence = target.getRange(trParam.range)
                val leftover =
                    if (trParam.codonLeftoverRange == null) null else target.getRange(trParam.codonLeftoverRange)
                val bigSeq = when {
                    leftover == null -> mainSequence
                    trParam.leftIncompleteCodonRange() != null -> leftover.concatenate(mainSequence)
                    else -> mainSequence.concatenate(leftover)
                }
                val aa = AminoAcidSequence.translate(bigSeq, trParam.translationParameters)
                var aaPosition = 0
                var ntPosition = trParam.range.from + AminoAcidSequence.convertAAPositionToNt(
                    aaPosition, mainSequence.size(), trParam.translationParameters
                )
                if (aa.codeAt(aaPosition) == INCOMPLETE_CODON) {
                    line[subjectToAlignmentPosition(ntPosition)] =
                        AminoAcidSequence.ALPHABET.codeToSymbol(aa.codeAt(aaPosition)) // '_'
                    ++aaPosition
                }
                while (aaPosition < aa.size() && (aaPosition < aa.size() - 1 || aa.codeAt(aaPosition) != INCOMPLETE_CODON)) {
                    ntPosition = trParam.range.from + AminoAcidSequence.convertAAPositionToNt(
                        aaPosition, bigSeq.size(), trParam.translationParameters
                    )
                    if (leftover != null && trParam.leftIncompleteCodonRange() != null) {
                        ntPosition -= trParam.leftIncompleteCodonRange().length()
                    }
                    var isLeftover = false
                    if (leftover != null) {
                        isLeftover = when {
                            trParam.leftIncompleteCodonRange() != null -> aaPosition == 0
                            else -> aaPosition == aa.size() - 1
                        }
                    }
                    if (aa.codeAt(aaPosition) != INCOMPLETE_CODON) {
                        ++ntPosition
                    }
                    var c = AminoAcidSequence.ALPHABET.codeToSymbol(aa.codeAt(aaPosition))
                    if (isLeftover) c = c.lowercaseChar()
                    line[subjectToAlignmentPosition(ntPosition)] = c
                    ++aaPosition
                }
                if (aaPosition < aa.size() && (aaPosition < aa.size() - 1 || aa.codeAt(aaPosition) == INCOMPLETE_CODON)) {
                    ntPosition = trParam.range.from + AminoAcidSequence.convertAAPositionToNt(
                        aaPosition, mainSequence.size(), trParam.translationParameters
                    )
                    line[subjectToAlignmentPosition(ntPosition)] =
                        AminoAcidSequence.ALPHABET.codeToSymbol(aa.codeAt(aaPosition))
                }
            }
            return MultiAlignmentHelper.AminoAcidsLine(content = String(line))
        }

        private fun MultiAlignmentHelper<*>.makePointsLines(
            pointsToDraw: Array<out PointToDraw>,
            partitioning: SequencePartitioning
        ): List<AnnotationLine> {
            val markers = mutableListOf<CharArray>()
            markers += emptyLine(size())
            var result: Boolean
            var i: Int
            for (point in pointsToDraw) {
                i = 0
                do {
                    if (markers.size == i) markers += emptyLine(size())
                    result = point.draw(partitioning, this, markers[i++], false)
                } while (!result)
            }
            return markers.reversed().map { marker ->
                MultiAlignmentHelper.ReferencePointsLine(
                    partitioning,
                    content = String(marker)
                )
            }
        }

        private fun emptyLine(size: Int): CharArray {
            val markers = CharArray(size)
            Arrays.fill(markers, ' ')
            return markers
        }
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
        val helper = MultiAlignmentHelper.build(
            MultiAlignmentHelper.DEFAULT_SETTINGS,
            Range(0, target.size()),
            name = "Target$targetId",
            target.sequence,
            alignmentsInputs,
//            listOf(
//                MultiAlignmentHelper.ReferencePointsInput(partitioning),
//                MultiAlignmentHelper.AminoAcidInput(partitioning),
//                MultiAlignmentHelper.QualityInput(target.quality)
//            )
        )
        if (alignmentsInputs.isNotEmpty()) {
            helper.makePointsLines(POINTS_FOR_REARRANGED, partitioning)
                .forEach {
                    helper.addAnnotation(it)
                }
        }
        helper.addAnnotation(helper.makeAALine(partitioning, target.sequence))
        helper.addAnnotation(helper.makeQualityLine(target.quality))
        return helper
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
