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

import com.milaboratory.mixcr.trees.SHMTreesWriter
import picocli.CommandLine.Parameters

abstract class CommandExportShmTreesAbstract : MiXCRCommand() {
    @Parameters(
        arity = "2",
        description = ["trees.${SHMTreesWriter.shmFileExtension} output"]
    )
    open var inOut: List<String> = ArrayList()

    override fun getInputFiles(): List<String> = listOf(inOut.first())

    override fun getOutputFiles(): List<String> = listOf(inOut.last())

    protected val inputFile get() = inputFiles.first()
    protected val outputFile get() = outputFiles.first()

    override fun validate() {
        if (!inputFile.endsWith(".${SHMTreesWriter.shmFileExtension}")) {
            throwValidationException("Input file should have extension ${SHMTreesWriter.shmFileExtension}. Given $inputFile")
        }
    }
}
