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
package com.milaboratory.mixcr.cli

import com.milaboratory.cli.POverridesBuilderOps
import com.milaboratory.cli.ParamsResolver
import com.milaboratory.mitool.helpers.K_YAML_OM
import com.milaboratory.mixcr.MiXCRParamsBundle
import com.milaboratory.mixcr.MiXCRParamsSpec
import picocli.CommandLine.ArgGroup
import picocli.CommandLine.Command
import picocli.CommandLine.Mixin
import picocli.CommandLine.Parameters
import java.nio.file.Path

@Command(
    description = ["Export a preset file given the preset name and a set of mix-ins"]
)
class CommandExportPreset : MiXCRCommandWithOutputs(), MiXCRPresetAwareCommand<Unit> {
    @Parameters(
        description = ["Preset name to export."],
        index = "0",
        arity = "1",
        paramLabel = "preset_name"
    )
    lateinit var presetName: String

    @Parameters(
        description = ["Path where to write preset yaml file. Will write to output if omitted."],
        arity = "0..1",
        paramLabel = "preset_file.(yaml|yml)"
    )
    private val outputFile: Path? = null

    override val inputFiles get() = mutableListOf<Path>()

    override val outputFiles get() = outputFile?.let { mutableListOf(it) } ?: mutableListOf()

    @ArgGroup(validate = false, heading = PipelineMiXCRMixins.DESCRIPTION)
    var pipelineMixins: PipelineMiXCRMixins? = null

    @ArgGroup(validate = false, heading = AlignMiXCRMixins.DESCRIPTION)
    var alignMixins: AlignMiXCRMixins? = null

    @ArgGroup(validate = false, heading = AssembleMiXCRMixins.DESCRIPTION)
    var assembleMixins: AssembleMiXCRMixins? = null

    @ArgGroup(validate = false, heading = AssembleContigsMiXCRMixins.DESCRIPTION)
    var assembleContigsMixins: AssembleContigsMiXCRMixins? = null

    @ArgGroup(validate = false, heading = ExportMiXCRMixins.DESCRIPTION)
    var exportMixins: ExportMiXCRMixins? = null

    @Mixin
    var genericMixins: GenericMiXCRMixins? = null

    override fun run0() {
        val mixins = MiXCRMixinCollection.combine(
            pipelineMixins, alignMixins, assembleMixins,
            assembleContigsMixins, exportMixins, genericMixins
        )
        val (bundle, _) = paramsResolver.resolve(
            MiXCRParamsSpec(presetName, mixins = mixins.mixins),
            printParameters = false
        )
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
