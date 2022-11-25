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

import cc.redberry.primitives.Filter
import cc.redberry.primitives.FilterUtil
import com.milaboratory.core.Range
import com.milaboratory.core.alignment.AffineGapAlignmentScoring
import com.milaboratory.core.alignment.Aligner
import com.milaboratory.core.sequence.AminoAcidAlphabet.INCOMPLETE_CODON
import com.milaboratory.core.sequence.AminoAcidSequence
import com.milaboratory.core.sequence.NSequenceWithQuality
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.core.sequence.SequenceQuality
import com.milaboratory.mixcr.basictypes.MultiAlignmentHelper.AnnotationLine
import io.repseq.core.GeneType
import io.repseq.core.ReferencePoint
import io.repseq.core.SequencePartitioning
import java.util.*

class VDJCAlignmentsFormatter(
    private val addReads: Boolean
) {
    companion object {
        @JvmStatic
        fun getTargetAsMultiAlignment(vdjcObject: VDJCObject, targetId: Int): MultiAlignmentHelper =
            VDJCAlignmentsFormatter(
                addReads = vdjcObject is VDJCAlignments && vdjcObject.getOriginalReads() != null
            ).formatMultiAlignments(vdjcObject, targetId)

        fun MultiAlignmentHelper.makeQualityLine(quality: SequenceQuality): AnnotationLine {
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

        fun MultiAlignmentHelper.makeAALine(
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
                var ntPosition = (trParam.range.from
                        + AminoAcidSequence.convertAAPositionToNt(
                    aaPosition, mainSequence.size(), trParam.translationParameters
                ))
                if (aa.codeAt(aaPosition) == INCOMPLETE_CODON) {
                    line[subjectToAlignmentPosition(ntPosition)] =
                        AminoAcidSequence.ALPHABET.codeToSymbol(aa.codeAt(aaPosition)) // '_'
                    ++aaPosition
                }
                while (aaPosition < aa.size() && (aaPosition < aa.size() - 1 || aa.codeAt(aaPosition) != INCOMPLETE_CODON)) {
                    ntPosition = (trParam.range.from
                            + AminoAcidSequence.convertAAPositionToNt(
                        aaPosition, bigSeq.size(), trParam.translationParameters
                    ))
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
                    ntPosition = (trParam.range.from
                            + AminoAcidSequence.convertAAPositionToNt(
                        aaPosition, mainSequence.size(), trParam.translationParameters
                    ))
                    line[subjectToAlignmentPosition(ntPosition)] =
                        AminoAcidSequence.ALPHABET.codeToSymbol(aa.codeAt(aaPosition))
                }
            }
            return MultiAlignmentHelper.AminoAcidsLine(content = String(line))
        }

        private fun MultiAlignmentHelper.makePointsLines(
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
                    content = String(marker)
                )
            }
        }

        private fun emptyLine(size: Int): CharArray {
            val markers = CharArray(size)
            Arrays.fill(markers, ' ')
            return markers
        }

        private val IsVP: Filter<SequencePartitioning> = Filter<SequencePartitioning> { `object` ->
            `object`.isAvailable(ReferencePoint.VEnd) && `object`.getPosition(ReferencePoint.VEnd) != `object`.getPosition(
                ReferencePoint.VEndTrimmed
            )
        }
        private val IsJP: Filter<SequencePartitioning> = Filter<SequencePartitioning> { `object` ->
            `object`.isAvailable(ReferencePoint.JBegin) && `object`.getPosition(ReferencePoint.JBegin) != `object`.getPosition(
                ReferencePoint.JBeginTrimmed
            )
        }
        private val IsDPLeft: Filter<SequencePartitioning> = Filter<SequencePartitioning> { `object` ->
            `object`.isAvailable(ReferencePoint.DBegin) && `object`.getPosition(ReferencePoint.DBegin) != `object`.getPosition(
                ReferencePoint.DBeginTrimmed
            )
        }
        private val IsDPRight: Filter<SequencePartitioning> = Filter<SequencePartitioning> { `object` ->
            `object`.isAvailable(ReferencePoint.DEnd) && `object`.getPosition(ReferencePoint.DEnd) != `object`.getPosition(
                ReferencePoint.DEndTrimmed
            )
        }
        private val NotDPLeft: Filter<SequencePartitioning> = FilterUtil.not(IsDPLeft)
        private val NotDPRight: Filter<SequencePartitioning> = FilterUtil.not(IsDPRight)
        private val NotVP: Filter<SequencePartitioning> = FilterUtil.not(IsVP)
        private val NotJP: Filter<SequencePartitioning> = FilterUtil.not(IsJP)
        private val POINTS_FOR_REARRANGED = arrayOf(
            pd(ReferencePoint.V5UTRBeginTrimmed, "<5'UTR"),
            pd(ReferencePoint.V5UTREnd, "5'UTR><L1"),
            pd(ReferencePoint.L1End, "L1>"),
            pd(ReferencePoint.L2Begin, "<L2"),
            pd(ReferencePoint.FR1Begin, "L2><FR1"),
            pd(ReferencePoint.CDR1Begin, "FR1><CDR1"),
            pd(ReferencePoint.FR2Begin, "CDR1><FR2"),
            pd(ReferencePoint.CDR2Begin, "FR2><CDR2"),
            pd(ReferencePoint.FR3Begin, "CDR2><FR3"),
            pd(ReferencePoint.CDR3Begin, "FR3><CDR3"),
            pd(ReferencePoint.VEndTrimmed, "V>", -1, NotVP),
            pd(ReferencePoint.VEnd, "V><VP", IsVP),
            pd(ReferencePoint.VEndTrimmed, "VP>", -1, IsVP),
            pd(ReferencePoint.DBeginTrimmed, "<D", NotDPLeft),
            pd(ReferencePoint.DBegin, "DP><D", IsDPLeft),
            pd(ReferencePoint.DBeginTrimmed, "<DP", IsDPLeft),
            pd(ReferencePoint.DEndTrimmed, "D>", -1, NotDPRight),
            pd(ReferencePoint.DEnd, "D><DP", IsDPRight),
            pd(ReferencePoint.DEndTrimmed, "DP>", IsDPRight),
            pd(ReferencePoint.JBeginTrimmed, "<J", NotJP),
            pd(ReferencePoint.JBegin, "JP><J", IsJP),
            pd(ReferencePoint.JBeginTrimmed, "<JP", IsJP),
            pd(ReferencePoint.CDR3End.move(-1), "CDR3><FR4").moveMarkerPoint(1),
            pd(ReferencePoint.FR4End, "FR4>", -1),
            pd(ReferencePoint.CBegin, "<C")
        )
        private val POINTS_FOR_GERMLINE = arrayOf(
            pd(ReferencePoint.V5UTRBeginTrimmed, "<5'UTR"),
            pd(ReferencePoint.V5UTREnd, "5'UTR><L1"),
            pd(ReferencePoint.L1End, "L1>"),
            pd(ReferencePoint.L2Begin, "<L2"),
            pd(ReferencePoint.FR1Begin, "L2><FR1"),
            pd(ReferencePoint.CDR1Begin, "FR1><CDR1"),
            pd(ReferencePoint.FR2Begin, "CDR1><FR2"),
            pd(ReferencePoint.CDR2Begin, "FR2><CDR2"),
            pd(ReferencePoint.FR3Begin, "CDR2><FR3"),
            pd(ReferencePoint.CDR3Begin, "FR3><CDR3"),
            pd(ReferencePoint.VEnd, "V>", -1),
            pd(ReferencePoint.DBegin, "<D"),
            pd(ReferencePoint.DEnd, "D>", -1),
            pd(ReferencePoint.JBegin, "<J"),
            pd(ReferencePoint.CDR3End.move(-1), "CDR3><FR4").moveMarkerPoint(1),
            pd(ReferencePoint.FR4End, "FR4>", -1)
        )

        private fun pd(rp: ReferencePoint, marker: String, activator: Filter<SequencePartitioning>): PointToDraw {
            return pd(rp, marker, 0, activator)
        }

        private fun pd(
            rp: ReferencePoint,
            marker: String,
            additionalOffset: Int = 0,
            activator: Filter<SequencePartitioning>? = null
        ): PointToDraw {
            var offset = marker.indexOf('>')
            if (offset >= 0) return PointToDraw(
                rp.move(additionalOffset),
                marker,
                -1 - offset - additionalOffset,
                activator
            )
            offset = marker.indexOf('<')
            return if (offset >= 0) PointToDraw(
                rp.move(additionalOffset),
                marker,
                -offset - additionalOffset,
                activator
            ) else PointToDraw(
                rp,
                marker,
                0,
                activator
            )
        }
    }

    fun formatMultiAlignments(vdjcObject: VDJCObject, targetId: Int): MultiAlignmentHelper {
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
            *alignmentsInputs.toTypedArray()
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

    private class PointToDraw(
        private val rp: ReferencePoint,
        private val marker: String,
        private val markerOffset: Int,
        private val activator: Filter<SequencePartitioning>?
    ) {
        fun moveMarkerPoint(offset: Int): PointToDraw {
            return PointToDraw(rp, marker, markerOffset + offset, activator)
        }

        fun draw(
            partitioning: SequencePartitioning,
            helper: MultiAlignmentHelper,
            line: CharArray,
            overwrite: Boolean
        ): Boolean {
            if (activator != null && !activator.accept(partitioning)) return true
            val positionInTarget = partitioning.getPosition(rp)
            if (positionInTarget < 0) return true
            var positionInHelper = -1
            for (i in 0 until helper.size()) if (positionInTarget == helper.getAbsSubjectPositionAt(i)) {
                positionInHelper = i
                break
            }
            if (positionInHelper == -1) return true

            // Checking
            if (!overwrite) for (i in marker.indices) {
                val positionInLine = positionInHelper + markerOffset + i
                if (positionInLine < 0 || positionInLine >= line.size) continue
                if (line[positionInLine] != ' ') return false
            }
            for (i in marker.indices) {
                val positionInLine = positionInHelper + markerOffset + i
                if (positionInLine < 0 || positionInLine >= line.size) continue
                line[positionInLine] = marker[i]
            }
            return true
        }
    }
}
