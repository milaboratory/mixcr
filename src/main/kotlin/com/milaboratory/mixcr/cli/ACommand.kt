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
import java.io.File

abstract class ACommand(appName: String) : ABaseCommand(appName), Runnable {
    /** queue of warning messages  */
    private val warningsQueue: MutableList<String> = ArrayList()

    /** flag that signals we are entered the run method  */
    private var running = false

    @CommandLine.Option(names = ["-nw", "--no-warnings"], description = ["Suppress all warning messages."])
    var quiet = false

    @CommandLine.Option(description = ["Verbose warning messages."], names = ["--verbose"])
    var verbose = false

    @CommandLine.Option(names = ["-f", "--force-overwrite"], description = ["Force overwrite of output file(s)."])
    var forceOverwrite = false

    @CommandLine.Option(names = ["--force"], hidden = true)
    fun setForce(value: Boolean) {
        if (value) {
            warn("--force option is deprecated; use --force-overwrite instead.")
            forceOverwrite = true
        }
    }

    /** Warning message  */
    fun warn(message: String) {
        if (quiet) return
        if (!running) // add to a queue
            warningsQueue.add(message) else  // print immediately
            printWarn(message)
    }

    private fun printWarn(message: String) {
        if (!quiet) System.err.println(message)
    }

    /** list of input files  */
    protected abstract val inputFiles: List<String>

    /** list of output files produces as result  */
    protected abstract val outputFiles: List<String>

    /** Intended to be overridden this to change validation behaviour  */
    protected open fun inputsMustExist(): Boolean {
        return true
    }

    /** Validate injected parameters and options  */
    open fun validate() {
        for (`in` in inputFiles) {
            if (!File(`in`).exists() && inputsMustExist()) {
                throw ValidationException("ERROR: input file \"$`in`\" does not exist.")
            }
        }
        for (f in outputFiles) if (File(f).exists()) handleExistenceOfOutputFile(f)
    }

    /** Specifies behaviour in the case with output exists (default is to throw exception)  */
    private fun handleExistenceOfOutputFile(outFileName: String) {
        if (!forceOverwrite)
            throw ValidationException("File \"$outFileName\" already exists. Use -f / --force-overwrite option to overwrite it.")
    }

    override fun run() {
        validate()
        if (!quiet) for (m in warningsQueue) printWarn(m)
        running = true
        try {
            run0()
        } catch (e: RuntimeException) {
            throw e
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    /** Do actual job  */
    @Throws(Exception::class)
    abstract fun run0()
}
