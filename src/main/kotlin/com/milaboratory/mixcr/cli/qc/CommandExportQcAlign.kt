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
package com.milaboratory.mixcr.cli.qc

import com.milaboratory.miplots.writeFile
import com.milaboratory.mixcr.qc.AlignmentQC.alignQc
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.nio.file.Path

@Command(description = ["QC plot for alignments."])
class CommandExportQcAlign : CommandExportQc() {
    companion object {
        private const val inputsLabel = "sample.vdjca..."

        private const val outputLabel = "align.(pdf|eps|png|jpeg)"

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

    @Option(names = ["--absolute-values"], description = ["Plot in absolute values instead of percent"])
    var absoluteValues = false

    override val inputFiles
        get() = inOut.subList(0, inOut.size - 1)

    override val outputFiles
        get() = listOf(inOut.last())

    override fun run0() {
        val plt = alignQc(
            inputFiles.map { it },
            !absoluteValues,
            sizeParameters
        )
        writeFile(outputFiles.first(), plt)
    }
}
