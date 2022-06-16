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
package com.milaboratory.mixcr.postanalysis.plots

import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Text
import com.milaboratory.mixcr.postanalysis.PostanalysisResult
import com.milaboratory.mixcr.postanalysis.SetPreprocessorStat
import org.jetbrains.kotlinx.dataframe.annotations.DataSchema
import org.jetbrains.kotlinx.dataframe.api.toDataFrame
import java.io.ByteArrayOutputStream


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

    fun pdfSummary(
        stat: Map<String, SetPreprocessorStat>,
        headerText: String
    ): ByteArray? = run {
        val statFiltered = stat.filter {
            it.value.nElementsAfter > 0 // && preproc.lowercase().contains("downsampl")
        }
        val elementsAfter = statFiltered.values.map { it.nElementsAfter }.distinct()
        val weightAfter = statFiltered.values.map { it.sumWeightAfter }.distinct()

        val downsamplingText1 =
            if (elementsAfter.size == 1)
                "Downsampled to ${elementsAfter[0]} clonotypes"
            else
                null

        val downsamplingText2 =
            if (weightAfter.size == 1)
                "Downsampled to ${weightAfter[0]} reads"
            else
                null

        // dropped samples:
        val dropped = stat.filter { it.value.dropped || it.value.nElementsAfter == 0L }.keys.distinct()

        if (downsamplingText1 == null && downsamplingText2 == null && dropped.isEmpty())
            return@run null

        document {
            add(Paragraph(headerText).setBold())
            if (downsamplingText1 != null)
                add(Paragraph(downsamplingText1))
            if (downsamplingText2 != null)
                add(Paragraph(downsamplingText2))
            if (dropped.isNotEmpty()) {
                add(
                    Paragraph("Dropped samples (" + dropped.size + "):\n")
                        .add(Text(dropped.joinToString(", ")).setFontSize(8.0f))
                )
            }
        }
    }

    private fun document(content: Document.() -> Unit) = run {
//        val dummyDoc = Document(PdfDocument(PdfWriter(OutputStream.nullOutputStream())))
//        dummyDoc.content()
//        val renderer = dummyDoc.renderer//.setParent(dummyDoc2.renderer)
//        val layout =
//            renderer.layout(LayoutContext(LayoutArea(0, Rectangle(1000.0f, 1000.0f))))

        val bs = ByteArrayOutputStream()
        val pdfDoc = PdfDocument(PdfWriter(bs))
        val document = Document(pdfDoc) //, PageSize(layout.occupiedArea.bBox.width, layout.occupiedArea.bBox.height))
        document.use {
            it.content()
            it.close()
            bs.toByteArray()
        }
    }

//
//    fun DataFrame<PreprocSummaryRow>.pdfTable(header: String = "") = run {
//        val nCols = 1 + (this.maxOfOrNull { it.chain.size } ?: 0)
//
//        val table = Table(1 + nCols * 5)
//        table.setFixedLayout()
//
//        // header
//        table.addCell("sample")
//        for (i in 0 until nCols) {
//            val suff: String = if (i == 0) "" else "#$i"
//            table.addCell("preprocessor$suff")
//            table.addCell("nElementsBefore$suff")
//            table.addCell("sumWeightBefore$suff")
//            table.addCell("nElementsAfter$suff")
//            table.addCell("sumWeightAfter$suff")
//        }
//
//        for (row in rows()) {
//            table.addCell(row.sample)
//            table.writeStat(row.stat)
//            for (cs in row.chain) {
//                table.writeStat(cs)
//            }
//            if (row.chain.size < (nCols - 1)) {
//                table.writeDummyStat(nCols - 1 - row.chain.size)
//            }
//        }
//
//        val bs = ByteArrayOutputStream()
//        val pdfDoc = PdfDocument(PdfWriter(bs))
//
//        val dummyDoc = Document(PdfDocument(PdfWriter(OutputStream.nullOutputStream())))
//        val renderer = table.createRendererSubTree().setParent(dummyDoc.renderer)
//        val layout =
//            renderer.layout(LayoutContext(LayoutArea(0, Rectangle(1000.0f, 1000.0f))))
//        dummyDoc.close()
//
//        val document = Document(pdfDoc, PageSize(layout.occupiedArea.bBox.width, layout.occupiedArea.bBox.height))
//        document.use {
//            document.add(Paragraph(header))
//            document.add(table)
//            document.close()
//            bs.toByteArray()
//        }
//    }
//
//    private fun Table.writeStat(stat: SetPreprocessorStat) {
//        addCell(stat.preprocId)
//        addCell(stat.nElementsBefore.toString())
//        addCell(stat.sumWeightBefore.toString())
//        addCell(stat.nElementsAfter.toString())
//        addCell(stat.sumWeightAfter.toString())
//    }
//
//    private fun Table.writeDummyStat(n: Int) {
//        for (j in 0 until n)
//            for (i in 0 until 5)
//                addCell("")
//    }
}