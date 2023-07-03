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
import com.milaboratory.app.matches
import com.milaboratory.mixcr.basictypes.IOUtil
import com.milaboratory.mixcr.basictypes.tag.TagsInfo
import com.milaboratory.mixcr.export.ExportFieldDescription
import com.milaboratory.mixcr.export.InfoWriter
import com.milaboratory.mixcr.export.MetaForExport
import com.milaboratory.mixcr.export.ReportFieldsExtractors
import com.milaboratory.mixcr.export.ReportFieldsExtractors.ReportsWithSource
import com.milaboratory.mixcr.export.RowMetaForExport
import com.milaboratory.mixcr.presets.StepDataCollection
import com.milaboratory.mixcr.presets.allReports
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
    description = ["Export reports from files in tabular format."]
)
class CommandExportReportsAsTable : MiXCRCommandWithOutputs() {
    @Parameters(
        paramLabel = "$inputsLabel $outputLabel",
        index = "0",
        arity = "1..*",
        hidden = true
    )
    private lateinit var inOut: List<Path>

    @Suppress("unused")
    @Option(
        names = ["--with-upstreams"],
        description = ["Export upstream reports for sources of steps with several inputs, like `${CommandFindShmTrees.COMMAND_NAME}`."],
        arity = "0",
        order = OptionsOrder.main + 10_200,
        hidden = true
    )
    fun withUpstreams(@Suppress("UNUSED_PARAMETER") value: Boolean) {
        throw ValidationException("--with-upstreams is deprecated, now it's default behaviour")
    }

    @Option(
        names = ["--without-upstreams"],
        description = ["Don't export upstream reports for sources of steps with several inputs, like `${CommandFindShmTrees.COMMAND_NAME}`."],
        arity = "0",
        order = OptionsOrder.main + 10_201
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

    override fun run1() {
        out?.toAbsolutePath()?.parent?.createDirectories()

        val forExport = inputFiles.flatMap { input ->
            val footer = IOUtil.extractFooter(input)
            val upstreamRecords = when {
                withoutUpstreams -> emptyList()
                else -> footer.reports.collection.upstreamReportsWithSources()
            }
            upstreamRecords + ReportsWithSource(
                input.toString(),
                footer.reports.collection.allReports(),
                upstreamRecords.flatMap { it.reports }
            )
        }
        val metaForExport = MetaForExport(emptyList(), null, forExport.flatMap { it.reports })
        val fieldExtractors = ReportFieldsExtractors.createExtractors(addedFields, metaForExport)
        InfoWriter.create(out, fieldExtractors, !noHeader) {
            RowMetaForExport(TagsInfo.NO_TAGS, metaForExport, false)
        }.use { output ->
            forExport.forEach { output.put(it) }
        }
    }

    private fun StepDataCollection<MiXCRCommandReport>.upstreamReportsWithSources(): List<ReportsWithSource> =
        upstreamCollections.flatMap { (sourceName, collection) ->
            val upstreamReports = collection.upstreamReportsWithSources()
            upstreamReports + ReportsWithSource(
                sourceName,
                collection.allReports(),
                upstreamReports.flatMap { it.reports })
        }

    companion object {
        const val COMMAND_NAME = ReportFieldsExtractors.commandExportReportsAsTableName

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
