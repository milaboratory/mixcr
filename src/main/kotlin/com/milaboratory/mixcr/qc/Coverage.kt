/*
 *
 * Copyright (c) 2022, MiLaboratories Inc. All Rights Reserved
 *
 * Before downloading or accessing the software, please read carefully the
 * License Agreement available at:
 * https://github.com/milaboratory/miplots/blob/main/LICENSE
 *
 * By downloading or accessing the software, you accept and agree to be bound
 * by the terms of the License Agreement. If you do not want to agree to the terms
 * of the Licensing Agreement, you must not download or access the software.
 */
package com.milaboratory.mixcr.qc

import cc.redberry.pipe.CUtils
import com.milaboratory.mixcr.basictypes.SequenceHistory.Merge
import com.milaboratory.mixcr.basictypes.SequenceHistory.RawSequence
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader
import com.milaboratory.mixcr.cli.AlignerReport
import gnu.trove.impl.Constants
import gnu.trove.map.hash.TIntLongHashMap
import io.repseq.core.ReferencePoint
import io.repseq.core.ReferencePoint.*
import jetbrains.letsPlot.*
import jetbrains.letsPlot.geom.geomLine
import jetbrains.letsPlot.scale.scaleXContinuous
import jetbrains.letsPlot.scale.scaleYContinuous
import java.nio.file.Path
import java.util.*

object Coverage {

    class RefPointStat {
        // position in read -> count
        val hist = TIntLongHashMap(
            Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR,
            -1, 0L
        )

        // number of reads where ref point lies to the left of the whole read
        var toTheLeft = 0L

        // number of reads where ref point lies to the right of the whole read
        var toTheRight = 0L

        // read does not cover any ref point
        var unmatched = 0L

        fun tot() = run {
            hist.values().sum() + toTheLeft + toTheRight + unmatched
        }

        fun mkHisto() = run {
            val result = TreeMap<Int, Double>()
            val it0 = hist.iterator()
            while (it0.hasNext()) {
                it0.advance()
                result[it0.key()] = toTheLeft + it0.value().toDouble()
            }
            val it1 = result.iterator()
            var prev = 0.0
            while (it1.hasNext()) {
                val e = it1.next()
                val newValue = prev + e.value
                e.setValue(newValue)
                prev = newValue
            }

            val it2 = result.iterator()
            val sum = prev + toTheRight
            while (it2.hasNext()) {
                val e = it2.next()
                val newValue = e.value / sum
                e.setValue(newValue)
            }

            result
        }


//        fun mkHisto() = run {
//            val len = (hist.keys().maxOrNull() ?: 0).let { if (it == -1) 0 else it }
//            val result = LongArray(len)
//            for (i in result.indices) {
//                result[i] = toTheLeft + hist.get(i)
//            }
//
//            result
//        }
    }

    fun coveragePlot(vdjca: Path) = run {
        VDJCAlignmentsReader(vdjca).use { reader ->
            println((reader.reports().get(0) as AlignerReport).totalReadsProcessed)
            println((reader.reports().get(0) as AlignerReport).aligned)
            // ordered from left to right
            val refPoints: List<ReferencePoint> = listOf(FR3Begin, CDR3Begin, CDR3End, FR4End)
            val refPointStats: List<RefPointStat> = refPoints.indices.map { RefPointStat() }

            var ttttt = 0
            for (al in CUtils.it(reader)) {
                ttttt += 1
//                if (al.numberOfTargets() == 1)
//                    continue

                val history = al.getHistory(0)
                if (history is Merge)
                    continue

                val target = al.getPartitionedTarget(0)
                println((history as RawSequence).index.isReverseComplement)
                println(target.sequence.size())

                // ref point positions in target
                val positionsInTarget = refPoints.map {
                    target.partitioning.getPosition(it)
                }

                var firstNonZero: Int = -1
                var lastNonZero: Int = -1
                for (i in refPoints.indices) {
                    if (firstNonZero == -1 && positionsInTarget[i] != -1)
                        firstNonZero = i
                    else if (firstNonZero != -1 && positionsInTarget[i] == -1) {
                        lastNonZero = i - 1
                        break
                    }
                }
                if (lastNonZero == -1 && firstNonZero != -1)
                    lastNonZero = refPoints.size - 1

                // filling all to the right
                for (i in 0 until firstNonZero)
                    refPointStats[i].toTheLeft += 1

                // filling histo
                if (firstNonZero != -1)
                    for (i in firstNonZero..lastNonZero) {
                        assert(positionsInTarget[i] != -1)
                        refPointStats[i].hist.adjustOrPutValue(positionsInTarget[i], 1, 1)
                    }

                // filling all to the left
                if (lastNonZero != -1)
                    for (i in (lastNonZero + 1) until refPoints.size)
                        refPointStats[i].toTheRight += 1

                if (firstNonZero == -1 && lastNonZero == -1)
                    for (i in positionsInTarget.indices)
                        refPointStats[i].unmatched += 1
            }

            val data = mapOf(
                "ref" to mutableListOf<Any>(),
                "cord" to mutableListOf(),
                "count" to mutableListOf()
            )

            for (i in refPoints.indices) {
                println("  n n n " + i + "   =   " + refPointStats[i].tot())
                val hist = refPointStats[i].mkHisto()
                val ref = refPoints[i]
                for ((pos, value) in hist) {
                    data["ref"]!! += ref
                    data["cord"]!! += pos
                    data["count"]!! += value
                }
            }

            println("=====    " + ttttt)
            var plt = ggplot(data) {
                x = "cord"
                y = "count"
            }

            plt += geomLine {
                color = "ref"
            }

            plt += scaleYContinuous(expand = listOf(0, 0))
            plt += scaleXContinuous(expand = listOf(0, 0))

            plt += theme(
                axisLineY = elementLine(),
                panelBorder = elementRect(),
                panelGrid = elementBlank()
            )

            plt
        }
    }

}