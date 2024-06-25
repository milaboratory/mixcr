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
package com.milaboratory.mixcr.qc.plots

import com.milaboratory.miplots.writePDF
import com.milaboratory.mixcr.tests.IntegrationTest
import org.junit.Test
import org.junit.experimental.categories.Category
import java.nio.file.Files
import java.nio.file.Paths

@Category(IntegrationTest::class)
class QcTest {
    val align = listOf(
        "Ig-2_S2.alignments.vdjca",
        "Ig-3_S3.alignments.vdjca",
        "Ig-4_S4.alignments.vdjca",
        "Ig-5_S5.alignments.vdjca",
        "Ig1_S1.alignments.vdjca",
        "Ig2_S2.alignments.vdjca",
        "Ig3_S3.alignments.vdjca",
        "Ig4_S4.alignments.vdjca",
        "Ig5_S5.alignments.vdjca"
    ).map {
        Paths.get(javaClass.getResource("/sequences/big/yf_sample_data/$it")!!.file)
    }

    @Test
    fun testQc() {
        val plt = mutableListOf(
            AlignmentQC.alignQc(align, percent = false),
            AlignmentQC.alignQc(align, percent = true),

            ChainUsage.chainUsageAlign(
                align,
                percent = false,
                showNonFunctional = true,
            ),
            ChainUsage.chainUsageAlign(
                align,
                percent = true,
                showNonFunctional = true,
            ),
            ChainUsage.chainUsageAlign(
                align,
                percent = false,
                showNonFunctional = false,
            ),
            ChainUsage.chainUsageAlign(
                align,
                percent = true,
                showNonFunctional = false,
            ),
        )

        align.forEach { plt += Coverage.coveragePlot(it) }

        writePDF(
            Files.createTempFile("QcTest", ".pdf"),
            plt
        )
    }
}
