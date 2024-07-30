/*
 * Copyright (c) 2014-2024, MiLaboratories Inc. All Rights Reserved
 *
 * Before downloading or accessing the software, please read carefully the
 * License Agreement available at:
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 *
 * By downloading or accessing the software, you accept and agree to be bound
 * by the terms of the License Agreement. If you do not want to agree to the terms
 * of the Licensing Agreement, you must not download or access the software.
 */
package com.milaboratory.mixcr.cli

import com.milaboratory.app.ValidationException
import com.milaboratory.app.logger
import com.milaboratory.cli.ParamsResolver
import com.milaboratory.cli.PresetAware
import com.milaboratory.mixcr.basictypes.HasFeatureToAlign
import com.milaboratory.mixcr.cli.CommonDescriptions.Labels
import com.milaboratory.mixcr.presets.AlignMixins
import com.milaboratory.mixcr.presets.AnalyzeCommandDescriptor.MiToolCommandDelegationDescriptor.parse
import com.milaboratory.mixcr.presets.AnalyzeCommandDescriptor.assemble
import com.milaboratory.mixcr.presets.AnalyzeCommandDescriptor.assembleCells
import com.milaboratory.mixcr.presets.AnalyzeCommandDescriptor.assembleContigs
import com.milaboratory.mixcr.presets.AssembleContigsMixins
import com.milaboratory.mixcr.presets.AssembleMixins
import com.milaboratory.mixcr.presets.Flags
import com.milaboratory.mixcr.presets.MiXCRMixin
import com.milaboratory.mixcr.presets.MiXCRParamsBundle
import com.milaboratory.mixcr.presets.PipelineMixins
import com.milaboratory.mixcr.presets.Presets
import com.milaboratory.mixcr.presets.RefineTagsAndSortMixins
import io.repseq.core.GeneFeature
import io.repseq.core.GeneFeature.CDR3
import io.repseq.core.GeneFeature.VDJRegion
import kotlin.reflect.KProperty1

abstract class MiXCRParamsResolver<P : Any>(
    paramsName: String, paramsProperty: MiXCRParamsBundle.() -> P?
) : ParamsResolver<MiXCRParamsBundle, P>(Presets.MiXCRBundleResolver, paramsName, paramsProperty) {
    constructor(paramsProperty: KProperty1<MiXCRParamsBundle, P?>) : this(paramsProperty.name, paramsProperty)

    override fun validateBundle(bundle: MiXCRParamsBundle) {
        if (bundle.flags.isNotEmpty()) {
            println("Preset errors: ")
            bundle.flags.forEach { flag ->
                println()
                println("- " + presetFlagsMessages[flag]!!.replace("\n", "\n  "))
            }
            println()

            throw ValidationException("Error validating preset bundle.");
        }
        val steps = bundle.pipeline?.steps ?: emptyList()
        if (assembleContigs in steps && bundle.assemble?.clnaOutput == false)
            throw ValidationException("assembleContigs step required clnaOutput=true on assemble step")

        bundle.align?.parameters?.featuresToAlignMap?.let { HasFeatureToAlign(it) }?.let { featuresToAlign ->
            if (assemble in steps) {
                CommandAssemble.validateParams(
                    bundle.assemble ?: throw ValidationException("no assemble params"),
                    featuresToAlign
                )
            }
            if (assembleContigs in steps) {
                CommandAssembleContigs.validateParams(
                    bundle.assembleContigs ?: throw ValidationException("no assembleContigs params"),
                    featuresToAlign
                )
            }
        }

        bundle.validation?.items?.forEach { validation ->
            validation.validate(bundle)
        }

        if (parse in steps) {
            val parseParams = bundle.mitool!!.parse!!
            val mitoolPattern = ValidationException.requireNotNull(parseParams.pattern) {
                "Tag pattern should be set in `mitool.parse.pattern`"
            }
            val alignParams = ValidationException.requireNotNull(bundle.align) {
                "Align parameters are not set"
            }
            val alignPattern = ValidationException.requireNotNull(alignParams.tagPattern) {
                "Tag pattern should be set in `align.tagPattern`"
            }
            ValidationException.require(mitoolPattern == alignPattern) {
                "Tag patterns are different in `mitool.parse.pattern` and `align.tagPattern`: $mitoolPattern and $alignPattern"
            }
            ValidationException.require(!alignParams.readIdAsCellTag) {
                "`readIdAsCellTag` is not supported with mitool commands in pipeline"
            }
            ValidationException.require(alignParams.headerExtractors.isEmpty()) {
                "`headerExtractors` are not supported with mitool commands in pipeline"
            }
            ValidationException.require(alignParams.tagTransformationSteps.isEmpty()) {
                "`tagTransformationSteps` are not supported with mitool commands in pipeline"
            }
            if (alignParams.parameters.isSaveOriginalReads) {
                logger.warn { "Saving original reads with mitool commands in pipeline will lead to saving reads after mitool processing, not original ones" }
            }
            if (alignParams.parameters.isSaveOriginalSequence) {
                logger.warn { "Saving original sequences with mitool commands in pipeline will lead to saving sequences after mitool processing, not original ones" }
            }

            bundle.mitool!!.refineTags?.let { refineTags ->
                ValidationException.requireEmpty(refineTags.dontCorrectTagsTypes) {
                    "With mitool refineTags command in pipeline, `${RefineTagsAndSortMixins.DontCorrectTagType.CMD_OPTION}` is not applicable, " +
                            "please use `${RefineTagsAndSortMixins.DontCorrectTagName.CMD_OPTION}` instead"
                }
            }
        }
    }
}

