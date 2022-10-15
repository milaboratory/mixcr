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
package com.milaboratory.mixcr.cli

import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Parameters
import picocli.CommandLine.Spec
import java.io.PrintStream
import java.nio.file.Path
import kotlin.io.path.createDirectories

@Command(
    description = ["Export all help messages to directory"],
    hidden = true
)
class CommandExportHelp : Runnable {
    @Parameters(index = "0", arity = "1")
    private lateinit var outputDir: Path

    @Spec
    private lateinit var spec: CommandSpec

    override fun run() {
        outputDir.toFile().deleteRecursively()
        exportHelp(spec.parent().commandLine(), outputDir)
    }

    private fun exportHelp(commandLine: CommandLine, dir: Path) {
        dir.createDirectories()
        val main = dir.resolve("main.txt")
        commandLine.usage(PrintStream(main.toFile()))
        commandLine.help.subcommands().forEach { (commandName, help) ->
            val output = dir.resolve("$commandName.txt")
            help.commandSpec().commandLine().usage(PrintStream(output.toFile()))
            if (help.subcommands().isNotEmpty()) {
                exportHelp(help.commandSpec().commandLine(), dir.resolve(commandName))
            }
        }
    }
}
