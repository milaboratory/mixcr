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

import com.milaboratory.mitool.refinement.gfilter.GroupFilterReport
import jetbrains.letsPlot.geom.geomPolygon
import jetbrains.letsPlot.ggplot
import jetbrains.letsPlot.label.xlab
import jetbrains.letsPlot.label.ylab
import jetbrains.letsPlot.scale.scaleXLog10
import jetbrains.letsPlot.scale.scaleYLog10

object TagRefinementQc {


    private fun GroupFilterReport.plot(idx: Int) = run {
        val op = this.operatorReports[idx]
        val hist = this.metricHists[idx]

        val data = mapOf(
            "x" to mutableListOf(),
            "y" to mutableListOf(),
            "g" to mutableListOf<Any>()
        )

        for ((i, bin) in hist.hist.bins.withIndex()) {
            data["x"]!! += bin.from
            data["y"]!! += 0.01
            data["g"]!! += i

            data["x"]!! += bin.from
            data["y"]!! += bin.weight
            data["g"]!! += i

            data["x"]!! += bin.to
            data["y"]!! += bin.weight
            data["g"]!! += i

            data["x"]!! += bin.to
            data["y"]!! += 0.01
            data["g"]!! += i
        }

        var plt = ggplot(data)

        plt += geomPolygon(fill = "black", alpha = 0.5) {
            x = "x"
            y = "y"
            group = "g"
        }

        plt += xlab(this.groupingKeys.joinToString { "+" })
        plt += ylab("# reads")

        if (hist.metric.reportHist?.log == true)
            plt += scaleXLog10()

        if (hist.hist.collectionSpec?.log == true)
            plt += scaleYLog10()
    }
}