val presetFlagsMessages = mapOf(
    Flags.Species to
            "This preset requires to specify species, \n" +
            "please use the following mix-in: ${AlignMixins.SetSpecies.CMD_OPTION} <name>",
    Flags.MaterialType to
            "This preset requires to specify material type, \n" +
            "please use one of the following mix-ins: ${AlignMixins.MaterialTypeDNA.CMD_OPTION}, ${AlignMixins.MaterialTypeRNA.CMD_OPTION}",
    Flags.LeftAlignmentMode to
            "This preset requires to specify left side (V gene) alignment boundary mode, \n" +
            "please use one of the following mix-ins: \n" +
            "${AlignMixins.AlignmentBoundaryConstants.LEFT_FLOATING_CMD_OPTION} [${Labels.ANCHOR_POINT}]\n" +
            "${AlignMixins.AlignmentBoundaryConstants.LEFT_RIGID_CMD_OPTION} [${Labels.ANCHOR_POINT}]",
    Flags.RightAlignmentMode to
            "This preset requires to specify left side (V gene) alignment boundary mode, \n" +
            "please use one of the following mix-ins: \n" +
            "${AlignMixins.AlignmentBoundaryConstants.RIGHT_FLOATING_CMD_OPTION} (${Labels.GENE_TYPE}|${Labels.ANCHOR_POINT})\n" +
            "${AlignMixins.AlignmentBoundaryConstants.RIGHT_RIGID_CMD_OPTION} [(${Labels.GENE_TYPE}|${Labels.ANCHOR_POINT})]",

    Flags.TagPattern to
            "This preset requires to specify tag pattern, \n" +
            "please use ${AlignMixins.SetTagPattern.CMD_OPTION} mix-in to set it, alternatively " +
            "tag pattern can be provided with sample table using ${AlignMixins.SetSampleSheet.CMD_OPTION_FUZZY} or " +
            "${AlignMixins.SetSampleSheet.CMD_OPTION_STRICT} mixin.",

    Flags.SampleTable to
            "This preset requires to specify sample table, \n" +
            "please use ${AlignMixins.SetSampleSheet.CMD_OPTION_FUZZY} or " +
            "${AlignMixins.SetSampleSheet.CMD_OPTION_STRICT} mix-in.",

    Flags.AssembleClonesBy to
            "This preset requires to specify feature to assemble, \n" +
            "please use `${AssembleMixins.SetClonotypeAssemblingFeatures.CMD_OPTION} ${Labels.GENE_FEATURES}`, \n" +
            "for example `${AssembleMixins.SetClonotypeAssemblingFeatures.CMD_OPTION} ${GeneFeature.encode(CDR3)}`.",
    Flags.AssembleContigsBy to
            "This preset requires to specify feature to assemble contigs, \n" +
            "please use `${AssembleContigsMixins.SetContigAssemblingFeatures.CMD_OPTION} ${Labels.GENE_FEATURES}`, \n" +
            "for example `${AssembleContigsMixins.SetContigAssemblingFeatures.CMD_OPTION} ${GeneFeature.encode(VDJRegion)}`.",
    Flags.AssembleContigsByOrMaxLength to
            "This preset requires to specify feature to assemble contigs mode, \n" +
            "please use `${AssembleContigsMixins.SetContigAssemblingFeatures.CMD_OPTION} ${Labels.GENE_FEATURES}` or `${AssembleContigsMixins.AssembleContigsWithMaxLength.CMD_OPTION}`, \n" +
            "for example `${AssembleContigsMixins.SetContigAssemblingFeatures.CMD_OPTION} ${GeneFeature.encode(VDJRegion)}`.",
    Flags.AssembleContigsByOrByCell to
            "This preset requires to specify feature to assemble contigs by \n" +
            "`${AssembleContigsMixins.SetContigAssemblingFeatures.CMD_OPTION} ${Labels.GENE_FEATURES}` or " +
            " `${PipelineMixins.AssembleContigsByCells.CMD_OPTION}` that will cancel `${assembleCells.name}` step,\n" +
            "for example `${AssembleContigsMixins.SetContigAssemblingFeatures.CMD_OPTION} ${GeneFeature.encode(VDJRegion)}`",
)


interface MiXCRPresetAwareCommand<P : Any> : PresetAware<MiXCRParamsBundle, P>

interface MiXCRMixinCollection {
    val mixins: List<MiXCRMixin>

    operator fun plus(another: MiXCRMixinCollection?): MiXCRMixinCollection = when {
        another != null -> object : MiXCRMixinCollection {
            override val mixins: List<MiXCRMixin> = this@MiXCRMixinCollection.mixins + another.mixins
        }

        else -> this
    }

    operator fun plus(another: Collection<MiXCRMixinCollection>): MiXCRMixinCollection = when {
        another.isNotEmpty() -> object : MiXCRMixinCollection {
            override val mixins: List<MiXCRMixin> = this@MiXCRMixinCollection.mixins + another.flatMap { it.mixins }
        }

        else -> this
    }

    companion object {
        val empty = object : MiXCRMixinCollection {
            override val mixins: List<MiXCRMixin> = emptyList()
        }

        val Collection<MiXCRMixinCollection>.mixins get() = (empty + this).mixins
    }
}

interface MiXCRMixinRegister {
    fun mixIn(mixin: MiXCRMixin)
}

abstract class MiXCRMixinCollector : MiXCRMixinCollection, MiXCRMixinRegister {
    private val _mixins = mutableListOf<MiXCRMixin>()

    override fun mixIn(mixin: MiXCRMixin) {
        _mixins += mixin
    }

    override val mixins: List<MiXCRMixin> get() = _mixins.sorted()
}
