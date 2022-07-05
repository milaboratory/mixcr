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

import com.milaboratory.mixcr.basictypes.IOUtil
import com.milaboratory.mixcr.cli.AlignerReport
import com.milaboratory.mixcr.vdjaligners.VDJCAlignmentFailCause
import jetbrains.letsPlot.*
import jetbrains.letsPlot.geom.geomBar
import jetbrains.letsPlot.label.labs
import jetbrains.letsPlot.label.xlab
import jetbrains.letsPlot.label.ylab
import jetbrains.letsPlot.scale.guideLegend
import jetbrains.letsPlot.scale.scaleFillManual
import jetbrains.letsPlot.scale.scaleXDiscrete
import java.nio.file.Path
import kotlin.io.path.name

object AlignmentQC {
    private const val successfullyAligned = "success"

    fun alignQc(
        files: List<Path>,
        percent: Boolean = false
    ) = run {
        val file2report = files.associate { it.fileName.name to IOUtil.extractReports(it).first() as AlignerReport }

        val data = mapOf<Any, MutableList<Any?>>(
            "sample" to mutableListOf(),
            "value" to mutableListOf(),
            "type" to mutableListOf()
        )

        for ((s, rep) in file2report) {
            val m = LinkedHashMap<Any, Long>()
            m[successfullyAligned] = rep.aligned
            m.putAll(rep.notAlignedReasons.mapKeys { it.key })

            val norm: Double = if (percent) (rep.totalReadsProcessed.toDouble() / 100.0) else 1.0
            for ((k, v) in m) {
                data["sample"]!! += s
                data["value"]!! += (v.toDouble() / norm)
                data["type"]!! += k
            }
        }

        var plt = ggplot(data) {
            x = "sample"
            y = "value"
        }

        plt += geomBar(
            position = Pos.stack,
            stat = Stat.identity,
        ) {
            fill = "type"
        }

        plt += scaleXDiscrete(
            breaks = files.map { it.fileName.name },
        )

        plt += scaleFillManual(
            name = "",
            values = listOf(
                "#56D688",     // successfullyAligned,
                "#FFF680",     // VDJCAlignmentFailCause.NoHits,
                "#FED470",     // VDJCAlignmentFailCause.NoCDR3Parts,
                "#FDA163",     // VDJCAlignmentFailCause.NoVHits,
                "#F36C5A",     // VDJCAlignmentFailCause.NoJHits,
                "#D64470",     // VDJCAlignmentFailCause.VAndJOnDifferentTargets
                "#A03080",     // VDJCAlignmentFailCause.LowTotalScore,
                "#702084",     // VDJCAlignmentFailCause.NoBarcode,
                "#451777",     // VDJCAlignmentFailCause.BarcodeNotInWhitelist
            ),
            breaks = listOf(
                successfullyAligned,
                VDJCAlignmentFailCause.NoHits,
                VDJCAlignmentFailCause.NoCDR3Parts,
                VDJCAlignmentFailCause.NoVHits,
                VDJCAlignmentFailCause.NoJHits,
                VDJCAlignmentFailCause.VAndJOnDifferentTargets,
                VDJCAlignmentFailCause.LowTotalScore,
                VDJCAlignmentFailCause.NoBarcode,
                VDJCAlignmentFailCause.BarcodeNotInWhitelist
            ),
            labels = listOf(
                "Successfully aligned",
                VDJCAlignmentFailCause.NoHits.reportLine,
                VDJCAlignmentFailCause.NoCDR3Parts.reportLine,
                VDJCAlignmentFailCause.NoVHits.reportLine,
                VDJCAlignmentFailCause.NoJHits.reportLine,
                VDJCAlignmentFailCause.VAndJOnDifferentTargets.reportLine,
                VDJCAlignmentFailCause.LowTotalScore.reportLine,
                VDJCAlignmentFailCause.NoBarcode.reportLine,
                VDJCAlignmentFailCause.BarcodeNotInWhitelist.reportLine
            ),
            guide = guideLegend(ncol = 2)
        )

        plt += ylab(if (percent) "%" else "# reads")
        plt += xlab("")
        plt += coordFlip()

        plt += theme(
            panelGrid = elementBlank(),
            axisTicksX = elementBlank(),
            axisLineX = elementBlank(),
            axisLineY = elementLine(),

            legendTitle = elementBlank(),
            legendText = elementText(),


            title = elementText()
        )
            .legendPositionTop()
            .legendDirectionVertical()

        plt += ggsize(850, 100 + 25 * files.size)

        plt += labs(
            title = "Alignments rate",
        )

        plt
    }
}