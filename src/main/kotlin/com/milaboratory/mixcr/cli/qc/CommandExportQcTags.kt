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
package com.milaboratory.mixcr.cli.qc

import com.milaboratory.app.InputFileType
import com.milaboratory.app.ValidationException
import com.milaboratory.miplots.ExportType
import com.milaboratory.miplots.writeFile
import com.milaboratory.miplots.writePDF
import com.milaboratory.mixcr.basictypes.IOUtil
import com.milaboratory.mixcr.cli.CommandRefineTagsAndSort
import com.milaboratory.mixcr.cli.CommonDescriptions.Labels
import com.milaboratory.mixcr.cli.MiXCRCommandWithOutputs
import com.milaboratory.mixcr.cli.exportTypes
import com.milaboratory.mixcr.presets.AnalyzeCommandDescriptor
import com.milaboratory.mixcr.qc.plots.TagRefinementQc
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension


@Command(description = ["Tag refinement statistics plots."])
class CommandExportQcTags : MiXCRCommandWithOutputs() {
    companion object {
        private const val inputsLabel = "sample.(vdjca|clns|clna)..."

        private const val outputLabel = "coverage.${Labels.EXPORT_TYPES}"

        fun mkCommandSpec(): CommandSpec = CommandSpec.forAnnotatedObject(CommandExportQcTags::class.java)
            .addPositional(
                CommandLine.Model.PositionalParamSpec.builder()
                    .index("0")
                    .required(false)
                    .arity("0..*")
                    .type(Path::class.java)
                    .paramLabel(inputsLabel)
                    .hideParamSyntax(true)
                    .description("Paths to input files")
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
                    .description("Path where to write output plots")
                    .build()
            )
    }


    @Parameters(
        index = "0",
        arity = "2..*",
        paramLabel = "$inputsLabel $outputLabel",
        hideParamSyntax = true,
        //help is covered by mkCommandSpec
        hidden = true
    )
    var files: List<Path> = mutableListOf()

    @Option(
        names = ["--log"],
        description = ["Use log10 scale for y-axis"],
        order = OptionsOrder.main + 10_100
    )
    var log = false

    override val inputFiles: List<Path>
        get() = files.subList(0, files.size - 1)

    override val outputFiles: List<Path>
        get() = listOf(files.last())

    private val out get() = outputFiles.last().toAbsolutePath()

    override fun validate() {
        inputFiles.forEach { input ->
            ValidationException.requireFileType(input, InputFileType.VDJCA, InputFileType.CLNX)
        }
        ValidationException.requireFileType(outputFiles.first(), InputFileType.exportTypes)
    }

    override fun run1() {
        val plots = inputFiles.mapNotNull { file ->
            val info = IOUtil.extractFileInfo(file)
            val report = info.footer.reports[AnalyzeCommandDescriptor.refineTagsAndSort]
            if (report.isEmpty()) {
                println("No tag refinement report for $file; did you run ${CommandRefineTagsAndSort.COMMAND_NAME} command?")
                null
            } else
                file to TagRefinementQc.tagRefinementQc(info)
        }.toMap()

        when (ExportType.determine(out)) {
            ExportType.PDF -> writePDF(out, plots.flatMap { it.value })
            else -> plots.forEach { (file, plts) ->
                plts.forEachIndexed { i, plt ->
                    val suffix = if (inputFiles.size == 1) "" else "." + file.nameWithoutExtension
                    writeFile(
                        out.parent.resolve(out.nameWithoutExtension + suffix + "." + i + "." + out.extension),
                        plt
                    )
                }
            }
        }
    }
}
