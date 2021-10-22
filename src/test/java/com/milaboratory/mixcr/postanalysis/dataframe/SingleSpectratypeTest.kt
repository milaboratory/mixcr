package com.milaboratory.mixcr.postanalysis.dataframe

import jetbrains.datalore.plot.PlotSvgExport
import jetbrains.letsPlot.coordFixed
import jetbrains.letsPlot.geom.geomBoxplot
import jetbrains.letsPlot.geom.geomTile
import jetbrains.letsPlot.ggsize
import jetbrains.letsPlot.intern.toSpec
import jetbrains.letsPlot.letsPlot
import jetbrains.letsPlot.scale.scaleXContinuous
import jetbrains.letsPlot.scale.scaleYContinuous
import org.jetbrains.dataframe.DataFrame
import org.jetbrains.dataframe.io.readCSV
import org.jetbrains.dataframe.toMap
import org.junit.Test
import java.nio.file.Path


/**
 *
 */
internal class SingleSpectratypeTest {
    @Test
    fun name1() {
        val data =
            DataFrame.readCSV("https://raw.githubusercontent.com/PoslavskySV/scratch/master/lets-plot-heatmap/data1.csv")
        val mData = data.head(1000).toMap()
        val plot = letsPlot(mData) {
            x = "sample"
            y = "gene"
            fill = "weight"
        } + geomTile(color = "white") + coordFixed()

        writePDF(
            Path.of("/Users/poslavskysv/Downloads/letsplot.pdf"), toPdf(
                PlotSvgExport.buildSvgImageFromRawSpecs(plot.toSpec())
            )
        )
    }

    @Test
    fun name() {
        val data =
            DataFrame.readCSV("https://raw.githubusercontent.com/PoslavskySV/scratch/master/lets-plot-heatmap/data1.csv")

        val mData = data.head(1000).toMap()
        val xs = mData["sample"]!!.toList().distinct().size
        val ys = mData["gene"]!!.toList().distinct().size
        val f = 25

        println(xs * f + 5 * f)
        println(ys * f)
        val plot = letsPlot(mData) {
            x = "sample"
            y = "gene"
            fill = "weight"
        } + geomTile(color = "white") +
                scaleXContinuous(expand = listOf(0.0, 0.0)) +
                scaleYContinuous(expand = listOf(0.0, 0.0)) +
                coordFixed() +
                ggsize(xs * f + 5 * f, 3 * ys * f / 5)


        writePDF(
            Path.of("/Users/poslavskysv/Downloads/letsplot.pdf"), toPdf(
                PlotSvgExport.buildSvgImageFromRawSpecs(plot.toSpec())
            )
        )
    }
}
