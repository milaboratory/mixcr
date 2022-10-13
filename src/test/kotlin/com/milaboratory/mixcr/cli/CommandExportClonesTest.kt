package com.milaboratory.mixcr.cli

import com.milaboratory.mixcr.tests.IntegrationTest
import com.milaboratory.mixcr.util.DummyIntegrationTest
import com.milaboratory.util.TempFileManager
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainInOrder
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
        val output = TempFileManager.getTempFile()
        output.delete()
        TestMain.execute("${CommandExportClones.COMMAND_NAME} $input ${output.path}")
        val columns = output.readLines().first().split("\t")
        columns shouldContainAll listOf("cloneId", "cloneCount")
    }

    @Test
    fun `append export field`() {
        val input =
            Paths.get(DummyIntegrationTest::class.java.getResource("/sequences/big/yf_sample_data/Ig1_S1.contigs.clns").file)
        val output = TempFileManager.getTempFile()
        output.delete()
        TestMain.execute("${CommandExportClones.COMMAND_NAME} -nFeature VDJRegion $input ${output.path}")
        val columns = output.readLines().first().split("\t")
        columns shouldContainInOrder listOf("cloneId", "cloneCount", "nSeqVDJRegion")
    }

    @Test
    fun `prepend export field`() {
        val input =
            Paths.get(DummyIntegrationTest::class.java.getResource("/sequences/big/yf_sample_data/Ig1_S1.contigs.clns").file)
        val output = TempFileManager.getTempFile()
        output.delete()
        TestMain.execute("${CommandExportClones.COMMAND_NAME} -nFeature VDJRegion --prepend-columns $input ${output.path}")
        val columns = output.readLines().first().split("\t")
        columns shouldContainInOrder listOf("nSeqVDJRegion", "cloneId", "cloneCount")
    }

    @Test
    fun `no default fields`() {
        val input =
            Paths.get(DummyIntegrationTest::class.java.getResource("/sequences/big/yf_sample_data/Ig1_S1.contigs.clns").file)
        val output = TempFileManager.getTempFile()
        output.delete()
        TestMain.execute("${CommandExportClones.COMMAND_NAME} --drop-default-fields -nFeature VDJRegion $input ${output.path}")
        val columns = output.readLines().first().split("\t")
        columns shouldBe listOf("nSeqVDJRegion")
    }
}
