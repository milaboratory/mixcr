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
import com.milaboratory.mixcr.basictypes.IOUtil.MiXCRFileType.*
import com.milaboratory.mixcr.qc.ChainUsage.chainUsageAlign
import com.milaboratory.mixcr.qc.ChainUsage.chainUsageAssemble
import picocli.CommandLine
import picocli.CommandLine.*
import java.nio.file.Paths

@Command(name = "chainUsage", separator = " ", description = ["Chain usage plot."])
class CommandExportQcChainUsage : CommandExportQc() {
    @Parameters(description = ["sample1.[vdjca|clnx] ... usage.[pdf|eps|png|jpeg]"], arity = "2..*")
    var `in`: List<String> = mutableListOf()

    @Option(names = ["--absolute-values"], description = ["Plot in absolute values instead of percent"])
    var absoluteValues = false

    @Option(
        names = ["--align-chain-usage"],
        description = ["When specifying .clnx files on input force to plot chain usage for alignments"]
    )
    var alignChainUsage = false

    @Option(
        names = ["--hide-non-functional"],
        description = ["Hide fractions of non-functional CDR3s (out-of-frames and containing stops)"]
    )
    var hideNonFunctional = false

    override fun getInputFiles(): List<String> = `in`.subList(0, `in`.size - 1)

    override fun getOutputFiles(): List<String> = listOf(`in`.last())

    override fun run0() {
        val files = inputFiles.map { Paths.get(it) }
        val fileTypes = files.map { IOUtil.extractFileType(it) }
        if (fileTypes.distinct().size != 1) {
            throwExecutionExceptionKotlin("Input files should have the same file type, got ${fileTypes.distinct()}")
        }
        val fileType = fileTypes.first()
        val plot = when (fileType) {
            CLNA, CLNS -> when {
                alignChainUsage -> chainUsageAlign(
                    files,
                    !absoluteValues,
                    !hideNonFunctional,
                    sizeParameters
                )
                else -> chainUsageAssemble(
                    files,
                    !absoluteValues,
                    !hideNonFunctional,
                    sizeParameters
                )
            }
            VDJCA -> chainUsageAlign(
                files,
                !absoluteValues,
                !hideNonFunctional,
                sizeParameters
            )
            SHMT -> throw IllegalArgumentException("Can't export chain usage from .shmt file")
        }
        writeFile(Paths.get(outputFiles[0]), plot)
    }
}
