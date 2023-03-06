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

import com.fasterxml.jackson.core.JacksonException
import com.milaboratory.app.InputFileType
import com.milaboratory.app.ValidationException
import com.milaboratory.mixcr.basictypes.IOUtil
import com.milaboratory.mixcr.presets.MiXCRCommandDescriptor
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

    override fun validate() {
        ValidationException.requireFileType(inputPath, InputFileType.VDJCA, InputFileType.CLNX, InputFileType.SHMT)
        outputFormatFlags?.let { outputFormatFlags ->
            if (outputFormatFlags.json) {
                ValidationException.requireFileType(outputPath, InputFileType.JSON)
            }
            if (outputFormatFlags.yaml) {
                ValidationException.requireFileType(outputPath, InputFileType.YAML)
            }
        }
    }

    override fun run1() {
        val footer = IOUtil.extractFooter(inputPath)
        when (outputPath) {
            null -> CloseShieldOutputStream.wrap(System.out)
            else -> outputPath!!.outputStream()
        }.use { o ->
            when {
                outputFormatFlags?.json == true || outputFormatFlags?.yaml == true -> {
                    val tree = when {
                        step != null -> K_OM.valueToTree(footer.reports.getTrees(step!!))
                        else -> footer.reports.asTree()
                    }
                    when (outputFormatFlags?.json) {
                        true -> K_OM.writeValue(o, tree)
                        else -> K_YAML_OM.writeValue(o, tree)
                    }
                }

                else -> {
                    val helper = ReportHelper(o, outputPath != null)
                    val steps = when {
                        step != null -> listOf(step!!)
                        else -> footer.reports.collection.steps
                    }
                    steps
                        .map { MiXCRCommandDescriptor.fromString(it) }
                        .flatMap { commandDescriptor ->
                            footer.reports.getBytes(commandDescriptor).map {
                                try {
                                    K_OM.readValue(it, commandDescriptor.reportClass.java)
                                } catch (e: JacksonException) {
                                    null
                                }
                            }
                        }
                        .forEach { report ->
                            if (report == null) {
                                helper.println("Can't read report, file has too old version")
                            } else {
                                report.writeHeader(helper)
                                report.writeReport(helper)
                            }
                        }
                }
            }
        }
    }
}
