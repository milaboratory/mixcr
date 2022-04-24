package com.milaboratory.mixcr.postanalysis.plots

import com.milaboratory.miplots.toPDF
import com.milaboratory.mixcr.postanalysis.PostanalysisResult
import com.milaboratory.mixcr.postanalysis.SetPreprocessorStat
import com.milaboratory.mixcr.postanalysis.overlap.OverlapKey
import com.milaboratory.mixcr.postanalysis.overlap.OverlapType
import jetbrains.letsPlot.label.ggtitle
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.annotations.DataSchema
import org.jetbrains.kotlinx.dataframe.api.*

/**
 * DataFrame row for V or J usage data
 */
@DataSchema
data class OverlapRow(
    val preproc: String,
    val preprocStat1: SetPreprocessorStat,
    val preprocStat2: SetPreprocessorStat,
    val sample1: String,
    val sample2: String,
    val metric: OverlapType,
    val weight: Double,
)

object Overlap {
    /**
     * Imports data into DataFrame
     **/
    fun dataFrame(
        paResult: PostanalysisResult,
        metricsFilter: List<String>?
    ) = run {
        val data = mutableListOf<OverlapRow>()
        val mf = metricsFilter?.map { it.lowercase() }?.toSet()
        for ((ch, charData) in paResult.data) {
            val preproc = charData.preproc
            for ((_, keys) in charData.data) {
                for (metric in keys.data) {
                    @Suppress("UNCHECKED_CAST")
                    val key = metric.key as? OverlapKey<OverlapType> ?: throw RuntimeException()
                    if (mf != null && !mf.contains(key.key.toString().lowercase())) {
                        continue
                    }
                    val sample1 = key.id1
                    val sample2 = key.id2
                    val preprocStat1 = paResult.getPreprocStat(ch, sample1)
                    val preprocStat2 = paResult.getPreprocStat(ch, sample2)
                    data += OverlapRow(preproc, preprocStat1, preprocStat2, sample1, sample2, key.key, metric.value)
                    data += OverlapRow(preproc, preprocStat2, preprocStat1, sample2, sample1, key.key, metric.value)
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
        var df: DataFrame<OverlapRow> = dataFrame(paResult, metricsFilter)
        if (metadata != null)
            df = df.withMetadata(metadata)
        df
    }

    private fun DataFrame<OverlapRow>.withMetadata(meta: Metadata) = run {
        var df = this
        val metaX = meta.rename { all() }.into { "x_" + it.name() }
        val metaY = meta.rename { all() }.into { "y_" + it.name() }
        df = attachMetadata(df, "sample1", metaX, "x_sample").cast()
        df = attachMetadata(df, "sample2", metaY, "y_sample").cast()
        df
    }

    fun filterOverlap(filter: Filter, df: DataFrame<OverlapRow>) = run {
        var ndf = df
        ndf = filter.rename { "x_$it" }.apply(ndf)
        ndf = filter.rename { "y_$it" }.apply(ndf)
        ndf
    }

    private fun plot(
        df: DataFrame<OverlapRow>,
        params: HeatmapParameters,
    ) = mkHeatmap(
        df,
        x = OverlapRow::sample1.name,
        y = OverlapRow::sample2.name,
        z = GeneUsageRow::weight.name,
        params = params
    )

    fun plots(
        df: DataFrame<OverlapRow>,
        par: HeatmapParameters,
    ) = df.groupBy { metric }.groups.toList()
        .map { sdf -> plot(df, par) + ggtitle(sdf.first()[OverlapRow::metric.name]!!.toString()) }

//    fun tables(
//        df: DataFrame<OverlapRow>,
//        pp: DataFrame<PreprocSummaryRow>
//    ) = df
//        .groupBy { preproc }
//        .groups.toList().map { byPreproc ->
//            val metrics = byPreproc.metric.distinct().toList()
//            val preprocId = byPreproc.first()[OverlapRow::preproc.name] as String
//            pp
//                .filter { preproc == preprocId }
//                .pdfTable(header = metrics.joinToString(",") { it.toString() })
//        }

    fun plotsAndSummary(
        df: DataFrame<OverlapRow>,
        par: HeatmapParameters,
    ) = df
        .groupBy { preproc }
        .groups.toList().flatMap { byPreproc ->
            val metrics = byPreproc.metric.distinct().toList()

            val droppedSamples =
                byPreproc.filter { preprocStat1.dropped || preprocStat1.nElementsAfter == 0L }
                    .sample1.distinct().toSet()

            val summary = Preprocessing.pdfSummary(
                byPreproc.rows().associate { it.sample1 to it.preprocStat1 },
                metrics.joinToString(", ")
            )

            val plots = byPreproc
                .filter { !droppedSamples.contains(it.sample1) && !droppedSamples.contains(it.sample2) }
                .groupBy { metric }.groups.toList()
                .map { byMetric ->
                    plot(byMetric, par) + ggtitle(byMetric.first()[OverlapRow::metric.name]!!.toString())
                }.map { it.toPDF() }

            if (summary == null)
                plots
            else
                listOf(summary) + plots
        }
}
