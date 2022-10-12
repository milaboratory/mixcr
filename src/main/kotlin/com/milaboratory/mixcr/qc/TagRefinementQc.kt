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
package com.milaboratory.mixcr.qc

import com.milaboratory.mitool.exhaustive
import com.milaboratory.mitool.refinement.gfilter.*
import com.milaboratory.mixcr.MiXCRCommand
import com.milaboratory.mixcr.basictypes.MiXCRFileInfo
import jetbrains.letsPlot.elementLine
import jetbrains.letsPlot.geom.geomPolygon
import jetbrains.letsPlot.geom.geomVLine
import jetbrains.letsPlot.ggplot
import jetbrains.letsPlot.intern.Plot
import jetbrains.letsPlot.label.ggtitle
import jetbrains.letsPlot.label.xlab
import jetbrains.letsPlot.label.ylab
import jetbrains.letsPlot.scale.scaleXContinuous
import jetbrains.letsPlot.scale.scaleXLog10
import jetbrains.letsPlot.scale.scaleYContinuous
import jetbrains.letsPlot.theme

object TagRefinementQc {
    /** Generates tag refinement QC plots */
    fun tagRefinementQc(info: MiXCRFileInfo, log: Boolean = false) = run {
        val report = info.footer.reports[MiXCRCommand.refineTagsAndSort].firstOrNull()
        val filter = info.header.stepParams[MiXCRCommand.refineTagsAndSort].firstOrNull()?.parameters?.postFilter
        val filterReport = report?.correctionReport?.filterReport
        if (report == null || filter == null || filterReport == null)
            return@run emptyList()
        tagRefinementQc(filter, filterReport, log).map {
            assert(report.outputFiles.size == 1)
            it + ggtitle(report.outputFiles[0])
        }
    }

    /** Generates tag refinement QC plots */
    fun tagRefinementQc(
        filter: KeyedRecordFilter,
        rep: KeyedFilterReport,
        log: Boolean = false,
    ): List<Plot> = run {
        when (filter) {
            is AndKeyedFilter -> {
                rep as AndTaggedFilterReport
                filter.filters.flatMapIndexed { idx, f ->
                    tagRefinementQc(f, rep.nestedReports[idx], log)
                }
            }

            is GroupFilter -> {
                rep as GroupFilterReport
                groupFilterQc(filter, rep, log)
            }

            else -> throw UnsupportedOperationException("${filter.javaClass} QC is not supported yet")
        }.exhaustive
    }

    private fun groupFilterQc(filter: GroupFilter, rep: GroupFilterReport, log: Boolean) =
        rep.operatorReports.indices.mapNotNull {
            rep.groupFilterQc(
                filter.predicates[it],
                rep.operatorReports[it],
                log
            )
        }

    private fun GroupFilterReport.groupFilterQc(
        predicate: GroupPredicate,
        rep: OperatorReport,
        log: Boolean
    ): Plot? = run {
        if (predicate.metrics.size > 1)
            return@run null

        val hist = rep.hist[0] ?: return@run null
        val metric = predicate.metrics[0]
        val xLabel = metric.toString()
        val yLabel = "Number of ${this.groupingKeys.joinToString(",")} groups"
        val thresholds = mutableListOf<Any>()

        (rep.operatorReport as? GenericHistOpReport)?.threshold?.let { thresholds += it }
        (predicate.operator as? RangeOp)?.let { op ->
            listOfNotNull(op.lower, op.upper).forEach { thresholds += it }
        }

        val data = mapOf(
            "x" to mutableListOf(),
            "y" to mutableListOf(),
            "g" to mutableListOf<Any>()
        )

        val zero = if (log) 1e-5 else 0
        for ((i, bin) in hist.bins.withIndex()) {
            data["x"]!! += bin.from
            data["y"]!! += zero
            data["g"]!! += i

            data["x"]!! += bin.from
            data["y"]!! += bin.weight
            data["g"]!! += i

            data["x"]!! += bin.to
            data["y"]!! += bin.weight
            data["g"]!! += i

            data["x"]!! += bin.to
            data["y"]!! += zero
            data["g"]!! += i
        }

        var plt = ggplot(data)

        plt += geomPolygon(fill = "#929bad") {
            x = "x"
            y = "y"
            group = "g"
        }

        if (thresholds.isNotEmpty()) {
            plt += geomVLine(
                data = mapOf("x" to thresholds),
                color = "#f05670",
                linetype = 5
            ) {
                xintercept = "x"
            }
        }

        plt += xlab(xLabel)
        plt += ylab(yLabel)

        plt += scaleXContinuous(expand = listOf(0, 0))
        plt += scaleYContinuous(expand = listOf(0, 0))

        if (hist.collectionSpec?.log == true)
            plt += scaleXLog10()

        plt += theme(axisLineY = elementLine(), panelBorder = elementLine())

        plt
    }
}