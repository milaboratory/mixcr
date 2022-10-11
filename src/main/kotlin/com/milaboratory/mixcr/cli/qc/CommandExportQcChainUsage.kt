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
import com.milaboratory.mixcr.cli.ValidationException
import com.milaboratory.mixcr.qc.ChainUsage.chainUsageAlign
import com.milaboratory.mixcr.qc.ChainUsage.chainUsageAssemble
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.nio.file.Path

@Command(name = "chainUsage", separator = " ", description = ["Chain usage plot."])
class CommandExportQcChainUsage : CommandExportQc() {
    @Parameters(description = ["sample1.[vdjca|clnx] ... usage.[pdf|eps|png|jpeg]"], arity = "2..*")
    var inOut: List<Path> = mutableListOf()

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

    override val inputFiles
        get() = inOut.subList(0, inOut.size - 1)

    override val outputFiles
        get() = listOf(inOut.last())

    override fun run0() {
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
        writeFile(outputFiles[0], plot)
    }
}
