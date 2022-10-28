/*
 * Copyright (c) 2014-2022, MiLaboratories Inc. All Rights Reserved
 *
 * Before downloading or accessing the software, please read carefully the
 * License Agreement available at:
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 *
 * By downloading or accessing the software, you accept and agree to be bound
 * by the terms of the License Agreement. If you do not want to agree to the terms
 * of the Licensing Agreement, you must not download or access the software.
 */
package com.milaboratory.mixcr

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import com.milaboratory.cli.Mixin
import com.milaboratory.cli.POverride
import com.milaboratory.cli.POverridesBuilderDsl
import com.milaboratory.cli.POverridesBuilderOps
import com.milaboratory.cli.POverridesBuilderOpsAbstract
import com.milaboratory.mixcr.assembler.CloneAssemblerParameters
import com.milaboratory.mixcr.assembler.fullseq.FullSeqAssemblerParameters
import com.milaboratory.mixcr.assembler.fullseq.PostFiltering
import com.milaboratory.mixcr.basictypes.GeneFeatures
import com.milaboratory.mixcr.cli.CommandAlign
import com.milaboratory.mixcr.cli.CommandAssemble
import com.milaboratory.mixcr.cli.CommandAssembleContigs
import com.milaboratory.mixcr.cli.CommandExportAlignments
import com.milaboratory.mixcr.cli.CommandExportClones
import com.milaboratory.mixcr.export.CloneFieldsExtractorsFactory
import com.milaboratory.mixcr.export.ExportFieldDescription
import com.milaboratory.mixcr.export.FieldExtractorsFactory
import com.milaboratory.mixcr.export.VDJCAlignmentsFieldsExtractorsFactory
import com.milaboratory.mixcr.vdjaligners.KGeneAlignmentParameters
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters
import io.repseq.core.GeneFeature
import io.repseq.core.GeneType
import io.repseq.core.GeneType.Constant
import io.repseq.core.GeneType.Joining
import io.repseq.core.GeneType.Variable
import io.repseq.core.ReferencePoint

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
sealed interface MiXCRMixin : Mixin<MiXCRParamsBundle>, Comparable<MiXCRMixin> {
    /** Returns command line arguments for this mixin */
    val cmdArgs: List<String>

    /** Mixins with higher importance are executed later in the modification chain */
    override fun compareTo(other: MiXCRMixin) = importance.compareTo(other.importance)
}

@POverridesBuilderDsl
interface MixinBuilderOps : POverridesBuilderOps<MiXCRParamsBundle> {
    fun dropFlag(flagName: String) = MiXCRParamsBundle::flags.updateBy { it - flagName }
}

//
// Generic helper methods
//

private fun MixinBuilderOps.modifyAlignmentParams(action: VDJCAlignerParameters.() -> Unit) =
    MiXCRParamsBundle::align.update {
        CommandAlign.Params::parameters.applyAfterClone(VDJCAlignerParameters::clone) {
            action()
        }
    }

private fun MixinBuilderOps.modifyGeneAlignmentParams(gt: GeneType, action: KGeneAlignmentParameters.() -> Unit) =
    modifyAlignmentParams {
        val p = getVJCGeneAlignerParameters(gt)
        p.action()
    }

sealed class MiXCRMixinBase(
    @JsonIgnore final override val importance: Int,
    @JsonIgnore private val flags: List<String>
) : MiXCRMixin {
    constructor(priority: Int, vararg flags: String) : this(priority, flags.asList())

    abstract fun MixinBuilderOps.action()

    override fun apply(target: MiXCRParamsBundle): MiXCRParamsBundle {
        val overrides = mutableListOf<POverride<MiXCRParamsBundle>>()
        val builderTarget = object : POverridesBuilderOpsAbstract<MiXCRParamsBundle>(), MixinBuilderOps {
            override fun addOverride(override: POverride<MiXCRParamsBundle>) {
                overrides += override
            }
        }
        builderTarget.apply {
            flags.forEach { dropFlag(it) }
            action()
        }
        return overrides.fold(target) { acc, o -> o.apply(acc) }
    }
}

// ==========================
//           Mixins
// ==========================

