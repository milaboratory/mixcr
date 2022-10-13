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
import com.milaboratory.mixcr.MiXCRStepReports
import com.milaboratory.mixcr.basictypes.IOUtil
import com.milaboratory.mixcr.cli.ChainUsageStats
import com.milaboratory.mixcr.cli.ChainUsageStatsRecord
import io.repseq.core.Chains.IGH
import io.repseq.core.Chains.IGK
import io.repseq.core.Chains.IGL
import io.repseq.core.Chains.TRA
import io.repseq.core.Chains.TRAD
import io.repseq.core.Chains.TRB
import io.repseq.core.Chains.TRD
import io.repseq.core.Chains.TRG
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
import jetbrains.letsPlot.label.labs
import jetbrains.letsPlot.label.xlab
import jetbrains.letsPlot.label.ylab
import jetbrains.letsPlot.sampling.samplingNone
import jetbrains.letsPlot.scale.guideLegend
import jetbrains.letsPlot.scale.scaleFillManual
import jetbrains.letsPlot.scale.scaleXDiscrete
import jetbrains.letsPlot.theme
import java.nio.file.Path


object ChainUsage {

    fun chainUsageAlign(
        files: List<Path>,
        percent: Boolean,
        showNonFunctional: Boolean,
        hw: SizeParameters? = null
    ) = chainUsage(files, percent, showNonFunctional, hw) {
        it[MiXCRCommandDescriptor.align].first().chainUsage
    }

    fun chainUsageAssemble(
        files: List<Path>,
        percent: Boolean,
        showNonFunctional: Boolean,
        hw: SizeParameters? = null
    ) =
        chainUsage(files, percent, showNonFunctional, hw) {
            it[MiXCRCommandDescriptor.assemble].first().clonalChainUsage
        } + ggtitle("Clonal chain usage")

