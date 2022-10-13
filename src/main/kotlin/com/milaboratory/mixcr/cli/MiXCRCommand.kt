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

import picocli.CommandLine.Mixin
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Spec

abstract class MiXCRCommand : Runnable {
//    @Suppress("unused", "UNUSED_PARAMETER")
//    @Option(names = ["-h", "--help"], usageHelp = true, description = ["Show this help message and exit."])
//    fun requestHelp(b: Boolean) {
//    }

    // injected by picocli
    @Spec
    lateinit var spec: CommandSpec

    val commandLineArguments: String
        get() = spec.commandLine().parseResult.originalArgs().joinToString(" ")

    @Mixin
    private lateinit var logger: logger

    /** Validate injected parameters and options */
    open fun validate() {
    }

    /** Initialize object after all params injected and validation */
    open fun initialize() {
    }

    override fun run() {
        validate()
        initialize()
        logger.printWarningQueue()
        logger.running = true
        run0()
    }

    /** Do actual job  */
    abstract fun run0()
}
