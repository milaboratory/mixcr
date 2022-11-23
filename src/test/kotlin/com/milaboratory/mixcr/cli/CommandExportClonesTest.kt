package com.milaboratory.mixcr.cli

import com.milaboratory.mixcr.tests.IntegrationTest
import com.milaboratory.mixcr.util.DummyIntegrationTest
import com.milaboratory.util.TempFileManager
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.experimental.categories.Category
import java.nio.file.Paths

@Category(IntegrationTest::class)
class CommandExportClonesTest {
    @Test
    fun `check default export columns`() {
        val input =
            Paths.get(DummyIntegrationTest::class.java.getResource("/sequences/big/yf_sample_data/Ig1_S1.contigs.clns").file)
        val output = TempFileManager.getTempDir().toPath().resolve("output.tsv").toFile()
        output.delete()
        TestMain.execute("${CommandExportClones.COMMAND_NAME} --dont-split-files $input ${output.path}")
        val columns = output.readLines().first().split("\t")
        columns shouldContainAll listOf("cloneId", "readCount")
        columns shouldContainInOrder listOf("nSeqImputedFR1", "minQualFR1", "nSeqImputedCDR2", "minQualCDR2")
    }

    @Test
    fun `use don't impute option`() {
        val input =
            Paths.get(DummyIntegrationTest::class.java.getResource("/sequences/big/yf_sample_data/Ig1_S1.contigs.clns").file)
        val output = TempFileManager.getTempDir().toPath().resolve("output.tsv").toFile()
        output.delete()
        TestMain.execute("${CommandExportClones.COMMAND_NAME} --dont-impute-germline-on-export --dont-split-files $input ${output.path}")
        val columns = output.readLines().first().split("\t")
        columns shouldNotContain listOf("nSeqImputedFR1", "nSeqImputedCDR2")
        columns shouldContainInOrder listOf("nSeqFR1", "minQualFR1", "nSeqCDR2", "minQualCDR2")
    }

    @Test
    fun `append export field`() {
        val input =
            Paths.get(DummyIntegrationTest::class.java.getResource("/sequences/big/yf_sample_data/Ig1_S1.contigs.clns").file)
        val output = TempFileManager.getTempDir().toPath().resolve("output.tsv").toFile()
        output.delete()
        TestMain.execute("${CommandExportClones.COMMAND_NAME} --dont-split-files -nFeature VDJRegion $input ${output.path}")
        val columns = output.readLines().first().split("\t")
        columns shouldContainInOrder listOf("cloneId", "readCount", "nSeqVDJRegion")
    }

    @Test
    fun `prepend export field`() {
        val input =
            Paths.get(DummyIntegrationTest::class.java.getResource("/sequences/big/yf_sample_data/Ig1_S1.contigs.clns").file)
        val output = TempFileManager.getTempDir().toPath().resolve("output.tsv").toFile()
        output.delete()
        TestMain.execute("${CommandExportClones.COMMAND_NAME} --dont-split-files -nFeature VDJRegion --prepend-columns $input ${output.path}")
        val columns = output.readLines().first().split("\t")
        columns shouldContainInOrder listOf("nSeqVDJRegion", "cloneId", "readCount")
    }

    @Test
    fun `no default fields`() {
        val input =
            Paths.get(DummyIntegrationTest::class.java.getResource("/sequences/big/yf_sample_data/Ig1_S1.contigs.clns").file)
        val output = TempFileManager.getTempDir().toPath().resolve("output.tsv").toFile()
        output.delete()
        TestMain.execute("${CommandExportClones.COMMAND_NAME} --dont-split-files --drop-default-fields -nFeature VDJRegion $input ${output.path}")
        val columns = output.readLines().first().split("\t")
        columns shouldBe listOf("nSeqVDJRegion")
    }

    @Test
    fun `export detailed mutations`() {
        val input =
            Paths.get(DummyIntegrationTest::class.java.getResource("/sequences/big/yf_sample_data/Ig1_S1.contigs.clns").file)
        val output = TempFileManager.getTempDir().toPath().resolve("output.tsv").toFile()
        output.delete()
        TestMain.execute("${CommandExportClones.COMMAND_NAME} -mutationsDetailed VRegion $input ${output.path}")
    }