@JsonTypeName("GenericMixin")
data class GenericMixin(
    @JsonProperty("fieldAddress") val fieldAddress: String,
    @JsonProperty("newValue") val newValue: String,
) : MiXCRMixinBase(100) {
    override val cmdArgs get() = listOf(CMD_OPTION, "${fieldAddress}=${newValue}")

    override fun MixinBuilderOps.action() {
        jsonOverrideWith(mapOf(fieldAddress to newValue))
    }

    companion object {
        const val CMD_OPTION = "-M"
    }
}

object AlignMixins {
    @JsonTypeName("SetSpecies")
    data class SetSpecies(
        @JsonProperty("species") val species: String
    ) : MiXCRMixinBase(50, Flags.Species) {
        override fun MixinBuilderOps.action() {
            MiXCRParamsBundle::align.update {
                CommandAlign.Params::species setTo species
            }
        }

        override val cmdArgs get() = listOf(CMD_OPTION, species)

        companion object {
            const val CMD_OPTION_ALIAS = "-s"
            const val CMD_OPTION = "--species"
        }
    }

    @JsonTypeName("SetLibrary")
    data class SetLibrary(
        @JsonProperty("library") val library: String
    ) : MiXCRMixinBase(50) {
        override fun MixinBuilderOps.action() {
            MiXCRParamsBundle::align.update {
                CommandAlign.Params::library setTo library
            }
        }

        override val cmdArgs get() = listOf(CMD_OPTION, library)

        companion object {
            const val CMD_OPTION_ALIAS = "-b"
            const val CMD_OPTION = "--library"
        }
    }

    @JsonTypeName("MaterialTypeDNA")
    object MaterialTypeDNA : MiXCRMixinBase(20, Flags.MaterialType) {
        override fun MixinBuilderOps.action() =
            modifyAlignmentParams {
                // Aligning V gene with intron sequence
                vAlignerParameters.geneFeatureToAlign = GeneFeature.VGeneWithP

                // TODO consider adding genomic sequences downstream J ( or add another mix-in for that ? )

                // C gene is too far from CDR3 in DNA data,
                // there is no need in spending time searching for it
                // NOTE: If such behaviour is still needed, other parameters should also be considered and changed,
                // to achieve expected results
                setGeneAlignerParameters(Constant, null)
            }

        override val cmdArgs get() = listOf(CMD_OPTION)

        const val CMD_OPTION = "--dna"
    }

    @JsonTypeName("MaterialTypeRNA")
    object MaterialTypeRNA : MiXCRMixinBase(20, Flags.MaterialType) {
        override fun MixinBuilderOps.action() =
            modifyGeneAlignmentParams(Variable) {
                // V gene without intron sequence, including 5'UTR sequence
                geneFeatureToAlign = GeneFeature.VTranscriptWithP
            }

        override val cmdArgs get() = listOf(CMD_OPTION)

        const val CMD_OPTION = "--rna"
    }

    @JsonTypeName("LimitInput")
    data class LimitInput(
        @JsonProperty("number") val number: Long
    ) : MiXCRMixinBase(10) {
        override val cmdArgs: List<String>
            get() = listOf(CMD_OPTION, number.toString())

        override fun MixinBuilderOps.action() {
            MiXCRParamsBundle::align.update {
                CommandAlign.Params::limit setTo number
            }
        }

        companion object {
            const val CMD_OPTION = "--limit-input"
        }
    }

    object AlignmentBoundaryConstants {
        const val LEFT_FLOATING_CMD_OPTION = "--floating-left-alignment-boundary"
        const val LEFT_RIGID_CMD_OPTION = "--rigid-left-alignment-boundary"
        const val RIGHT_FLOATING_CMD_OPTION = "--floating-right-alignment-boundary"
        const val RIGHT_RIGID_CMD_OPTION = "--rigid-right-alignment-boundary"
    }

    //
    // Left Boundary
    //

    @JsonTypeName("LeftAlignmentBoundaryNoPoint")
    data class LeftAlignmentBoundaryNoPoint(
        @JsonProperty("floating") val floating: Boolean
    ) :
        MiXCRMixinBase(10, Flags.LeftAlignmentMode) {
        override fun MixinBuilderOps.action() =
            modifyGeneAlignmentParams(Variable) {
                parameters.isFloatingLeftBound = floating
            }

        override val cmdArgs
            get() = listOf(
                if (floating)
                    AlignmentBoundaryConstants.LEFT_FLOATING_CMD_OPTION
                else
                    AlignmentBoundaryConstants.LEFT_RIGID_CMD_OPTION
            )
    }

