package com.milaboratory.mixcr

import com.fasterxml.jackson.module.kotlin.readValue
import com.milaboratory.cli.ParamsBundleSpecBaseAddress
import com.milaboratory.cli.ParamsBundleSpecBaseEmbedded
import com.milaboratory.mixcr.basictypes.tag.TagInfo
import com.milaboratory.mixcr.basictypes.tag.TagType
import com.milaboratory.mixcr.basictypes.tag.TagValueType
import com.milaboratory.mixcr.basictypes.tag.TagsInfo
import com.milaboratory.mixcr.cli.presetFlagsMessages
import com.milaboratory.mixcr.export.CloneFieldsExtractorsFactory
import com.milaboratory.mixcr.export.MetaForExport
import com.milaboratory.mixcr.export.VDJCAlignmentsFieldsExtractorsFactory
import com.milaboratory.mixcr.presets.MiXCRCommandDescriptor
import com.milaboratory.mixcr.presets.MiXCRParamsBundle
import com.milaboratory.mixcr.presets.MiXCRPresetCategory
import com.milaboratory.mixcr.presets.Presets
import com.milaboratory.test.TestUtil.assertJson
import com.milaboratory.util.K_OM
import com.milaboratory.util.K_YAML_OM
import io.kotest.assertions.asClue
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.withClue
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldContainAnyOf
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.repseq.core.GeneFeature
import org.junit.Assert
import org.junit.Test
import java.nio.file.Paths
import kotlin.io.path.Path
import kotlin.io.path.bufferedWriter
import kotlin.io.path.listDirectoryEntries

