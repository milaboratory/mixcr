package com.milaboratory.mixcr.postanalysis.plots

import com.milaboratory.miplots.Position
import com.milaboratory.miplots.color.Palletes
import com.milaboratory.miplots.heatmap.*
import com.milaboratory.mixcr.postanalysis.PostanalysisResult
import com.milaboratory.mixcr.postanalysis.additive.KeyFunctions
import jetbrains.letsPlot.label.ggtitle
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.annotations.DataSchema
import org.jetbrains.kotlinx.dataframe.api.first
import org.jetbrains.kotlinx.dataframe.api.groupBy
import org.jetbrains.kotlinx.dataframe.api.toDataFrame
import org.jetbrains.kotlinx.dataframe.io.read

/**
 * DataFrame row for V or J usage data
 */
@DataSchema
data class VJUsageRow(
    /** Sample ID */
    val sample: String,

    val vGene: String,
    val jGene: String,
    /** Payload weight */
    val weight: Double,
)

object VJUsage {
    /**
     * Imports data into DataFrame
     **/
    @Suppress("UNCHECKED_CAST")
    fun dataFrame(paResult: PostanalysisResult) = run {
        val data = mutableListOf<VJUsageRow>()

        for ((_, charData) in paResult.data) {
            for ((sampleId, keys) in charData.data) {
                for (metric in keys.data) {
                    val key = metric.key as? KeyFunctions.VJGenes<String> ?: throw RuntimeException()
                    data += VJUsageRow(sampleId, key.vGene, key.jJene, metric.value)
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
        var df = dataFrame(paResult)
        if (metadataPath != null)
            df = df.withMetadata(DataFrame.read(metadataPath))
        df
    }

    data class PlotParameters(
        val colorKey: List<String>? = null,
        val clusterV: Boolean = true,
        val clusterJ: Boolean = true
    )

    fun plots(
        df: DataFrame<VJUsageRow>,
        pp: PlotParameters,
    ) = df.groupBy { "sample"<String>() }.groups.toList()
        .map { sdf -> plot(df, pp) + ggtitle(sdf.first()[VJUsageRow::sample.name]!!.toString()) }

    fun plot(
        df: DataFrame<VJUsageRow>,
        pp: PlotParameters,
    ) = run {
        var plt = Heatmap(
            df,
            x = VJUsageRow::jGene.name,
            y = VJUsageRow::vGene.name,
            z = GeneUsageRow::weight.name,
            xOrder = if (pp.clusterV) Hierarchical() else null,
            yOrder = if (pp.clusterJ) Hierarchical() else null,
            fillPallette = Palletes.Diverging.lime90rose130
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

        if (pp.clusterV)
            plt = plt.withDendrogram(pos = Position.Top, 0.1)
        if (pp.clusterJ)
            plt = plt.withDendrogram(pos = Position.Right, 0.1)

        plt = plt.withLabels(Position.Bottom, angle = 45, sep = 0.1)
        plt = plt.withLabels(Position.Left, sep = 0.1, width = 4.0)
        plt = plt.withFillLegend(Position.Left, size = 0.5)
        plt = plt.withColorKeyLegend(Position.Left)
        plt.plot
    }
}
