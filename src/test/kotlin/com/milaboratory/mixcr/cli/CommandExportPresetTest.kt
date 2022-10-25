package com.milaboratory.mixcr.cli

import com.fasterxml.jackson.module.kotlin.readValue
import com.milaboratory.mitool.helpers.K_YAML_OM
import com.milaboratory.mixcr.MiXCRCommandDescriptor
import com.milaboratory.mixcr.MiXCRParamsBundle
import com.milaboratory.util.TempFileManager
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.shouldBe
import org.junit.Test

class CommandExportPresetTest {
    @Test
    fun `add several export fields`() {
        val output = TempFileManager.getTempDir().toPath().resolve("output.yaml").toFile()
        output.delete()
        TestMain.execute(
            "exportPreset --species hs --dna " +
                    "--append-export-clones-field -aaFeature VDJRegion " +
                    "--append-export-clones-field -aaFeature VRegion " +
                    "--append-export-clones-field -aaFeature JRegion " +
                    "--preset-name test-tcr-shotgun ${output.path}"
        )
        val result = K_YAML_OM.readValue<MiXCRParamsBundle>(output)
        result.exportClones!!.fields
            .filter { it.field == "-aaFeature" }
            .map { it.args.toList() } shouldContainInOrder listOf("VDJRegion", "VRegion", "JRegion").map { listOf(it) }
    }

    @Test
    fun `add assemble contig step`() {
        val output = TempFileManager.getTempDir().toPath().resolve("output.yaml").toFile()
        output.delete()
        TestMain.execute("exportPreset --species hs --dna --add-step assembleContigs --preset-name test-tcr-shotgun ${output.path}")
        val result = K_YAML_OM.readValue<MiXCRParamsBundle>(output)
        result.pipeline!!.steps shouldContain MiXCRCommandDescriptor.assembleContigs
        result.assemble!!.clnaOutput shouldBe true
    }
}
