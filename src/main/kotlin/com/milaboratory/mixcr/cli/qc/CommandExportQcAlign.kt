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
package com.milaboratory.mixcr.cli.qc

import com.milaboratory.app.InputFileType
import com.milaboratory.app.ValidationException
import com.milaboratory.miplots.writeFile
import com.milaboratory.mixcr.cli.CommonDescriptions.Labels
import com.milaboratory.mixcr.cli.exportTypes
import com.milaboratory.mixcr.qc.plots.AlignmentQC.alignQc
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.nio.file.Path

@Command(description = ["QC plot for alignments."])
class CommandExportQcAlign : CommandExportQc() {
    companion object {
        private const val inputsLabel = "sample.(vdjca|clns|clna)..."

        private const val outputLabel = "align.${Labels.EXPORT_TYPES}"

        fun mkCommandSpec(): CommandSpec = CommandSpec.forAnnotatedObject(CommandExportQcAlign::class.java)
            .addPositional(
                CommandLine.Model.PositionalParamSpec.builder()
                    .index("0")
                    .required(false)
                    .arity("0..*")
                    .type(Path::class.java)
                    .paramLabel(inputsLabel)
                    .hideParamSyntax(true)
                    .description("Paths to input files with alignments")
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
    var inOut: List<Path> = mutableListOf()

    @Option(
        names = ["--absolute-values"],
        description = ["Plot in absolute values instead of percent"],
        order = OptionsOrder.main + 30_000
    )
    var absoluteValues = false

    private val output get() = inOut.last()

    override val inputFiles
        get() = inOut.dropLast(1)

    override val outputFiles
        get() = listOf(output)


    override fun validate() {
        inputFiles.forEach { input ->
            ValidationException.requireFileType(input, InputFileType.VDJCA, InputFileType.CLNX)
        }
        ValidationException.requireFileType(output, InputFileType.exportTypes)
    }

    override fun run1() {
        val plt = alignQc(
            inputFiles.map { it },
            !absoluteValues,
            width = sizeParameters?.width,
            height = sizeParameters?.height,
        )
        writeFile(output, plt)
    }
}
