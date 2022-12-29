@file:Suppress("LocalVariableName")

package com.milaboratory.mixcr.export

import com.fasterxml.jackson.module.kotlin.readValue
import com.milaboratory.mitool.helpers.K_OM
import com.milaboratory.mixcr.cli.TestMain
import com.milaboratory.util.TempFileManager
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainInOrder
import org.junit.Test
import java.nio.file.Paths
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.writeLines

class ExportAlignmentsPrettyTest {
    @Test
    fun `check all examples`() {
        val override = false
        val samplesDir = when {
            override -> Paths.get("").toAbsolutePath().resolve("src/test/resources/export_pretty/samples")
            else -> Paths.get(ExportAlignmentsPrettyTest::class.java.getResource("/export_pretty/samples").file)
        }
        samplesDir.listDirectoryEntries().forEach { dir ->
            withClue(dir.fileName) {
                val tempDir = TempFileManager.getTempDir().toPath()
                val mixins = K_OM.readValue<List<String>>(dir.resolve("mixins.json").toFile()).joinToString(" ")
                val preset = dir.resolve("preset_name.txt").readLines().first()
                val R1 = dir.resolve("R1.fastq")
                val R2 = dir.resolve("R2.fastq")
                val alignmentsFile = tempDir.resolve("result.vdjca")
                TestMain.execute("align -p $preset $mixins $R1 $R2 $alignmentsFile")
                val exportResultFile = tempDir.resolve("result.txt")
                TestMain.execute("exportAlignmentsPretty $alignmentsFile $exportResultFile")
                val expectedResult = dir.resolve("exportPretty.txt")

                if (override) {
                    expectedResult.writeLines(exportResultFile.readLines())
                }

                exportResultFile.readText().also { println(it) }.lines().map { it.trim() } shouldContainInOrder
                        expectedResult.readLines().map { it.trim() }
            }
        }
    }
}
