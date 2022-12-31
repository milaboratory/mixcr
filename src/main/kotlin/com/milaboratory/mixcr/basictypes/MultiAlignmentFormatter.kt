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
import cc.redberry.primitives.not
import com.milaboratory.core.sequence.AminoAcidAlphabet
import com.milaboratory.core.sequence.AminoAcidSequence
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.core.sequence.Sequence
import com.milaboratory.core.sequence.SequenceQuality
import io.repseq.core.ReferencePoint
import io.repseq.core.ReferencePoint.*
import io.repseq.core.SequencePartitioning
import java.util.*
import kotlin.math.max

object MultiAlignmentFormatter {
    sealed interface AnnotationLine {
        val content: String
    }

    data class QualityLine(
        override val content: String
    ) : AnnotationLine

    data class AminoAcidsLine(
        override val content: String
    ) : AnnotationLine

    data class ReferencePointsLine(
        override val content: String
    ) : AnnotationLine


    class LinesFormatter(
        private val addHitScore: Boolean = false,
        private val minimalPositionWidth: Int = 0,
        private val pointsToDraw: Array<PointToDraw> = POINTS_FOR_REARRANGED
    ) {

        private fun fixedWidthL(strings: Array<String>, minWidth: Int = 0): Int {
            var length = 0
            for (string in strings) length = max(length, string.length)
            length = max(length, minWidth)
            for (i in strings.indices) strings[i] = spaces(length - strings[i].length) + strings[i]
            return length
        }

        private fun fixedWidthR(strings: Array<String>, minWidth: Int = 0): Int {
            var length = 0
            for (string in strings) length = max(length, string.length)
            length = max(length, minWidth)
            for (i in strings.indices) strings[i] = strings[i] + spaces(length - strings[i].length)
            return length
        }

        private fun spaces(n: Int): String {
            val c = CharArray(n)
            Arrays.fill(c, ' ')
            return String(c)
        }

        private val MultiAlignmentHelper.QueryLine.rightTitle: String?
            get() = when (this) {
                is MultiAlignmentHelper.AlignmentLine -> "" + alignmentScore + if (addHitScore) " ($hitScore)" else ""
                is MultiAlignmentHelper.ReadLine -> null
            }

        private val MultiAlignmentHelper.QueryLine.leftTitle: String
            get() = when (this) {
                is MultiAlignmentHelper.AlignmentLine -> geneName
                is MultiAlignmentHelper.ReadLine -> index
            }

        private val AnnotationLine.leftTitle: String?
            get() = when (this) {
                is AminoAcidsLine -> null
                is QualityLine -> "Quality"
                is ReferencePointsLine -> null
            }

        fun <S : Sequence<S>> formatLines(
            multiAlignmentHelper: MultiAlignmentHelper<S>
        ): String = multiAlignmentHelper.run {
            val annotations = buildAnnotationLines(this)

            val aCount = queries.size
            val asSize = annotations.size
            val lines: Array<String> = Array(aCount + 1 + asSize) { "" }
            lines[asSize] = "" + subject.firstPosition
            for (i in 0 until aCount)
                lines[i + 1 + asSize] = "" + queries[i].firstPosition
            val width = fixedWidthL(lines, minimalPositionWidth)
            for (i in 0 until asSize) {
                lines[i] = (annotations[i].leftTitle ?: "") + spaces(width + 1)
            }
            lines[asSize] = subject.name + " " + lines[asSize]
            for (i in 0 until aCount)
                lines[i + 1 + asSize] = queries[i].leftTitle + " " + lines[i + 1 + asSize]
            fixedWidthL(lines)

            for (i in 0 until asSize)
                lines[i] += " " + annotations[i].content
            lines[asSize] += " ${subject.content} ${subject.lastPosition}"
            for (i in 0 until aCount)
                lines[i + 1 + asSize] += " ${queries[i].content} ${queries[i].lastPosition}"

            fixedWidthR(lines)
            lines[asSize] += "  Score" + if (addHitScore) " (hit score)" else ""
            for (i in 0 until aCount) {
                if (queries[i].rightTitle != null) {
                    lines[i + 1 + asSize] += "  " + queries[i].rightTitle
                }
            }
            val result = StringBuilder()
            for (i in lines.indices) {
                if (i != 0) result.append("\n")
                result.append(lines[i])
            }
            return result.toString()
        }

        fun <S : Sequence<S>> buildAnnotationLines(multiAlignmentHelper: MultiAlignmentHelper<S>): List<AnnotationLine> =
            multiAlignmentHelper.metaInfo.flatMap { meta ->
                when (meta) {
                    is MultiAlignmentHelper.AminoAcidInput -> listOf(
                        @Suppress("UNCHECKED_CAST")
                        makeAALine(
                            multiAlignmentHelper as MultiAlignmentHelper<NucleotideSequence>,
                            meta.partitioning,
                            multiAlignmentHelper.subject.source
                        )
                    )

                    is MultiAlignmentHelper.QualityInput -> listOf(
                        makeQualityLine(multiAlignmentHelper, meta.quality)
                    )

                    is MultiAlignmentHelper.ReferencePointsInput -> makePointsLines(
                        multiAlignmentHelper,
                        this.pointsToDraw,
                        meta.partitioning
                    )
                }
            }

        companion object {
            fun makeQualityLine(
                multiAlignmentHelper: MultiAlignmentHelper<*>,
                quality: SequenceQuality
            ): AnnotationLine {
                val chars = CharArray(multiAlignmentHelper.size())
                for (i in 0 until multiAlignmentHelper.size()) chars[i] = when {
                    multiAlignmentHelper.subject.positions[i] < 0 -> ' '
                    else -> simplifiedQuality(quality.value(multiAlignmentHelper.subject.positions[i]).toInt())
                }
                return QualityLine(content = String(chars))
            }

            private fun simplifiedQuality(value: Int): Char {
                var result = value
                result /= 5
                if (result > 9) result = 9
                return result.toString()[0]
            }

            fun makeAALine(
                multiAlignmentHelper: MultiAlignmentHelper<NucleotideSequence>,
                partitioning: SequencePartitioning,
                target: NucleotideSequence
            ): AnnotationLine {
                val trParams = partitioning.getTranslationParameters(target.size())
                val line = CharArray(multiAlignmentHelper.size())
                Arrays.fill(line, ' ')
                for (trParam in trParams) {
                    val mainSequence = target.getRange(trParam.range)
                    val leftover = trParam.codonLeftoverRange?.let { target.getRange(it) }
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
                    if (aa.codeAt(aaPosition) == AminoAcidAlphabet.INCOMPLETE_CODON) {
                        val position = multiAlignmentHelper.subjectToAlignmentPosition(ntPosition)
                        if (position != -1) {
                            line[position] = AminoAcidSequence.ALPHABET.codeToSymbol(aa.codeAt(aaPosition)) // '_'
                        }
                        ++aaPosition
                    }
                    while (aaPosition < aa.size() && (aaPosition < aa.size() - 1 || aa.codeAt(aaPosition) != AminoAcidAlphabet.INCOMPLETE_CODON)) {
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
                        if (aa.codeAt(aaPosition) != AminoAcidAlphabet.INCOMPLETE_CODON) {
                            ++ntPosition
                        }
                        var c = AminoAcidSequence.ALPHABET.codeToSymbol(aa.codeAt(aaPosition))
                        if (isLeftover) c = c.lowercaseChar()
                        val position = multiAlignmentHelper.subjectToAlignmentPosition(ntPosition)
                        if (position != -1) {
                            line[position] = c
                        }
                        ++aaPosition
                    }
                    if (aaPosition < aa.size() && (aaPosition < aa.size() - 1 || aa.codeAt(aaPosition) == AminoAcidAlphabet.INCOMPLETE_CODON)) {
                        ntPosition = trParam.range.from + AminoAcidSequence.convertAAPositionToNt(
                            aaPosition, mainSequence.size(), trParam.translationParameters
                        )
                        val position = multiAlignmentHelper.subjectToAlignmentPosition(ntPosition)
                        if (position != -1) {
                            line[position] = AminoAcidSequence.ALPHABET.codeToSymbol(aa.codeAt(aaPosition))
                        }
                    }
                }
                return AminoAcidsLine(content = String(line))
            }

            fun makePointsLines(
                multiAlignmentHelper: MultiAlignmentHelper<*>,
                pointsToDraw: Array<out PointToDraw>,
                partitioning: SequencePartitioning
            ): List<AnnotationLine> {
                val markers = mutableListOf<CharArray>()
                markers += emptyLine(multiAlignmentHelper.size())
                var result: Boolean
                var i: Int
                for (point in pointsToDraw) {
                    i = 0
                    do {
                        if (markers.size == i) markers += emptyLine(multiAlignmentHelper.size())
                        result = point.draw(partitioning, multiAlignmentHelper, markers[i++], false)
                    } while (!result)
                }
                return markers.reversed().map { marker ->
                    ReferencePointsLine(content = String(marker))
                }
            }

            private fun emptyLine(size: Int): CharArray {
                val markers = CharArray(size)
                Arrays.fill(markers, ' ')
                return markers
            }
        }
    }

