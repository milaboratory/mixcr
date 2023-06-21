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
import com.milaboratory.mixcr.util.RunMiXCR
import org.junit.Test
import java.nio.file.Files

class CoverageTest {
    @Test
    fun testCoverage() {
        val params = RunMiXCR.RunMiXCRAnalysis(
            RunMiXCR::class.java.getResource("/sequences/test_R1.fastq")!!.file,
            RunMiXCR::class.java.getResource("/sequences/test_R2.fastq")!!.file
        )

        val align = RunMiXCR.align(params)
        val path = align.alignmentsPath()
        writePDF(
            Files.createTempFile("CoverageTest", ".pdf"),
            Coverage.coveragePlot(path, false),
        )
    }
}