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

import com.milaboratory.mixcr.MiXCRCommandDescriptor
import com.milaboratory.mixcr.basictypes.IOUtil
import com.milaboratory.mixcr.export.ExportFieldDescription
import com.milaboratory.mixcr.export.HeaderForExport
import com.milaboratory.mixcr.export.InfoWriter
import com.milaboratory.mixcr.export.ReportFieldsExtractors
import com.milaboratory.mixcr.export.ReportFieldsExtractors.ReportWithSource
import com.milaboratory.mixcr.export.RowMetaForExport
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

@Command(
    description = [
        "Export reports from file in tabular format.",
        "There will be one row per input file and exported step."
    ]
)
class CommandExportReportsAsTable : MiXCRCommandWithOutputs() {
    @Parameters(
        paramLabel = "$inputsLabel $outputLabel",
        index = "0",
        arity = "1..*"
    )
    private lateinit var inOut: List<Path>

    @Option(
        names = ["--step"],
        description = ["Export report only for a specific step."],
        paramLabel = "<step>",
        order = OptionsOrder.main + 10_100
    )
    private var stepToExport: String? = null

    @Option(
        names = ["--without-upstreams"],
        description = ["Don't export upstream reports for sources of steps that get several inputs, like `${CommandFindShmTrees.COMMAND_NAME}`."],
        arity = "0",
        order = OptionsOrder.main + 10_200
    )
    private var withoutUpstreams: Boolean = false

    @Option(
        description = ["Don't print first header line, print only data"],
        names = ["--no-header"],
        order = OptionsOrder.exportOptions
    )
    var noHeader = false

    val addedFields: MutableList<ExportFieldDescription> = mutableListOf()

    override val inputFiles: List<Path>
        get() = (inOut - setOfNotNull(out))
            .flatMap {
                when {
                    it.isDirectory() -> it.listDirectoryEntries()
                    else -> listOf(it)
                }
            }

    private val out: Path?
        get() = inOut.last().takeIf { it.matches(InputFileType.TSV) }

    override val outputFiles: List<Path>
        get() = listOfNotNull(out)

    override fun validate() {
        ValidationException.requireFileType(
            inOut.last(),
            InputFileType.CLNX,
            InputFileType.VDJCA,
            InputFileType.SHMT,
            InputFileType.TSV
        )
        inputFiles.forEach { input ->
            ValidationException.requireFileType(input, InputFileType.CLNX, InputFileType.VDJCA, InputFileType.SHMT)
        }
    }

    override fun run0() {
        out?.toAbsolutePath()?.parent?.createDirectories()

        val fieldExtractors = ReportFieldsExtractors.createExtractors(addedFields, HeaderForExport.empty)
        InfoWriter.create(out, fieldExtractors, !noHeader) { RowMetaForExport.empty }.use { output ->
            inputFiles.forEach { input ->
                val footer = IOUtil.extractFooter(input)
                val upstreamReports = if (withoutUpstreams) {
                    emptyList()
                } else {
                    footer.reports.upstreams
                }
                (upstreamReports + footer.reports).forEach { stepReports ->
                    val stepsToExport = when (stepToExport) {
                        null -> stepReports.steps
                        !in stepReports.steps -> emptyList()
                        else -> listOf(stepToExport!!)
                    }
                    stepsToExport.forEach { step ->
                        val command = MiXCRCommandDescriptor.fromString(step)
                        stepReports[command].zip(stepReports.getTrees(step)).forEach { (report, json) ->
                            output.put(ReportWithSource(report, json, command, input))
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val COMMAND_NAME = "exportReportsTable"

        private const val inputsLabel = "(data.(vdjca|clns|clna|shmt)|directory)..."

        private const val outputLabel = "[report.tsv]"

        fun mkCommandSpec(): CommandSpec {
            val command = CommandExportReportsAsTable()
            val spec = CommandSpec.forAnnotatedObject(command)
            command.spec = spec // inject spec manually
            ReportFieldsExtractors.addOptionsToSpec(command.addedFields, spec)
            return spec
                .addPositional(
                    CommandLine.Model.PositionalParamSpec.builder()
                        .index("0")
                        .required(false)
                        .arity("0..*")
                        .type(Path::class.java)
                        .paramLabel(inputsLabel)
                        .hideParamSyntax(true)
                        .description(
                            "Path to input files or directories.",
                            "In case of directory no filter by file type will be applied."
                        )
                        .build()
                )
                .addPositional(
                    CommandLine.Model.PositionalParamSpec.builder()
                        .index("1")
                        .required(false)
                        .arity("0..*")
                        .type(Path::class.java)
                        .paramLabel(outputLabel)
                        .hideParamSyntax(true)
                        .description("Path where to write reports. Print in stdout if omitted.")
                        .build()
                )
        }

    }
}