    class PointToDraw(
        private val rp: ReferencePoint,
        private val marker: String,
        private val markerOffset: Int,
        private val activator: Filter<SequencePartitioning>?
    ) {
        fun moveMarkerPoint(offset: Int): PointToDraw =
            PointToDraw(rp, marker, markerOffset + offset, activator)

        fun draw(
            partitioning: SequencePartitioning,
            helper: MultiAlignmentHelper<*>,
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

    private val IsVP: Filter<SequencePartitioning> = Filter { partitioning ->
        partitioning.isAvailable(VEnd) && partitioning.getPosition(VEnd) != partitioning.getPosition(VEndTrimmed)
    }
    private val IsJP: Filter<SequencePartitioning> = Filter { partitioning ->
        partitioning.isAvailable(JBegin) && partitioning.getPosition(JBegin) != partitioning.getPosition(JBeginTrimmed)
    }
    private val IsDPLeft: Filter<SequencePartitioning> = Filter { partitioning ->
        partitioning.isAvailable(DBegin) && partitioning.getPosition(DBegin) != partitioning.getPosition(DBeginTrimmed)
    }
    private val IsDPRight: Filter<SequencePartitioning> = Filter { partitioning ->
        partitioning.isAvailable(DEnd) && partitioning.getPosition(DEnd) != partitioning.getPosition(DEndTrimmed)
    }
    private val NotDPLeft: Filter<SequencePartitioning> = IsDPLeft.not()
    private val NotDPRight: Filter<SequencePartitioning> = IsDPRight.not()
    private val NotVP: Filter<SequencePartitioning> = IsVP.not()
    private val NotJP: Filter<SequencePartitioning> = IsJP.not()
    val POINTS_FOR_REARRANGED = arrayOf(
        pd(V5UTRBeginTrimmed, "<5'UTR"),
        pd(V5UTREnd, "5'UTR><L1"),
        pd(L1End, "L1>"),
        pd(L2Begin, "<L2"),
        pd(FR1Begin, "L2><FR1"),
        pd(CDR1Begin, "FR1><CDR1"),
        pd(FR2Begin, "CDR1><FR2"),
        pd(CDR2Begin, "FR2><CDR2"),
        pd(FR3Begin, "CDR2><FR3"),
        pd(CDR3Begin, "FR3><CDR3"),
        pd(VEndTrimmed, "V>", -1, NotVP),
        pd(VEnd, "V><VP", IsVP),
        pd(VEndTrimmed, "VP>", -1, IsVP),
        pd(DBeginTrimmed, "<D", NotDPLeft),
        pd(DBegin, "DP><D", IsDPLeft),
        pd(DBeginTrimmed, "<DP", IsDPLeft),
        pd(DEndTrimmed, "D>", -1, NotDPRight),
        pd(DEnd, "D><DP", IsDPRight),
        pd(DEndTrimmed, "DP>", IsDPRight),
        pd(JBeginTrimmed, "<J", NotJP),
        pd(JBegin, "JP><J", IsJP),
        pd(JBeginTrimmed, "<JP", IsJP),
        pd(CDR3End.move(-1), "CDR3><FR4").moveMarkerPoint(1),
        pd(FR4End, "FR4>", -1),
        pd(CBegin, "<C")
    )
    val POINTS_FOR_GERMLINE = arrayOf(
        pd(V5UTRBeginTrimmed, "<5'UTR"),
        pd(V5UTREnd, "5'UTR><L1"),
        pd(L1End, "L1>"),
        pd(L2Begin, "<L2"),
        pd(FR1Begin, "L2><FR1"),
        pd(CDR1Begin, "FR1><CDR1"),
        pd(FR2Begin, "CDR1><FR2"),
        pd(CDR2Begin, "FR2><CDR2"),
        pd(FR3Begin, "CDR2><FR3"),
        pd(CDR3Begin, "FR3><CDR3"),
        pd(VEnd, "V>", -1),
        pd(DBegin, "<D"),
        pd(DEnd, "D>", -1),
        pd(JBegin, "<J"),
        pd(CDR3End.move(-1), "CDR3><FR4").moveMarkerPoint(1),
        pd(FR4End, "FR4>", -1)
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
