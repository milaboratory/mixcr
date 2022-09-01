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
package com.milaboratory.mixcr.qc

import cc.redberry.pipe.CUtils
import com.milaboratory.mixcr.basictypes.SequenceHistory.Merge
import com.milaboratory.mixcr.basictypes.VDJCAlignments
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader
import gnu.trove.impl.Constants
import gnu.trove.map.hash.TIntLongHashMap
import io.repseq.core.GeneType
import io.repseq.core.GeneType.Joining
import io.repseq.core.GeneType.Variable
import io.repseq.core.ReferencePoint.CDR1Begin
import io.repseq.core.ReferencePoint.CDR2Begin
import io.repseq.core.ReferencePoint.CDR3Begin
import io.repseq.core.ReferencePoint.FR1Begin
import io.repseq.core.ReferencePoint.FR2Begin
import io.repseq.core.ReferencePoint.FR3Begin
import io.repseq.core.ReferencePoint.FR4Begin
import io.repseq.core.ReferencePoint.FR4End
import io.repseq.core.RelativePointSide
import jetbrains.letsPlot.elementBlank
import jetbrains.letsPlot.elementLine
import jetbrains.letsPlot.elementRect
import jetbrains.letsPlot.geom.geomLine
import jetbrains.letsPlot.geom.geomPoint
import jetbrains.letsPlot.ggplot
import jetbrains.letsPlot.intern.Plot
import jetbrains.letsPlot.label.ggtitle
import jetbrains.letsPlot.label.xlab
import jetbrains.letsPlot.label.ylab
import jetbrains.letsPlot.sampling.samplingNone
import jetbrains.letsPlot.scale.scaleColorManual
import jetbrains.letsPlot.scale.scaleXContinuous
import jetbrains.letsPlot.scale.scaleXReverse
import jetbrains.letsPlot.scale.scaleYContinuous
import jetbrains.letsPlot.theme
import java.nio.file.Path
import java.util.*
import kotlin.math.max

object Coverage {
    // List of ref points to use
    private val predefinedRefPoints =
        listOf(
            FR1Begin,
            CDR1Begin,
            FR2Begin,
            CDR2Begin,
            FR3Begin,
            CDR3Begin,
            FR4Begin,
            FR4End,
        )

    private class RefPointCoverage {
        // position in read -> count
        private val stat = TIntLongHashMap(
            Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR,
            -1, 0L
        )

        // number of reads where ref point lies to the left of the whole read
        var toTheLeft = 0L

        // number of reads where ref point lies to the right of the whole read
        var toTheRight = 0L

        // read coverы ref point, but position unknown
        var positionUknown = 0L

        // read does not cover any ref point
        var unmatched = 0L

        fun sum() = run {
            stat.values().sum() + toTheLeft + toTheRight + unmatched + positionUknown
        }

        fun addPosition(pos: Int) {
            stat.adjustOrPutValue(pos, 1, 1)
        }

        fun cumsum(norm: Long? = null) = run {
            val result = TreeMap<Int, Double>()
            val it0 = stat.iterator()
            while (it0.hasNext()) {
                it0.advance()
                result[it0.key()] = it0.value().toDouble()
            }

            val it1 = result.iterator()
            var prev = 0.0
            while (it1.hasNext()) {
                val e = it1.next()
                val newValue = prev + e.value
                e.setValue(newValue + toTheLeft)
                prev = newValue
            }

            val it2 = result.iterator()
            val sum = (norm ?: (prev + toTheLeft + positionUknown + toTheRight + unmatched)).toDouble()
            while (it2.hasNext()) {
                val e = it2.next()
                val newValue = 100.0 * e.value / sum
                e.setValue(newValue)
            }

            result
        }
    }

    private enum class Var {
        RefPoint,
        RefPointPosition,
        RefPointFraction
    }

    private enum class Target { R1, R2, Overlap }
    private data class AlignmentEdgePoint(
        val gt: GeneType,
        /** 0 - left, 1 - right */
        val bound: Int
    )

