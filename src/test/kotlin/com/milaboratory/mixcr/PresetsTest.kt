package com.milaboratory.mixcr

import com.milaboratory.mitool.helpers.K_OM
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
}