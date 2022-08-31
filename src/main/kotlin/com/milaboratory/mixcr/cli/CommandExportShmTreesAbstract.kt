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
import picocli.CommandLine

abstract class CommandExportShmTreesAbstract : MiXCRCommand() {
    @CommandLine.Parameters(index = "0", description = ["trees.${SHMTreesWriter.shmFileExtension}"])
    lateinit var `in`: String

    abstract var out: String

    override fun getInputFiles(): List<String> = listOf(`in`)

    override fun getOutputFiles(): List<String> = listOf(out)

    override fun validate() {
        super.validate()
        if (!`in`.endsWith(".${SHMTreesWriter.shmFileExtension}")) {
            throwValidationExceptionKotlin("Input file should have extension ${SHMTreesWriter.shmFileExtension}. Given $`in`")
        }
    }
}
