package com.milaboratory.mixcr.postanalysis.plots

import com.milaboratory.mixcr.postanalysis.PostanalysisResult
import com.milaboratory.mixcr.postanalysis.ui.PostanalysisSchema
import jetbrains.datalore.plot.PlotSvgExport
import jetbrains.letsPlot.Pos
import jetbrains.letsPlot.geom.geomBoxplot
import jetbrains.letsPlot.geom.geomPoint
import jetbrains.letsPlot.intern.toSpec
import jetbrains.letsPlot.label.ggtitle
import jetbrains.letsPlot.label.labs
import jetbrains.letsPlot.letsPlot
import jetbrains.letsPlot.theme
import org.apache.batik.transcoder.TranscoderInput
import org.apache.batik.transcoder.TranscoderOutput
import org.apache.fop.render.ps.EPSTranscoder
import org.apache.fop.svg.PDFTranscoder
import org.apache.pdfbox.io.MemoryUsageSetting
import org.apache.pdfbox.multipdf.PDFMergerUtility
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.writeBytes

/**
 *
 */
object BoxPlots {
    fun individualBoxPlots(
        destination: Path,
        paSett: PostanalysisSchema<*>,
        paResult: PostanalysisResult,
        group: String,
        metadata: Map<String, String>? = null
    ) {

        // metricName -> list (sample, value)
        val metrics = mutableMapOf<String, MutableList<Pair<String, Double>>>()
        for ((_, charData) in paResult.forGroup(paSett.getGroup<Any>(group)).data) {
            for ((sampleId, allMetrics) in charData.data) {
                for (metricValue in allMetrics.data) {
                    val metricName = metricValue.key.toString()
                    val samples = metrics.computeIfAbsent(metricName) { mutableListOf() }
                    samples.add(sampleId to metricValue.value)
                }
            }
        }

        val svgs = metrics.map { (metric, data) ->
            var plt = letsPlot(mapOf("y" to data.map { it.second })) {
                y = "y"
            }
            plt += geomBoxplot()
            plt += geomPoint(
                position = Pos.jitterdodge,
                shape = 21,
                color = "black"
            )
            plt += ggtitle(metric)
            plt += labs(y = metric)
            plt += theme().axisTitleXBlank()
            plt += theme().axisTextXBlank()
            plt += theme().axisTicksXBlank()

            PlotSvgExport.buildSvgImageFromRawSpecs(plt.toSpec())
        }

        writePDF(destination, svgs.map { toPdf(it) })
    }

    fun toPdf(svg: String) = toVector(svg, ExportType.PDF)
    fun toEPS(svg: String) = toVector(svg, ExportType.EPS)

    enum class ExportType { PDF, EPS }

    private fun toVector(svg: String, type: ExportType) = run {
        val pdfTranscoder = if (type == ExportType.PDF) PDFTranscoder() else EPSTranscoder()
        val input = TranscoderInput(ByteArrayInputStream(svg.toByteArray()))
        ByteArrayOutputStream().use { byteArrayOutputStream ->
            val output = TranscoderOutput(byteArrayOutputStream)
            pdfTranscoder.transcode(input, output)
            byteArrayOutputStream.toByteArray()
        }
    }

    fun writeEPS(destination: Path, image: ByteArray) {
        destination.writeBytes(image)
    }

    fun writePDF(destination: Path, images: List<ByteArray>) {
        val merger = PDFMergerUtility()
        merger.destinationFileName = destination.absolutePathString()

        for (image in images) {
            merger.addSource(ByteArrayInputStream(image))
        }

        merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly())
    }
}
