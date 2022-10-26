package com.milaboratory.mixcr.cli

import com.milaboratory.mixcr.tests.IntegrationTest
import com.milaboratory.mixcr.util.DummyIntegrationTest
import com.milaboratory.util.TempFileManager
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.experimental.categories.Category
import java.nio.file.Paths

@Category(IntegrationTest::class)
class CommandExportAlignmentsTest {
    @Test
    fun `check default export columns`() {
        val input =
            Paths.get(DummyIntegrationTest::class.java.getResource("/sequences/big/yf_sample_data/Ig1_S1.clna").file)
        val output = TempFileManager.getTempDir().toPath().resolve("output.tsv").toFile()
        output.delete()
        TestMain.execute("${CommandExportAlignments.COMMAND_NAME} $input ${output.path}")
        val columns = output.readLines().first().split("\t")
        columns shouldContain "refPoints"
        columns shouldNotContain "readHistory"
    }

    @Test
    fun `append export field`() {
        val input =
            Paths.get(DummyIntegrationTest::class.java.getResource("/sequences/big/yf_sample_data/Ig1_S1.clna").file)
        val output = TempFileManager.getTempDir().toPath().resolve("output.tsv").toFile()
        output.delete()
        TestMain.execute("${CommandExportAlignments.COMMAND_NAME} -readHistory $input ${output.path}")
        val columns = output.readLines().first().split("\t")
        columns shouldContainInOrder listOf("refPoints", "readHistory")
    }

    @Test
    fun `prepend export field`() {
        val input =
            Paths.get(DummyIntegrationTest::class.java.getResource("/sequences/big/yf_sample_data/Ig1_S1.clna").file)
        val output = TempFileManager.getTempDir().toPath().resolve("output.tsv").toFile()
        output.delete()
        TestMain.execute("${CommandExportAlignments.COMMAND_NAME} -readHistory --prepend-columns $input ${output.path}")
        val columns = output.readLines().first().split("\t")
        columns shouldContainInOrder listOf("readHistory", "refPoints")
    }

    @Test
    fun `no default fields`() {
        val input =
            Paths.get(DummyIntegrationTest::class.java.getResource("/sequences/big/yf_sample_data/Ig1_S1.clna").file)
        val output = TempFileManager.getTempDir().toPath().resolve("output.tsv").toFile()
        output.delete()
        TestMain.execute("${CommandExportAlignments.COMMAND_NAME} -readHistory --drop-default-fields $input ${output.path}")
        val columns = output.readLines().first().split("\t")
        columns shouldBe listOf("readHistory")
    }

    @Test
    fun `export all nFeatures equal to export by one`() {
        val input =
            Paths.get(DummyIntegrationTest::class.java.getResource("/sequences/big/yf_sample_data/Ig1_S1.clna").file)
        val outputComposite = TempFileManager.getTempDir().toPath().resolve("output1.tsv").toFile()
        outputComposite.delete()
        val outputByOne = TempFileManager.getTempDir().toPath().resolve("output2.tsv").toFile()
        outputComposite.delete()
        TestMain.execute("${CommandExportAlignments.COMMAND_NAME} --drop-default-fields -allNFeatures FR1Begin FR4End $input ${outputComposite.path}")
        TestMain.execute(
            "${CommandExportAlignments.COMMAND_NAME} --drop-default-fields " +
                    "-nFeature FR1 " +
                    "-nFeature CDR1 " +
                    "-nFeature FR2 " +
                    "-nFeature CDR2 " +
                    "-nFeature FR3 " +
                    "-nFeature CDR3 " +
                    "-nFeature FR4 " +
                    "$input ${outputByOne.path}"
        )
        val columnsComposite = outputComposite.readLines().first().split("\t")
        val columnsByOne = outputByOne.readLines().first().split("\t")
        columnsComposite shouldBe columnsByOne
        outputComposite.readLines() shouldBe outputByOne.readLines()
    }
}
