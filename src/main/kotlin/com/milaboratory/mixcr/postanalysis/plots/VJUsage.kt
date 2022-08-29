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
                    data += VJUsageRow(sampleId, key.vGene, key.jGene, metric.value)
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

    fun plots(
        df: DataFrame<VJUsageRow>,
        pp: HeatmapParameters,
    ) = df.groupBy { "sample"<String>() }.groups.toList()
        .map { sdf -> plot(sdf, pp) + ggtitle(sdf.first()[VJUsageRow::sample.name]!!.toString()) }

    fun plot(
        df: DataFrame<VJUsageRow>,
        params: HeatmapParameters,
    ) = mkHeatmap(
        df,
        x = VJUsageRow::jGene.name,
        y = VJUsageRow::vGene.name,
        z = GeneUsageRow::weight.name,
        params = params
    )
}
