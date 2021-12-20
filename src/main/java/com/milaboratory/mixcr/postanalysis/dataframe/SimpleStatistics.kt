package com.milaboratory.mixcr.postanalysis.dataframe

import com.milaboratory.mixcr.postanalysis.PostanalysisResult
import com.milaboratory.mixcr.postanalysis.dataframe.SimpleMetricsRow.Companion.metric
import com.milaboratory.mixcr.postanalysis.dataframe.SimpleMetricsRow.Companion.value
import com.milaboratory.mixcr.postanalysis.stat.HolmBonferroni
import jetbrains.datalore.plot.PlotSvgExport
import jetbrains.letsPlot.Pos
import jetbrains.letsPlot.geom.geomBoxplot
import jetbrains.letsPlot.geom.geomPoint
import jetbrains.letsPlot.intern.toSpec
import jetbrains.letsPlot.label.ggtitle
import jetbrains.letsPlot.label.labs
import jetbrains.letsPlot.letsPlot
import jetbrains.letsPlot.stat.statBoxplot
import jetbrains.letsPlot.stat.statContour
import org.jetbrains.kotlinx.dataframe.*
import org.jetbrains.kotlinx.dataframe.annotations.DataSchema
import org.jetbrains.kotlinx.dataframe.api.*
import java.nio.file.Path

/**
 * DataFrame row for single statistical char group
 */
@DataSchema
@Suppress("UNCHECKED_CAST")
interface SimpleMetricsRow {
    /** Sample ID */
    val sample: String

    /** Metric name */
    val metric: String

    /** Value */
    val value: Double

    companion object {
        ////// DSL

        val sample by column<String>()

        val DataFrame<SimpleMetricsRow>.sample get() = this[SimpleMetricsRow::sample.name] as DataColumn<String>
        val DataRow<SimpleMetricsRow>.sample get() = this[SimpleMetricsRow::sample.name] as String

        val metric by column<String>()

        val DataFrame<SimpleMetricsRow>.metric get() = this[SimpleMetricsRow::metric.name] as DataColumn<String>
        val DataRow<SimpleMetricsRow>.metric get() = this[SimpleMetricsRow::metric.name] as String

        val value by column<Double>()

        val DataFrame<SimpleMetricsRow>.value get() = this[SimpleMetricsRow::value.name] as DataColumn<Double>
        val DataRow<SimpleMetricsRow>.value get() = this[SimpleMetricsRow::value.name] as Double
    }
}

object SimpleStatistics {

    /**
     * Box plot settings
     *
     * @param metrics metrics to export in plots (null for all available metrics)
     * @param primaryGroup metadata field used for primary grouping
     * @param secondaryGroup metadata field used for secondary grouping
     * */
    data class BoxPlotSettings(
        val metrics: List<String>? = null,
        val primaryGroup: String? = null,
        val primaryGroupOrder: List<String>? = null,
        val secondaryGroup: String? = null,
        val secondaryGroupOrder: List<String>? = null,
        val applyHolmBonferroni: Boolean = false,
        val HolmBonferroniFWer: Double = 0.01
    ) {
        companion object {
            val Default = BoxPlotSettings()
        }
    }

    /**
     * Imports data into DataFrame
     **/
    fun dataFrame(
        paResult: PostanalysisResult,
        metricsFilter: Set<String>?,
    ) = run {

        val data = mutableMapOf<String, MutableList<Any>>(
            SimpleMetricsRow::sample.name to mutableListOf(),
            SimpleMetricsRow::metric.name to mutableListOf(),
            SimpleMetricsRow::value.name to mutableListOf(),
        )

        for ((_, charData) in paResult.data) {
            for ((sampleId, keys) in charData.data) {
                for (metric in keys.data) {
                    val key = metric.key.toString()
                    if (metricsFilter != null && !metricsFilter.contains(key)) {
                        continue
                    }
                    data[SimpleMetricsRow::sample.name]!! += sampleId
                    data[SimpleMetricsRow::metric.name]!! += key
                    data[SimpleMetricsRow::value.name]!! += metric.value
                }
            }
        }

        data.toDataFrame().cast<SimpleMetricsRow>()
    }

    /**
     * Attaches metadata to statistics
     **/
    fun DataFrame<SimpleMetricsRow>.withMetadata(metadata: AnyFrame) = run {
        this.leftJoin(metadata) { SimpleMetricsRow.sample }
    }

    /**
     * Create Plots for all metrics
     **/
    fun DataFrame<SimpleMetricsRow>.plots(
        settings: BoxPlotSettings,
    ) = groupBy { SimpleMetricsRow.metric }.groups.map { df ->

        val filteredDf =
            if (settings.applyHolmBonferroni)
                HolmBonferroni.run(
                    df.rows().toList(),
                    { it.value },
                    settings.HolmBonferroniFWer
                ).toDataFrame()
            else
                df

        if (filteredDf.isEmpty())
            return@map null

        var plt = letsPlot(filteredDf.toMap()) {
            x = settings.primaryGroup
            y = SimpleMetricsRow.value.name()
            group = settings.secondaryGroup
            fill = settings.secondaryGroup ?: settings.primaryGroup
        }


        plt += geomBoxplot(
            fatten = 2,
            outlierShape = null,
            outlierColor = null,
            outlierSize = 0,
//            position = positionDodge(0.1)
        ) {
//            color = settings.secondaryGroup
//            fill = settings.secondaryGroup
        }


        plt += geomPoint(
            position = Pos.jitterdodge,
            shape = 21,
            color = "black"
        ) {
            color = settings.secondaryGroup
            fill = settings.secondaryGroup
        }

        val metricName = if (!df.isEmpty()) {
            df.first().metric
        } else {
            ""
        }

        plt += ggtitle(metricName)
        plt += labs(x = settings.primaryGroup, y = metricName)

//        plt += theme().axisTitleXBlank()
//        plt += theme().axisTextXBlank()
//        plt += theme().axisTicksXBlank()

        metricName to plt
    }.filter { it != null }.map { it!! }.toList().toMap()


    /**
     * Export plot into single PDF file
     *
     * @param destination path to export PDF
     * @param settings plot settings
     * */
    fun DataFrame<SimpleMetricsRow>.plotPDF(
        destination: Path,
        settings: BoxPlotSettings,
    ) = run {
        writePDF(destination,
            plots(settings).values.map { metric ->
                PlotSvgExport.buildSvgImageFromRawSpecs(
                    metric.toSpec()
                )
            }.toList().map { toPdf(it) }
        )
    }

    /**
     * Create and export all plots into single PDF file
     *
     * @param destination path to export PDF
     * @param paResult PA results
     * @param settings plot settings
     * */
    fun plotPDF(
        destination: Path,
        paResult: PostanalysisResult,
        metricsFilter: Set<String>?,
        settings: BoxPlotSettings
    ) {
        dataFrame(paResult, metricsFilter).plotPDF(destination, settings)
    }
}
