package com.milaboratory.mixcr

import com.fasterxml.jackson.module.kotlin.readValue
import com.milaboratory.cli.ParamsBundleSpecBaseAddress
import com.milaboratory.cli.ParamsBundleSpecBaseEmbedded
import com.milaboratory.mixcr.basictypes.tag.TagInfo
import com.milaboratory.mixcr.basictypes.tag.TagType
import com.milaboratory.mixcr.basictypes.tag.TagValueType
import com.milaboratory.mixcr.basictypes.tag.TagsInfo
import com.milaboratory.mixcr.cli.CommandAlignParams
import com.milaboratory.mixcr.cli.allClonesWillBeCoveredByFeature
import com.milaboratory.mixcr.cli.presetFlagsMessages
import com.milaboratory.mixcr.export.CloneFieldsExtractorsFactory
import com.milaboratory.mixcr.export.MetaForExport
import com.milaboratory.mixcr.export.VDJCAlignmentsFieldsExtractorsFactory
import com.milaboratory.mixcr.presets.AnalyzeCommandDescriptor
import com.milaboratory.mixcr.presets.AnalyzeCommandDescriptor.assembleCells
import com.milaboratory.mixcr.presets.AnalyzeCommandDescriptor.assembleContigs
import com.milaboratory.mixcr.presets.AssembleContigsMixins.AssembleContigsWithMaxLength
import com.milaboratory.mixcr.presets.Flags
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
import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual
import io.kotest.matchers.floats.shouldBeLessThanOrEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.repseq.core.GeneType.Variable
import org.junit.Assert
import org.junit.Ignore
import org.junit.Test
import java.nio.file.Paths
import kotlin.io.path.Path
import kotlin.io.path.bufferedWriter
import kotlin.io.path.listDirectoryEntries

