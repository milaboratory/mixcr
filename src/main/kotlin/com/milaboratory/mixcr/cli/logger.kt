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

import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Option
import picocli.CommandLine.Spec
import picocli.CommandLine.Spec.Target.MIXEE

@Suppress("ClassName")
object logger {
    @Option(
        names = ["-nw", "--no-warnings"],
        description = ["Suppress all warning messages."],
        order = 1_000_000 - 2
    )
    var quiet = false

    @Option(
        description = ["Verbose warning messages."],
        names = ["--verbose"],
        order = 1_000_000 - 1
    )
    var verbose = false

    @Spec(MIXEE)
    lateinit var spec: CommandSpec

    /** queue of warning messages  */
    private val warningsQueue: MutableList<String> = ArrayList()

    /** flag that signals we are entered the run method  */
    var running = false

    fun warn(message: String) {
        val formattedMessage = "WARNING: $message"
        if (quiet) return
        if (!running) // add to a queue
            warningsQueue.add(formattedMessage) else  // print immediately
            printWarn(formattedMessage)
    }

    fun warnUnfomatted(message: String) {
        if (quiet) return
        if (!running) // add to a queue
            warningsQueue.add(message) else  // print immediately
            printWarn(message)
    }

    private fun printWarn(message: String) {
        if (!quiet) spec.commandLine().err.println(message)
    }

    fun printWarningQueue() {
        if (!quiet) {
            for (m in warningsQueue) printWarn(m)
        }
        warningsQueue.clear()
    }
}
