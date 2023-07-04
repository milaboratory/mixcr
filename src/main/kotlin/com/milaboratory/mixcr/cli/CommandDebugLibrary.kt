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
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.nio.file.Path
import kotlin.io.path.absolutePathString

@Command(
    description = ["Debug reference library file"]
)
class CommandDebugLibrary : MiXCRCommandWithOutputs() {

    @set:Option(
        names = ["--all"],
        description = ["Show all genes (not only problematic ones)"],
        order = 23000
    )
    var showAll: Boolean = false

    @Parameters(
        description = ["Input library."],
        paramLabel = "library.json[.gz]",
        index = "0",
    )
    lateinit var input: Path

    override val inputFiles: List<Path> get() = listOf(input)
    override val outputFiles: List<Path> get() = emptyList()

    override fun run1() {
        val deCmd = mutableListOf("debug", "--all")
        if (!showAll)
            deCmd += "--problems"
        deCmd += listOf(input.absolutePathString())
        io.repseq.cli.Main.main(deCmd.toTypedArray())
    }
}