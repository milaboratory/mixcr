package com.milaboratory.mixcr

import com.milaboratory.mitool.helpers.K_OM
import com.milaboratory.mitool.helpers.K_YAML_OM
import com.milaboratory.mixcr.basictypes.MiXCRHeader
import com.milaboratory.mixcr.basictypes.tag.TagInfo
import com.milaboratory.mixcr.basictypes.tag.TagType
import com.milaboratory.mixcr.basictypes.tag.TagValueType
import com.milaboratory.mixcr.basictypes.tag.TagsInfo
import com.milaboratory.mixcr.export.CloneFieldsExtractorsFactory
import com.milaboratory.test.TestUtil.assertJson
import org.junit.Assert
import org.junit.Test

class PresetsTest {
    @Test
    fun test1() {
        for (presetName in Presets.nonAbstractPresetNames) {
            println(presetName)
            val bundle = Presets.resolveParamsBundle(presetName)
            assertJson(K_OM, bundle, false)
            println(bundle.flags)
            println()
            Assert.assertNotNull("pipeline must be set for all non-abstract presets ($presetName)", bundle.pipeline)
            for (step in bundle.pipeline!!.steps) {
                Assert.assertNotNull(
                    "params for all pipeline steps must be set in non-abstract bundle ($step)",
                    step.extractFromBundle(bundle)
                )
            }
            bundle.flags.forEach {
                Assert.assertTrue("Flag = $it", Flags.flagMessages.containsKey(it))
            }
        }
    }

    @Test
    fun test3() {
        // val bundle = Presets.resolveParamsBundle("assemblePartial-universal")
        // val bundle = Presets.resolveParamsBundle("simple-base")
        // val bundle = Presets.resolveParamsBundle("_10x_vdj")
        val bundle = Presets.resolveParamsBundle("test-subCloningRegions")
        Presets.assembleContigs("test-subCloningRegions")
        assertJson(K_YAML_OM, bundle, true)
    }

    @Test
    fun testExport2() {
        for (presetName in Presets.nonAbstractPresetNames) {
            val bundle = Presets.resolveParamsBundle(presetName)
            if (bundle.align == null)
                continue
            val tagsInfo = TagsInfo(
                0,
                TagInfo(TagType.Cell, TagValueType.Sequence, "CELL", 0),
                TagInfo(TagType.Cell, TagValueType.Sequence, "CELL1", 1),
                TagInfo(TagType.Cell, TagValueType.Sequence, "CELL2", 2),
                TagInfo(TagType.Cell, TagValueType.Sequence, "CELL3", 3),
                TagInfo(TagType.Cell, TagValueType.Sequence, "CELL1ROW", 4),
                TagInfo(TagType.Cell, TagValueType.Sequence, "CELL2COLUMN", 5),
                TagInfo(TagType.Cell, TagValueType.Sequence, "CELL3PLATE", 6),
                TagInfo(TagType.Molecule, TagValueType.Sequence, "UMI", 7),
                TagInfo(TagType.Molecule, TagValueType.Sequence, "UMI1", 8),
                TagInfo(TagType.Molecule, TagValueType.Sequence, "UMI2", 9),
                TagInfo(TagType.Molecule, TagValueType.Sequence, "UMI3", 10),
            )
            val header = MiXCRHeader(
                "hashA123",
                MiXCRParamsSpec(presetName),
                MiXCRStepParams().add(MiXCRCommandDescriptor.align, bundle.align!!),
                tagsInfo,
                bundle.align!!.parameters,
                null,
                null,
                null
            )
            bundle.exportAlignments?.let { al ->
                println(
                    CloneFieldsExtractorsFactory.createExtractors(
                        al.fields,
                        header
                    ).size
                )
            }
            bundle.exportClones?.let { al ->
                println(
                    CloneFieldsExtractorsFactory.createExtractors(
                        al.fields, // .filter { !it.field.contains("tag", ignoreCase = true) }
                        header
                    ).size
                )
            }
        }
    }
}
