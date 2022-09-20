package com.milaboratory.mixcr

import com.milaboratory.mitool.helpers.K_OM
import com.milaboratory.mitool.helpers.K_YAML_OM
import com.milaboratory.mixcr.basictypes.MiXCRHeader
import com.milaboratory.mixcr.basictypes.tag.TagInfo
import com.milaboratory.mixcr.basictypes.tag.TagType
import com.milaboratory.mixcr.basictypes.tag.TagValueType
import com.milaboratory.mixcr.basictypes.tag.TagsInfo
import com.milaboratory.mixcr.export.CloneFieldsExtractorsFactory
import com.milaboratory.mixcr.export.OutputMode
import com.milaboratory.mixcr.export.VDJCAlignmentsFieldsExtractorsFactory
import com.milaboratory.test.TestUtil.assertJson
import org.junit.Assert
import org.junit.Test

class PresetsTest {
    @Test
    fun test1() {
        for (presetName in Presets.allPresetNames) {
            val bundle = Presets.resolveParamsBundle(presetName)
            println(presetName)
            assertJson(K_OM, bundle, true)
            bundle.flags.forEach {
                Assert.assertTrue("Flag = $it", Flags.flagMessages.containsKey(it))
            }
        }
    }

    @Test
    fun test3() {
        // val bundle = Presets.resolveParamsBundle("assemblePartial_universal")
        // val bundle = Presets.resolveParamsBundle("_universal")
        // val bundle = Presets.resolveParamsBundle("_10x_vdj")
        val bundle = Presets.resolveParamsBundle("10x_vdj_bcr")
        Presets.assemblePartial("10x_vdj_bcr")
        assertJson(K_YAML_OM, bundle, true)
    }

    @Test
    fun testExport2() {
        for (presetName in Presets.allPresetNames) {
            val bundle = Presets.resolveParamsBundle(presetName)
            if (bundle.align == null)
                continue
            val tagsInfo = TagsInfo(
                0,
                TagInfo(TagType.Cell, TagValueType.Sequence, "CELL", 0),
                TagInfo(TagType.Molecule, TagValueType.Sequence, "UMI", 1),
            )
            val header = MiXCRHeader(
                MiXCRParamsSpec(presetName), tagsInfo, bundle.align!!.parameters,
                null, null, null
            )
            bundle.exportAlignments?.let { al ->
                println(
                    VDJCAlignmentsFieldsExtractorsFactory.createExtractors(
                        al.fields.filter { !it.field.contains("tag", ignoreCase = true) },
                        header,
                        OutputMode.ScriptingFriendly
                    ).size
                )
            }
            bundle.exportClones?.let { al ->
                println(
                    CloneFieldsExtractorsFactory.createExtractors(
                        al.fields.filter { !it.field.contains("tag", ignoreCase = true) },
                        header,
                        OutputMode.ScriptingFriendly
                    ).size
                )
            }
        }
    }
}
