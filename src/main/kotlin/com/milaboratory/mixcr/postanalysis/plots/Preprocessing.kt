package com.milaboratory.mixcr.postanalysis.plots

import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.geom.Rectangle
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.layout.LayoutArea
import com.itextpdf.layout.layout.LayoutContext
import com.milaboratory.mixcr.postanalysis.PostanalysisResult
import com.milaboratory.mixcr.postanalysis.SetPreprocessorStat
import org.apache.commons.io.output.NullOutputStream
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.annotations.DataSchema
import org.jetbrains.kotlinx.dataframe.api.maxOfOrNull
import org.jetbrains.kotlinx.dataframe.api.rows
import org.jetbrains.kotlinx.dataframe.api.toDataFrame
import java.io.ByteArrayOutputStream
import java.io.OutputStream


@DataSchema
data class PreprocSummaryRow(
    val preproc: String,
    val sample: String,
    val stat: SetPreprocessorStat,
    val chain: List<SetPreprocessorStat>,
)

object Preprocessing {
    /** Imports data into DataFrame **/
    fun dataFrame(paResult: PostanalysisResult, preprocFilter: Set<String>?) = run {
        val rows = mutableListOf<PreprocSummaryRow>()
        for ((preproc, summary) in paResult.preprocSummary) {
            if (preprocFilter == null || preprocFilter.contains(preproc))
                for ((sample, stat) in summary.result) {
                    rows += PreprocSummaryRow(
                        preproc, sample,
                        SetPreprocessorStat.cumulative(stat),
                        stat
                    )
                }
        }
        rows.toDataFrame()
    }

    fun DataFrame<PreprocSummaryRow>.pdfTable(header: String = "") = run {
        val nCols = 1 + (this.maxOfOrNull { it.chain.size } ?: 0)

        val table = Table(1 + nCols * 5)
        table.setFixedLayout()

        // header
        table.addCell("sample")
        for (i in 0 until nCols) {
            val suff: String = if (i == 0) "" else "#$i"
            table.addCell("preprocessor$suff")
            table.addCell("nElementsBefore$suff")
            table.addCell("sumWeightBefore$suff")
            table.addCell("nElementsAfter$suff")
            table.addCell("sumWeightAfter$suff")
        }

        for (row in rows()) {
            table.addCell(row.sample)
            table.writeStat(row.stat)
            for (cs in row.chain) {
                table.writeStat(cs)
            }
            if (row.chain.size < (nCols - 1)) {
                table.writeDummyStat(nCols - 1 - row.chain.size)
            }
        }

        val bs = ByteArrayOutputStream()
        val pdfDoc = PdfDocument(PdfWriter(bs))

        val dummyDoc = Document(PdfDocument(PdfWriter(NullOutputStream.NULL_OUTPUT_STREAM)))
        val renderer = table.createRendererSubTree().setParent(dummyDoc.renderer)
        val layout =
            renderer.layout(LayoutContext(LayoutArea(0, Rectangle(1000.0f, 1000.0f))))
        dummyDoc.close()

        val document = Document(pdfDoc, PageSize(layout.occupiedArea.bBox.width, layout.occupiedArea.bBox.height))
        document.use {
            document.add(Paragraph(header))
            document.add(table)
            document.close()
            bs.toByteArray()
        }
    }

    private fun Table.writeStat(stat: SetPreprocessorStat) {
        addCell(stat.preprocId)
        addCell(stat.nElementsBefore.toString())
        addCell(stat.sumWeightBefore.toString())
        addCell(stat.nElementsAfter.toString())
        addCell(stat.sumWeightAfter.toString())
    }

    private fun Table.writeDummyStat(n: Int) {
        for (j in 0 until n)
            for (i in 0 until 5)
                addCell("")
    }
}
