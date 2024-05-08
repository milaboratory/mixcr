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

import com.milaboratory.app.InputFileType
import com.milaboratory.app.ValidationException
import com.milaboratory.app.logger
import com.milaboratory.cli.POverridesBuilderOps
import com.milaboratory.cli.ParamsBundleSpecBaseAddress
import com.milaboratory.cli.ParamsBundleSpecBaseEmbedded
import com.milaboratory.cli.ParamsResolver
import com.milaboratory.mitool.MiToolParamsBundle
import com.milaboratory.mixcr.basictypes.ClnAReader
import com.milaboratory.mixcr.basictypes.ClnsReader
import com.milaboratory.mixcr.basictypes.IOUtil
import com.milaboratory.mixcr.basictypes.IOUtil.MiXCRFileType.CLNA
import com.milaboratory.mixcr.basictypes.IOUtil.MiXCRFileType.CLNS
import com.milaboratory.mixcr.basictypes.IOUtil.MiXCRFileType.SHMT
import com.milaboratory.mixcr.basictypes.IOUtil.MiXCRFileType.VDJCA
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader
import com.milaboratory.mixcr.cli.CommonDescriptions.Labels
import com.milaboratory.mixcr.presets.AnalyzeCommandDescriptor
import com.milaboratory.mixcr.presets.MiXCRParams
import com.milaboratory.mixcr.presets.MiXCRParamsBundle
import com.milaboratory.mixcr.presets.MiXCRParamsSpec
import com.milaboratory.util.K_YAML_OM
import io.repseq.core.VDJCLibraryRegistry
import picocli.CommandLine.ArgGroup
import picocli.CommandLine.Command
import picocli.CommandLine.Mixin
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.nio.file.Path

@Command(
    description = ["Export a preset file given the preset name or source file and a set of mix-ins"]
)
class CommandExportPreset : MiXCRCommandWithOutputs(), MiXCRPresetAwareCommand<Unit> {
    class PresetInput {
        @Option(
            names = ["--preset-name"],
            description = ["Preset name to export."],
            paramLabel = Labels.PRESET,
            required = true,
            order = 1,
            completionCandidates = PresetsCandidates::class
        )
        var presetName: String? = null

        @Option(
            names = ["--mixcr-file"],
            description = ["File that was processed by MiXCR."],
            paramLabel = "<input.(vdjca|clns|clna)>",
            required = true,
            order = 2
        )
        var input: Path? = null
            set(value) {
                ValidationException.requireFileType(value, InputFileType.VDJCA, InputFileType.CLNX)
                ValidationException.requireFileExists(value)
                field = value
            }
    }

    @Mixin
    lateinit var resetPreset: ResetPresetOptions

    @ArgGroup(
        exclusive = true,
        multiplicity = "1",
        order = OptionsOrder.main + 10_100
    )
    lateinit var presetInput: PresetInput

    @Option(
        names = ["--no-validation"],
        description = ["Don't validate preset before export."],
        arity = "0",
        order = OptionsOrder.main + 10_200
    )
    var noValidation: Boolean = false

    @set:Parameters(
        description = ["Path where to write preset yaml file. Will write to output if omitted."],
        arity = "0..1",
        paramLabel = "preset_file.(yaml|yml)"
    )
    private var outputFile: Path? = null
        set(value) {
            ValidationException.requireFileType(value, InputFileType.YAML)
            field = value
        }

    override val inputFiles get() = mutableListOf<Path>()

    override val outputFiles get() = listOfNotNull(outputFile)

    @ArgGroup(
        validate = false,
        heading = PipelineMiXCRMixins.DESCRIPTION,
        multiplicity = "0..*",
        order = OptionsOrder.mixins.pipeline
    )
    var pipelineMixins: List<PipelineMiXCRMixins> = mutableListOf()

    @ArgGroup(
        validate = false,
        heading = AlignMiXCRMixins.DESCRIPTION,
        multiplicity = "0..*",
        order = OptionsOrder.mixins.align
    )
    var alignMixins: List<AlignMiXCRMixins> = mutableListOf()

    @ArgGroup(
        validate = false,
        heading = AssembleMiXCRMixins.DESCRIPTION,
        multiplicity = "0..*",
        order = OptionsOrder.mixins.assemble
    )
    var assembleMixins: List<AssembleMiXCRMixins> = mutableListOf()

    @ArgGroup(
        validate = false,
        heading = AssembleContigsMiXCRMixins.DESCRIPTION,
        multiplicity = "0..*",
        order = OptionsOrder.mixins.assembleContigs
    )
    var assembleContigsMixins: List<AssembleContigsMiXCRMixins> = mutableListOf()