class PresetsTest {
    @Test
    fun `check there are params for every step`() {
        for (presetName in Presets.nonAbstractPresetNames) {
            val bundle = Presets.MiXCRBundleResolver.resolvePreset(presetName)
            assertJson(K_OM, bundle, false)
            assertSoftly(presetName) {
                "pipeline must be set for all non-abstract presets".asClue {
                    bundle.pipeline shouldNotBe null
                }
                for (step in bundle.pipeline!!.steps) {
                    step.asClue {
                        "params for all pipeline steps must be set in non-abstract bundle".asClue {
                            step.extractFromBundle(bundle, 0) shouldNotBe null
                        }
                    }
                }
                bundle.flags.forEach { flag ->
                    flag.asClue {
                        presetFlagsMessages.containsKey(flag) shouldBe true
                    }
                }
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
            AnalyzeCommandDescriptor.exportClones !in bundle.pipeline!!.steps
        } shouldBe emptyList()
    }

    @Ignore
    @Test
    fun `all presets with export steps should have required depended steps`() {
        Presets.nonAbstractPresetNames.forEach { presetName ->
            presetName.asClue {
                val bundle = Presets.MiXCRBundleResolver.resolvePreset(presetName)
                bundle.pipeline!!.steps
                    .filterIsInstance<AnalyzeCommandDescriptor.ExportCommandDescriptor<*>>()
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
                assembleCells in bundle.pipeline!!.steps
            }
            .filter { presetName ->
                val bundle = Presets.MiXCRBundleResolver.resolvePreset(presetName)
                AnalyzeCommandDescriptor.exportCloneGroups !in bundle.pipeline!!.steps
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
    fun `all presets should have assemble feature or flag for it`() {
        Presets.visiblePresets
            .filter { presetName ->
                val bundle = Presets.MiXCRBundleResolver.resolvePreset(presetName)
                val hasFeature = bundle.assemble!!.cloneAssemblerParameters.assemblingFeatures != null
                val hasFlag = Flags.AssembleClonesBy in bundle.flags
                (hasFeature && hasFlag) || (!hasFeature && !hasFlag)
            } shouldBe emptyList()
    }

    @Test
    fun `all presets should have assemble contigs feature or flag for it`() {
        Presets.visiblePresets
            .filterNot { "gex" in it }
            .filterNot {
                val parent = Presets.rawResolve(it).inheritFrom
                parent != null && ("gex" in parent || parent == "shotgun-base")
            }
            .filter { presetName ->
                val bundle = Presets.MiXCRBundleResolver.resolvePreset(presetName)
                val steps = bundle.pipeline?.steps ?: emptyList()
                assembleContigs in steps
            }
            .filter { presetName ->
                val bundle = Presets.MiXCRBundleResolver.resolvePreset(presetName)
                val hasFeature = bundle.assembleContigs!!.parameters.allClonesWillBeCoveredByFeature()
                val assembleMaxLength =
                    Presets.rawResolve(presetName).mixins?.contains(AssembleContigsWithMaxLength) ?: false
                val hasFlag = Flags.AssembleContigsBy in bundle.flags ||
                        Flags.AssembleContigsByOrMaxLength in bundle.flags ||
                        Flags.AssembleContigsByOrByCell in bundle.flags
                !assembleMaxLength && ((hasFeature && hasFlag) || (!hasFeature && !hasFlag))
            } shouldBe emptyList()
    }

    @Test
    fun `all presets with assemble cells should have assemble contigs feature`() {
        Presets.visiblePresets
            .filter { presetName ->
                val bundle = Presets.MiXCRBundleResolver.resolvePreset(presetName)
                val steps = bundle.pipeline?.steps ?: emptyList()
                assembleCells in steps && assembleContigs in steps
                        && Flags.AssembleContigsBy !in bundle.flags && Flags.AssembleContigsByOrByCell !in bundle.flags
            }
            .forAll { presetName ->
                val bundle = Presets.MiXCRBundleResolver.resolvePreset(presetName)
                bundle.assembleContigs!!.parameters.allClonesWillBeCoveredByFeature() shouldBe true
            }
    }

    @Test
    fun `consistency of flags`() {
        "Presets should have only one AssembleContigsBy flag".asClue {
            Presets.visiblePresets
                .filter { presetName ->
                    val bundle = Presets.MiXCRBundleResolver.resolvePreset(presetName)
                    val flags = listOf(
                        Flags.AssembleContigsBy,
                        Flags.AssembleContigsByOrByCell,
                        Flags.AssembleContigsByOrMaxLength
                    ).filter {
                        it in bundle.flags
                    }
                    flags.size > 1
                } shouldBe emptyList()
        }
        "Presets with cell barcodes should not contain ${Flags.AssembleContigsBy} or ${Flags.AssembleContigsByOrMaxLength} flag".asClue {
            Presets.visiblePresets
                .filter { presetName ->
                    val bundle = Presets.MiXCRBundleResolver.resolvePreset(presetName)
                    bundle.align!!.tagsValidations
                        .filterIsInstance<CommandAlignParams.MustContainTagType>()
                        .any { it.tagType == TagType.Cell }
                }
                .filter { presetName ->
                    val bundle = Presets.MiXCRBundleResolver.resolvePreset(presetName)
                    Flags.AssembleContigsBy in bundle.flags || Flags.AssembleContigsByOrMaxLength in bundle.flags
                } shouldBe emptyList()
        }
        "Presets without cell barcodes should not contain ${Flags.AssembleContigsByOrByCell} flag".asClue {
            Presets.visiblePresets
                .filter { presetName ->
                    val bundle = Presets.MiXCRBundleResolver.resolvePreset(presetName)
                    bundle.align!!.tagsValidations
                        .filterIsInstance<CommandAlignParams.MustContainTagType>()
                        .none { it.tagType == TagType.Cell }
                }
                .filter { presetName ->
                    val bundle = Presets.MiXCRBundleResolver.resolvePreset(presetName)
                    Flags.AssembleContigsByOrByCell in bundle.flags
                } shouldBe emptyList()
        }
    }

    @Test
    fun `ranges of min relative scores`() = assertSoftly {
        val lowerBond = 0.8f
        val upperBond = 0.9f

        val exclusions = setOf("generic-ont", "generic-ont-with-umi")

        "Presets with `assembleContigs` should have lowered minRelativeScore for `assemble` step".asClue {
            (Presets.visiblePresets - exclusions)
                .filter { presetName ->
                    val bundle = Presets.MiXCRBundleResolver.resolvePreset(presetName)
                    assembleContigs in bundle.pipeline!!.steps
                }.forEach { presetName ->
                    val bundle = Presets.MiXCRBundleResolver.resolvePreset(presetName)
                    val alignParams = bundle.align!!.parameters
                    val assembleParams =
                        bundle.assemble!!.cloneAssemblerParameters.updateFrom(alignParams).cloneFactoryParameters
                    val assembleContigsParams = bundle.assembleContigs!!.cloneFactoryParameters?.clone()?.also {
                        // set defaults from align params
                        it.update(alignParams)
                    } ?: assembleParams

                    presetName.asClue {
                        assembleParams.getRelativeMinScore(Variable) shouldBeLessThanOrEqual lowerBond
                        assembleContigsParams.getRelativeMinScore(Variable) shouldBeGreaterThanOrEqual upperBond
                    }
                }
        }
        "Presets without `assembleContigs` should have high minRelativeScore for `assemble` step".asClue {
            Presets.visiblePresets
                .filter { presetName ->
                    val bundle = Presets.MiXCRBundleResolver.resolvePreset(presetName)
                    assembleContigs !in bundle.pipeline!!.steps
                }.forEach { presetName ->
                    val bundle = Presets.MiXCRBundleResolver.resolvePreset(presetName)
                    val alignParams = bundle.align!!.parameters
                    val assembleParams =
                        bundle.assemble!!.cloneAssemblerParameters.updateFrom(alignParams).cloneFactoryParameters

                    presetName.asClue {
                        assembleParams.getRelativeMinScore(Variable) shouldBeGreaterThanOrEqual upperBond
                    }
                }
        }
    }
}
