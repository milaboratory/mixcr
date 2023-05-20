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
import com.milaboratory.mixcr.presets.MiXCRParamsBundle
import com.milaboratory.mixcr.presets.MiXCRStepReports
import com.milaboratory.mixcr.presets.Presets
import com.milaboratory.test.TestUtil.assertJson
import com.milaboratory.util.K_OM
import com.milaboratory.util.K_YAML_OM
import io.kotest.assertions.asClue
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.Assert
import org.junit.Test
import java.nio.file.Paths
import kotlin.io.path.Path
import kotlin.io.path.bufferedWriter
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readLines
import kotlin.io.path.readText

class PresetsTest {
    @Test
    fun `vdj online presets, all exist`() {
        val presets = readVdjOnlinePresets()
        "some presets from vdj_online_presets.json are not exist anymore".asClue {
            presets
                .map { it.presetName }
                .filterNot { it in Presets.visiblePresets }
                .sorted()
                .also { println(it) } shouldBe emptyList()
        }
    }

    @Test
    fun `vdj online presets, all visible presets included`() {
        val presets = readVdjOnlinePresets()
        val existInVdiOnline = presets
            .map { it.presetName }
            .toSet()
        val exclusions = Paths.get(PresetsTest::class.java.getResource("/exclusions_from_vdj_online.txt")!!.file)
            .readLines().toSet()
        "some presets from exclusion are not exists".asClue {
            exclusions.filterNot { it in Presets.visiblePresets } shouldBe emptyList()
        }
        "some presets from exclusion already in vdj_online_presets.json".asClue {
            exclusions.filter { it in existInVdiOnline } shouldBe emptyList()
        }
        "presets should be added to vdj_online_presets.json or to exclusions".asClue {
            Presets.visiblePresets
                .filterNot { it in exclusions }
                .filterNot { it in existInVdiOnline }
                .sorted()
                .also { println(it) } shouldBe emptyList()
        }
    }

    private fun readVdjOnlinePresets(): List<VdjOnlinePresetModel> {
        val source = PresetsTest::class.java.getResource("/vdj_online_presets.json")!!.file
        val formatted = Paths.get(source).readText().replace("/", "\\/")
        return K_OM.readValue<List<VdjOnlinePresetModel>>(formatted)
    }

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
            val bundle = Presets.MiXCRBundleResolver.resolvePreset(presetName)
            if (bundle.align == null)
                continue
            val tagsInfo = TagsInfo(
                0,
                TagInfo(TagType.Cell, TagValueType.Sequence, "CELL", 0),
                TagInfo(TagType.Cell, TagValueType.Sequence, "CELL1", 1),
                TagInfo(TagType.Cell, TagValueType.Sequence, "CELL2", 2),
                TagInfo(TagType.Cell, TagValueType.Sequence, "CELL3", 3),
                TagInfo(TagType.Cell, TagValueType.Sequence, "CELL1ROW", 4),
                TagInfo(TagType.Cell, TagValueType.Sequence, "CELL2COLUMN", 5),
                TagInfo(TagType.Cell, TagValueType.Sequence, "CELL3PLATE", 6),
                TagInfo(TagType.Molecule, TagValueType.Sequence, "UMI", 7),
                TagInfo(TagType.Molecule, TagValueType.Sequence, "UMI1", 8),
                TagInfo(TagType.Molecule, TagValueType.Sequence, "UMI2", 9),
                TagInfo(TagType.Molecule, TagValueType.Sequence, "UMI3", 10),
            )
            val metaForExport = MetaForExport(
                listOf(tagsInfo),
                null,
                MiXCRStepReports()
            )
            bundle.exportAlignments?.let { al ->
                println(
                    CloneFieldsExtractorsFactory.createExtractors(
                        al.fields,
                        metaForExport
                    ).size
                )
            }
            bundle.exportClones?.let { al ->
                println(
                    CloneFieldsExtractorsFactory.createExtractors(
                        al.fields, // .filter { !it.field.contains("tag", ignoreCase = true) }
                        metaForExport
                    ).size
                )
            }
        }
    }

    @Test
    fun `backward capability of deserialization of presets`() {
        val dir = Paths.get(PresetsTest::class.java.getResource("/backward_compatibility/presets/").file)
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

    data class VdjOnlinePresetModel(
        val text: String,
        val value: String,
        val company: String,
        val species: List<String>?,
        val singleR: Boolean?
    ) {
        val presetName: String get() = value.replace(Regex("#\\d"), "")
    }
}