    @JsonTypeName("LeftAlignmentBoundaryWithPoint")
    data class LeftAlignmentBoundaryWithPoint(
        @JsonProperty("floating") val floating: Boolean,
        @JsonProperty("anchorPoint") val anchorPoint: ReferencePoint
    ) :
        MiXCRMixinBase(10, Flags.LeftAlignmentMode) {
        init {
            // Checking parameters
            if (anchorPoint.geneType != Variable)
                throw RuntimeException("$anchorPoint is not inside the V gene")
        }

        override fun MixinBuilderOps.action() =
            modifyGeneAlignmentParams(Variable) {
                if (!geneFeatureToAlign.contains(anchorPoint))
                    throw RuntimeException("Can't apply mixin because $geneFeatureToAlign does not contain $anchorPoint")
                geneFeatureToAlign = geneFeatureToAlign.splitBy(anchorPoint).right
                parameters.isFloatingLeftBound = floating
            }

        override val cmdArgs
            get() = listOf(
                if (floating)
                    AlignmentBoundaryConstants.LEFT_FLOATING_CMD_OPTION
                else
                    AlignmentBoundaryConstants.LEFT_RIGID_CMD_OPTION,
                anchorPoint.toString()
            )
    }

    //
    // Right Boundary
    //

    @JsonTypeName("RightAlignmentBoundaryNoPoint")
    data class RightAlignmentBoundaryNoPoint(
        @JsonProperty("floating") val floating: Boolean,
        @JsonProperty("geneType") val geneType: GeneType?
    ) :
        MiXCRMixinBase(10, Flags.RightAlignmentMode) {
        override fun MixinBuilderOps.action() =
            when (geneType) {
                null -> {
                    if (floating)
                        throw IllegalArgumentException() // must be checked in the options
                    modifyAlignmentParams {
                        jAlignerParameters.parameters.isFloatingRightBound = false
                        cAlignerParameters.parameters.isFloatingRightBound = false
                    }
                }

                Joining ->
                    modifyAlignmentParams {
                        // Setting alignment mode
                        jAlignerParameters.parameters.isFloatingRightBound = floating
                        // And turn off C gene alignment as alignment should terminate somewhere in J gene
                        setGeneAlignerParameters(Constant, null)
                    }

                Constant ->
                    modifyAlignmentParams {
                        // Checking mixin assumptions
                        if (jAlignerParameters.geneFeatureToAlign.lastPoint != ReferencePoint.FR4End)
                            throw RuntimeException(
                                "Incompatible J gene right alignment feature boundary for the mix-in: " +
                                        "${jAlignerParameters.geneFeatureToAlign.lastPoint}"
                            )
                        if (cAlignerParameters == null)
                            throw RuntimeException(
                                "Wrong application of mixin \"${cmdArgs.joinToString(" ")}\", " +
                                        "underlying parameter set has no alignment parameters for C gene"
                            )

                        // And setting strict alignment mode for the J gene
                        jAlignerParameters.parameters.isFloatingRightBound = false
                        // Setting alignment mode
                        cAlignerParameters.parameters.isFloatingRightBound = floating
                    }

                else -> throw IllegalArgumentException()
            }

        override val cmdArgs
            get() = listOf(
                if (floating)
                    AlignmentBoundaryConstants.RIGHT_FLOATING_CMD_OPTION
                else
                    AlignmentBoundaryConstants.RIGHT_RIGID_CMD_OPTION,
            ) + if (geneType != null)
                listOf(geneType.letter.toString())
            else
                emptyList()
    }

