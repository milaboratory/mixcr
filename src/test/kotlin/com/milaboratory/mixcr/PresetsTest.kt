package com.milaboratory.mixcr

import com.milaboratory.mitool.helpers.K_OM
import com.milaboratory.test.TestUtil.assertJson
import org.junit.Test

class PresetsTest {
    @Test
    fun test1() {
        for (presetName in Presets.allPresetNames)
            assertJson(K_OM, Presets.resolveParamsBundle(presetName), true)
    }
}