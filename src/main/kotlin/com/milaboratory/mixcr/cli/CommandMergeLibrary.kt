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

import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import java.nio.file.Path
import kotlin.io.path.absolutePathString

@Command(
    description = ["Merge multiple reference libraries in one file"]
)
class CommandMergeLibrary : MiXCRCommandWithOutputs() {
    @Parameters(
        index = "0",
        arity = "2..*",
        paramLabel = "input.json[.gz]... output.json[.gz]",
    )
    var inOut: List<Path> = mutableListOf()
    private val output: Path get() = inOut.last()

    public override val inputFiles
        get() = inOut.dropLast(1)

    override val outputFiles
        get() = listOf(output)

    override fun run1() {
        val meCmd = mutableListOf("merge", "-f")
        meCmd += inOut.map { it.absolutePathString() }
        io.repseq.cli.Main.main(meCmd.toTypedArray())
    }
}