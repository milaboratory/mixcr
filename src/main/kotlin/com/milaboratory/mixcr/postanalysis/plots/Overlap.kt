package com.milaboratory.mixcr.postanalysis.plots

import com.milaboratory.mixcr.postanalysis.PostanalysisResult
import com.milaboratory.mixcr.postanalysis.overlap.OverlapKey
import com.milaboratory.mixcr.postanalysis.overlap.OverlapType
import com.milaboratory.mixcr.postanalysis.plots.Preprocessing.pdfTable
import com.milaboratory.mixcr.postanalysis.ui.CharacteristicGroup
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
        ch: CharacteristicGroup<*, *>,
        paResult: PostanalysisResult,
        metricsFilter: List<String>?
    ) = run {
        val data = mutableListOf<OverlapRow>()

        val ch2preproc = ch.characteristics.associate { it.name to it.preprocessor.id() }
        val projectedResult = paResult.forGroup(ch)
        val mf = metricsFilter?.map { it.lowercase() }?.toSet()
        for ((char, charData) in projectedResult.data) {
            val preproc = ch2preproc[char]!!
            for ((_, keys) in charData.data) {
                for (metric in keys.data) {
                    @Suppress("UNCHECKED_CAST")
                    val key = metric.key as? OverlapKey<OverlapType> ?: throw RuntimeException()
                    if (mf != null && !mf.contains(key.key.toString().lowercase())) {
                        continue
                    }
                    data += OverlapRow(preproc, key.id1, key.id2, key.key, metric.value)
                    data += OverlapRow(preproc, key.id2, key.id1, key.key, metric.value)
                }
            }
        }

        data.toDataFrame()
    }

    /**
     * Imports data into DataFrame
     **/
    fun dataFrame(
        ch: CharacteristicGroup<*, *>,
        paResult: PostanalysisResult,
        metricsFilter: List<String>?,
        metadata: Metadata?,
    ) = run {
        var df: DataFrame<OverlapRow> = dataFrame(ch, paResult, metricsFilter)
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

    fun tables(
        df: DataFrame<OverlapRow>,
        pp: DataFrame<PreprocSummaryRow>
    ) = df
        .groupBy { preproc }
        .groups.toList().map { byChar ->
            val metrics = byChar.metric.distinct().toList()
            val preprocId = byChar.first()[OverlapRow::preproc.name] as String
            pp
                .filter { preproc == preprocId }
                .pdfTable(header = metrics.joinToString(",") { it.toString() })
        }
}
