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
import java.nio.file.Paths

@CommandLine.Command(name = "align", separator = " ", description = ["QC plot for alignments."])
class CommandExportQcAlign : CommandExportQc() {
    @CommandLine.Parameters(description = ["sample1.vdjca ... align.[pdf|eps|png|jpeg]"], arity = "2..*")
    var `in`: List<String> = mutableListOf()

    @CommandLine.Option(names = ["--absolute-values"], description = ["Plot in absolute values instead of percent"])
    var absoluteValues = false

    override val inputFiles: List<String>
        get() = `in`.subList(0, `in`.size - 1)

    override val outputFiles: List<String>
        get() = listOf(`in`.last())

    override fun run0() {
        val plt = alignQc(
            inputFiles.map { Paths.get(it) },
            !absoluteValues,
            sizeParameters
        )
        writeFile(Paths.get(outputFiles.first()), plt)
    }
}
