package com.milaboratory.mixcr

import org.junit.Assert.*
import org.junit.Test

class PresetsTest{
    @Test
    fun test1() {
        for (presetName in Presets.allPresetNames)
            println(presetName)
    }
}