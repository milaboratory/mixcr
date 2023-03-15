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
import com.milaboratory.app.logger
import com.milaboratory.cli.POverridesBuilderOps
import com.milaboratory.cli.ParamsResolver
import com.milaboratory.mixcr.basictypes.IOUtil
import com.milaboratory.mixcr.presets.MiXCRCommandDescriptor
import com.milaboratory.mixcr.presets.MiXCRParamsBundle
import com.milaboratory.util.K_PRETTY_OM
import picocli.CommandLine.Command
import picocli.CommandLine.Mixin
import picocli.CommandLine.Parameters
import java.nio.file.Path

object CommandQcChecks {
    const val COMMAND_NAME = MiXCRCommandDescriptor.qcCheck.name

    abstract class CmdBase : MiXCRCommandWithOutputs(), MiXCRPresetAwareCommand<CommandQcChecksParams> {
        @Mixin
        lateinit var resetPreset: ResetPresetOptions

        override val paramsResolver: ParamsResolver<MiXCRParamsBundle, CommandQcChecksParams>
            get() = object : MiXCRParamsResolver<CommandQcChecksParams>(MiXCRParamsBundle::qcChecks) {
                override fun POverridesBuilderOps<CommandQcChecksParams>.paramsOverrides() {
                }
            }
    }

    @Command(
        hidden = true,
        description = ["Perform quality control checks on results."]
    )
    class Cmd : CmdBase() {
        @Parameters(
            description = ["Path to input file."],
            paramLabel = "input.(vdjca|clns|clna|shmt)",
            index = "0"
        )
        lateinit var input: Path

        @Parameters(
            description = ["Path where to write reports. Print in stdout if omitted."],
            paramLabel = "output.(txt|json)",
            index = "1",
            arity = "0..1"
        )
        var output: Path? = null

        override val inputFiles
            get() = listOf(input)

        override val outputFiles
            get() = listOfNotNull(output)

        override fun validate() {
            ValidationException.requireFileType(input, InputFileType.VDJCA, InputFileType.CLNX, InputFileType.SHMT)
            ValidationException.requireFileType(output, InputFileType.TXT, InputFileType.JSON)
        }

        override fun run1() {
            val fileInfo = IOUtil.extractFileInfo(input)

            val (_, params) = paramsResolver.resolve(
                resetPreset.overridePreset(fileInfo.header.paramsSpec),
                printParameters = logger.verbose && output != null
            )

            val reports = fileInfo.footer.reports
            val results = params.checks
                .map { it.checker() }
                .filter { it.supports(reports) }
                .flatMap { it.check(reports) }
            val output = output
            when {
                output == null -> {
                    TODO()
                }

                InputFileType.TXT.matches(output) -> {
                    TODO()
                }

                InputFileType.JSON.matches(output) -> {
                    K_PRETTY_OM.writeValue(output.toFile(), results)
                }

                else -> throw IllegalArgumentException()
            }
        }
    }
}
