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
import com.milaboratory.mixcr.cli.*
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
    @JvmStatic
    val DefaultChainsList = listOf(
        TRAD_NAMED,
        TRB_NAMED,
        TRG_NAMED,
        IGH_NAMED,
        IGK_NAMED,
        IGL_NAMED
    )

    fun chainUsageAlign(
        files: List<Path>,
        percent: Boolean = false,
        chains: List<NamedChains>,
    ) =
        chainUsage(files, percent, chains) {
            (it.first() as AlignerReport).chainUsage
        }

    fun chainUsageAssemble(
        files: List<Path>,
        percent: Boolean = false,
        chains: List<NamedChains>,
    ) =
        chainUsage(files, percent, chains) {
            (it.filter { it is CloneAssemblerReport }.first() as CloneAssemblerReport).clonalChainUsage
        } + ggtitle("Clonal chain usage")

    private const val typeProductive = "productive"
    private const val typeOOF = "oof"
    private const val typeStops = "stops"

    private val chainsData = mapOf(
        TRAD_NAMED to mapOf(
            "values" to listOf(
                "#105BCC",   // Chains.TRAD_NAMED
                "#2D93FA",   // Chains.TRAD_NAMED STOPS
                "#99CCFF",   // Chains.TRAD_NAMED OOF
            ),

            "breaks" to listOf(
                TRAD_NAMED.name + typeProductive,
                TRAD_NAMED.name + typeStops,
                TRAD_NAMED.name + typeOOF,
            ),

            "labels" to listOf(
                TRAD_NAMED.name,
                TRAD_NAMED.name + " (stops)",
                TRAD_NAMED.name + " (OOF)",
            )
        ),

        TRB_NAMED to mapOf(
            "values" to listOf(
                "#198020",    // Chains.TRB_NAMED
                "#42B842",    // Chains.TRB_NAMED STOPS
                "#99E099",    // Chains.TRB_NAMED OOF
            ),

            "breaks" to listOf(
                TRB_NAMED.name + typeProductive,
                TRB_NAMED.name + typeStops,
                TRB_NAMED.name + typeOOF,
            ),

            "labels" to listOf(
                TRB_NAMED.name,
                TRB_NAMED.name + " (stops)",
                TRB_NAMED.name + " (OOF)",
            )
        ),

        TRG_NAMED to mapOf(
            "values" to listOf(
                "#5F31CC",    // Chains.TRG_NAMED
                "#845CFF",    // Chains.TRG_NAMED STOPS
                "#C1ADFF",    // Chains.TRG_NAMED OOF
            ),

            "breaks" to listOf(
                TRG_NAMED.name + typeProductive,
                TRG_NAMED.name + typeStops,
                TRG_NAMED.name + typeOOF,
            ),

            "labels" to listOf(
                TRG_NAMED.name,
                TRG_NAMED.name + " (stops)",
                TRG_NAMED.name + " (OOF)",
            )
        ),

        IGH_NAMED to mapOf(
            "values" to listOf(
                "#C26A27",    // Chains.IGH_NAMED
                "#FF9429",    // Chains.IGH_NAMED STOPS
                "#FFCB8F",    // Chains.IGH_NAMED OOF
            ),

            "breaks" to listOf(
                IGH_NAMED.name + typeProductive,
                IGH_NAMED.name + typeStops,
                IGH_NAMED.name + typeOOF,
            ),

            "labels" to listOf(
                IGH_NAMED.name,
                IGH_NAMED.name + " (stops)",
                IGH_NAMED.name + " (OOF)",
            )
        ),

        IGK_NAMED to mapOf(
            "values" to listOf(
                "#A324B2",    // Chains.IGK_NAMED
                "#E553E5",    // Chains.IGK_NAMED STOPS
                "#FAAAFA",    // Chains.IGK_NAMED OOF
            ),

            "breaks" to listOf(
                IGK_NAMED.name + typeProductive,
                IGK_NAMED.name + typeStops,
                IGK_NAMED.name + typeOOF,
            ),

            "labels" to listOf(
                IGK_NAMED.name,
                IGK_NAMED.name + " (stops)",
                IGK_NAMED.name + " (OOF)",
            )
        ),

        IGL_NAMED to mapOf(
            "values" to listOf(
                "#AD3757",    // Chains.IGL_NAMED
                "#F05670",    // Chains.IGL_NAMED STOPS
                "#FFADBA",    // Chains.IGL_NAMED OOF
            ),

            "breaks" to listOf(
                IGL_NAMED.name + typeProductive,
                IGL_NAMED.name + typeStops,
                IGL_NAMED.name + typeOOF,
            ),

            "labels" to listOf(
                IGL_NAMED.name,
                IGL_NAMED.name + " (stops)",
                IGL_NAMED.name + " (OOF)",
            )
        )
    )

    fun chainUsage(
        files: List<Path>,
        percent: Boolean,
        chains: List<NamedChains>,
        usageExtractor: (List<MiXCRCommandReport>) -> ChainUsageStats,
    ) = run {
        val file2report = files.associate { it.fileName.name to usageExtractor(IOUtil.extractReports(it)) }

        val data = mapOf<Any, MutableList<Any?>>(
            "sample" to mutableListOf(),
            "value" to mutableListOf(),
            "chain" to mutableListOf(),
        )

        for ((s, rep) in file2report) {
            val erec = ChainUsageStatsRecord.EMPTY

            val map = DefaultChainsList
                .filter { chains.contains(it) }
                .associateWith { rep.chains.getOrDefault(it, erec) }

            val total = chains.sumOf { rep.chains[it]?.total ?: 0L }
            val norm: Double = if (percent) (total / 100.0) else 1.0

            for ((k, v) in map) {
                for (type in listOf(typeProductive, typeOOF, typeStops)) {
                    data["sample"]!! += s
                    data["chain"]!! += (k.name + type)
                    data["value"]!! += when (type) {
                        typeProductive -> v.productive().toDouble() / norm
                        typeOOF -> v.isOOF.toDouble() / norm
                        typeStops -> v.hasStops.toDouble() / norm
                        else -> throw RuntimeException()
                    }
                }
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
            values = chains.flatMap { chainsData[it]!!["values"]!! },
            breaks = chains.flatMap { chainsData[it]!!["breaks"]!! },
            labels = chains.flatMap { chainsData[it]!!["labels"]!! },
            guide = guideLegend(nrow = 3)
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

        plt += ggsize(1000, 300 + 35 * files.size)


        plt += labs(
            title = "Chain usage",
        )

        plt
    }
}