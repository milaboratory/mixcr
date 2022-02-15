package com.milaboratory.mixcr.postanalysis.dataframe

import com.milaboratory.mixcr.postanalysis.PostanalysisResult
import com.milaboratory.mixcr.postanalysis.ui.PostanalysisSchema
import jetbrains.letsPlot.coordCartesian
import jetbrains.letsPlot.coordFixed
import jetbrains.letsPlot.geom.geomTile
import jetbrains.letsPlot.intern.Plot
import jetbrains.letsPlot.letsPlot
import jetbrains.letsPlot.scale.scaleSizeIdentity
import jetbrains.letsPlot.scale.scaleXDiscrete
import jetbrains.letsPlot.scale.scaleYDiscrete
import jetbrains.letsPlot.theme
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.annotations.DataSchema
import org.jetbrains.kotlinx.dataframe.api.cast
import org.jetbrains.kotlinx.dataframe.api.toDataFrame
import org.jetbrains.kotlinx.dataframe.api.toMap
import org.jetbrains.kotlinx.dataframe.io.writeCSV


/**
 * DataFrame row for V or J usage data
 */
@DataSchema
@Suppress("UNCHECKED_CAST")
interface GeneUsageRow {
    /** Sample ID */
    val sample: String

    /** Payload (Gene) */
    val gene: String

    /** Payload weight */
    val weight: Double
}

object GeneUsage {
    /**
     * Imports data into DataFrame
     **/
    fun dataFrame(
        paSett: PostanalysisSchema<*>,
        paResult: PostanalysisResult,
        group: String
    ) = run {

        val data = mutableMapOf<String, MutableList<Any>>(
            GeneUsageRow::sample.name to mutableListOf(),
            GeneUsageRow::gene.name to mutableListOf(),
            GeneUsageRow::weight.name to mutableListOf(),
        )

        for ((_, charData) in paResult.forGroup(paSett.getGroup<Any>(group)).data) {
            for ((sampleId, keys) in charData.data) {
                for (metric in keys.data) {
                    val key = metric.key.toString()
                    data[GeneUsageRow::sample.name]!! += sampleId
                    data[GeneUsageRow::gene.name]!! += key
                    data[GeneUsageRow::weight.name]!! += metric.value
                }
            }
        }

        data.toDataFrame().cast<GeneUsageRow>()
    }
//
//    /**
//     * Create Plot
//     **/
//    fun DataFrame<GeneUsageRow>.plot() = run {
//        var plt = letsPlot(toMap()) {
//            x = GeneUsageRow::gene.name
//            y = GeneUsageRow::sample.name
//            fill = GeneUsageRow::weight.name
//        }
//
//        writeCSV("data.csv")
//        val xValues = this.gene.toList()
//        val yValues = this.sample.toList()
//
//        plt += geomTile(
////            sampling = samplingNone,
////            size = 0.0,
////            width = xValues.size * 40.0,
////            height = yValues.size * 40.0
//        )
//
////        plt += scaleFillGradient()
////        plt += ggsize(1024, 1024)
//
//        addCommonParams(plt, xValues, yValues, true)
//    }

    /////// from CorrPlot
    private fun addCommonParams(
        plot: Plot,
        xValues: List<String>,
        yValues: List<String>,
        onlyTiles: Boolean
    ): Plot {
        @Suppress("NAME_SHADOWING")
        var plot = plot
        plot += theme()
            .axisTitleBlank()
            .axisLineBlank()

        plot += scaleSizeIdentity(naValue = 0, guide = "none")

        val expand = listOf(0.0, 0.0)
        plot += scaleXDiscrete(breaks = xValues, limits = xValues, expand = expand)
        plot += scaleYDiscrete(
            breaks = yValues,
            limits = yValues,
            expand = expand
        )

        val xLim = Pair(-0.6, xValues.size - 1 + 0.6)
        val yLim = Pair(-0.6, yValues.size - 1 + 0.6)
        if (onlyTiles) {
            plot += coordCartesian(xlim = xLim, ylim = yLim)
        } else {
            plot += coordFixed(xlim = xLim, ylim = yLim)
        }
        return plot
    }
}