    private class TargetCoverage(
        val target: Target,
        val showAlignmentsBoundaries: Boolean
    ) {

        // reference points
        val refPointStats: List<RefPointCoverage> =
            predefinedRefPoints.map {
                RefPointCoverage()
            }

        // v alignment begin
        val vEdge = RefPointCoverage()

        // j alignment end
        val jEdge = RefPointCoverage()

        private var readLength = 0
        var totalNumberOfReads = 0L
        var counter = 0L

        fun process(al: VDJCAlignments) {
            ++totalNumberOfReads

            // skip overlap for R1/R2
            if (target != Target.Overlap
                && (0 until al.numberOfTargets()).any { al.getHistory(it) is Merge }
            )
                return

            // skip R1/R2 for Overlap
            if (target == Target.Overlap
                && (al.getHistory(0) !is Merge)
            )
                return

            val iTarget = when (target) {
                Target.R1, Target.Overlap -> 0
                Target.R2 -> 1
            }

            if (al.numberOfTargets() <= iTarget)
                return

            val partitionedSequence = al.getPartitionedTarget(iTarget)

            ++counter
            readLength = max(readLength, partitionedSequence.sequence.size())

            fun pCorrected(p: Int) = if (target == Target.R2) -p else p
            val partitioning = partitionedSequence.partitioning
            for (i in predefinedRefPoints.indices) {
                val refPoint = predefinedRefPoints[i]

                val side = partitioning.getRelativeSide(refPoint)
                @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
                when (side) {
                    RelativePointSide.Unknown -> ++refPointStats[i].unmatched
                    RelativePointSide.Left -> ++refPointStats[i].toTheLeft
                    RelativePointSide.Right -> ++refPointStats[i].toTheRight
                    RelativePointSide.MatchOrInside -> {
                        val p = partitioning.getPosition(refPoint)
                        if (p != -1) {
                            refPointStats[i].addPosition(pCorrected(p))
                        } else {
                            when (refPoint.geneType) {
                                Variable -> {
                                    al.getBestHit(Variable)?.getAlignment(iTarget)?.let {
                                        refPointStats[i].addPosition(pCorrected(it.sequence2Range.from))
                                    } ?: ++refPointStats[i].positionUknown
                                }
                                Joining -> {
                                    al.getBestHit(Joining)?.getAlignment(iTarget)?.let {
                                        refPointStats[i].addPosition(pCorrected(it.sequence2Range.to))
                                    } ?: ++refPointStats[i].positionUknown
                                }
                                else ->
                                    ++refPointStats[i].positionUknown
                            }
                        }
                    }
                }
            }
            al.getBestHit(Variable)?.getAlignment(iTarget)?.let {
                vEdge.addPosition(pCorrected(it.sequence2Range.from))
            } ?: ++vEdge.unmatched
            al.getBestHit(Joining)?.getAlignment(iTarget)?.let {
                jEdge.addPosition(pCorrected(it.sequence2Range.to))
            } ?: ++jEdge.unmatched
        }

        fun build() = run {
            for (stat in refPointStats) {
                assert(counter == stat.sum()) { "$counter == ${stat.sum()}" }
            }
            assert(counter == vEdge.sum()) { "$counter == ${vEdge.sum()}" }
            assert(counter == jEdge.sum()) { "$counter == ${jEdge.sum()}" }

            val data = mapOf(
                Var.RefPoint to mutableListOf<Any>(),
                Var.RefPointPosition to mutableListOf(),
                Var.RefPointFraction to mutableListOf()
            )

            for (i in predefinedRefPoints.indices) {
                val hist = refPointStats[i].cumsum(totalNumberOfReads)
                val ref = predefinedRefPoints[i]
                for ((pos, value) in hist) {
                    data[Var.RefPoint]!! += ref
                    data[Var.RefPointPosition]!! += if (target == Target.R2) pos + readLength else pos
                    data[Var.RefPointFraction]!! += value
                }
            }


            if (showAlignmentsBoundaries)
                for (ie in listOf(vEdge, jEdge).indices) {
                    val e = if (ie == 0) vEdge else jEdge
                    val gt = if (ie == 0) Variable else Joining
                    val bound = if (ie == 0) 0 else 1
                    val hist = e.cumsum(totalNumberOfReads)
                    for ((pos, value) in hist) {
                        data[Var.RefPoint]!! += AlignmentEdgePoint(gt, bound)
                        data[Var.RefPointPosition]!! += if (target == Target.R2) pos + readLength else pos
                        data[Var.RefPointFraction]!! += value
                    }
                }

            data
        }

        fun plt(): Plot? {
            if (counter == 0L)
                return null

            var plt = ggplot(build()) {
                x = Var.RefPointPosition
                y = Var.RefPointFraction
            }
            plt += geomLine(
                size = 1.0,
                sampling = samplingNone
            ) {
                color = Var.RefPoint
            }
            plt += geomPoint(
                sampling = samplingNone
            ) {
                color = Var.RefPoint
            }

            plt += scaleYContinuous(expand = listOf(0, 0))
            plt += if (target == Target.R2)
                scaleXReverse(expand = listOf(0, 0))
            else
                scaleXContinuous(expand = listOf(0, 0))

            val scaleValues = mutableListOf(
                "#42B842",
                "#845CFF",
                "#FF9429",
                "#27C2C2",
                "#E553E5",
                "#95C700",
                "#2D93FA",
                "#F05670",
            )

            val scaleLimits = mutableListOf<Any>(
                FR1Begin,
                CDR1Begin,
                FR2Begin,
                CDR2Begin,
                FR3Begin,
                CDR3Begin,
                FR4Begin,
                FR4End,
            )

            val scaleLabels = mutableListOf(
                "FR1  Begin",
                "CDR1 Begin",
                "FR2  Begin",
                "CDR2 Begin",
                "FR3  Begin",
                "CDR3 Begin",
                "CDR3 End",
                "FR4  End",
            )

            if (showAlignmentsBoundaries) {
                scaleValues += listOf(
                    "#929BAD",
                    "#5E5E70"
                )

                scaleLimits.addAll(
                    listOf(
                        "V alignment begin",
                        "J alignment end"
                    )
                )

                scaleLabels += listOf(
                    "V alignment begin",
                    "J alignment end",
                )
            }

            plt += scaleColorManual(
                values = scaleValues,
                limits = scaleLimits,
                labels = scaleLabels,
            )

            plt += theme(
                panelBorder = elementRect(),
                panelGrid = elementBlank(),
                axisLineY = elementLine(),
                legendTitle = elementBlank(),
            )

            plt += xlab(
                when (target) {
                    Target.R1 -> "R1 -&gt;"
                    Target.R2 -> "&lt;- R2"
                    Target.Overlap -> "R1 -&gt; overlap &lt;- R2"
                }
            )
            plt += ylab("Fraction, %")

            return plt
        }
    }

    fun coveragePlot(
        vdjca: Path,
        showAlignmentsBoundaries: Boolean = false
    ): List<Plot> = run {
        val r1 = TargetCoverage(Target.R1, showAlignmentsBoundaries)
        val r2 = TargetCoverage(Target.R2, showAlignmentsBoundaries)
        val overlap = TargetCoverage(Target.Overlap, showAlignmentsBoundaries)
        VDJCAlignmentsReader(vdjca).use {
            for (al in CUtils.it(it.readAlignments())) {
                r1.process(al)
                r2.process(al)
                overlap.process(al)
            }
        }

        listOfNotNull(
            r1.plt(),
            r2.plt(),
            overlap.plt()
        ).map {
            it + ggtitle(vdjca.fileName.toString())
        }
    }
}
