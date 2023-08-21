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

import com.milaboratory.app.ApplicationException
import com.milaboratory.app.InputFileType
import com.milaboratory.app.ValidationException
import com.milaboratory.app.logger
import com.milaboratory.cli.POverridesBuilderOps
import com.milaboratory.cli.ParamsResolver
import com.milaboratory.mixcr.basictypes.IOUtil
import com.milaboratory.mixcr.presets.MiXCRCommandDescriptor
import com.milaboratory.mixcr.presets.MiXCRParamsBundle
import com.milaboratory.mixcr.qc.checks.QcCheckResult
import com.milaboratory.mixcr.qc.checks.QcChecker.QualityStatus.ALERT
import com.milaboratory.mixcr.qc.checks.QcChecker.QualityStatus.OK
import com.milaboratory.mixcr.qc.checks.QcChecker.QualityStatus.WARN
import com.milaboratory.util.K_PRETTY_OM
import org.apache.logging.log4j.core.tools.picocli.CommandLine.Help.Ansi
import picocli.CommandLine.Command
import picocli.CommandLine.Mixin
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.io.PrintStream
import java.nio.file.Path

object CommandQcChecks {
    const val COMMAND_NAME = MiXCRCommandDescriptor.qc.name

    abstract class CmdBase : MiXCRCommandWithOutputs(), MiXCRPresetAwareCommand<CommandQcChecksParams> {
        @Mixin
        lateinit var resetPreset: ResetPresetOptions

        override val paramsResolver: ParamsResolver<MiXCRParamsBundle, CommandQcChecksParams>
            get() = object : MiXCRParamsResolver<CommandQcChecksParams>(MiXCRParamsBundle::qc) {
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
            paramLabel = "input.(vdjca|clns|clna)",
            index = "0"
        )
        lateinit var input: Path

        @Parameters(
            description = ["Paths where to write reports. Print in stdout if omitted."],
            paramLabel = "output.(txt|json)",
            index = "1",
            arity = "0..2"
        )
        override val outputFiles: List<Path> = mutableListOf()

        @Option(
            description = ["Export in json format. Used if output printed in stdout"],
            names = ["--json"],
        )
        private var json = false

        @Option(
            description = ["Print to stdout."],
            names = ["--print-to-stdout"],
        )
        private var printToStdout = false

        @Option(
            description = ["Print warning if some checks not passed to stderr."],
            names = ["--print-warn"],
        )
        private var printWarn = false

        override val inputFiles
            get() = listOf(input)

        override fun validate() {
            ValidationException.requireFileType(input, InputFileType.VDJCA, InputFileType.CLNX)
            outputFiles.forEach { output ->
                ValidationException.requireFileType(output, InputFileType.TXT, InputFileType.JSON)
            }
        }

        override fun run1() {
            val fileInfo = IOUtil.extractFileInfo(input)

            val (_, params) = paramsResolver.resolve(
                resetPreset.overridePreset(fileInfo.header.paramsSpec),
                printParameters = logger.verbose && outputFiles.isNotEmpty()
            )

            val results = params.checks
                .map { it.checker() }
                .filter { it.supports(fileInfo) }
                .flatMap { it.check(fileInfo) }

            if (outputFiles.isNotEmpty() && printToStdout)
                results.print(System.out, Ansi.AUTO)

            if (outputFiles.isEmpty()) {
                when {
                    json -> K_PRETTY_OM.writeValue(System.out, results)
                    else -> results.print(System.out, Ansi.AUTO)
                }
            }
            outputFiles.forEach { output ->
                when {
                    InputFileType.JSON.matches(output) -> {
                        K_PRETTY_OM.writeValue(output.toFile(), results)
                    }

                    InputFileType.TXT.matches(output) -> {
                        results.print(PrintStream(output.toFile()), Ansi.OFF)
                    }

                    else -> throw IllegalArgumentException()
                }
            }

            val failedChecks = results.count { it.status == ALERT }
            val message = "Failed $failedChecks of ${results.size} qc checks"
            if (printWarn) {
                logger.warn { message }
            }
            if (params.errorOnFailedCheck) {
                if (failedChecks > 0) {
                    throw ApplicationException(message)
                }
            }
        }

        private fun List<QcCheckResult>.print(out: PrintStream, ansi: Ansi) {
            out.println()

            val labelLength = this.maxOf { it.label.length }
            val printedValueLength = this.maxOf { it.payload[QcCheckResult.printedValue]?.toString()?.length ?: 0 }
            groupBy { it.step }.forEach { (_, checks) ->
                // out.println("Quality checks for $step:")

                checks.forEach { check ->
                    val printedValue = check.payload[QcCheckResult.printedValue]?.toString() ?: ""
                    val label =
                        check.label + ": " +
                                " ".repeat(labelLength - check.label.length) +
                                printedValue +
                                " ".repeat(printedValueLength - printedValue.length)


                    val color = when (check.status) {
                        ALERT -> "red"
                        OK -> "green"
                        WARN -> "yellow"
                    }

                    val text = ansi.Text("  $label [@|fg($color) ${check.status.text}|@]")
                    out.println(text.toString())
                }
            }
        }
    }
}
