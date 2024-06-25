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
        val output = TempFileManager.newTempDir().toPath().resolve("output.tsv").toFile()
        output.delete()
        TestMain.execute("${CommandExportAlignments.COMMAND_NAME} $input ${output.path}")
        val columns = output.readLines().first().split("\t")
        columns shouldContain "refPoints"
        columns shouldContainInOrder listOf("nSeqImputedFR1", "minQualFR1", "nSeqImputedCDR2", "minQualCDR2")
        columns shouldNotContain "readHistory"
    }

    @Test
    fun `use don't impute option`() {
        val input =
            Paths.get(DummyIntegrationTest::class.java.getResource("/sequences/big/yf_sample_data/Ig1_S1.clna").file)
        val output = TempFileManager.newTempDir().toPath().resolve("output.tsv").toFile()
        output.delete()
        TestMain.execute("${CommandExportAlignments.COMMAND_NAME} --dont-impute-germline-on-export $input ${output.path}")
        val columns = output.readLines().first().split("\t")
        columns shouldNotContain listOf("nSeqImputedFR1", "nSeqImputedCDR2")
        columns shouldContainInOrder listOf("nSeqFR1", "minQualFR1", "nSeqCDR2", "minQualCDR2")
    }

    @Test
    fun `append export field`() {
        val input =
            Paths.get(DummyIntegrationTest::class.java.getResource("/sequences/big/yf_sample_data/Ig1_S1.clna").file)
        val output = TempFileManager.newTempDir().toPath().resolve("output.tsv").toFile()
        output.delete()
        TestMain.execute("${CommandExportAlignments.COMMAND_NAME} -readHistory $input ${output.path}")
        val columns = output.readLines().first().split("\t")
        columns shouldContainInOrder listOf("refPoints", "readHistory")
    }

    @Test
    fun `prepend export field`() {
        val input =
            Paths.get(DummyIntegrationTest::class.java.getResource("/sequences/big/yf_sample_data/Ig1_S1.clna").file)
        val output = TempFileManager.newTempDir().toPath().resolve("output.tsv").toFile()
        output.delete()
        TestMain.execute("${CommandExportAlignments.COMMAND_NAME} -readHistory --prepend-columns $input ${output.path}")
        val columns = output.readLines().first().split("\t")
        columns shouldContainInOrder listOf("readHistory", "refPoints")
    }

    @Test
    fun `no default fields`() {
        val input =
            Paths.get(DummyIntegrationTest::class.java.getResource("/sequences/big/yf_sample_data/Ig1_S1.clna").file)
        val output = TempFileManager.newTempDir().toPath().resolve("output.tsv").toFile()
        output.delete()
        TestMain.execute("${CommandExportAlignments.COMMAND_NAME} -readHistory --drop-default-fields $input ${output.path}")
        val columns = output.readLines().first().split("\t")
        columns shouldBe listOf("readHistory")
    }

    @Test
    fun `don't get features from allCoveredBy for clna`() {
        val input =
            Paths.get(DummyIntegrationTest::class.java.getResource("/sequences/big/yf_sample_data/Ig1_S1.clna").file)
        val output = TempFileManager.newTempDir().toPath().resolve("output0.tsv").toFile()
        output.delete()
        TestMain.execute("${CommandExportAlignments.COMMAND_NAME} --drop-default-fields -allNFeatures $input ${output.path}")
        val columnsCompositeDefaults = output.readLines().first().split("\t")
        columnsCompositeDefaults shouldContain "nSeqFR1"
    }

    @Test
    fun `don't get features from allCoveredBy for vdjca`() {
        val input =
            Paths.get(DummyIntegrationTest::class.java.getResource("/sequences/big/yf_sample_data/Ig1_S1.alignments.vdjca").file)
        val output = TempFileManager.newTempDir().toPath().resolve("output0.tsv").toFile()
        output.delete()
        TestMain.execute("${CommandExportAlignments.COMMAND_NAME} --drop-default-fields -allNFeatures $input ${output.path}")
        val columnsCompositeDefaults = output.readLines().first().split("\t")
        columnsCompositeDefaults shouldContain "nSeqFR1"
    }
}
