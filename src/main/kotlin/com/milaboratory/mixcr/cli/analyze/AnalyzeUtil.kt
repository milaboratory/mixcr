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
package com.milaboratory.mixcr.cli.analyze

import com.milaboratory.mixcr.cli.MiXCRCommand
import picocli.CommandLine
import picocli.CommandLine.Model.CommandSpec

internal object AnalyzeUtil {
    private fun flatten(args: List<String>) = args
        .flatMap { it.split("\n") }
        .flatMap { it.split(" ") }
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    fun <T : MiXCRCommand> runCommand(obj: T, spec: CommandSpec, args: List<String>) {
        obj.spec = spec
        CommandLine(obj).parseArgs(*flatten(args).toTypedArray())
        obj.run()
    }

    fun runCommand(obj: CommandSpec, args: List<String>) {
        val cmd = CommandLine(obj)
        cmd.parseArgs(*flatten(args).toTypedArray())
        (cmd.commandSpec.userObject() as MiXCRCommand).run()
    }
}