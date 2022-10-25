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
import com.milaboratory.util.FormatUtils
import com.milaboratory.util.ReportHelper
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics
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

    interface Stats {
        val size: Long
        val sum: Double
        val min: Double
        val max: Double
        val avg: Double
        val quadraticMean: Double
        val stdDeviation: Double
    }

    @JsonAutoDetect(
        fieldVisibility = ANY,
        isGetterVisibility = NONE,
        getterVisibility = NONE
    )
    data class StandardStats(
        override val size: Long,
        override val sum: Double,
        override val min: Double,
        override val max: Double,
        override val avg: Double,
        override val quadraticMean: Double,
        override val stdDeviation: Double
    ) : Stats {
        companion object {
            fun from(statistics: SummaryStatistics): StandardStats = StandardStats(
                size = statistics.n,
                sum = statistics.sum,
                min = statistics.min,
                max = statistics.max,
                avg = statistics.mean,
                quadraticMean = statistics.quadraticMean,
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
        override val size: Long,
        override val sum: Double,
        override val min: Double,
        override val max: Double,
        override val avg: Double,
        override val quadraticMean: Double,
        override val stdDeviation: Double,
        val percentile25: Double,
        val percentile50: Double,
        val percentile75: Double
    ) : Stats {
        companion object {
            fun from(data: Collection<Double>): StatsWithQuantiles {
                val statistics = DescriptiveStatistics()
                data.forEach { statistics.addValue(it) }
                return StatsWithQuantiles(
                    size = statistics.n,
                    sum = statistics.sum,
                    min = statistics.min,
                    max = statistics.max,
                    avg = statistics.mean,
                    quadraticMean = statistics.quadraticMean,
                    stdDeviation = statistics.standardDeviation,
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
        if (stats is StatsWithQuantiles) {
            writeField("\tpercentile 25", stats.percentile25)
            writeField("\tpercentile 50", stats.percentile50)
            writeField("\tpercentile 75", stats.percentile75)
        }
    }
}
