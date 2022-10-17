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
import picocli.CommandLine.Model.UsageMessageSpec.SECTION_KEY_OPTION_LIST
import picocli.CommandLine.Model.UsageMessageSpec.SECTION_KEY_PARAMETER_LIST
import picocli.CommandLine.Option
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

    @Option(names = ["--md"])
    var md: Boolean = false

    @Spec
    private lateinit var spec: CommandSpec

    override fun run() {
        if (md) {
            System.setProperty("picocli.usage.width", "400")
        }
        outputDir.toFile().deleteRecursively()
        exportHelp(spec.parent().commandLine(), outputDir)
    }

    private fun exportHelp(commandLine: CommandLine, dir: Path) {
        dir.createDirectories()
        val main = dir.resolve("main.txt")
        commandLine.usage(PrintStream(main.toFile()))
        commandLine.help.subcommands().forEach { (commandName, subcommandHelp) ->
            val output = dir.resolve("$commandName.txt")
            if (md) {
                subcommandHelp.commandSpec().commandLine().usageHelpLongOptionsMaxWidth = 200

                val originalParametersRender =
                    subcommandHelp.commandSpec().commandLine().helpSectionMap[SECTION_KEY_PARAMETER_LIST]!!
                subcommandHelp.commandSpec().commandLine().helpSectionMap.put(SECTION_KEY_PARAMETER_LIST) { help ->
                    originalParametersRender.render(help)
                        .replace(Regex(""" {2,15}(\S+)\n? {3,15}([\S ]+)""")) {
                            "\n`${it.groups[1]!!.value}`\n: ${it.groups[2]!!.value}"
                        }
                        .replace(Regex(""": +"""), ": ")
                        .replace(Regex("""\n {15,50}"""), " ")
                }

                val originalOptionsRender =
                    subcommandHelp.commandSpec().commandLine().helpSectionMap[SECTION_KEY_OPTION_LIST]!!
                subcommandHelp.commandSpec().commandLine().helpSectionMap.put(SECTION_KEY_OPTION_LIST) { help ->
                    originalOptionsRender.render(help)
                        .replace(Regex(""" {2,10}(-\S+(, {1,2}\S+)?( {1,2}\(?(<\S+>\|?|none)+\)?(\[\S+])?)?)\n? {3,10}(.+)""")) {
                            "\n`${it.groups[1]!!.value}`\n: ${it.groups[6]!!.value}"
                        }
                        .replace(Regex(""": +"""), ": ")
                        .replace(Regex("""\n {15,50}"""), " ")
                        .replace(" ", " ")
                        .replace(Regex(""" {3,15}"""), "\n\n")
                }
            }
            subcommandHelp.commandSpec().commandLine().usage(PrintStream(output.toFile()))
            if (subcommandHelp.subcommands().isNotEmpty()) {
                exportHelp(subcommandHelp.commandSpec().commandLine(), dir.resolve(commandName))
            }
        }
    }
}