    fun chainUsage(
        files: List<Path>,
        percent: Boolean,
        showNonFunctional: Boolean,
        hw: SizeParameters? = null,
        usageExtractor: (MiXCRStepReports) -> ChainUsageStats
    ) = run {
        val typesToShow = if (showNonFunctional)
            listOf(typeProductive, typeStops, typeOOF)
        else
            listOf(typeProductive)

        val file2report = files.associate { it.fileName.toString() to usageExtractor(IOUtil.extractFooter(it).reports) }

        val data = mapOf<Any, MutableList<Any?>>(
            "sample" to mutableListOf(),
            "value" to mutableListOf(),
            "chain" to mutableListOf(),
        )

        val chains = file2report.values.flatMap { it.chains.keys }.toSet()

        for ((s, rep) in file2report) {
            val erec = ChainUsageStatsRecord.EMPTY

            val map = chains.associateWith { rep.chains.getOrDefault(it, erec) }

            val total = chains.sumOf { rep.chains[it]?.total ?: 0L }
            val norm: Double = if (percent) (total / 100.0) else 1.0

            for ((k, v) in map) {
                for (type in typesToShow) {
                    data["sample"]!! += s
                    data["chain"]!! += (k.toString() + type)
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
            breaks = files.map { it.fileName.toString() },
        )

        fun getData(param: String) = chains.flatMap { typesToShow.map { t -> chainsData[it]!![param]!![t]!! } }
        plt += scaleFillManual(
            name = "",
            values = getData("values"),
            breaks = getData("breaks"),
            labels = getData("labels"),
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

        if (hw != null)
            plt += ggsize(hw.width, hw.height)
        else
            plt += ggsize(1000, 300 + 35 * files.size)


        plt += labs(
            title = "Chain usage",
        )

        plt
    }


    private const val typeProductive = "productive"
    private const val typeOOF = "oof"
    private const val typeStops = "stops"

    private val chainsData = mapOf(
        TRAD to mapOf(
            "values" to mapOf(
                typeProductive to "#105BCC",   // Chains.TRAD
                typeStops to "#2D93FA",   // Chains.TRAD STOPS
                typeOOF to "#99CCFF",   // Chains.TRAD OOF
            ),

            "breaks" to mapOf(
                typeProductive to ("$TRAD$typeProductive"),
                typeStops to ("$TRAD$typeStops"),
                typeOOF to ("$TRAD$typeOOF"),
            ),

            "labels" to mapOf(
                typeProductive to (TRAD.toString()),
                typeStops to ("$TRAD (stops)"),
                typeOOF to ("$TRAD (OOF)"),
            )
        ),

        TRA to mapOf(
            "values" to mapOf(
                typeProductive to "#105BCC",   // Chains.TRA
                typeStops to "#2D93FA",   // Chains.TRA STOPS
                typeOOF to "#99CCFF",   // Chains.TRA OOF
            ),

            "breaks" to mapOf(
                typeProductive to ("$TRA$typeProductive"),
                typeStops to ("$TRA$typeStops"),
                typeOOF to ("$TRA$typeOOF"),
            ),

            "labels" to mapOf(
                typeProductive to (TRA.toString()),
                typeStops to ("$TRA (stops)"),
                typeOOF to ("$TRA (OOF)"),
            )
        ),

        TRD to mapOf(
            "values" to mapOf(
                typeProductive to "#068A94",   // Chains.TRD
                typeStops to "#27C2C2",   // Chains.TRD STOPS
                typeOOF to "#90E0E0",   // Chains.TRD OOF
            ),

            "breaks" to mapOf(
                typeProductive to ("$TRD$typeProductive"),
                typeStops to ("$TRD$typeStops"),
                typeOOF to ("$TRD$typeOOF"),
            ),

            "labels" to mapOf(
                typeProductive to (TRD.toString()),
                typeStops to ("$TRD (stops)"),
                typeOOF to ("$TRD (OOF)"),
            )
        ),

        TRB to mapOf(
            "values" to mapOf(
                typeProductive to "#198020",    // Chains.TRB
                typeStops to "#42B842",    // Chains.TRB STOPS
                typeOOF to "#99E099",    // Chains.TRB OOF
            ),

            "breaks" to mapOf(
                typeProductive to ("$TRB$typeProductive"),
                typeStops to ("$TRB$typeStops"),
                typeOOF to ("$TRB$typeOOF"),
            ),

            "labels" to mapOf(
                typeProductive to (TRB.toString()),
                typeStops to ("$TRB (stops)"),
                typeOOF to ("$TRB (OOF)"),
            )
        ),

        TRG to mapOf(
            "values" to mapOf(
                typeProductive to "#5F31CC",    // Chains.TRG
                typeStops to "#845CFF",    // Chains.TRG STOPS
                typeOOF to "#C1ADFF",    // Chains.TRG OOF
            ),

            "breaks" to mapOf(
                typeProductive to ("$TRG$typeProductive"),
                typeStops to ("$TRG$typeStops"),
                typeOOF to ("$TRG$typeOOF"),
            ),

            "labels" to mapOf(
                typeProductive to (TRG.toString()),
                typeStops to ("$TRG (stops)"),
                typeOOF to ("$TRG (OOF)"),
            )
        ),

        IGH to mapOf(
            "values" to mapOf(
                typeProductive to "#C26A27",    // Chains.IGH
                typeStops to "#FF9429",    // Chains.IGH STOPS
                typeOOF to "#FFCB8F",    // Chains.IGH OOF
            ),

            "breaks" to mapOf(
                typeProductive to ("$IGH$typeProductive"),
                typeStops to ("$IGH$typeStops"),
                typeOOF to ("$IGH$typeOOF"),
            ),

            "labels" to mapOf(
                typeProductive to (IGH.toString()),
                typeStops to ("$IGH (stops)"),
                typeOOF to ("$IGH (OOF)"),
            )
        ),

        IGK to mapOf(
            "values" to mapOf(
                typeProductive to "#A324B2",    // Chains.IGK
                typeStops to "#E553E5",    // Chains.IGK STOPS
                typeOOF to "#FAAAFA",    // Chains.IGK OOF
            ),

            "breaks" to mapOf(
                typeProductive to ("$IGK$typeProductive"),
                typeStops to ("$IGK$typeStops"),
                typeOOF to ("$IGK$typeOOF"),
            ),

            "labels" to mapOf(
                typeProductive to (IGK.toString()),
                typeStops to ("$IGK (stops)"),
                typeOOF to ("$IGK (OOF)"),
            )
        ),

        IGL to mapOf(
            "values" to mapOf(
                typeProductive to "#AD3757",    // Chains.IGL
                typeStops to "#F05670",    // Chains.IGL STOPS
                typeOOF to "#FFADBA",    // Chains.IGL OOF
            ),

            "breaks" to mapOf(
                typeProductive to ("$IGL$typeProductive"),
                typeStops to ("$IGL$typeStops"),
                typeOOF to ("$IGL$typeOOF"),
            ),

            "labels" to mapOf(
                typeProductive to (IGL.toString()),
                typeStops to ("$IGL (stops)"),
                typeOOF to ("$IGL (OOF)"),
            )
        )
    )
}
