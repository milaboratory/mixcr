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
import jetbrains.letsPlot.facet.facetWrap
import jetbrains.letsPlot.geom.geomBoxplot
import jetbrains.letsPlot.label.ggtitle
import jetbrains.letsPlot.label.xlab
import jetbrains.letsPlot.letsPlot
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

    fun plots(
        df: DataFrame<BasicStatRow>,
        pp: PlotParameters,
    ) = df.groupBy { metric }.groups.toList()
        .filter { !it.isEmpty() }
        .map { mdf -> plot(mdf, pp) + ggtitle(mdf.first()[BasicStatRow::metric.name]!!.toString()) }

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

    fun plot(
        df: DataFrame<BasicStatRow>,
        par: PlotParameters,
    ) =
        if (par.primaryGroup == null) {
            val data = df.add(List(df.rowsCount()) { "" }.toColumn("__x__")).toMap()
            var plt = letsPlot(data) {
                x = "__x__"
                y = BasicStatRow::value.name
            }
            plt += geomBoxplot()
            if (par.facetBy != null)
                plt += facetWrap(par.facetBy)

            plt += xlab("")
            plt
        } else if (df.isNumeric(par.primaryGroup)) {
            val plt = GGScatter(
                df,
                x = par.primaryGroup,
                y = BasicStatRow::value.name,
                facetBy = par.facetBy,
                facetNRow = 1,
            ) {
                shape = par.secondaryGroup
                color = par.secondaryGroup
                linetype = par.secondaryGroup
            }

            plt += statCor(method = par.correlationMethod)

            plt.plot
        } else {
            val plt = GGBoxPlot(
                df,
                x = par.primaryGroup,
                y = BasicStatRow::value.name,
                facetBy = par.facetBy,
                facetNRow = 1,
            ) {
                fill = par.secondaryGroup ?: par.primaryGroup
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
        }
}
