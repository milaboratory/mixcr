package com.milaboratory.mixcr.postanalysis.plots

import com.milaboratory.mixcr.postanalysis.PostanalysisResult
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
        for ((_, charData) in paResult.data) {
            for ((_, keys) in charData.data) {
                for (metric in keys.data) {
                    @Suppress("UNCHECKED_CAST")
                    val key = metric.key as? OverlapKey<OverlapType> ?: throw RuntimeException()
                    if (mf != null && !mf.contains(key.key.toString().lowercase())) {
                        continue
                    }
                    data += OverlapRow(key.id1, key.id2, key.key, metric.value)
                    data += OverlapRow(key.id2, key.id1, key.key, metric.value)
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

    fun plots(
        df: DataFrame<OverlapRow>,
        pp: HeatmapParameters,
    ) = df.groupBy { "metric"<String>() }.groups.toList()
        .map { sdf -> plot(df, pp) + ggtitle(sdf.first()[OverlapRow::metric.name]!!.toString()) }

    fun plot(
        df: DataFrame<OverlapRow>,
        params: HeatmapParameters,
    ) = mkHeatmap(
        df,
        x = OverlapRow::sample1.name,
        y = OverlapRow::sample2.name,
        z = GeneUsageRow::weight.name,
        params = params
    )
}
