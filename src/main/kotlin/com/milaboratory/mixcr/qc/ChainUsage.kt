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

import com.milaboratory.mixcr.basictypes.IOUtil
import com.milaboratory.mixcr.cli.AlignerReport
import com.milaboratory.mixcr.cli.ChainUsageStats
import com.milaboratory.mixcr.cli.CloneAssemblerReport
import com.milaboratory.mixcr.cli.MiXCRCommandReport
import io.repseq.core.Chains
import io.repseq.core.Chains.*
import jetbrains.letsPlot.*
import jetbrains.letsPlot.geom.geomBar
import jetbrains.letsPlot.label.ggtitle
import jetbrains.letsPlot.label.labs
import jetbrains.letsPlot.label.xlab
import jetbrains.letsPlot.label.ylab
import jetbrains.letsPlot.sampling.samplingNone
import jetbrains.letsPlot.scale.guideLegend
import jetbrains.letsPlot.scale.scaleFillManual
import jetbrains.letsPlot.scale.scaleXDiscrete
import java.nio.file.Path
import kotlin.io.path.name

object ChainUsage {
    fun chainUsageAlign(
        files: List<Path>,
        percent: Boolean = false
    ) =
        chainUsage(files, percent) {
            (it.first() as AlignerReport).chainUsage
        }

    fun chainUsageAssemble(
        files: List<Path>,
        percent: Boolean = false
    ) =
        chainUsage(files, percent) {
            (it.filter { it is CloneAssemblerReport }.first() as CloneAssemblerReport).clonalChainUsage
        } + ggtitle("Clonal chain usage")

    fun chainUsage(
        files: List<Path>,
        percent: Boolean,
        usageExtractor: (List<MiXCRCommandReport>) -> ChainUsageStats,
    ) = run {
        val file2report = files.associate { it.fileName.name to usageExtractor(IOUtil.extractReports(it)) }

        val data = mapOf<Any, MutableList<Any?>>(
            "sample" to mutableListOf(),
            "value" to mutableListOf(),
            "chain" to mutableListOf()
        )

        for ((s, rep) in file2report) {

            val norm: Double = if (percent) (rep.total / 100.0) else 1.0
            val map = linkedMapOf(
                TRAD_NAMED to rep.chains.getOrDefault(TRAD_NAMED, 0L),
                TRB_NAMED to rep.chains.getOrDefault(TRB_NAMED, 0L),
                TRG_NAMED to rep.chains.getOrDefault(TRG_NAMED, 0L),
                IGH_NAMED to rep.chains.getOrDefault(IGH_NAMED, 0L),
                IGKL_NAMED to rep.chains.getOrDefault(IGKL_NAMED, 0L)
            )
            for ((k, v) in map) {
                data["sample"]!! += s
                data["value"]!! += (v.toDouble() / norm)
                data["chain"]!! += k
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
            fill = "chain"
        }

        plt += scaleXDiscrete(
            breaks = files.map { it.fileName.name },
        )

        plt += scaleFillManual(
            name = "",
            values = listOf(
                "#2D93FA",     // Chains.TRAD_NAMED
                "#42B842",     // Chains.TRB_NAMED
                "#845CFF",     // Chains.TRG_NAMED

                "#FF9429",     // Chains.IGH_NAMED,
                "#E553E5",     // Chains.IGKL_NAMED,
                //"#F05670",     // Chains.IGL_NAMED,
            ),
            breaks = listOf(
                TRAD_NAMED,
                TRB_NAMED,
                TRG_NAMED,
                IGH_NAMED,
                IGKL_NAMED,
            ),
            labels = listOf(
                TRAD_NAMED.name,
                TRB_NAMED.name,
                TRG_NAMED.name,
                IGH_NAMED.name,
                IGKL_NAMED.name,
            ),
            guide = guideLegend(nrow = 1)
        )

        plt += ylab(if (percent) "%" else "#")
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
            title = "Chain usage",
        )

        plt
    }
}