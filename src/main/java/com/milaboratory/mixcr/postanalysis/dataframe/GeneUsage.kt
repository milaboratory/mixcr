package com.milaboratory.mixcr.postanalysis.dataframe

import com.milaboratory.mixcr.postanalysis.PostanalysisResult
import com.milaboratory.mixcr.postanalysis.dataframe.GeneUsageRow.Companion.gene
import com.milaboratory.mixcr.postanalysis.dataframe.GeneUsageRow.Companion.sample
import com.milaboratory.mixcr.postanalysis.dataframe.GeneUsageRow.Companion.weight
import com.milaboratory.mixcr.postanalysis.ui.PostanalysisSchema
import jetbrains.datalore.plot.PlotSvgExport
import jetbrains.letsPlot.coordCartesian
import jetbrains.letsPlot.coordFixed
import jetbrains.letsPlot.geom.geomTile
import jetbrains.letsPlot.intern.Plot
import jetbrains.letsPlot.intern.toSpec
import jetbrains.letsPlot.letsPlot
import jetbrains.letsPlot.scale.scaleSizeIdentity
import jetbrains.letsPlot.scale.scaleXDiscrete
import jetbrains.letsPlot.scale.scaleYDiscrete
import jetbrains.letsPlot.theme
import org.jetbrains.dataframe.*
import org.jetbrains.dataframe.annotations.DataSchema
import org.jetbrains.dataframe.columns.DataColumn
import org.jetbrains.dataframe.io.writeCSV
import java.nio.file.Path


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

    companion object {
        ////// DSL

        val sample by column<String>()
        val gene by column<String>()
        val weight by column<Double>()

        val DataFrame<GeneUsageRow>.sample get() = this[GeneUsageRow::sample.name] as DataColumn<String>
        val DataRow<GeneUsageRow>.sample get() = this[GeneUsageRow::sample.name] as String

        val DataFrame<GeneUsageRow>.gene get() = this[GeneUsageRow::gene.name] as DataColumn<String>
        val DataRow<GeneUsageRow>.gene get() = this[GeneUsageRow::gene.name] as String

        val DataFrame<GeneUsageRow>.weight get() = this[GeneUsageRow::weight.name] as DataColumn<Double>
        val DataRow<GeneUsageRow>.weight get() = this[GeneUsageRow::weight.name] as Double
    }
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

        data.toDataFrame().typed<GeneUsageRow>()
    }

    /**
     * Create Plot
     **/
    fun DataFrame<GeneUsageRow>.plot() = run {
        var plt = letsPlot(toMap()) {
            x = GeneUsageRow::gene.name
            y = GeneUsageRow::sample.name
            fill = GeneUsageRow::weight.name
        }

        writeCSV("data.csv")
        val xValues = this.gene.toList()
        val yValues = this.sample.toList()

        plt += geomTile(
//            sampling = samplingNone,
//            size = 0.0,
//            width = xValues.size * 40.0,
//            height = yValues.size * 40.0
        )

//        plt += scaleFillGradient()
//        plt += ggsize(1024, 1024)

        addCommonParams(plt, xValues, yValues, true)
    }

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

    /**
     * Export plot to PDF
     *
     * @param destination path to export PDF
     */
    fun DataFrame<GeneUsageRow>.plotPDF(
        destination: Path
    ) = run {
        writePDF(
            destination,
            toPdf(PlotSvgExport.buildSvgImageFromRawSpecs(plot().toSpec()))
        )
    }

    /**
     * Create and export plot into single PDF file
     *
     * @param destination path to export PDF
     * @param paSett PA settings
     * @param paResult PA results
     * */
    fun plotPDF(
        destination: Path,
        paSett: PostanalysisSchema<*>,
        paResult: PostanalysisResult,
        group: String
    ) {
        dataFrame(paSett, paResult, group).plotPDF(destination)
    }
}
