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
import picocli.CommandLine.Option
import picocli.CommandLine.Spec

open class ABaseCommand(protected val appName: String) {
    // injected by picocli
    @Spec
    lateinit var spec: CommandLine.Model.CommandSpec

    @Suppress("unused", "UNUSED_PARAMETER")
    @Option(names = ["-h", "--help"], usageHelp = true)
    fun requestHelp(b: Boolean) {
    }

    val commandLineArguments: String
        get() = spec.commandLine().parseResult.originalArgs().joinToString(" ")
}