    @JsonTypeName("RightAlignmentBoundaryWithPoint")
    data class RightAlignmentBoundaryWithPoint(
        @JsonProperty("floating") val floating: Boolean,
        @JsonProperty("anchorPoint") val anchorPoint: ReferencePoint
    ) :
        MiXCRMixinBase(10, Flags.RightAlignmentMode) {
        override fun MixinBuilderOps.action() =
            when (anchorPoint.geneType) {
                Joining ->
                    modifyAlignmentParams {
                        // Checking mixin assumptions
                        if (jAlignerParameters.geneFeatureToAlign.lastPoint != ReferencePoint.FR4End &&
                            !jAlignerParameters.geneFeatureToAlign.contains(anchorPoint)
                        )
                            throw RuntimeException(
                                "Incompatible J gene alignment feature for the mix-in: " +
                                        "${jAlignerParameters.geneFeatureToAlign}"
                            )

                        // Adjusting feature to align
                        jAlignerParameters.geneFeatureToAlign =
                            jAlignerParameters.geneFeatureToAlign.setLastPoint(anchorPoint)
                        // Setting alignment mode
                        jAlignerParameters.parameters.isFloatingRightBound = floating
                        // And turn off C gene alignment as alignment should terminate somewhere in J gene
                        setGeneAlignerParameters(Constant, null)
                    }

                Constant ->
                    modifyAlignmentParams {
                        // Checking mixin assumptions
                        if (cAlignerParameters.geneFeatureToAlign.lastPoint != ReferencePoint.CExon1End &&
                            !cAlignerParameters.geneFeatureToAlign.contains(anchorPoint)
                        )
                            throw RuntimeException(
                                "Incompatible C gene alignment feature for the mix-in: " +
                                        "${cAlignerParameters.geneFeatureToAlign}"
                            )
                        if (jAlignerParameters.geneFeatureToAlign.lastPoint != ReferencePoint.FR4End)
                            throw RuntimeException(
                                "Incompatible J gene right alignment feature boundary for the mix-in: " +
                                        "${jAlignerParameters.geneFeatureToAlign.lastPoint}"
                            )

                        // And setting strict alignment mode for the J gene
                        jAlignerParameters.parameters.isFloatingRightBound = false

                        // Adjusting feature to align
                        cAlignerParameters.geneFeatureToAlign =
                            cAlignerParameters.geneFeatureToAlign.setLastPoint(anchorPoint)
                        // Setting alignment mode
                        cAlignerParameters.parameters.isFloatingRightBound = floating
                    }

                else -> throw RuntimeException("$anchorPoint is not inside the J or C gene")
            }

        override val cmdArgs
            get() = listOf(
                if (floating)
                    AlignmentBoundaryConstants.RIGHT_FLOATING_CMD_OPTION
                else
                    AlignmentBoundaryConstants.RIGHT_RIGID_CMD_OPTION,
                anchorPoint.toString()
            )
    }

    @JsonTypeName("SetTagPattern")
    data class SetTagPattern(
        @JsonProperty("tagPattern") val tagPattern: String
    ) : MiXCRMixinBase(50, Flags.TagPattern) {
        override fun MixinBuilderOps.action() {
            MiXCRParamsBundle::align.update {
                CommandAlign.Params::tagPattern setTo tagPattern
            }
        }

        override val cmdArgs get() = listOf(CMD_OPTION, tagPattern)

        companion object {
            const val CMD_OPTION = "--tag-pattern"
        }
    }

    @JsonTypeName("KeepNonCDR3Alignments")
    object KeepNonCDR3Alignments : MiXCRMixinBase(10) {
        override val cmdArgs get() = listOf(CMD_OPTION)

        override fun MixinBuilderOps.action() {
            modifyAlignmentParams {
                allowPartialAlignments = true
                allowNoCDR3PartAlignments = true
            }
        }

        const val CMD_OPTION = "--keep-non-CDR3-alignments"
    }

    @JsonTypeName("DropNonCDR3Alignments")
    object DropNonCDR3Alignments : MiXCRMixinBase(10) {
        override val cmdArgs get() = listOf(CMD_OPTION)

        override fun MixinBuilderOps.action() {
            modifyAlignmentParams {
                allowPartialAlignments = false
                allowNoCDR3PartAlignments = false
            }
        }

        const val CMD_OPTION = "--drop-non-CDR3-alignments"
    }
}

object AssembleMixins {
    @JsonTypeName("SetClonotypeAssemblingFeatures")
    data class SetClonotypeAssemblingFeatures(
        @JsonProperty("features") val features: GeneFeatures
    ) : MiXCRMixinBase(50) {
        override fun MixinBuilderOps.action() {
            MiXCRParamsBundle::assemble.update {
                CommandAssemble.Params::cloneAssemblerParameters.applyAfterClone(CloneAssemblerParameters::clone) {
                    assemblingFeatures = features.features.toTypedArray()
                }
            }
        }

        override val cmdArgs get() = listOf(CMD_OPTION, features.encode())

        companion object {
            const val CMD_OPTION = "--assemble-clonotypes-by"
        }
    }

