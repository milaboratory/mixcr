package com.milaboratory.mixcr.postanalysis.plots

import com.milaboratory.miplots.stat.util.PValueCorrection
import com.milaboratory.miplots.stat.util.RefGroup
import com.milaboratory.miplots.stat.util.TestMethod
import com.milaboratory.miplots.stat.xcontinious.CorrelationMethod
import com.milaboratory.miplots.stat.xcontinious.GGScatter
import com.milaboratory.miplots.stat.xcontinious.plusAssign
import com.milaboratory.miplots.stat.xcontinious.statCor
import com.milaboratory.miplots.stat.xdiscrete.GGBoxPlot
import com.milaboratory.miplots.stat.xdiscrete.LabelFormat
import com.milaboratory.miplots.stat.xdiscrete.plusAssign
import com.milaboratory.miplots.stat.xdiscrete.statCompareMeans
import com.milaboratory.miplots.toPDF
import com.milaboratory.mixcr.postanalysis.PostanalysisResult
import com.milaboratory.mixcr.postanalysis.SetPreprocessorStat
import com.milaboratory.mixcr.postanalysis.plots.BasicStatistics.PlotType.*
import jetbrains.letsPlot.intern.Plot
import jetbrains.letsPlot.label.ggtitle
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.annotations.DataSchema
import org.jetbrains.kotlinx.dataframe.api.*

/**
 * DataFrame row for single statistical char group
 */
@DataSchema
@Suppress("UNCHECKED_CAST")
data class BasicStatRow(
    /** Preprocessor */
    val preproc: String,
    /** Preprocessor statistics for the sample */
    val preprocStat: SetPreprocessorStat,
    /** Sample ID */
    val sample: String,
    /** Metric name */
    val metric: String,
    /** Value */
    val value: Double,
)

object BasicStatistics {
    /**
     * Imports data into DataFrame
     **/
    fun dataFrame(
        paResult: PostanalysisResult,
        metricsFilter: List<String>?,
    ): DataFrame<BasicStatRow> = run {
        val data = mutableListOf<BasicStatRow>()

        val mf = metricsFilter?.toSet()
        for ((ch, charData) in paResult.data) {
            for ((sampleId, keys) in charData.data) {
                for (metric in keys.data) {
                    val key = metric.key.toString()
                    if (mf != null && !mf.contains(key)) {
                        continue
                    }
                    data += BasicStatRow(
                        charData.preproc,
                        paResult.getPreprocStat(ch, sampleId),
                        sampleId, key, metric.value
                    )
                }
            }
        }

        data.toDataFrame()
    }

    /**
     * Imports data into DataFrame
     **/
    fun dataFrame(
        paResult: PostanalysisResult,
        metricsFilter: List<String>?,
        metadata: Metadata?,
    ) = run {
        var df = dataFrame(paResult, metricsFilter)
        if (metadata != null)
            df = df.withMetadata(metadata)
        df
    }

    /**
     * Imports data into DataFrame
     **/
    fun dataFrame(
        paResult: PostanalysisResult,
        metricsFilter: List<String>?,
        metadataPath: String?,
    ) = dataFrame(paResult, metricsFilter, readMetadata(metadataPath))

    data class PlotParameters(
        val plotType: PlotType = Auto,
        val primaryGroup: String? = null,
        val secondaryGroup: String? = null,
        val facetBy: String? = null,
        val showOverallPValue: Boolean = true,
        val showPairwisePValue: Boolean = true,
        val refGroup: RefGroup? = null,
        val hideNS: Boolean = false,
        val overallPValueFormat: LabelFormat? = null,
        val refPValueFormat: LabelFormat? = null,
        val comparisonsPValueFormat: LabelFormat? = null,
        val paired: Boolean = false,
        val method: TestMethod = TestMethod.Wilcoxon,
        val multipleGroupsMethod: TestMethod = TestMethod.KruskalWallis,
        val pAdjustMethod: PValueCorrection.Method? = PValueCorrection.Method.Bonferroni,
        val correlationMethod: CorrelationMethod = CorrelationMethod.Pearson,
    )

    enum class PlotType {
        Auto,
        BoxPlot,
        LinePlot,
        Scatter;

        companion object {
            fun parse(str: String) =
                values().find { it.name.equals(str, ignoreCase = true) }
                    ?: throw IllegalArgumentException("invalid plot type: $str")
        }
    }

    private fun isCategorial(t: PlotType) = when (t) {
        Scatter, LinePlot -> false
        else -> true
    }

