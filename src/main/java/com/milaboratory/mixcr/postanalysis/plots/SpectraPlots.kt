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
import org.jetbrains.dataframe.annotations.DataSchema
import org.jetbrains.dataframe.columns.DataColumn
import org.jetbrains.dataframe.sortBy
import org.jetbrains.dataframe.toDataFrame
import java.nio.file.Path

/**
 *
 */

object SpectraPlots {
    private const val lenCDR3 = "l"
    private const val payload = "p"
    private const val count = "c"
    private const val other = "Other"

    @DataSchema
    interface Row {
        val sample: String
        val len: Int
        val payload: String
        val count: Double
    }

    private fun mkDf() = mutableMapOf<String, MutableList<Any>>(
        lenCDR3 to mutableListOf(),
        payload to mutableListOf(),
        count to mutableListOf()
    )

    fun singleSpectra(
        destination: Path,
        paSett: PostanalysisSchema<*>,
        paResult: PostanalysisResult,
        group: String,
        nTop: Int = 20,
        limits: IntRange? = null,
        normalize: Boolean = true
    ) {

        // sample -> dataframe
        val samples = mutableMapOf<String, MutableMap<String, MutableList<Any>>>()

        for ((_, charData) in paResult.forGroup(paSett.getGroup<SpectratypeKey<String>>(group)).data) {
            for ((sampleId, keys) in charData.data) {
                val df = samples.computeIfAbsent(sampleId) { mkDf() }
                for (metric in keys.data) {
                    @Suppress("UNCHECKED_CAST")
                    val key = metric.key as SpectratypeKey<String>
                    df[lenCDR3]!! += key.length
                    df[payload]!! += (key.payload ?: other).toString()
                    df[count]!! += metric.value
                }
            }
        }


        val svgs = samples.map { (sample, idf) ->
            val df = filter(
                idf,
                normalize = normalize,
                nTop = nTop
            )

            var plt = letsPlot(df)
            plt += geomBar(position = Pos.stack, stat = Stat.identity, width = 0.6) {
                x = asDiscrete(lenCDR3, order = 1)
                y = count
                fill = asDiscrete(payload, orderBy = count, order = -1)
            }
            plt += ggtitle(sample)
            plt += labs(y = "Clone count", x = "CDR3 length, bp")

            val breaks = df[lenCDR3]!!
                .map { it as Int }
                .sorted()
                .distinct()

            plt += scaleXDiscrete(
                breaks = breaks,
                labels = breaks.map { if (it % 3 == 0) it.toString() else "" },
                limits = limits?.toList()
            )
            plt += ggsize(1000, 500)

            PlotSvgExport.buildSvgImageFromRawSpecs(plt.toSpec())
        }

        BoxPlots.writePDF(destination, svgs.map { BoxPlots.toPdf(it) })
    }

    @Suppress("UNCHECKED_CAST")
    fun filter(df: DataFrame, normalize: Boolean = true, nTop: Int = 10) = run {
        val top = df[payload]!!.zip(df[count]!!)
            .groupingBy { it.first as String }
            .fold(0.0) { acc: Double, el -> acc + el.second as Double }
            .toList()
            .sortedBy { it.second }
            .map { it.first }
            .distinct()
            .takeLast(nTop)
            .toSet()


        val totalCount = if (normalize)
            df[count]!!.map { it as Double }.sum()
        else
            1.0

        val ndf = mkDf()
        val otherCounts = mutableMapOf<Int, Double>()
        for (i in 0 until df.nRows()) {
            val l = df[lenCDR3]!![i] as Int
            val p = df[payload]!![i]
            val c = (df[count]!![i] as Double) / totalCount
            if (!top.contains(p))
                otherCounts[l] = (otherCounts[l] ?: 0.0) + c
            else {
                ndf[lenCDR3]!! += l
                ndf[payload]!! += p
                ndf[count]!! += c
            }
        }

        val ll = ndf[lenCDR3]!!.map { it as Int }.sorted().distinct()
        val min = ll.minOrNull() ?: 0
        val max = ll.maxOrNull() ?: 0

        for (l in (min..max))
            otherCounts.putIfAbsent(l, 0.0)

        for ((l, c) in otherCounts) {
            ndf[lenCDR3]!! += l
            ndf[payload]!! += other
            ndf[count]!! += c
        }

        ndf.sort(count, descending = true)
    }
}
