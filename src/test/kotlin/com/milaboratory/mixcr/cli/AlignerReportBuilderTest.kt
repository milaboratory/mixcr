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
package com.milaboratory.mixcr.cli

import com.milaboratory.mitool.pattern.search.IntListKey
import com.milaboratory.mitool.report.ParseReport
import com.milaboratory.util.K_PRETTY_OM
import io.kotest.matchers.shouldBe
import org.junit.Assert
import org.junit.Test
import java.nio.file.Paths

class AlignerReportBuilderTest {
    @Test
    fun testIsSerializable() {
        val rep = reportBuilder().buildReport()
        Assert.assertNotNull(K_PRETTY_OM.writeValueAsString(rep))
    }

    @Test
    fun testNotSerializeDate() {
        val rep = reportBuilder().buildReport()
        val asJson = K_PRETTY_OM.writeValueAsString(rep)
        Assert.assertNull(K_PRETTY_OM.readValue(asJson, AlignerReport::class.java).date)
    }

    @Test
    fun testSerializeInputFiles() {
        val rep = reportBuilder()
            .setInputFiles(Paths.get("file1"))
            .buildReport()
        val asJson = K_PRETTY_OM.writeValueAsString(rep)
        Assert.assertArrayEquals(arrayOf("file1"), K_PRETTY_OM.readValue(asJson, AlignerReport::class.java).inputFiles)
    }

    @Test
    fun testSerializeCommandLine() {
        val rep = reportBuilder()
            .setCommandLine("cmd args")
            .buildReport()
        val asJson = K_PRETTY_OM.writeValueAsString(rep)
        Assert.assertEquals("cmd args", K_PRETTY_OM.readValue(asJson, AlignerReport::class.java).commandLine)
    }

    @Test
    fun testSerializeProjections() {
        val rep = reportBuilder()
            .setTagReport(
                ParseReport(
                    0L,
                    0L,
                    0L,
                    0.0,
                    mapOf(IntListKey(listOf(1, 2)) to 10L),
                    emptyList()
                )
            )
            .buildReport()
        val asJson = K_PRETTY_OM.writeValueAsString(rep)
        val result = K_PRETTY_OM.readValue(asJson, AlignerReport::class.java)
        result.tagParsingReport.projections.keys shouldBe setOf(IntListKey(listOf(1, 2)))
    }

    private fun reportBuilder(): AlignerReportBuilder = AlignerReportBuilder()
        .setCommandLine("from test")
        .setStartMillis(123)
        .setFinishMillis(123)
        .setInputFiles()
        .setOutputFiles()
}
