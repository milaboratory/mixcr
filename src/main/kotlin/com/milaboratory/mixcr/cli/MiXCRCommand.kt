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

    object OptionsOrder {
        const val main = 10_000

        const val notAligned = 90_000

        const val width = 100_000
        const val height = 100_000 + 100

        @Suppress("ClassName")
        object mixins {
            private const val begin = 200_000

            const val pipeline = begin + 10_000
            const val align = begin + 20_000
            const val assemble = begin + 30_000
            const val assembleContigs = begin + 40_000
            const val exports = begin + 50_000
        }

        const val exportOptions = 490_000

        const val overrides = 500_000

        const val report = 1_000_000 + 1_000
        const val localTemp = 1_000_000 + 2_000
        const val threads = 1_000_000 + 3_000
        const val forceOverride = 1_000_000 + 4_000
        const val logger = 1_000_000 + 5_000
        const val help = 1_000_000 + 6_000
    }
}
