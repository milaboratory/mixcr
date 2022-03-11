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
        metadata: Metadata?,
    ) = run {
        var df = dataFrame(paResult)
        if (metadata != null)
            df = df.withMetadata(metadata)
        df
    }

    data class PlotParameters(
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

        if (pp.clusterV)
            plt = plt.withDendrogram(pos = Position.Top, 0.1)
        if (pp.clusterJ)
            plt = plt.withDendrogram(pos = Position.Right, 0.1)

        plt = plt
            .withLabels(Position.Bottom, angle = 45, sep = 0.1)
            .withLabels(Position.Left, sep = 0.1, width = 4.0)
            .withFillLegend(Position.Left, size = 0.5)

        plt.plot
    }
}