    @JsonTypeName("SetSplitClonesBy")
    data class SetSplitClonesBy(
        @JsonProperty("geneType") val geneType: GeneType,
        @JsonProperty("value") val value: Boolean
    ) : MiXCRMixinBase(10) {
        init {
            if (geneType !in arrayOf(Variable, Joining, Constant))
                throw IllegalArgumentException("Clone splitting supported only for V, J and C genes.")
        }

        override fun MixinBuilderOps.action() {
            MiXCRParamsBundle::assemble.update {
                CommandAssemble.Params::cloneAssemblerParameters
                    .applyAfterClone(CloneAssemblerParameters::clone) {
                        setSeparateBy(geneType, value)
                    }
            }
        }

        override val cmdArgs
            get() = listOf(
                if (value) CMD_OPTION_TRUE else CMD_OPTION_FALSE,
                geneType.letter.toString()
            )

        companion object {
            const val CMD_OPTION_TRUE = "--split-clones-by"
            const val CMD_OPTION_FALSE = "--dont-split-clones-by"
        }
    }
}

object AssembleContigsMixins {
    @JsonTypeName("SetContigAssemblingFeatures")
    data class SetContigAssemblingFeatures(
        @JsonProperty("features") val features: GeneFeatures
    ) : MiXCRMixinBase(50) {
        override fun MixinBuilderOps.action() {
            MiXCRParamsBundle::assembleContigs.update {
                CommandAssembleContigs.Params::parameters.update {
                    FullSeqAssemblerParameters::assemblingRegions.setTo(features)
                    FullSeqAssemblerParameters::subCloningRegions.setTo(features)
                    FullSeqAssemblerParameters::isAlignedRegionsOnly.setTo(true)
                    FullSeqAssemblerParameters::postFiltering.setTo(PostFiltering.OnlyFullyDefined)
                }
            }
        }

        override val cmdArgs get() = listOf(CMD_OPTION, features.encode())

        companion object {
            const val CMD_OPTION = "--assemble-contigs-by"
        }
    }
}

object PipelineMixins {
    @JsonTypeName("AddPipelineStep")
    data class AddPipelineStep(
        @JsonProperty("step") val step: String
    ) : MiXCRMixinBase(10) {
        @JsonIgnore
        private val command = MiXCRCommandDescriptor.fromString(step)
        override fun MixinBuilderOps.action() {
            MiXCRParamsBundle::pipeline.updateBy {
                MiXCRPipeline((it.steps + command).sorted())
            }

            if (command == MiXCRCommandDescriptor.assembleContigs)
                MiXCRParamsBundle::assemble.update {
                    CommandAssemble.Params::clnaOutput setTo true
                }
        }

        override val cmdArgs get() = listOf(CMD_OPTION, step)

        companion object {
            const val CMD_OPTION = "--add-step"
        }
    }

    @JsonTypeName("RemovePipelineStep")
    data class RemovePipelineStep(
        @JsonProperty("step") val step: String
    ) : MiXCRMixinBase(10) {
        @JsonIgnore
        private val command = MiXCRCommandDescriptor.fromString(step)
        override fun MixinBuilderOps.action() {
            MiXCRParamsBundle::pipeline.updateBy {
                MiXCRPipeline(it.steps - command)
            }

            if (command == MiXCRCommandDescriptor.assembleContigs)
                MiXCRParamsBundle::assemble.update {
                    CommandAssemble.Params::clnaOutput setTo false
                }
        }

        override val cmdArgs get() = listOf(CMD_OPTION, step)

        companion object {
            const val CMD_OPTION = "--remove-step"
        }
    }
}

object ExportMixins {
    private fun imputeFieldTransform(field: String) =
        when (field) {
            "-nFeature" -> "-nFeatureImputed"
            "-aaFeature" -> "-aaFeatureImputed"
            "-allNFeatures" -> "-allNFeaturesImputed"
            "-allNFeaturesWithMinQuality" -> "-allNFeaturesImputedWithMinQuality"
            "-allAaFeatures" -> "-allAaFeaturesImputed"
            else -> field
        }

    private fun dontImputeFieldTransform(field: String) =
        when (field) {
            "-nFeatureImputed" -> "-nFeature"
            "-aaFeatureImputed" -> "-aaFeature"
            "-allNFeaturesImputed" -> "-allNFeatures"
            "-allNFeaturesImputedWithMinQuality" -> "-allNFeaturesWithMinQuality"
            "-allAaFeaturesImputed" -> "-allAaFeatures"
            else -> field
        }

