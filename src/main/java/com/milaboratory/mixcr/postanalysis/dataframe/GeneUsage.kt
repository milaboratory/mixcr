package com.milaboratory.mixcr.postanalysis.dataframe

import com.milaboratory.miplots.Position
import com.milaboratory.miplots.heatmap.*
import com.milaboratory.mixcr.postanalysis.PostanalysisResult
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.annotations.DataSchema
import org.jetbrains.kotlinx.dataframe.api.toDataFrame
import org.jetbrains.kotlinx.dataframe.io.read


/**
 * DataFrame row for V or J usage data
 */
@DataSchema
@Suppress("UNCHECKED_CAST")
data class GeneUsageRow(
    /** Sample ID */
    val sample: String,

    /** Payload (Gene) */
    val gene: String,

    /** Payload weight */
    val weight: Double,
)

object GeneUsage {
    /**
     * Imports data into DataFrame
     **/
    fun dataFrame(paResult: PostanalysisResult) = run {
        val data = mutableListOf<GeneUsageRow>()

        for ((_, charData) in paResult.data) {
            for ((sampleId, keys) in charData.data) {
                for (metric in keys.data) {
                    val key = metric.key.toString()
                    data += GeneUsageRow(sampleId, key, metric.value)
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
        val clusterSamples: Boolean = true,
        val clusterGenes: Boolean = true
    )

    fun plot(
        df: DataFrame<GeneUsageRow>,
        pp: PlotParameters,
    ) = run {
        var plt = Heatmap(
            df,
            x = GeneUsageRow::sample.name,
            y = GeneUsageRow::gene.name,
            z = GeneUsageRow::weight.name,
            xOrder = if (pp.clusterSamples) Hierarchical() else null,
            yOrder = if (pp.clusterGenes) Hierarchical() else null
        )

        pp.colorKey?.let {
            var first = false
            for (key in it) {
                plt.withColorKey(key, pos = Position.Bottom, sep = if (first) 0.1 else 0.0)
                first = true
            }
        }

        if (pp.clusterSamples)
            plt = plt.withDendrogram(pos = Position.Top, 0.1)
        if (pp.clusterGenes)
            plt = plt.withDendrogram(pos = Position.Right, 0.1)

        plt = plt.withLabels(Position.Bottom, angle = 45, sep = 0.1)
        plt = plt.withLabels(Position.Left, sep = 0.1, width = 7.0)
        plt = plt.withFillLegend(Position.Left)
        plt = plt.withColorKeyLegend(Position.Left)
        plt.plot
    }
}
