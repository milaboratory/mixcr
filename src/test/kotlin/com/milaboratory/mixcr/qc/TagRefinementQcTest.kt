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

import com.milaboratory.miplots.writePDF
import com.milaboratory.mitool.exhaustive
import com.milaboratory.mitool.refinement.gfilter.*
import com.milaboratory.mixcr.basictypes.ClnsReader
import com.milaboratory.mixcr.cli.RefineTagsAndSortReport
import io.repseq.core.VDJCLibraryRegistry
import jetbrains.letsPlot.geom.geomPolygon
import jetbrains.letsPlot.ggplot
import jetbrains.letsPlot.scale.scaleXLog10
import jetbrains.letsPlot.scale.scaleYLog10
import org.junit.Test
import java.nio.file.Paths

class TagRefinementQcTest {
    @Test
    internal fun name() {
        ClnsReader("/Users/poslavskysv/Downloads/FebControl10.clns", VDJCLibraryRegistry.getDefault()).use { reader ->
            val report = reader.footer.reports.find { it is RefineTagsAndSortReport } as RefineTagsAndSortReport
            val filter = report.correctionReport.filterReport

            when (filter) {
                is GroupFilterReport -> {

                    val hist: Hist = filter.metricHists[0].hist
                    println(hist)

                    val data = mapOf(
                        "x" to mutableListOf<Any>(),
                        "y" to mutableListOf<Any>(),
                        "g" to mutableListOf<Any>()
                    )

                    var i = 0
                    for (bin in hist.bins) {
                        data["x"]!! += bin.from
                        data["y"]!! += 1
                        data["g"]!! += i

                        data["x"]!! += bin.from
                        data["y"]!! += bin.weight
                        data["g"]!! += i

                        data["x"]!! += bin.to
                        data["y"]!! += bin.weight
                        data["g"]!! += i

                        data["x"]!! += bin.to
                        data["y"]!! += 1
                        data["g"]!! += i

                        ++i
                    }

                    var plt = ggplot(data) + geomPolygon(fill = "black", alpha = 0.5) {
                        x = "x"
                        y = "y"
                        group = "g"
                    }

                    plt += scaleXLog10()
                    plt += scaleYLog10()

                    writePDF(
                        Paths.get("scratch/bp.pdf"),
                        plt
                    )

                }

                is AndTaggedFilterReport -> TODO()
                is GenericHistOpReport -> TODO()
                is InGroupsFilterReport -> TODO()
                DummyKeyedFilterReport -> TODO()
                null -> TODO()
            }.exhaustive
        }
    }
}