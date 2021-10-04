package com.milaboratory.mixcr.postanalysis.dataframe

import com.milaboratory.mixcr.postanalysis.PostanalysisResult
import com.milaboratory.mixcr.postanalysis.ui.PostanalysisSchema
import jetbrains.letsPlot.Pos
import jetbrains.letsPlot.geom.geomBoxplot
import jetbrains.letsPlot.geom.geomPoint
import jetbrains.letsPlot.label.ggtitle
import jetbrains.letsPlot.label.labs
import jetbrains.letsPlot.letsPlot
import jetbrains.letsPlot.theme
import org.jetbrains.dataframe.*
import org.jetbrains.dataframe.annotations.DataSchema
import org.jetbrains.dataframe.columns.DataColumn

/**
 * DataFrame row for single statistical char group
 */
@DataSchema
@Suppress("UNCHECKED_CAST")
interface SimpleMetricsRow {
    /** Sample ID */
    val sample: String

    companion object {
        ////// DSL

        val sample by column<String>()

        val DataFrame<SpectratypeRow>.sample get() = this[SpectratypeRow::sample.name] as DataColumn<String>
        val DataRow<SpectratypeRow>.sample get() = this[SpectratypeRow::sample.name] as String
    }
}


object SimpleStatistics {
    /**
     * Imports data into DataFrame
     **/
    fun dataFrame(
        paSett: PostanalysisSchema<*>,
        paResult: PostanalysisResult,
        group: String
    ) = run {

        val data = mutableMapOf<String, MutableList<Any>>(
            SpectratypeRow::sample.name to mutableListOf(),
        )

        for ((_, charData) in paResult.forGroup(paSett.getGroup<Any>(group)).data) {
            for ((sampleId, keys) in charData.data) {
                for (metric in keys.data) {
                    val key = metric.key.toString()
                    data[SimpleMetricsRow::sample.name]!! += sampleId
                    data.computeIfAbsent(key) { mutableListOf() } += metric.value
                }
            }
        }

        data.toDataFrame().typed<SimpleMetricsRow>()
    }


    /**
     * Attaches metadata to statistics
     **/
    fun DataFrame<SimpleMetricsRow>.withMetadata(metadata: AnyFrame) = run {
        this.leftJoin(metadata) { SimpleMetricsRow.sample }
    }

    /**
     * Creates plot spec
     **/
    fun DataFrame<SimpleMetricsRow>.plot(
        metric: String,
        primaryGroup: String? = null,
        secondaryGroup: String? = null
    ) = run {
        var plt = letsPlot(toMap()) {
            x = primaryGroup
            y = metric
            group = secondaryGroup
        }

        plt += geomBoxplot()
        plt += geomPoint(
            position = Pos.jitterdodge,
            shape = 21,
            color = "black"
        )
        plt += ggtitle(metric)
        plt += labs(x = primaryGroup, y = metric)
        plt += theme().axisTitleXBlank()
        plt += theme().axisTextXBlank()
        plt += theme().axisTicksXBlank()

        plt
    }
}
