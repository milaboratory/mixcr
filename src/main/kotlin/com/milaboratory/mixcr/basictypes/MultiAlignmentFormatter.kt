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
import com.milaboratory.core.sequence.Sequence
import io.repseq.core.ReferencePoint
import io.repseq.core.SequencePartitioning
import java.util.*
import kotlin.math.max

object MultiAlignmentFormatter {

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

        private val MultiAlignmentHelper.AnnotationLine.leftTitle: String?
            get() = when (this) {
                is MultiAlignmentHelper.AminoAcidsLine -> null
                is MultiAlignmentHelper.QualityLine -> "Quality"
                is MultiAlignmentHelper.ReferencePointsLine -> null
            }

        fun <S : Sequence<S>> formatLines(
            multiAlignmentHelper: MultiAlignmentHelper<S>
        ): String = multiAlignmentHelper.run {
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
    }

    class PointToDraw(
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
    val POINTS_FOR_REARRANGED = arrayOf(
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
    val POINTS_FOR_GERMLINE = arrayOf(
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
