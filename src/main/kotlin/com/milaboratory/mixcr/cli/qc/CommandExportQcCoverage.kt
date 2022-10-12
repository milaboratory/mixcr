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

import com.milaboratory.miplots.ExportType
import com.milaboratory.miplots.writeFile
import com.milaboratory.miplots.writePDF
import com.milaboratory.mixcr.cli.AbstractMiXCRCommand
import com.milaboratory.mixcr.qc.Coverage.coveragePlot
import com.milaboratory.primitivio.flatten
import com.milaboratory.primitivio.mapInParallelOrdered
import com.milaboratory.primitivio.port
import com.milaboratory.primitivio.toList
import picocli.CommandLine
import picocli.CommandLine.*
import java.nio.file.Paths

@Command(name = "coverage", separator = " ", description = ["Reads coverage plots."])
class CommandExportQcCoverage : AbstractMiXCRCommand() {
    @Parameters(description = ["sample1.vdjca ... coverage.[pdf|eps|png|jpeg]"])
    var `in`: List<String> = mutableListOf()

    @Option(names = ["--show-boundaries"], description = ["Show V alignment begin and J alignment end"])
    var showAlignmentBoundaries = false
    override fun getInputFiles(): List<String> = `in`.subList(0, `in`.size - 1)

    override fun getOutputFiles(): List<String> = listOf(`in`.last())

    override fun run0() {
        val inputFiles = inputFiles.map { Paths.get(it) }
        val plots = inputFiles.port
            .mapInParallelOrdered(Runtime.getRuntime().availableProcessors()) {
                coveragePlot(it, showAlignmentBoundaries)
            }
            .flatten()
            .toList()
        val out = Paths.get(outputFiles.first())
        if (ExportType.determine(out) === ExportType.PDF) {
            writePDF(out, plots)
        } else {
            plots.forEachIndexed { i, plt ->
                var outStr = outputFiles.first()
                val l = outStr.lastIndexOf(".")
                val suffix = if (i < 3) "R$i" else "Overlap"
                outStr = "${outStr.substring(0, l)}_$suffix${outStr.substring(l)}"
                writeFile(Paths.get(outStr), plt)
            }
        }
    }
}
