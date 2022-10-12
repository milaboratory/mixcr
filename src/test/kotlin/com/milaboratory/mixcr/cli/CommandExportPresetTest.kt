package com.milaboratory.mixcr.cli

import com.fasterxml.jackson.module.kotlin.readValue
import com.milaboratory.mitool.helpers.K_YAML_OM
import com.milaboratory.mixcr.MiXCRParamsBundle
import com.milaboratory.util.TempFileManager
import io.kotest.matchers.collections.shouldContainInOrder
import org.junit.Test

class CommandExportPresetTest {
    @Test
    fun `add several export fields`() {
        val output = TempFileManager.getTempFile()
        output.delete()
        val argString = "exportPreset +dna " +
                "+appendExportClonesField -aaFeature VDJRegion " +
                "+appendExportClonesField -aaFeature VRegion " +
                "+appendExportClonesField -aaFeature JRegion " +
                "tcr_shotgun ${output.path}"
        Main.main(*argString.split(" ").toTypedArray())
        val result = K_YAML_OM.readValue<MiXCRParamsBundle>(output)
        result.exportClones!!.fields
            .filter { it.field == "-aaFeature" }
            .map { it.args.toList() } shouldContainInOrder listOf("VDJRegion", "VRegion", "JRegion").map { listOf(it) }
    }
}
