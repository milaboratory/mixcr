package com.milaboratory.mixcr.postanalysis.plots

import com.milaboratory.mixcr.postanalysis.PostanalysisResult
import com.milaboratory.mixcr.postanalysis.spectratype.SpectratypeKey
import com.milaboratory.mixcr.postanalysis.ui.PostanalysisSchema
import jetbrains.letsPlot.*
import jetbrains.letsPlot.geom.geomBar
import jetbrains.letsPlot.label.ggtitle
import jetbrains.letsPlot.label.labs
import jetbrains.letsPlot.scale.scaleXDiscrete
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.annotations.DataSchema
import org.jetbrains.kotlinx.dataframe.api.*

/**
 * DataFrame row for single (V/J/CDR3) spectratype
 */
@DataSchema
@Suppress("UNCHECKED_CAST")
data class SpectratypeRow(
    /** Sample ID */
    val sample: String,

    /** Gene feature length */
    val length: Int,

    /** Payload (gene name / gene feature aa) */
    val payload: String,

    /** Payload weight */
    val weight: Double,
)

object SingleSpectratype {
    private const val OtherPayload = "Other"

    /**
     * Imports spectratype data into DataFrame
     **/
    fun dataFrame(
        paSett: PostanalysisSchema<*>,
        paResult: PostanalysisResult,
        group: String
    ): DataFrame<SpectratypeRow> {
        val data = mutableListOf<SpectratypeRow>()

        for ((_, charData) in paResult.forGroup(paSett.getGroup<SpectratypeKey<String>>(group)).data) {
            for ((sampleId, keys) in charData.data) {
                for (metric in keys.data) {
                    @Suppress("UNCHECKED_CAST")
                    val key = metric.key as SpectratypeKey<String>
                    data += SpectratypeRow(sampleId, key.length, (key.payload ?: OtherPayload).toString(), metric.value)
                }
            }
        }

        return data.toDataFrame()
    }

    /**
     * Spectratype plot settings
     * @param nTop number of top payloads to show on plot
     * @param limits specify length range (null for all)
     * @param normalize normalize by total weight
     * */
    data class SpectratypePlotSettings(
        val nTop: Int,
        val limits: IntRange?,
        val normalize: Boolean,
        val width: Int,
        val height: Int,
    ) {
        companion object {
            val Default = SpectratypePlotSettings(
                nTop = 20,
                limits = null,
                normalize = true,
                width = 1024,
                height = 512
            )
        }
    }

    /**
     * Creates Plot for sample
     *
     * @param smpl sample
     * @param settings plot settings
     * */
    fun DataFrame<SpectratypeRow>.plot(
        smpl: String,
        settings: SpectratypePlotSettings = SpectratypePlotSettings.Default,
    ) = run {

        var df = this
        // for sample
        df = df.filter { sample == smpl }
        // sort by weight
        df = df.sortByDesc { weight }

        // select top
        val top = df.payload
            .distinct().toList()
            .take(settings.nTop)
            .toSet()

        // replace non top payloads with "Other"
        df = df.update { payload }
            .where { !top.contains(it) }
            .with { OtherPayload }

        // find min max
        val min = df.length.min()
        val max = df.length.max()

        // add auxiliary points for each cdr3 length
        df = df.concat(
            (min..max)
                .map { SpectratypeRow(smpl, it, OtherPayload, 0.0) }
                .toDataFrame()
        )

        // collapse all "Other" into a single row
        df = df
            .groupBy { length and payload }
            .aggregate {
                sumOf { it.weight } into SpectratypeRow::weight.name
            }

        // sort so that "Other" will be at the bottom when it is big
        df = df.sortByDesc { weight }

        if (settings.normalize)
            df.add(SpectratypeRow::weight.name) { df.weight / df.weight.sum() }

        // plot
        var plt = letsPlot(df.toMap()) + geomBar(
            position = Pos.stack,
            stat = Stat.identity,
            width = 0.6
        ) {
            x = asDiscrete(SpectratypeRow::length.name, order = 1)
            y = SpectratypeRow::weight.name
            fill = asDiscrete(
                SpectratypeRow::payload.name,
                orderBy = SpectratypeRow::weight.name, order = -1
            )
        }
        plt += ggtitle(smpl)
        plt += labs(y = "", x = "CDR3 length, bp")

        // adjust breaks
        val breaks = df.length.toList()
            .sorted()
            .distinct()
        plt += scaleXDiscrete(
            breaks = breaks,
            labels = breaks.map { if (it % 3 == 0) it.toString() else "" },
            limits = settings.limits?.toList()
        )

        // fix size
        plt += ggsize(settings.width, settings.height)

        plt
    }
}
