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
import com.milaboratory.mixcr.basictypes.IOUtil
import com.milaboratory.mixcr.basictypes.IOUtil.MiXCRFileType.CLNA
import com.milaboratory.mixcr.basictypes.IOUtil.MiXCRFileType.CLNS
import com.milaboratory.mixcr.basictypes.IOUtil.MiXCRFileType.SHMT
import com.milaboratory.mixcr.basictypes.IOUtil.MiXCRFileType.VDJCA
import com.milaboratory.mixcr.cli.InputFileType
import com.milaboratory.mixcr.cli.ValidationException
import com.milaboratory.mixcr.qc.ChainUsage.chainUsageAlign
import com.milaboratory.mixcr.qc.ChainUsage.chainUsageAssemble
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.nio.file.Path

@Command(description = ["Chain usage plot."])
class CommandExportQcChainUsage : CommandExportQc() {
    companion object {
        private const val inputsLabel = "sample.(vdjca|clns|clna)..."

        private const val outputLabel = "usage.${InputFileType.exportTypesLabel}"

        fun mkCommandSpec(): CommandSpec = CommandSpec.forAnnotatedObject(CommandExportQcChainUsage::class.java)
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
    var inOut: List<Path> = mutableListOf()

    @Option(
        names = ["--absolute-values"],
        description = ["Plot in absolute values instead of percent"],
        order = OptionsOrder.main + 10_100
    )
    var absoluteValues = false

    @Option(
        names = ["--align-chain-usage"],
        description = ["When specifying .clnx files on input force to plot chain usage for alignments"],
        order = OptionsOrder.main + 10_200
    )
    var alignChainUsage = false

    @Option(
        names = ["--hide-non-functional"],
        description = ["Hide fractions of non-functional CDR3s (out-of-frames and containing stops)"],
        order = OptionsOrder.main + 10_300
    )
    var hideNonFunctional = false

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
        val fileTypes = inputFiles.map { IOUtil.extractFileType(it) }
        if (fileTypes.distinct().size != 1) {
            throw ValidationException("Input files should have the same file type, got ${fileTypes.distinct()}")
        }
        val plot = when (fileTypes.first()) {
            CLNA, CLNS -> when {
                alignChainUsage -> chainUsageAlign(
                    inputFiles,
                    !absoluteValues,
                    !hideNonFunctional,
                    sizeParameters
                )
                else -> chainUsageAssemble(
                    inputFiles,
                    !absoluteValues,
                    !hideNonFunctional,
                    sizeParameters
                )
            }
            VDJCA -> chainUsageAlign(
                inputFiles,
                !absoluteValues,
                !hideNonFunctional,
                sizeParameters
            )
            SHMT -> throw IllegalArgumentException("Can't export chain usage from .shmt file")
        }
        writeFile(output, plot)
    }
}