    @Test
    fun `specify duplicated column`() {
        val input =
            Paths.get(DummyIntegrationTest::class.java.getResource("/sequences/big/yf_sample_data/Ig1_S1.contigs.clns").file)
        val output = TempFileManager.getTempDir().toPath().resolve("output.tsv").toFile()
        output.delete()
        TestMain.execute("${CommandExportClones.COMMAND_NAME} --dont-split-files -cloneId $input ${output.path}")
        val columns = output.readLines().first().split("\t")
        columns.count { it == "cloneId" } shouldBe 1
    }

    @Test
    fun `specify duplicated column with composite column`() {
        val input =
            Paths.get(DummyIntegrationTest::class.java.getResource("/sequences/big/yf_sample_data/Ig1_S1.contigs.clns").file)
        val output = TempFileManager.getTempDir().toPath().resolve("output.tsv").toFile()
        output.delete()
        TestMain.execute("${CommandExportClones.COMMAND_NAME} --dont-split-files -nFeatureImputed CDR3 $input ${output.path}")
        val columns = output.readLines().first().split("\t")
        columns.count { it == "nSeqImputedCDR3" } shouldBe 1
    }

    @Test
    fun `export all nFeatures equal to export by one`() {
        val input =
            Paths.get(DummyIntegrationTest::class.java.getResource("/sequences/big/yf_sample_data/Ig1_S1.contigs.clns").file)
        val outputCompositeDefaults = TempFileManager.getTempDir().toPath().resolve("output0.tsv").toFile()
        outputCompositeDefaults.delete()
        val outputComposite = TempFileManager.getTempDir().toPath().resolve("output1.tsv").toFile()
        outputComposite.delete()
        val outputByOne = TempFileManager.getTempDir().toPath().resolve("output2.tsv").toFile()
        outputByOne.delete()
        TestMain.execute("${CommandExportClones.COMMAND_NAME} --dont-split-files --drop-default-fields -allNFeatures $input ${outputCompositeDefaults.path}")
        TestMain.execute("${CommandExportClones.COMMAND_NAME} --dont-split-files --drop-default-fields -allNFeatures FR1Begin FR4End $input ${outputComposite.path}")
        TestMain.execute(
            "${CommandExportClones.COMMAND_NAME} --dont-split-files --drop-default-fields " +
                    "-nFeature FR1 " +
                    "-nFeature CDR1 " +
                    "-nFeature FR2 " +
                    "-nFeature CDR2 " +
                    "-nFeature FR3 " +
                    "-nFeature CDR3 " +
                    "-nFeature FR4 " +
                    "$input ${outputByOne.path}"
        )
        val columnsCompositeDefaults = outputCompositeDefaults.readLines().first().split("\t")
        val columnsComposite = outputComposite.readLines().first().split("\t")
        val columnsByOne = outputByOne.readLines().first().split("\t")
        columnsCompositeDefaults shouldBe columnsComposite
        columnsComposite shouldBe columnsByOne
        outputComposite.readLines() shouldBe outputByOne.readLines()
    }

    @Test
    fun `get features from allCoveredBy`() {
        val input =
            Paths.get(DummyIntegrationTest::class.java.getResource("/sequences/big/yf_sample_data/Ig1_S1.clna").file)
        val outputCompositeDefaults = TempFileManager.getTempDir().toPath().resolve("output0.tsv").toFile()
        outputCompositeDefaults.delete()
        val outputByOne = TempFileManager.getTempDir().toPath().resolve("output2.tsv").toFile()
        outputByOne.delete()
        TestMain.execute("${CommandExportClones.COMMAND_NAME} --dont-split-files --drop-default-fields -allNFeatures $input ${outputCompositeDefaults.path}")
        TestMain.execute(
            "${CommandExportClones.COMMAND_NAME} --dont-split-files --drop-default-fields " +
                    "-nFeature CDR3 " +
                    "$input ${outputByOne.path}"
        )
        val columnsCompositeDefaults = outputCompositeDefaults.readLines().first().split("\t")
        val columnsByOne = outputByOne.readLines().first().split("\t")
        columnsCompositeDefaults shouldBe columnsByOne
    }

    @Test
    fun `try to get not covered feature`() {
        val input =
            Paths.get(DummyIntegrationTest::class.java.getResource("/sequences/big/yf_sample_data/Ig1_S1.clna").file)
        val output = TempFileManager.getTempDir().toPath().resolve("output0.tsv").toFile()
        output.delete()
        TestMain.execute("${CommandExportClones.COMMAND_NAME} --dont-split-files --drop-default-fields -nFeature CDR1 $input ${output.path}")
        output.readLines()[1].split("\t") shouldBe listOf("")
    }
}
