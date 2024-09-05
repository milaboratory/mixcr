package com.milaboratory.mixcr

import com.milaboratory.core.sequence.NSequenceWithQuality
import com.milaboratory.mitool.tag.SequenceAndQualityTagValue
import com.milaboratory.mitool.tag.StringTagValue
import com.milaboratory.mitool.tag.TagType.Cell
import com.milaboratory.mitool.tag.TagType.Sample
import com.milaboratory.mitool.tag.TagType.Technical
import com.milaboratory.mitool.tag.TagValueType.NonSequence
import com.milaboratory.mitool.tag.TagValueType.SequenceAndQuality
import com.milaboratory.mitool.tag.TechnicalTag
import com.milaboratory.mitool.tag.TagInfo
import com.milaboratory.mitool.tag.TagsInfo
import com.milaboratory.mixcr.presets.AlignMixins
import com.milaboratory.test.TestUtil
import com.milaboratory.util.K_YAML_OM
import io.repseq.core.ReferencePoint
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
        val slMixin = AlignMixins.SetSampleSheet(null, sampleList)
        Assert.assertTrue(slMixin.packed)
        TestUtil.assertJson(K_YAML_OM, slMixin, false)
        val parsed = slMixin.parse()
        Assert.assertEquals(
            "^attagaca\\^attacaca(CELL1:NNNN)||^attagaca\\^gacatata(CELL1:NNNN)".replace(" ", ""),
            parsed.tagPattern!!.replace(" ", "")
        )

        val transformation = parsed.createTagTransformationStep(false)

        val transformer = transformation.createTransformer(
            TagsInfo(
                0,
                TagInfo(Cell, SequenceAndQuality, "CELL1", 0),
                TagInfo(Technical, NonSequence, TechnicalTag.TAG_PATTERN_READ_VARIANT_ID, 1)
            )
        )

        Assert.assertEquals(
            TagsInfo(
                0,
                TagInfo(Sample, NonSequence, "Sample", 0),
            ),
            transformer.outputTagsInfo
        )

        listOf(
            listOf("ATTG", "0", "S1"),
            listOf("ACCC", "0", "S2"),
            listOf("ATTG", "1", "S3"),
            listOf("ACCC", "1", "S4")
        ).forEach { testValue ->
            Assert.assertArrayEquals(
                arrayOf(
                    StringTagValue(testValue[2])
                ),
                transformer.transform(
                    arrayOf(
                        SequenceAndQualityTagValue(NSequenceWithQuality(testValue[0])),
                        StringTagValue(testValue[1])
                    )
                )
            )
        }
    }

    @Test
    fun mixinSerializationTest1() {
        TestUtil.assertJson(K_YAML_OM, AlignMixins.LeftAlignmentBoundaryWithPoint(true, ReferencePoint.FR1Begin), true)
    }
}
