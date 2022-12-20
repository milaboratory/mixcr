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

import com.milaboratory.app.ApplicationException
import picocli.CommandLine.Command
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Parameters
import picocli.CommandLine.Spec
import picocli.CommandLine.Unmatched

@Command(
    hidden = true
)
class DeprecatedHelp : Runnable {
    @Parameters(index = "0", arity = "1")
    private lateinit var commandName: String

    @Unmatched
    private var unknown: List<String> = mutableListOf()

    @Spec
    private lateinit var spec: CommandSpec

    override fun run() {
        throw ApplicationException("Command 'help' is deprecated. Use `${spec.commandLine().parent.commandName} $commandName -h` instead")
    }
}
