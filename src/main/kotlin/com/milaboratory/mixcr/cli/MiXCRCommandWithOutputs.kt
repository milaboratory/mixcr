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

import picocli.CommandLine.Option
import java.nio.file.Path
import kotlin.io.path.exists

abstract class MiXCRCommandWithOutputs : MiXCRCommand() {
    @Option(
        names = ["-f", "--force-overwrite"],
        description = ["Force overwrite of output file(s)."],
        order = OptionsOrder.forceOverride
    )
    var forceOverwrite = false

    @Suppress("unused")
    @Option(names = ["--force"], hidden = true)
    fun setForce(value: Boolean) {
        if (value) {
            logger.warn("--force option is deprecated; use --force-overwrite instead.")
            forceOverwrite = true
        }
    }

    /** list of input files  */
    protected abstract val inputFiles: List<Path>

    /** list of output files produces as result  */
    protected abstract val outputFiles: List<Path>

    /** Intended to be overridden this to change validation behaviour  */
    protected open fun inputsMustExist(): Boolean = true

    private fun validateInputsOutputs() {
        if (inputsMustExist()) {
            for (input in inputFiles) {
                if (!input.exists()) {
                    throw ValidationException("ERROR: input file \"$input\" does not exist.")
                }
            }
        }
        for (out in outputFiles) {
            if (out.exists()) {
                handleExistenceOfOutputFile(out)
            }
        }
    }

    /** Specifies behaviour in the case with output exists (default is to throw exception)  */
    private fun handleExistenceOfOutputFile(outFile: Path) {
        if (!forceOverwrite)
            throw ValidationException("File \"$outFile\" already exists. Use -f / --force-overwrite option to overwrite it.")
    }

    final override fun run() {
        validateInputsOutputs()
        super.run()
    }
}
