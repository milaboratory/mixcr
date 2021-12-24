package com.milaboratory.mixcr.postanalysis.dataframe.pubr

import com.milaboratory.mixcr.postanalysis.dataframe.toPDF
import com.milaboratory.mixcr.postanalysis.dataframe.writePDF
import jetbrains.letsPlot.elementRect
import jetbrains.letsPlot.geom.geomBoxplot
import jetbrains.letsPlot.letsPlot
import jetbrains.letsPlot.theme
import org.jetbrains.kotlinx.dataframe.api.toMap
import org.junit.Test
import java.nio.file.Paths

/**
 *
 */
class StatCompareMeansTest {

    @Test
    fun test1() {
        val data = CompareMeansTest.rndData(
            "V" to Gaussian,
            "A" to Category(2),
            "C" to Category(2),
            len = 100000
        )

        var plot = letsPlot()

        plot += geomBoxplot(data.toMap()) {
            x = "A"
            y = "V"
            fill = "A"
        } + theme(panelGrid = "blank", panelBackground = elementRect(color = "black"))

        plot += statCompareMeans()

        writePDF(
            Paths.get("scratch/bp.pdf"),
            plot.toPDF()
        )
    }
}