class PresetsTest {
    @Test
    fun test1() {
        for (presetName in Presets.nonAbstractPresetNames) {
            println(presetName)
            val bundle = Presets.MiXCRBundleResolver.resolvePreset(presetName)
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
                Assert.assertTrue("Flag = $it", presetFlagsMessages.containsKey(it))
            }
        }
    }

    @Test
    fun test2XConsistentHash() {
        val mapOfHashes = mutableMapOf<String, MutableList<String>>()
        for (presetName in Presets.nonAbstractPresetNames) {
            val bundle = Presets.MiXCRBundleResolver.resolvePreset(presetName)
            val bundleJson = K_OM.writeValueAsString(bundle)
            val bundleDeserialized = K_OM.readValue(bundleJson, MiXCRParamsBundle::class.java)
            Assert.assertEquals(bundle.hashCode(), bundleDeserialized.hashCode())
            val asAddressSpec = ParamsBundleSpecBaseAddress<MiXCRParamsBundle>(presetName)
            val asEmbeddedSpec = ParamsBundleSpecBaseEmbedded(bundle)
            for (hash in listOf(asAddressSpec.consistentHashString(), asEmbeddedSpec.consistentHashString()))
                mapOfHashes.compute(hash) { _, b ->
                    val l = (b ?: mutableListOf())
                    l += presetName
                    l
                }
        }
        Path("hash_projection.txt").bufferedWriter().use { writer ->
            mapOfHashes.forEach { (hash, presets) ->
                presets.forEach { preset ->
                    writer.write("$hash\t$preset\n")
                }
            }
        }
    }

    @Test
    fun test3() {
        // val bundle = Presets.resolveParamsBundle("assemblePartial-universal")
        // val bundle = Presets.resolveParamsBundle("simple-base")
        // val bundle = Presets.resolveParamsBundle("_10x_vdj")
        val bundle = Presets.MiXCRBundleResolver.resolvePreset("umi-guided-consensus-test")
        // Presets.assemble("umi-guided-consensus-test")
        assertJson(K_YAML_OM, bundle, true)
    }

    @Test
    fun testExport2() {
        for (presetName in Presets.nonAbstractPresetNames) {
            presetName.asClue {
                shouldNotThrowAny {
                    val bundle = Presets.MiXCRBundleResolver.resolvePreset(presetName)
                    val tagsInfo = TagsInfo(
                        0,
                        TagInfo(TagType.Cell, TagValueType.Sequence, "CELL", 0),
                        TagInfo(TagType.Cell, TagValueType.Sequence, "CELL1", 1),
                        TagInfo(TagType.Cell, TagValueType.Sequence, "CELL1ROW", 2),
                        TagInfo(TagType.Cell, TagValueType.Sequence, "CELL2", 3),
                        TagInfo(TagType.Cell, TagValueType.Sequence, "CELL2COLUMN", 4),
                        TagInfo(TagType.Cell, TagValueType.Sequence, "CELL3", 5),
                        TagInfo(TagType.Cell, TagValueType.Sequence, "CELL3PLATE", 6),
                        TagInfo(TagType.Molecule, TagValueType.Sequence, "UMI", 7),
                        TagInfo(TagType.Molecule, TagValueType.Sequence, "UMI1", 8),
                        TagInfo(TagType.Molecule, TagValueType.Sequence, "UMI2", 9),
                        TagInfo(TagType.Molecule, TagValueType.Sequence, "UMI3", 10),
                    )
                    val metaForExport = MetaForExport(
                        listOf(tagsInfo),
                        null,
                        emptyList(),
                        false
                    )
                    bundle.exportAlignments?.let { al ->
                        VDJCAlignmentsFieldsExtractorsFactory.createExtractors(
                            al.fields,
                            metaForExport
                        ).size
                    }
                    bundle.exportClones?.let { al ->
                        CloneFieldsExtractorsFactory.createExtractors(
                            al.fields, // .filter { !it.field.contains("tag", ignoreCase = true) }
                            metaForExport
                        ).size
                    }
                }
            }
        }
    }

    @Test
    fun `backward capability of deserialization of presets`() {
        val dir = Paths.get(PresetsTest::class.java.getResource("/backward_compatibility/presets/")!!.file)
        dir.listDirectoryEntries()
            .flatMap { it.listDirectoryEntries() }
            .forEach { filesToCheck ->
                withClue(filesToCheck) {
                    shouldNotThrowAny {
                        K_YAML_OM.readValue<MiXCRParamsBundle>(filesToCheck.toFile())
                    }
                }
            }
    }

    @Test
    fun `all presets should have exportClones step`() {
        Presets.nonAbstractPresetNames.filter { presetName ->
            val bundle = Presets.MiXCRBundleResolver.resolvePreset(presetName)
            MiXCRCommandDescriptor.exportClones !in bundle.pipeline!!.steps
        } shouldBe emptyList()
    }

    @Test
    fun `all presets with export steps should have required depended steps`() {
        Presets.nonAbstractPresetNames.forEach { presetName ->
            presetName.asClue {
                val bundle = Presets.MiXCRBundleResolver.resolvePreset(presetName)
                bundle.pipeline!!.steps
                    .filterIsInstance<MiXCRCommandDescriptor.ExportCommandDescriptor<*>>()
                    .forEach { exportStep ->
                        exportStep.command.asClue {
                            bundle.pipeline!!.steps shouldContainAnyOf exportStep.runAfterLastOf()
                        }
                    }
            }
        }
    }

    @Test
    fun `all presets with group clones should have exportCloneGroups step`() {
        Presets.nonAbstractPresetNames
            .filter { presetName ->
                val bundle = Presets.MiXCRBundleResolver.resolvePreset(presetName)
                MiXCRCommandDescriptor.groupClones in bundle.pipeline!!.steps
            }
            .filter { presetName ->
                val bundle = Presets.MiXCRBundleResolver.resolvePreset(presetName)
                MiXCRCommandDescriptor.exportCloneGroups !in bundle.pipeline!!.steps
            } shouldBe emptyList()
    }

    @Test
    fun `all presets should have settings for exportAlignments`() {
        Presets.nonAbstractPresetNames
            .filter { !it.contains("-legacy-v") }
            .filter { presetName ->
                val bundle = Presets.MiXCRBundleResolver.resolvePreset(presetName)
                bundle.exportAlignments == null
            } shouldBe emptyList()
    }

    @Test
    fun `all visible presets should have label, etc`() {
        Presets.visiblePresets.forAll { presetName ->
            assertSoftly {
                val bundle = Presets.rawResolve(presetName)
                "category".asClue {
                    bundle.resolvedCategory.shouldNotBeNull()
                }
                "label (the property will not inherit)".asClue {
                    bundle.label.shouldNotBeNull()
                }
                if (bundle.resolvedCategory == MiXCRPresetCategory.`non-generic`) {
                    "vendor".asClue {
                        bundle.resolvedVendor.shouldNotBeNull()
                    }
                }
            }
        }
    }

    @Test
    fun `should be no split by VJ if VDJRegion set as assemble feature`() {
        Presets.visiblePresets.filter { presetName ->
            val bundle = Presets.MiXCRBundleResolver.resolvePreset(presetName)
            val assemble = bundle.assemble ?: return@filter false
            if (assemble.cloneAssemblerParameters.assemblingFeatures.toList() != listOf(GeneFeature.VDJRegion))
                return@filter false
            assemble.cloneAssemblerParameters.separateByV || assemble.cloneAssemblerParameters.separateByJ
        }.also { println(it) } shouldBe emptyList()
    }
}
