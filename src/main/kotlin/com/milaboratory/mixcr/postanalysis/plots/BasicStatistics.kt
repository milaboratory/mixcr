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
package com.milaboratory.mixcr.postanalysis.plots

import com.milaboratory.miplots.StandardPlots.PlotType
import com.milaboratory.miplots.StandardPlots.PlotType.BoxPlot
import com.milaboratory.miplots.StandardPlots.PlotType.Scatter
import com.milaboratory.miplots.stat.util.PValueCorrection
import com.milaboratory.miplots.stat.util.RefGroup
import com.milaboratory.miplots.stat.util.TestMethod
import com.milaboratory.miplots.stat.xcontinious.CorrelationMethod
import com.milaboratory.miplots.stat.xcontinious.GGScatter
import com.milaboratory.miplots.stat.xcontinious.plusAssign
import com.milaboratory.miplots.stat.xcontinious.statCor
import com.milaboratory.miplots.stat.xdiscrete.GGXDiscrete
import com.milaboratory.miplots.stat.xdiscrete.LabelFormat
import com.milaboratory.miplots.stat.xdiscrete.plusAssign
import com.milaboratory.miplots.stat.xdiscrete.statCompareMeans
import com.milaboratory.miplots.toPDF
import com.milaboratory.mixcr.postanalysis.PostanalysisResult
import com.milaboratory.mixcr.postanalysis.SetPreprocessorStat
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
        metricsFilter: (String) -> Boolean,
    ): DataFrame<BasicStatRow> = run {
        val data = mutableListOf<BasicStatRow>()

        for ((ch, charData) in paResult.data) {
            for ((sampleId, keys) in charData.data) {
                for (metric in keys.data) {
                    val key = metric.key.toString()
                    if (!metricsFilter(key)) {
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

        data.toDataFrame().drop { !it.value.isFinite() }
    }

    /**
     * Imports data into DataFrame
     **/
    fun dataFrame(
        paResult: PostanalysisResult,
        metadata: Metadata?,
        metricsFilter: (String) -> Boolean = { true },
    ): DataFrame<BasicStatRow> {
        val df = this.dataFrame(paResult, metricsFilter)
        return when {
            metadata != null -> df.withMetadata(metadata)
            else -> df
        }
    }

    /**
     * Imports data into DataFrame
     **/
    fun dataFrame(
        paResult: PostanalysisResult,
        metadataPath: String?,
        metricsFilter: (String) -> Boolean = { true },
    ) = dataFrame(paResult, readMetadata(metadataPath), metricsFilter)

    data class PlotParameters(
        val plotType: PlotType? = null,
        val primaryGroup: String? = null,
        val secondaryGroup: String? = null,
        val primaryGroupValues: List<String>? = null,
        val secondaryGroupValues: List<String>? = null,
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

    fun parsePlotType(str: String?) =
        if (str == null)
            null
        else
            PlotType.values().find { it.cliName.lowercase() == str.lowercase() }
                ?: throw IllegalArgumentException("unknown plot type: $str")

    private fun isCategorical(t: PlotType) = when (t) {
        Scatter -> false
        else -> true
    }

    private fun guessPlotType(par: PlotParameters, meta: Metadata?) =
        if (par.primaryGroup == null || meta == null || meta.isCategorical(par.primaryGroup))
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
    ): Plot {
        val y = BasicStatRow::value.name
        val type = par.plotType ?: guessPlotType(par, df)
        if (par.primaryGroup == null)
            return type.plot(df, y)

        val dfRefined =
            if (isCategorical(type)) {
                if (df.isNumeric(par.primaryGroup) || (par.secondaryGroup != null && df.isNumeric(par.secondaryGroup))) {
                    toCategorical(df, *listOfNotNull(par.primaryGroup, par.secondaryGroup).toTypedArray())
                } else
                    df
            } else {
                df
            }

        if (isCategorical(type)) {
            val plt = type.plot(
                dfRefined,
                BasicStatRow::value.name,
                par.primaryGroup,
                par.secondaryGroup,
                par.primaryGroupValues,
                par.secondaryGroupValues,
                par.facetBy
            )
                    as GGXDiscrete

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

            if (par.showOverallPValue)
                plt += statCompareMeans(
                    method = par.method,
                    multipleGroupsMethod = par.multipleGroupsMethod,
                    labelFormat = par.overallPValueFormat,
                )

            return plt.plot
        } else {
            val plt = type.plot(
                dfRefined,
                par.primaryGroup,
                BasicStatRow::value.name,
                par.secondaryGroup,
                par.primaryGroupValues,
                par.secondaryGroupValues,
                par.facetBy
            )
                    as GGScatter


            plt += statCor(method = par.correlationMethod)

            return plt.plot
        }
    }
}