    private fun guessPlotType(par: PlotParameters, meta: Metadata?) =
        if (par.primaryGroup == null || meta == null || meta.isCategorial(par.primaryGroup))
            BoxPlot
        else
            Scatter

    fun plots(
        df: DataFrame<BasicStatRow>,
        par: PlotParameters,
    ) = df.groupBy { metric }.groups.toList()
        .filter { !it.isEmpty() }
        .map { mdf -> plot(mdf, par) + ggtitle(mdf.first()[BasicStatRow::metric.name]!!.toString()) }

    fun plotsAndSummary(
        df: DataFrame<BasicStatRow>,
        par: PlotParameters,
    ): List<ByteArray> = df.groupBy { preproc }.groups.toList().flatMap { byPreproc ->
        val metrics = byPreproc.metric.distinct().toList()

        val droppedSamples =
            df.filter { preprocStat.dropped || preprocStat.nElementsAfter == 0L }
                .sample.distinct().toSet()

        val summary = Preprocessing.pdfSummary(
            byPreproc.rows().associate { it.sample to it.preprocStat },
            metrics.joinToString(", ")
        )

        val plots = byPreproc
            .filter { !droppedSamples.contains(it.sample) }
            .groupBy { metric }.groups.toList()
            .filter { !it.isEmpty() }
            .map { mdf -> plot(mdf, par) + ggtitle(mdf.first()[BasicStatRow::metric.name]!!.toString()) }
            .map { it.toPDF() }
            .toList()

        if (summary == null)
            plots
        else
            listOf(summary) + plots
    }

    private fun toCategorical(df: DataFrame<BasicStatRow>, vararg cols: String) = run {
        var r = df
        for (col in cols)
            r = r.replace { col<Any>() }.with { it.convertToString() }
        r
    }

    fun plot(
        df: DataFrame<BasicStatRow>,
        par: PlotParameters,
    ): Plot = run {
        val type = if (par.plotType == Auto) guessPlotType(par, df) else par.plotType

        val dfRefined = (
                if (isCategorial(type)) {
                    if (par.primaryGroup == null)
                        df.add(List(df.rowsCount()) { "" }.toColumn("__x__"))
                    else if (df.isNumeric(par.primaryGroup) || (par.secondaryGroup != null && df.isNumeric(par.secondaryGroup))) {
                        toCategorical(df, *listOfNotNull(par.primaryGroup, par.secondaryGroup).toTypedArray())
                    } else
                        df
                } else {
                    df
                }
                )

        if (isCategorial(type)) {
            val plt = when (type) {
                BoxPlot -> GGBoxPlot(
                    dfRefined,
                    x = par.primaryGroup ?: "__x__",
                    y = BasicStatRow::value.name,
                    facetBy = par.facetBy,
                    facetNRow = 1,
                ) {
                    fill = par.secondaryGroup ?: par.primaryGroup
                }
                else -> throw RuntimeException("$type")
            }

            if (par.showPairwisePValue)
                plt += statCompareMeans(
                    method = par.method,
                    multipleGroupsMethod = par.multipleGroupsMethod,
                    pAdjustMethod = par.pAdjustMethod,
                    paired = par.paired,
                    hideNS = par.hideNS,
                    labelFormat = par.comparisonsPValueFormat,
                    allComparisons = true
                )

            if (par.refGroup != null)
                plt += statCompareMeans(
                    method = par.method,
                    multipleGroupsMethod = par.multipleGroupsMethod,
                    pAdjustMethod = par.pAdjustMethod,
                    paired = par.paired,
                    hideNS = par.hideNS,
                    labelFormat = par.refPValueFormat,
                    refGroup = par.refGroup
                )

            if (par.showOverallPValue && par.secondaryGroup == null)
                plt += statCompareMeans(
                    method = par.method,
                    multipleGroupsMethod = par.multipleGroupsMethod,
                    labelFormat = par.overallPValueFormat,
                )

            plt.plot

        } else {

            par.primaryGroup!!
            val plt = when (type) {
                Scatter -> GGScatter(
                    dfRefined,
                    x = par.primaryGroup,
                    y = BasicStatRow::value.name,
                    facetBy = par.facetBy,
                    facetNRow = 1,
                ) {
                    shape = par.secondaryGroup
                    color = par.secondaryGroup
                    linetype = par.secondaryGroup
                }
                LinePlot -> TODO()
                else -> throw RuntimeException("$type")
            }

            plt += statCor(method = par.correlationMethod)

            plt.plot
        }
    }
}