    sealed class MiXCRExportMixinBase(
        importance: Int,
        @JsonIgnore private val applyToAlignments: Boolean,
        @JsonIgnore private val applyToClones: Boolean,
    ) : MiXCRMixinBase(importance) {
        protected abstract fun modifyFields(fields: List<ExportFieldDescription>): List<ExportFieldDescription>
        override fun MixinBuilderOps.action() {
            MiXCRParamsBundle::exportAlignments.update { CommandExportAlignments.Params::fields.updateBy(::modifyFields) }
            MiXCRParamsBundle::exportClones.update { CommandExportClones.Params::fields.updateBy(::modifyFields) }
        }
    }

    @JsonTypeName("ImputeGermlineOnExport")
    object ImputeGermlineOnExport : MiXCRExportMixinBase(10, true, true) {
        override val cmdArgs get() = listOf(CMD_OPTION)

        override fun modifyFields(fields: List<ExportFieldDescription>) =
            fields.map { fd -> fd.copy(field = imputeFieldTransform(fd.field)) }

        const val CMD_OPTION = "--impute-germline-on-export"
    }

    @JsonTypeName("DontImputeGermlineOnExport")
    object DontImputeGermlineOnExport : MiXCRExportMixinBase(10, true, true) {
        override val cmdArgs get() = listOf(CMD_OPTION)

        override fun modifyFields(fields: List<ExportFieldDescription>) =
            fields.map { fd -> fd.copy(field = dontImputeFieldTransform(fd.field)) }

        const val CMD_OPTION = "--dont-impute-germline-on-export"
    }

    sealed class AddExportField(
        clones: Boolean,
        @JsonIgnore private val insertIndex: Int,
        @JsonIgnore private val field: String,
        @JsonIgnore private val args: List<String>
    ) : MiXCRExportMixinBase(10, !clones, clones) {
        @get:JsonIgnore
        val fieldDescr get() = ExportFieldDescription(field, args)

        private fun checkFor(exf: FieldExtractorsFactory<*>) {
            val arity = exf[field].arity
            check(arity.min() <= args.size && args.size <= arity.max()) {
                "Unexpected number of arguments for field $field. Expected $arity but found ${args.size} " +
                        "(${args.joinToString(", ")})"
            }
        }

        init {
            if (clones)
                checkFor(CloneFieldsExtractorsFactory)
            else
                checkFor(VDJCAlignmentsFieldsExtractorsFactory)
        }

        override fun modifyFields(fields: List<ExportFieldDescription>) = run {
            val idx = if (insertIndex >= 0)
                insertIndex
            else
                fields.size + insertIndex + 1
            fields.take(idx) + listOf(fieldDescr) + fields.drop(idx)
        }
    }

    @JsonTypeName("AddExportAlignmentsField")
    data class AddExportAlignmentsField(
        @JsonProperty("insertIndex") val insertIndex: Int,
        @JsonProperty("field") val field: String,
        @JsonProperty("args") @JsonInclude(NON_EMPTY) val args: List<String> = emptyList(),
    ) : AddExportField(false, insertIndex, field, args) {
        override val cmdArgs
            get() = listOf(
                when (insertIndex) {
                    0 -> CMD_OPTION_PREPEND_PREFIX
                    -1 -> CMD_OPTION_APPEND_PREFIX
                    else -> throw IllegalArgumentException()
                },
                field
            ) + args

        companion object {
            const val CMD_OPTION_PREPEND_PREFIX = "--prepend-export-alignments-field"
            const val CMD_OPTION_APPEND_PREFIX = "--append-export-alignments-field"
        }
    }

    @JsonTypeName("AddExportClonesField")
    data class AddExportClonesField(
        @JsonProperty("insertIndex") val insertIndex: Int,
        @JsonProperty("field") val field: String,
        @JsonProperty("args") @JsonInclude(NON_EMPTY) val args: List<String> = emptyList(),
    ) : AddExportField(true, insertIndex, field, args) {
        override val cmdArgs
            get() = listOf(
                when (insertIndex) {
                    0 -> CMD_OPTION_PREPEND_PREFIX
                    -1 -> CMD_OPTION_APPEND_PREFIX
                    else -> throw IllegalArgumentException()
                },
                field
            ) + args

        companion object {
            const val CMD_OPTION_PREPEND_PREFIX = "--prepend-export-clones-field"
            const val CMD_OPTION_APPEND_PREFIX = "--append-export-clones-field"
        }
    }
}
