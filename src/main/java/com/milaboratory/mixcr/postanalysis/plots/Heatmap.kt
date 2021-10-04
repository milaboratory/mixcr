package com.milaboratory.mixcr.postanalysis.plots

import com.milaboratory.mixcr.postanalysis.PostanalysisResult
import com.milaboratory.mixcr.postanalysis.spectratype.SpectratypeKey
import com.milaboratory.mixcr.postanalysis.ui.PostanalysisSchema
import jetbrains.datalore.plot.PlotSvgExport
import jetbrains.letsPlot.*
import jetbrains.letsPlot.geom.geomBar
import jetbrains.letsPlot.intern.toSpec
import jetbrains.letsPlot.label.ggtitle
import jetbrains.letsPlot.label.labs
import jetbrains.letsPlot.scale.scaleXDiscrete
import java.nio.file.Path

/**
 *
 */
object Heatmap {
    private const val x = "l"
    private const val y = "p"
    private const val w = "c"
    private const val other = "Other"

    private fun mkDf() = mutableMapOf<String, MutableList<Any>>(
        x to mutableListOf(),
        y to mutableListOf(),
        w to mutableListOf()
    )


    fun vUsage(
        destination: Path,
        paSett: PostanalysisSchema<*>,
        paResult: PostanalysisResult,
        group: String
    ) {

        // x - sample, y - gene
        val df = mkDf()
        for ((_, charData) in paResult.forGroup(paSett.getGroup<Any>(group)).data) {
            for ((sampleId, keys) in charData.data) {
                for (metric in keys.data) {
                    @Suppress("UNCHECKED_CAST")
                    val key = metric.key as SpectratypeKey<String>
                    df[x]!! += sampleId
                    df[y]!! += key.payload
                    df[w]!! += metric.value
                }
            }
        }


//        val svgs = samples.map { (sample, idf) ->
//            val df = SpectraPlots.filter(
//                idf,
//                normalize = normalize,
//                nTop = nTop
//            )
//
//            var plt = letsPlot(df)
//            plt += geomBar(position = Pos.stack, stat = Stat.identity, width = 0.6) {
//                x = asDiscrete(SpectraPlots.lenCDR3, order = 1)
//                y = SpectraPlots.count
//                fill = asDiscrete(SpectraPlots.payload, orderBy = SpectraPlots.count, order = -1)
//            }
//            plt += ggtitle(sample)
//            plt += labs(y = "Clone count", x = "CDR3 length, bp")
//
//            val breaks = df[SpectraPlots.lenCDR3]!!
//                .map { it as Int }
//                .sorted()
//                .distinct()
//
//            plt += scaleXDiscrete(
//                breaks = breaks,
//                labels = breaks.map { if (it % 3 == 0) it.toString() else "" },
//                limits = limits?.toList()
//            )
//            plt += ggsize(1000, 500)
//
//            PlotSvgExport.buildSvgImageFromRawSpecs(plt.toSpec())
//        }
//
//        BoxPlots.writePDF(destination, svgs.map { BoxPlots.toPdf(it) })
    }

}
