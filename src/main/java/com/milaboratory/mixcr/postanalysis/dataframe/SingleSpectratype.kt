package com.milaboratory.mixcr.postanalysis.dataframe

import com.milaboratory.mixcr.postanalysis.PostanalysisResult
import com.milaboratory.mixcr.postanalysis.dataframe.SpectratypeRow.Companion.length
import com.milaboratory.mixcr.postanalysis.dataframe.SpectratypeRow.Companion.payload
import com.milaboratory.mixcr.postanalysis.dataframe.SpectratypeRow.Companion.sample
import com.milaboratory.mixcr.postanalysis.dataframe.SpectratypeRow.Companion.weight
import com.milaboratory.mixcr.postanalysis.spectratype.SpectratypeKey
import com.milaboratory.mixcr.postanalysis.ui.PostanalysisSchema
import jetbrains.datalore.plot.PlotSvgExport
import jetbrains.letsPlot.*
import jetbrains.letsPlot.geom.geomBar
import jetbrains.letsPlot.intern.toSpec
import jetbrains.letsPlot.label.ggtitle
import jetbrains.letsPlot.label.labs
import jetbrains.letsPlot.scale.scaleXDiscrete
import org.jetbrains.kotlinx.dataframe.DataColumn
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.DataRow
import org.jetbrains.kotlinx.dataframe.annotations.DataSchema
import org.jetbrains.kotlinx.dataframe.api.*
import java.nio.file.Path

/**
 * DataFrame row for single (V/J/CDR3) spectratype
 */
@DataSchema
@Suppress("UNCHECKED_CAST")
interface SpectratypeRow {
    /** Sample ID */
    val sample: String

    /** Gene feature length */
    val length: Int

    /** Payload (gene name / gene feature aa) */
    val payload: String

    /** Payload weight */
    val weight: Double

    companion object {
        ////// DSL

        val sample by column<String>()
        val length by column<Int>()
        val payload by column<String>()
        val weight by column<Double>()

        val DataFrame<SpectratypeRow>.sample get() = this[SpectratypeRow::sample.name] as DataColumn<String>
        val DataRow<SpectratypeRow>.sample get() = this[SpectratypeRow::sample.name] as String

        val DataFrame<SpectratypeRow>.length get() = this[SpectratypeRow::length.name] as DataColumn<Int>
        val DataRow<SpectratypeRow>.length get() = this[SpectratypeRow::length.name] as Int

        val DataFrame<SpectratypeRow>.payload get() = this[SpectratypeRow::payload.name] as DataColumn<String>
        val DataRow<SpectratypeRow>.payload get() = this[SpectratypeRow::payload.name] as String

        val DataFrame<SpectratypeRow>.weight get() = this[SpectratypeRow::weight.name] as DataColumn<Double>
        val DataRow<SpectratypeRow>.weight get() = this[SpectratypeRow::weight.name] as Double
    }
}

data class SpectratypeRowImp(
    override val sample: String,
    override val length: Int,
    override val weight: Double,
    override val payload: String
) : SpectratypeRow


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
        val data = mutableMapOf<String, MutableList<Any>>(
            SpectratypeRow::sample.name to mutableListOf(),
            SpectratypeRow::length.name to mutableListOf(),
            SpectratypeRow::weight.name to mutableListOf(),
            SpectratypeRow::payload.name to mutableListOf()
        )

        for ((_, charData) in paResult.forGroup(paSett.getGroup<SpectratypeKey<String>>(group)).data) {
            for ((sampleId, keys) in charData.data) {
                for (metric in keys.data) {
                    @Suppress("UNCHECKED_CAST")
                    val key = metric.key as SpectratypeKey<String>
                    data[SpectratypeRow::sample.name]!! += sampleId
                    data[SpectratypeRow::length.name]!! += key.length
                    data[SpectratypeRow::payload.name]!! += (key.payload ?: OtherPayload).toString()
                    data[SpectratypeRow::weight.name]!! += metric.value
                }
            }
        }

        return data.toDataFrame().cast()
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
     * @param sample sample
     * @param settings plot settings
     * */
    fun DataFrame<SpectratypeRow>.plot(
        sample: String,
        settings: SpectratypePlotSettings = SpectratypePlotSettings.Default,
    ) = run {

        var df = this
        // for sample
        df = df.filter { SpectratypeRow.sample.eq(sample) }
        // sort by weight
        df = df.sortByDesc(SpectratypeRow.weight)

        // select top
        val top = df.payload
            .distinct().toList()
            .take(settings.nTop)
            .toSet()

        // replace non top payloads with "Other"
        df = df.update { SpectratypeRow.payload }
            .where { !top.contains(it) }
            .with { OtherPayload }

        // find min max
        val min = df.length.min() ?: 0
        val max = df.length.max() ?: 0

        // add auxiliary points for each cdr3 length
        df = df.concat(
            (min..max)
                .map { SpectratypeRowImp(sample, it, 0.0, OtherPayload) }
                .toDataFrame()
        )

        // collapse all "Other" into a single row
        df = df
            .groupBy { SpectratypeRow.length and SpectratypeRow.payload }
            .aggregate {
                sumOf { it.weight } into SpectratypeRow::weight.name
            }

        // sort so that "Other" will be at the bottom when it is big
        df = df.sortByDesc(SpectratypeRow.weight)

        if (settings.normalize)
            df.add(SpectratypeRow::weight.name) { df.weight / df.weight.sum() }

        // plot
        var plt = letsPlot(df.toMap()) + geomBar(
            position = Pos.stack,
            stat = Stat.identity,
            width = 0.6
        ) {
            x = asDiscrete(SpectratypeRow.length.name(), order = 1)
            y = SpectratypeRow.weight.name()
            fill = asDiscrete(
                SpectratypeRow.payload.name(),
                orderBy = SpectratypeRow::weight.name, order = -1
            )
        }
        plt += ggtitle(sample)
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

    /**
     * Export all plots into single PDF file
     *
     * @param destination path to export PDF
     * @param settings plot settings
     * */
    fun DataFrame<SpectratypeRow>.plotPDF(
        destination: Path,
        settings: SpectratypePlotSettings = SpectratypePlotSettings.Default,
    ) = run {
        writePDF(destination,
            this.sample
                .distinct().map { sample ->
                    PlotSvgExport.buildSvgImageFromRawSpecs(
                        plot(sample, settings).toSpec()
                    )
                }.toList()
                .map { toPdf(it) }
        )
    }

    /**
     * Create and export all plots into single PDF file
     *
     * @param destination path to export PDF
     * @param paSett PA settings
     * @param paResult PA results
     * @param settings plot settings
     * */
    fun plotPDF(
        destination: Path,
        paSett: PostanalysisSchema<*>,
        paResult: PostanalysisResult,
        group: String,
        settings: SpectratypePlotSettings = SpectratypePlotSettings.Default
    ) {
        dataFrame(paSett, paResult, group).plotPDF(destination, settings)
    }
}
