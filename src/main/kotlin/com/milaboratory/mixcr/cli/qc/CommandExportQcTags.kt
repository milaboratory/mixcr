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
import com.milaboratory.mixcr.MiXCRCommandDescriptor
import com.milaboratory.mixcr.basictypes.IOUtil
import com.milaboratory.mixcr.cli.MiXCRCommandWithOutputs
import com.milaboratory.mixcr.qc.TagRefinementQc
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension


@Command(description = ["Tag refinement statistics plots."])
class CommandExportQcTags : MiXCRCommandWithOutputs() {
    @Parameters(description = ["sample1.(vdjca|clns|clna|shmt) ... coverage.[pdf|eps|png|jpeg]"])
    var files: List<Path> = mutableListOf()

    @Option(names = ["--log"], description = ["Use log10 scale for y-axis"])
    var log = false

    override val inputFiles: List<Path>
        get() = files.subList(0, files.size - 1)

    override val outputFiles: List<Path>
        get() = listOf(files.last())

    private val out get() = outputFiles.last().toAbsolutePath()

    override fun run0() {
        val plots = inputFiles.mapNotNull { file ->
            val info = IOUtil.extractFileInfo(file)
            val report = info.footer.reports[MiXCRCommandDescriptor.refineTagsAndSort]
            if (report.isEmpty()) {
                println("No tag refinement report for $file; did you run refineTagsAndSort command?")
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
