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
import com.milaboratory.core.alignment.Alignment
import com.milaboratory.core.sequence.AminoAcidAlphabet.INCOMPLETE_CODON
import com.milaboratory.core.sequence.AminoAcidSequence
import com.milaboratory.core.sequence.NucleotideSequence
import io.repseq.core.GeneType
import io.repseq.core.ReferencePoint
import io.repseq.core.SequencePartitioning
import java.util.*

object VDJCAlignmentsFormatter {
    @JvmStatic
    fun getTargetAsMultiAlignment(vdjcObject: VDJCObject, targetId: Int): MultiAlignmentHelper =
        getTargetAsMultiAlignment(
            vdjcObject, targetId, false,
            vdjcObject is VDJCAlignments && vdjcObject.getOriginalReads() != null
        )

    fun getTargetAsMultiAlignment(
        vdjcObject: VDJCObject, targetId: Int,
        addHitScore: Boolean, addReads: Boolean
    ): MultiAlignmentHelper {
        require(!(addReads && vdjcObject !is VDJCAlignments)) { "Read alignments supported only for VDJCAlignments." }
        val target = vdjcObject.getTarget(targetId)
        val targetSeq = target.sequence
        val partitioning = vdjcObject.getPartitionedTarget(targetId).partitioning
        val alignments = mutableListOf<Alignment<NucleotideSequence>>()
        val alignmentLeftComments = mutableListOf<String>()
        val alignmentRightComments = mutableListOf<String>()
        for (gt in GeneType.values()) {
            for (hit in vdjcObject.getHits(gt)) {
                val alignment = hit.getAlignment(targetId) ?: continue
                alignments.add(alignment.invert(targetSeq))
                alignmentLeftComments.add(hit.gene.name)
                alignmentRightComments.add(" " + hit.getAlignment(targetId).score.toInt() + if (addHitScore) " (" + hit.score.toInt() + ")" else "")
            }
        }

        // Adding read information
        if (addReads) {
            val vdjcAlignments = vdjcObject as VDJCAlignments
            val history = vdjcAlignments.getHistory(targetId)
            val reads = history.rawReads()
            for (read in reads) {
                val seq = vdjcAlignments.getOriginalSequence(read.index).sequence
                val offset = history.offset(read.index)
                val alignment = Aligner.alignOnlySubstitutions(
                    targetSeq, seq, offset, seq.size(), 0, seq.size(),
                    AffineGapAlignmentScoring.IGBLAST_NUCLEOTIDE_SCORING
                )
                alignments.add(alignment)
                alignmentLeftComments.add(read.index.toString())
                alignmentRightComments.add("")
            }
        }
        val helper = MultiAlignmentHelper.build(
            MultiAlignmentHelper.DEFAULT_SETTINGS,
            Range(0, target.size()), targetSeq, *alignments.toTypedArray()
        )
        if (alignments.isNotEmpty()) {
            drawPoints(helper, partitioning, *POINTS_FOR_REARRANGED)
        }
        drawAASequence(helper, partitioning, targetSeq)
        helper.addSubjectQuality("Quality", target.quality)
        helper.subjectLeftTitle = "Target$targetId"
        helper.subjectRightTitle = " Score" + if (addHitScore) " (hit score)" else ""
        for (i in alignmentLeftComments.indices) {
            helper.setQueryLeftTitle(i, alignmentLeftComments[i])
            helper.setQueryRightTitle(i, alignmentRightComments[i])
        }
        return helper
    }

    @JvmStatic
    fun drawAASequence(
        helper: MultiAlignmentHelper, partitioning: SequencePartitioning,
        target: NucleotideSequence
    ) {
        val trParams = partitioning.getTranslationParameters(target.size())
        val line = CharArray(helper.size())
        Arrays.fill(line, ' ')
        for (trParam in trParams) {
            val mainSequence = target.getRange(trParam.range)
            val leftover = if (trParam.codonLeftoverRange == null) null else target.getRange(trParam.codonLeftoverRange)
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
                line[helper.subjectToAlignmentPosition(ntPosition)] =
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
                line[helper.subjectToAlignmentPosition(ntPosition)] = c
                ++aaPosition
            }
            if (aaPosition < aa.size() && (aaPosition < aa.size() - 1 || aa.codeAt(aaPosition) == INCOMPLETE_CODON)) {
                ntPosition = (trParam.range.from
                        + AminoAcidSequence.convertAAPositionToNt(
                    aaPosition, mainSequence.size(), trParam.translationParameters
                ))
                line[helper.subjectToAlignmentPosition(ntPosition)] =
                    AminoAcidSequence.ALPHABET.codeToSymbol(aa.codeAt(aaPosition))
            }
        }
        helper.addAnnotationString("", String(line))
    }

    private fun drawPoints(
        helper: MultiAlignmentHelper, partitioning: SequencePartitioning, vararg pointsToDraw: PointToDraw
    ) {
        val markers = ArrayList<CharArray>()
        markers.add(emptyLine(helper.size()))
        var result: Boolean
        var i: Int
        for (point in pointsToDraw) {
            i = 0
            do {
                if (markers.size == i) markers.add(emptyLine(helper.size()))
                result = point.draw(partitioning, helper, markers[i++], false)
            } while (!result)
        }
        i = markers.size - 1
        while (i >= 0) {
            helper.addAnnotationString("", String(markers[i]))
            --i
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

    class PointToDraw(
        val rp: ReferencePoint,
        val marker: String,
        val markerOffset: Int,
        val activator: Filter<SequencePartitioning>?
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
