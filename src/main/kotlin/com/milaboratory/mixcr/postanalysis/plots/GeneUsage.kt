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

import com.milaboratory.miplots.StandardPlots
import com.milaboratory.miplots.toPDF
import com.milaboratory.mixcr.postanalysis.PostanalysisResult
import com.milaboratory.mixcr.postanalysis.SetPreprocessorStat
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.annotations.DataSchema
import org.jetbrains.kotlinx.dataframe.api.rows
import org.jetbrains.kotlinx.dataframe.api.toDataFrame


/**
 * DataFrame row for V or J usage data
 */
@DataSchema
data class GeneUsageRow(
    /** Sample ID */
    val sample: String,
    /** Preprocessor statistics for the sample */
    val preprocStat: SetPreprocessorStat,
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

        for ((ch, charData) in paResult.data) {
            for ((sampleId, keys) in charData.data) {
                for (metric in keys.data) {
                    val key = metric.key.toString()
                    data += GeneUsageRow(
                        sampleId,
                        paResult.getPreprocStat(ch, sampleId),
                        key,
                        metric.value
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
        metadata: Metadata?,
    ) = run {
        var df = dataFrame(paResult)
        if (metadata != null)
            df = df.withMetadata(metadata)
        df
    }

    fun plotAndSummary(
        df: DataFrame<GeneUsageRow>,
        params: HeatmapParameters,
    ) = run {
        val summary = Preprocessing.pdfSummary(
            df.rows().associate { it.sample to it.preprocStat },
            ""
        )

        val plt = mkHeatmap(
            df,
            x = GeneUsageRow::sample.name,
            y = GeneUsageRow::gene.name,
            z = GeneUsageRow::weight.name,
            params = params
        ).toPDF()

        if (summary == null)
            listOf(plt)
        else
            listOf(summary, plt)
    }

    fun plot(
        df: DataFrame<GeneUsageRow>,
        params: HeatmapParameters,
    ) = mkHeatmap(
        df,
        x = GeneUsageRow::sample.name,
        y = GeneUsageRow::gene.name,
        z = GeneUsageRow::weight.name,
        params = params
    )

    fun plotBarPlot(
        df: DataFrame<GeneUsageRow>,
        facetBy: String?,
    ) = StandardPlots.PlotType.BarPlot.plot(
        df,
        y = GeneUsageRow::weight.name,
        primaryGroup = GeneUsageRow::sample.name,
        facetBy = facetBy
    ).plot
}
