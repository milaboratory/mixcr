package com.milaboratory.mixcr

import com.milaboratory.mitool.helpers.K_OM
import com.milaboratory.mixcr.basictypes.MiXCRHeader
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
    fun testExport2() {
        for (presetName in Presets.allPresetNames) {
            val bundle = Presets.resolveParamsBundle(presetName)
            if (bundle.align == null)
                continue
            val header = MiXCRHeader(
                MiXCRParamsSpec(presetName), TagsInfo.NO_TAGS, bundle.align!!.parameters,
                null, null, null
            )
            bundle.exportAlignments?.let { al ->
                println(
                    VDJCAlignmentsFieldsExtractorsFactory.createExtractors(
                        al.fields,
                        header,
                        OutputMode.ScriptingFriendly
                    ).size
                )
            }
            bundle.exportClones?.let { al ->
                println(
                    CloneFieldsExtractorsFactory.createExtractors(
                        al.fields,
                        header,
                        OutputMode.ScriptingFriendly
                    ).size
                )
            }
        }
    }
}