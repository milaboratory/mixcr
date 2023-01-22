package com.milaboratory.mixcr

import com.milaboratory.mitool.helpers.K_YAML_OM
import com.milaboratory.mixcr.cli.CommandAlign
import com.milaboratory.test.TestUtil
import org.junit.Assert
import org.junit.Test

class MixinsTest {
    @Test
    fun name1() {
        val sampleList = "Sample\tTagPattern\tCELL1\n" +
                "S1\t^attagaca \\ ^attacaca(CELL1:NNNN)\tATTG\n" +
                "S2\t^attagaca \\ ^attacaca(CELL1:NNNN)\tACCC\n" +
                "S3\t^attagaca \\ ^gacatata(CELL1:NNNN)\tATTG\n" +
                "S4\t^attagaca \\ ^gacatata(CELL1:NNNN)\tACCC\n"
        val slMixin = AlignMixins.SetSampleTable(null, sampleList)
        Assert.assertTrue(slMixin.packed)
        TestUtil.assertJson(K_YAML_OM, slMixin, false)
        val parsed = slMixin.parse()
        Assert.assertEquals(
            "^attagaca\\^attacaca(CELL1:NNNN)||^attagaca\\^gacatata(CELL1:NNNN)".replace(" ", ""),
            parsed.tagPattern!!.replace(" ", "")
        )
        Assert.assertEquals(
            parsed.sampleTable,
            CommandAlign.SampleTable(
                listOf("Sample"),
                listOf(
                    CommandAlign.SampleTableRow(sortedMapOf("CELL1" to "ATTG"), 0, listOf("S1")),
                    CommandAlign.SampleTableRow(sortedMapOf("CELL1" to "ACCC"), 0, listOf("S2")),
                    CommandAlign.SampleTableRow(sortedMapOf("CELL1" to "ATTG"), 1, listOf("S3")),
                    CommandAlign.SampleTableRow(sortedMapOf("CELL1" to "ACCC"), 1, listOf("S4")),
                )
            )
        )
    }
}