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
import picocli.CommandLine.Option
import java.io.File

abstract class ACommand(appName: String) : ABaseCommand(appName), Runnable {
    @Mixin
    private lateinit var logger: logger

    @Option(names = ["-f", "--force-overwrite"], description = ["Force overwrite of output file(s)."])
    var forceOverwrite = false

    @Option(names = ["--force"], hidden = true)
    fun setForce(value: Boolean) {
        if (value) {
            logger.warn("--force option is deprecated; use --force-overwrite instead.")
            forceOverwrite = true
        }
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
        logger.printWarningQueue()
        logger.running = true
        run0()
    }

    /** Do actual job  */
    abstract fun run0()
}
