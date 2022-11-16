package com.milaboratory.mixcr.cli

import com.milaboratory.mixcr.tests.IntegrationTest
import com.milaboratory.mixcr.util.DummyIntegrationTest
import com.milaboratory.mixcr.util.XSV
import com.milaboratory.util.TempFileManager
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Test
import org.junit.experimental.categories.Category
import java.nio.file.Paths

@Category(IntegrationTest::class)
class CommandExportReportsAsTableTest {
    @Test
    fun `check default export columns`() {
        val input =
            Paths.get(DummyIntegrationTest::class.java.getResource("/sequences/big/yf_sample_data/Ig1_S1.contigs.clns").file)
        val output = TempFileManager.getTempDir().toPath().resolve("output.tsv").toFile()
        output.delete()
        TestMain.execute("${CommandExportReportsAsTable.COMMAND_NAME} $input ${output.path}")
        val columns = output.readLines().first().split("\t")
        columns shouldContainAll listOf("fileName", "commandName", "commandLine", "MiXCRVersion")
    }

    @Test
    fun `check export of json path`() {
        val input =
            Paths.get(DummyIntegrationTest::class.java.getResource("/sequences/big/yf_sample_data/Ig1_S1.contigs.clns").file)
        val output = TempFileManager.getTempDir().toPath().resolve("output.tsv").toFile()
        output.delete()
        TestMain.execute(
            "${CommandExportReportsAsTable.COMMAND_NAME} " +
                    "-commandName " +
                    "-reportJsonPart /notAlignedReasons/NoHits " +
                    "-reportJsonPart /totalReadsProcessed " +
                    "-reportJsonPart /chainUsage/chains " +
                    "$input ${output.path}"
        )
        val expectedColumns =
            listOf(
                "commandName",
                "report/notAlignedReasons/NoHits",
                "report/totalReadsProcessed",
                "report/chainUsage/chains"
            )
        val columns = output.readLines().first().split("\t")
        columns shouldContainExactly expectedColumns
        val rows = XSV.readXSV(output, expectedColumns, "\t")
        rows shouldHaveSize 3
        rows.count { it["report/notAlignedReasons/NoHits"] == null } shouldBe 2
        rows.count { it["report/totalReadsProcessed"] == null } shouldBe 0
        rows.count { it["report/chainUsage/chains"] == null } shouldBe 2
        val alignRow = rows.filter { it["commandName"] == "align" }
        alignRow.map { it["report/notAlignedReasons/NoHits"] } shouldNotBe null
        alignRow.map { it["report/chainUsage/chains"] } shouldNotBe null
    }
}