    @ArgGroup(
        validate = false,
        heading = ExportMiXCRMixins.DESCRIPTION,
        multiplicity = "0..*",
        order = OptionsOrder.mixins.exports
    )
    var exportMixins: List<ExportMiXCRMixins.All> = mutableListOf()

    @ArgGroup(
        multiplicity = "0..*",
        order = OptionsOrder.mixins.generic
    )
    var genericMixins: List<GenericMiXCRMixins> = mutableListOf()

    @ArgGroup(
        multiplicity = "0..*",
        order = OptionsOrder.mixins.qc
    )
    var qcMixins: List<QcChecksMixins> = mutableListOf()

    override fun initialize() {
        if (outputFile == null)
            logger.redirectSysOutToSysErr()
    }

    override fun run1() {
        val mixinsFromArgs = MiXCRMixinCollection.empty + genericMixins + alignMixins + assembleMixins +
                assembleContigsMixins + exportMixins + pipelineMixins + qcMixins
        val bundleSpec = when {
            presetInput.presetName != null -> ParamsBundleSpecBaseAddress(presetInput.presetName!!)

            else -> {
                val inputFile = presetInput.input!!
                val header = when (IOUtil.extractFileType(inputFile)) {
                    VDJCA -> VDJCAlignmentsReader(inputFile)
                        .use { reader -> reader.header }

                    CLNS -> ClnsReader(inputFile, VDJCLibraryRegistry.getDefault())
                        .use { reader -> reader.header }

                    CLNA -> ClnAReader(inputFile, VDJCLibraryRegistry.getDefault(), 1)
                        .use { reader -> reader.header }

                    SHMT -> throw UnsupportedOperationException("Command doesn't support .shmt")
                }
                val (originalPreset) = paramsResolver.resolve(
                    header.paramsSpec, printParameters = false, validate = false
                )

                fun <P : MiXCRParams, T : AnalyzeCommandDescriptor<P, *>> paramsWithOverride(
                    descriptor: T
                ): P? = header.stepParams[descriptor].firstOrNull() ?: descriptor.extractFromBundle(originalPreset)

                val bundle = MiXCRParamsBundle(
                    flags = originalPreset.flags,
                    pipeline = originalPreset.pipeline,
                    validation = originalPreset.validation,
                    mitool = MiToolParamsBundle(
                        paramsWithOverride(AnalyzeCommandDescriptor.MiToolCommandDelegationDescriptor.parse)?.params,
                        paramsWithOverride(AnalyzeCommandDescriptor.MiToolCommandDelegationDescriptor.refineTags)?.params,
                        paramsWithOverride(AnalyzeCommandDescriptor.MiToolCommandDelegationDescriptor.consensus)?.params,
                    ).nullIfEmpty(),
                    align = paramsWithOverride(AnalyzeCommandDescriptor.align),
                    refineTagsAndSort = paramsWithOverride(AnalyzeCommandDescriptor.refineTagsAndSort),
                    assemblePartial = paramsWithOverride(AnalyzeCommandDescriptor.assemblePartial),
                    extend = paramsWithOverride(AnalyzeCommandDescriptor.extend),
                    assemble = paramsWithOverride(AnalyzeCommandDescriptor.assemble),
                    assembleContigs = paramsWithOverride(AnalyzeCommandDescriptor.assembleContigs),
                    assembleCells = paramsWithOverride(AnalyzeCommandDescriptor.assembleCells),
                    exportAlignments = paramsWithOverride(AnalyzeCommandDescriptor.exportAlignments),
                    exportClones = paramsWithOverride(AnalyzeCommandDescriptor.exportClones),
                    exportCloneGroups = paramsWithOverride(AnalyzeCommandDescriptor.exportCloneGroups),
                    qc = paramsWithOverride(AnalyzeCommandDescriptor.qc)
                )
                ParamsBundleSpecBaseEmbedded(bundle)
            }
        }

        val bundle = paramsResolver.resolve(
            MiXCRParamsSpec(bundleSpec, mixins = mixinsFromArgs.mixins),
            printParameters = false,
            validate = !noValidation
        ).first

        val of = outputFile
        if (of != null)
            K_YAML_OM.writeValue(of.toFile(), bundle)
        else
            K_YAML_OM.writeValue(System.out, bundle)
    }

    override val paramsResolver: ParamsResolver<MiXCRParamsBundle, Unit>
        get() = object : MiXCRParamsResolver<Unit>(MiXCRParamsBundle::exportPreset) {
            override fun POverridesBuilderOps<Unit>.paramsOverrides() {
            }
        }
}
