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

import com.milaboratory.mixcr.MiXCRCommandDescriptor
import com.milaboratory.mixcr.basictypes.IOUtil
import com.milaboratory.mixcr.cli.qc.CommandExportQc.SizeParameters
import com.milaboratory.mixcr.vdjaligners.VDJCAlignmentFailCause
import jetbrains.letsPlot.Pos
import jetbrains.letsPlot.Stat
import jetbrains.letsPlot.coordFlip
import jetbrains.letsPlot.elementBlank
import jetbrains.letsPlot.elementLine
import jetbrains.letsPlot.elementText
import jetbrains.letsPlot.geom.geomBar
import jetbrains.letsPlot.ggplot
import jetbrains.letsPlot.ggsize
import jetbrains.letsPlot.label.ggtitle
import jetbrains.letsPlot.label.xlab
import jetbrains.letsPlot.label.ylab
import jetbrains.letsPlot.sampling.samplingNone
import jetbrains.letsPlot.scale.guideLegend
import jetbrains.letsPlot.scale.scaleFillManual
import jetbrains.letsPlot.scale.scaleXDiscrete
import jetbrains.letsPlot.theme
import java.nio.file.Path
import kotlin.io.path.name

object AlignmentQC {
    private const val successfullyAligned = "success"

    fun alignQc(
        files: List<Path>,
        percent: Boolean = false,
        hw: SizeParameters? = null
    ) = run {
        val file2report =
            files.associate { it.fileName.name to IOUtil.extractFooter(it).reports[MiXCRCommandDescriptor.align].first() }

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
            sampling = samplingNone
        ) {
            fill = "type"
        }

        plt += scaleXDiscrete(
            breaks = files.map { it.fileName.name },
        )

        plt += scaleFillManual(
            name = "",
            values = listOf(
                "#3ECD8D",     // successfullyAligned,
                "#FED470",     // VDJCAlignmentFailCause.NoHits,
                "#FDA163",     // VDJCAlignmentFailCause.NoCDR3Parts,
                "#F36C5A",     // VDJCAlignmentFailCause.NoVHits,
                "#D64470",     // VDJCAlignmentFailCause.NoJHits,
                "#A03080",     // VDJCAlignmentFailCause.VAndJOnDifferentTargets
                "#702084",     // VDJCAlignmentFailCause.LowTotalScore,
                "#451777",     // VDJCAlignmentFailCause.NoBarcode,
//                "#2B125C",     // VDJCAlignmentFailCause.BarcodeNotInWhitelist
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
            ),
            labels = listOf(
                "Successfully aligned",
                VDJCAlignmentFailCause.NoHits.shortReportLine,
                VDJCAlignmentFailCause.NoCDR3Parts.shortReportLine,
                VDJCAlignmentFailCause.NoVHits.shortReportLine,
                VDJCAlignmentFailCause.NoJHits.shortReportLine,
                VDJCAlignmentFailCause.VAndJOnDifferentTargets.shortReportLine,
                VDJCAlignmentFailCause.LowTotalScore.shortReportLine,
                VDJCAlignmentFailCause.NoBarcode.shortReportLine,
            ),
            guide = guideLegend(ncol = 2)
        )

        plt += ylab(if (percent) "%" else "# reads")
        plt += xlab("")
        plt += ggtitle("Alignments rate")

        plt += coordFlip()

        plt += theme(
            panelGrid = elementBlank(),
            axisTicksX = elementBlank(),
            axisLineX = elementBlank(),
            axisLineY = elementLine(),

            legendTitle = elementBlank(),
            legendText = elementText(),

            title = elementText(),
        )
            .legendPositionTop()
            .legendDirectionVertical()

        if (hw != null)
            plt += ggsize(hw.width, hw.height)
        else
            plt += ggsize(1000, 300 + 35 * files.size)


        plt
    }
}
