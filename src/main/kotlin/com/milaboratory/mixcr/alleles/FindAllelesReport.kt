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
package com.milaboratory.mixcr.alleles

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE
import com.milaboratory.mixcr.cli.AbstractCommandReportBuilder
import com.milaboratory.mixcr.cli.AbstractMiXCRCommandReport
import com.milaboratory.mixcr.cli.CommandFindAlleles
import com.milaboratory.mixcr.cli.MiXCRCommandReport
import com.milaboratory.util.ReportHelper
import org.apache.commons.math3.stat.descriptive.SummaryStatistics
import org.apache.commons.math3.stat.descriptive.SynchronizedSummaryStatistics
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.LongAdder

@JsonAutoDetect(
    fieldVisibility = ANY,
    isGetterVisibility = NONE,
    getterVisibility = NONE
)
class FindAllelesReport(
    date: Date?,
    commandLine: String,
    inputFiles: Array<String>,
    outputFiles: Array<String>,
    executionTimeMillis: Long?,
    version: String,
    private val clonesCountWithNoChangeOfScore: Long,
    private val clonesCountWithNegativeScoreChange: Long,
    private val clonesScoreDeltaStats: MiXCRCommandReport.Stats,
    private val foundAlleles: Int,
    private val zygotes: Map<Int, Int>
) : AbstractMiXCRCommandReport(date, commandLine, inputFiles, outputFiles, executionTimeMillis, version) {
    override fun command(): String = CommandFindAlleles.COMMAND_NAME

    override fun writeReport(helper: ReportHelper) {
        // Writing common analysis information
        writeSuperReport(helper)

        helper.write("Clones score delta stats", clonesScoreDeltaStats)
        helper.writeField("Clones count with no change of score", clonesCountWithNoChangeOfScore)
        helper.writeField("Clones count with negative score change", clonesCountWithNegativeScoreChange)
        helper.writeField("Found alleles", foundAlleles)
        helper.writeField("Zygotes", zygotes)
    }

    class Builder : AbstractCommandReportBuilder<Builder>() {
        private val foundAlleles = AtomicInteger(0)
        private val zygotes: MutableMap<Int, LongAdder> = ConcurrentHashMap()
        private val clonesCountWithNoChangeOfScore: LongAdder = LongAdder()
        private val clonesCountWithNegativeScoreChange: LongAdder = LongAdder()
        private val clonesScoreDeltaStats: SummaryStatistics = SynchronizedSummaryStatistics()

        fun scoreDelta(delta: Float) {
            if (delta == 0.0F) {
                clonesCountWithNoChangeOfScore.increment()
            } else {
                if (delta < 0.0F) {
                    clonesCountWithNegativeScoreChange.increment()
                }
                clonesScoreDeltaStats.addValue(delta.toDouble())
            }
        }

        fun foundAlleles(count: Int) {
            foundAlleles.addAndGet(count)
        }

        fun zygote(count: Int) {
            zygotes.computeIfAbsent(count) { LongAdder() }.increment()
        }

        override fun buildReport(): MiXCRCommandReport = FindAllelesReport(
            date,
            commandLine,
            inputFiles,
            outputFiles,
            executionTimeMillis,
            version,
            clonesCountWithNoChangeOfScore.sum(),
            clonesCountWithNegativeScoreChange.sum(),
            MiXCRCommandReport.Stats.from(clonesScoreDeltaStats),
            foundAlleles.get(),
            zygotes.mapValues { it.value.toInt() }
        )

        override fun that() = this
    }
}