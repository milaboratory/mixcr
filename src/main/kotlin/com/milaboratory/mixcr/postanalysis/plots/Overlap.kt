package com.milaboratory.mixcr.postanalysis.plots

import com.milaboratory.miplots.Position
import com.milaboratory.miplots.color.Palletes
import com.milaboratory.miplots.heatmap.*
import com.milaboratory.mixcr.postanalysis.PostanalysisResult
import com.milaboratory.mixcr.postanalysis.overlap.OverlapKey
import com.milaboratory.mixcr.postanalysis.overlap.OverlapType
import jetbrains.letsPlot.ggsize
import jetbrains.letsPlot.label.ggtitle
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.annotations.DataSchema
import org.jetbrains.kotlinx.dataframe.api.cast
import org.jetbrains.kotlinx.dataframe.api.first
import org.jetbrains.kotlinx.dataframe.api.groupBy
import org.jetbrains.kotlinx.dataframe.api.toDataFrame
import org.jetbrains.kotlinx.dataframe.io.read

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
     fun dataFrame(paResult: PostanalysisResult) = run {
        val data = mutableListOf<OverlapRow>()

        for ((_, charData) in paResult.data) {
            for ((_, keys) in charData.data) {
                for (metric in keys.data) {
                    val key = metric.key as? OverlapKey<OverlapType> ?: throw RuntimeException()
                    data += OverlapRow(key.id1, key.id2, key.key, metric.value)
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
        metadataPath: String?,
    ) = run {
        var df: DataFrame<OverlapRow> = dataFrame(paResult)
        if (metadataPath != null)
            df = attachMetadata(df, "sample1", DataFrame.read(metadataPath), "sample").cast()
        df
    }

    data class OverlapParameters(
        val colorKey: List<String>? = null,
        val cluster: Boolean = true,
        override val width: Int,
        override val height: Int
    ) : PlotParameters

    fun plots(
        df: DataFrame<OverlapRow>,
        pp: OverlapParameters,
    ) = df.groupBy { "metric"<String>() }.groups.toList()
        .map { sdf -> plot(df, pp) + ggtitle(sdf.first()[OverlapRow::metric.name]!!.toString()) }

    fun plot(
        df: DataFrame<OverlapRow>,
        pp: OverlapParameters,
    ) = run {
        var plt = Heatmap(
            df,
            x = OverlapRow::sample1.name,
            y = OverlapRow::sample2.name,
            z = GeneUsageRow::weight.name,
            xOrder = if (pp.cluster) Hierarchical() else null,
            yOrder = if (pp.cluster) Hierarchical() else null,
            fillPallette = Palletes.Diverging.lime90rose130,
            fillNoValue = true,
            noValue = 0.0
        )

        plt = plt.withBorder()

        val ncats = pp.colorKey?.map { df[it].distinct().size() }?.sum() ?: 0
        var pallete = Palletes.Categorical.auto(ncats)
        pp.colorKey?.let {
            var first = true
            for (key in it) {
                val nColors = df[key].distinct().size()
                plt.withColorKey(
                    key,
                    pos = Position.Bottom,
                    sep = if (first) 0.1 else 0.0,
                    labelPos = Position.Left,
                    labelSep = 0.1,
                    pallete = pallete
                )
                pallete = pallete.shift(nColors)
                first = false
            }
        }

        if (pp.cluster) {
            plt = plt.withDendrogram(pos = Position.Top, 0.1)
            plt = plt.withDendrogram(pos = Position.Right, 0.1)
        }

        plt = plt.withLabels(Position.Bottom, angle = 45, sep = 0.1)
        plt = plt.withLabels(Position.Left, sep = 0.1, width = 4.0)
        plt = plt.withFillLegend(Position.Left, size = 0.5)
        plt = plt.withColorKeyLegend(Position.Left)
        if (pp.width > 0 && pp.height > 0)
            plt.plusAssign(ggsize(pp.width, pp.height))
        plt.plot
    }
}
