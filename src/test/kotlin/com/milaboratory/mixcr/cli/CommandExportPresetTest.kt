package com.milaboratory.mixcr.cli

import com.fasterxml.jackson.module.kotlin.readValue
import com.milaboratory.mixcr.export.ExportFieldDescription
import com.milaboratory.mixcr.presets.MiXCRCommandDescriptor
import com.milaboratory.mixcr.presets.MiXCRParamsBundle
import com.milaboratory.util.K_YAML_OM
import com.milaboratory.util.TempFileManager
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.repseq.core.GeneFeature.VDJRegion
import org.junit.Test

class CommandExportPresetTest {
    @Test
    fun `add several export fields`() {
        val output = TempFileManager.newTempDir().toPath().resolve("output.yaml").toFile()
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
    fun `add field with default`() {
        val output = TempFileManager.newTempDir().toPath().resolve("output.yaml").toFile()
        output.delete()
        TestMain.execute(
            "exportPreset --species hs --dna " +
                    "--append-export-clones-field -allAAFeatures " +
                    "--preset-name test-tcr-shotgun ${output.path}"
        )
        val result = K_YAML_OM.readValue<MiXCRParamsBundle>(output)
        result.exportClones!!.fields shouldContain ExportFieldDescription("-allAAFeatures")
    }

    @Test
    fun `add assemble contig step`() {
        val output = TempFileManager.newTempDir().toPath().resolve("output.yaml").toFile()
        output.delete()
        TestMain.execute("exportPreset --species hs --dna --add-step assembleContigs --preset-name test-tcr-shotgun ${output.path}")
        val result = K_YAML_OM.readValue<MiXCRParamsBundle>(output)
        result.pipeline!!.steps shouldContain MiXCRCommandDescriptor.assembleContigs
        result.assemble!!.clnaOutput shouldBe true
    }

    @Test
    fun `remove two steps`() {
        val output = TempFileManager.newTempDir().toPath().resolve("output.yaml").toFile()
        output.delete()
        TestMain.execute("exportPreset --species hs --dna --remove-step exportClones --remove-step exportAlignments --preset-name test-tcr-shotgun ${output.path}")
        val result = K_YAML_OM.readValue<MiXCRParamsBundle>(output)
        result.pipeline!!.steps shouldNotContain MiXCRCommandDescriptor.exportClones
        result.pipeline!!.steps shouldNotContain MiXCRCommandDescriptor.exportAlignments
    }

    @Test
    fun `test that all mixins are applied`() {
        val output = TempFileManager.newTempDir().toPath().resolve("output.yaml").toFile()
        output.delete()
        TestMain.execute(
            "exportPreset --preset-name generic-bcr-amplicon-umi " +
                    "--species hsa --rna " +
                    "--remove-step exportClones --remove-step exportAlignments " +
                    "--tag-pattern ^(R1F:N{0:2}(C:gggggaaaagggttg)(R1:*)) " +
                    "-M align.tagUnstranded=true " +
                    "-M align.limit=1000 " +
                    "--floating-left-alignment-boundary --floating-right-alignment-boundary C " +
                    "--assemble-clonotypes-by VDJRegion " +
                    "--split-clones-by C " +
                    output.path
        )
        val result = K_YAML_OM.readValue<MiXCRParamsBundle>(output)
        result.flags shouldBe emptySet()
        result.align!!.species shouldBe "hsa"
        result.pipeline!!.steps shouldNotContain MiXCRCommandDescriptor.exportClones
        result.pipeline!!.steps shouldNotContain MiXCRCommandDescriptor.exportAlignments
        result.align!!.tagPattern shouldBe "^(R1F:N{0:2}(C:gggggaaaagggttg)(R1:*))"
        result.align!!.tagUnstranded shouldBe true
        result.align!!.limit shouldBe 1000
        result.align!!.parameters.vAlignerParameters.parameters.isFloatingLeftBound shouldBe true
        result.align!!.parameters.cAlignerParameters.parameters.isFloatingRightBound shouldBe true
        result.align!!.parameters.jAlignerParameters.parameters.isFloatingRightBound shouldBe false
        result.assemble!!.cloneAssemblerParameters.assemblingFeatures shouldContainExactly arrayOf(VDJRegion)
        result.assemble!!.cloneAssemblerParameters.separateByC shouldBe true
    }
}
