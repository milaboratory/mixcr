package com.milaboratory.mixcr

import com.milaboratory.mitool.helpers.K_OM
import com.milaboratory.mixcr.basictypes.VDJCFileHeaderData
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
            bundle.exportAlignments?.let { al ->
                println(
                    VDJCAlignmentsFieldsExtractorsFactory.createExtractors(
                        al.fields,
                        object : VDJCFileHeaderData {
                            override val tagsInfo: TagsInfo
                                get() = TODO("Not yet implemented")
                        },
                        OutputMode.ScriptingFriendly
                    ).size
                )
            }
            bundle.exportClones?.let { al ->
                println(
                    CloneFieldsExtractorsFactory.createExtractors(
                        al.fields,
                        object : VDJCFileHeaderData {
                            override val tagsInfo: TagsInfo
                                get() = TODO("Not yet implemented")
                        },
                        OutputMode.ScriptingFriendly
                    ).size
                )
            }
        }
    }
}