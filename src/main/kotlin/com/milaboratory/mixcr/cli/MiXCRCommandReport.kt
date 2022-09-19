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
package com.milaboratory.mixcr.cli

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE
import com.fasterxml.jackson.annotation.JsonUnwrapped
import com.milaboratory.util.FormatUtils
import com.milaboratory.util.ReportHelper
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics
import org.apache.commons.math3.stat.descriptive.StatisticalSummary
import org.apache.commons.math3.stat.descriptive.SummaryStatistics
import java.io.ByteArrayOutputStream
import java.util.*

interface MiXCRCommandReport : MiXCRReport {
    val date: Date?
    val commandLine: String
    val inputFiles: Array<String>
    val outputFiles: Array<String>
    val executionTimeMillis: Long?
    val version: String

    fun command(): String

    fun writeHeader(helper: ReportHelper) {
        val command = command().substring(0, 1).uppercase(Locale.getDefault()) + command().substring(1)
        helper.writeLine("============== $command Report ==============")
    }

    fun writeSuperReport(helper: ReportHelper) {
        if (helper.isStdout) {
            writeHeader(helper)
        } else {
            helper.writeNotNullField("Analysis date", date)
                .writeField("Input file(s)", inputFiles.joinToString(","))
                .writeField("Output file(s)", outputFiles.joinToString(","))
                .writeField("Version", version)
                .writeField("Command line arguments", commandLine)
        }
        executionTimeMillis?.let { executionTimeMillis ->
            helper.writeNotNullField("Analysis time", FormatUtils.nanoTimeToString(executionTimeMillis * 1000000))
        }
    }

    fun asString(): String {
        val os = ByteArrayOutputStream()
        writeReport(ReportHelper(os, true))
        return os.toString()
    }

    @JsonAutoDetect(
        fieldVisibility = ANY,
        isGetterVisibility = NONE,
        getterVisibility = NONE
    )
    data class Stats(
        val size: Long,
        val sum: Double,
        val min: Double,
        val max: Double,
        val avg: Double,
        val quadraticMean: Double,
        val stdDeviation: Double
    ) {
        companion object {
            fun from(statistics: SummaryStatistics): Stats =
                from(statistics, statistics.quadraticMean)

            fun from(statistics: DescriptiveStatistics): Stats =
                from(statistics, statistics.quadraticMean)

            fun from(statistics: StatisticalSummary, quadraticMean: Double): Stats = Stats(
                size = statistics.n,
                sum = statistics.sum,
                min = statistics.min,
                max = statistics.max,
                avg = statistics.sum / statistics.n,
                quadraticMean = quadraticMean,
                stdDeviation = statistics.standardDeviation
            )
        }
    }

    @JsonAutoDetect(
        fieldVisibility = ANY,
        isGetterVisibility = NONE,
        getterVisibility = NONE
    )
    data class StatsWithQuantiles(
        @field:JsonUnwrapped
        val stats: Stats,
        val percentile25: Double,
        val percentile50: Double,
        val percentile75: Double
    ) {
        companion object {
            fun from(data: Collection<Double>): StatsWithQuantiles {
                val statistics = DescriptiveStatistics()
                statistics.quadraticMean
                data.forEach { statistics.addValue(it) }
                return StatsWithQuantiles(
                    stats = Stats.from(statistics),
                    percentile25 = statistics.getPercentile(25.0),
                    percentile50 = statistics.getPercentile(50.0),
                    percentile75 = statistics.getPercentile(75.0)
                )
            }
        }
    }

    fun ReportHelper.write(title: String, stats: Stats) {
        writeLine("$title:")
        writeField("\tsize", stats.size)
        writeField("\tsum", stats.sum)
        writeField("\tmin", stats.min)
        writeField("\tmax", stats.max)
        writeField("\tavg", stats.avg)
        writeField("\tquadratic mean", stats.quadraticMean)
        writeField("\tstd deviation", stats.stdDeviation)
    }

    fun ReportHelper.write(title: String, statsWithQuantiles: StatsWithQuantiles) {
        write(title, statsWithQuantiles.stats)
        writeField("\tpercentile 25", statsWithQuantiles.percentile25)
        writeField("\tpercentile 50", statsWithQuantiles.percentile50)
        writeField("\tpercentile 75", statsWithQuantiles.percentile75)
    }

}
