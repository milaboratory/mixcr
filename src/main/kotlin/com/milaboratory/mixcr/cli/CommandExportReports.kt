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
import com.milaboratory.app.InputFileType.JSON
import com.milaboratory.app.InputFileType.TXT
import com.milaboratory.app.InputFileType.YAML
import com.milaboratory.app.ValidationException
import com.milaboratory.app.logger
import com.milaboratory.mixcr.basictypes.IOUtil
import com.milaboratory.mixcr.presets.AnalyzeCommandDescriptor
import com.milaboratory.mixcr.presets.getReportSafe
import com.milaboratory.util.K_OM
import com.milaboratory.util.K_YAML_OM
import com.milaboratory.util.ReportHelper
import org.apache.commons.io.output.CloseShieldOutputStream
import picocli.CommandLine.ArgGroup
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.nio.file.Path
import kotlin.io.path.outputStream

@Command(
    description = ["Export MiXCR reports."]
)
class CommandExportReports : MiXCRCommandWithOutputs() {
    @Parameters(
        description = ["Path to input file."],
        paramLabel = "data.(vdjca|clns|clna|shmt)",
        index = "0"
    )
    private lateinit var inputPath: Path

    @Parameters(
        description = ["Path where to write reports. Print in stdout if omitted."],
        paramLabel = "report.(txt|json|yaml|yml)",
        index = "1",
        arity = "0..1"
    )
    private var outputPath: Path? = null

    @Option(
        names = ["--step"],
        description = ["Export report only for a specific step"],
        paramLabel = "<step>",
        order = OptionsOrder.main + 10_100
    )
    private var step: String? = null

    internal class OutputFormatFlags {
        @Option(
            names = ["--yaml"],
            description = ["Export as yaml"],
            order = 1
        )
        var yaml = false

        @Option(
            names = ["--json"],
            description = ["Export as json"],
            order = 2
        )
        var json = false
    }

    @ArgGroup(
        exclusive = true,
        multiplicity = "0..1",
        order = OptionsOrder.main + 10_200
    )
    private var outputFormatFlags: OutputFormatFlags? = null

    override val inputFiles
        get() = listOf(inputPath)

    override val outputFiles
        get() = listOfNotNull(outputPath)

    override fun initialize() {
        if (outputPath == null)
            logger.redirectSysOutToSysErr()
    }

    override fun validate() {
        ValidationException.requireFileType(inputPath, InputFileType.VDJCA, InputFileType.CLNX, InputFileType.SHMT)
        ValidationException.requireFileType(outputPath, JSON, YAML, TXT)
        // check consistency if format specified
        outputFormatFlags?.let { outputFormatFlags ->
            if (outputFormatFlags.json) {
                ValidationException.requireFileType(outputPath, JSON)
            }
            if (outputFormatFlags.yaml) {
                ValidationException.requireFileType(outputPath, YAML)
            }
        }
    }

    override fun run1() {
        val footer = IOUtil.extractFooter(inputPath)
        val format = when (outputPath) {
            null -> when {
                outputFormatFlags == null -> TXT
                outputFormatFlags!!.json -> JSON
                outputFormatFlags!!.yaml -> YAML
                else -> throw IllegalStateException()
            }

            else -> listOf(TXT, JSON, YAML).first { it.matches(outputPath!!) }
        }
        when (outputPath) {
            null -> CloseShieldOutputStream.wrap(System.out)
            else -> outputPath!!.outputStream()
        }.use { o ->
            when (format) {
                TXT -> {
                    val helper = ReportHelper(o, outputPath != null)
                    val steps = when {
                        step != null -> listOf(step!!)
                        else -> footer.reports.collection.steps
                    }
                    steps.forEach { step ->
                        val command = AnalyzeCommandDescriptor.fromString(step)
                        val reports = footer.reports.getReportSafe(command)
                        if (reports == null) {
                            helper.println("Can't read report for $step, file has too old version")
                        } else {
                            reports.forEach { report ->
                                report.writeHeader(helper)
                                report.writeReport(helper)
                            }
                        }
                    }
                }

                else -> {
                    val tree = when {
                        step != null -> K_OM.valueToTree(footer.reports.getTrees(step!!))
                        else -> footer.reports.asTree()
                    }
                    when (format) {
                        JSON -> K_OM.writeValue(o, tree)
                        YAML -> K_YAML_OM.writeValue(o, tree)
                        else -> throw IllegalStateException()
                    }
                }
            }
        }
    }
}
