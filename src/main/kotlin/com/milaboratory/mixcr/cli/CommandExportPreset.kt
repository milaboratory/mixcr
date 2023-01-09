/*
 * Copyright (c) 2014-2023, MiLaboratories Inc. All Rights Reserved
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
import com.milaboratory.cli.POverridesBuilderOps
import com.milaboratory.cli.ParamsResolver
import com.milaboratory.mitool.helpers.K_YAML_OM
import com.milaboratory.mixcr.MiXCRParamsBundle
import com.milaboratory.mixcr.MiXCRParamsSpec
import com.milaboratory.mixcr.basictypes.ClnAReader
import com.milaboratory.mixcr.basictypes.ClnsReader
import com.milaboratory.mixcr.basictypes.IOUtil
import com.milaboratory.mixcr.basictypes.IOUtil.MiXCRFileType.CLNA
import com.milaboratory.mixcr.basictypes.IOUtil.MiXCRFileType.CLNS
import com.milaboratory.mixcr.basictypes.IOUtil.MiXCRFileType.SHMT
import com.milaboratory.mixcr.basictypes.IOUtil.MiXCRFileType.VDJCA
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader
import io.repseq.core.VDJCLibraryRegistry
import picocli.CommandLine.ArgGroup
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.nio.file.Path

@Command(
    description = ["Export a preset file given the preset name and a set of mix-ins"]
)
class CommandExportPreset : MiXCRCommandWithOutputs(), MiXCRPresetAwareCommand<Unit> {
    class PresetInput {
        @Option(
            names = ["--preset-name"],
            description = ["Preset name to export."],
            paramLabel = "<preset>",
            required = true,
            order = 1
        )
        var presetName: String? = null

        class FileInput {
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

            @ArgGroup(exclusive = true, multiplicity = "0..1")
            var resetPreset: ResetPresetArgs = ResetPresetArgs()
        }

        @ArgGroup(exclusive = false, multiplicity = "1", order = 2)
        var fileInput: FileInput? = null
    }

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

    override fun run1() {
        val mixinsFromArgs = MiXCRMixinCollection.empty + genericMixins + alignMixins + assembleMixins +
                assembleContigsMixins + exportMixins + pipelineMixins
        val spec: MiXCRParamsSpec = when {
            presetInput.presetName != null -> MiXCRParamsSpec(presetInput.presetName!!, mixins = mixinsFromArgs.mixins)
            else -> {
                val fileInput = presetInput.fileInput!!
                val inputFile = fileInput.input
                val paramsSpec = when (IOUtil.extractFileType(inputFile)) {
                    VDJCA -> VDJCAlignmentsReader(inputFile)
                        .use { reader -> reader.header }

                    CLNS -> ClnsReader(inputFile, VDJCLibraryRegistry.getDefault())
                        .use { reader -> reader.header }

                    CLNA -> ClnAReader(inputFile, VDJCLibraryRegistry.getDefault(), 1)
                        .use { reader -> reader.header }

                    SHMT -> throw UnsupportedOperationException("Command doesn't support .shmt")
                }.paramsSpec

                fileInput.resetPreset.overridePreset(paramsSpec).addMixins(mixinsFromArgs.mixins)
            }
        }

        val bundle = paramsResolver.resolve(
            spec,
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
