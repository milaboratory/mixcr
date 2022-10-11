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

import com.milaboratory.mixcr.trees.SHMTreesWriter.Companion.shmFileExtension
import picocli.CommandLine.Parameters
import java.nio.file.Path
import kotlin.io.path.extension

abstract class CommandExportShmTreesAbstract : MiXCRCommandWithOutputs() {
    @Parameters(
        index = "0",
        paramLabel = "trees.$shmFileExtension",
        hideParamSyntax = true,
        description = ["Input file produced by ${CommandFindShmTrees.COMMAND_NAME}."]
    )
    lateinit var input: Path

    override val inputFiles
        get() = listOf(input)

    override fun validate() {
        ValidationException.require(input.extension == shmFileExtension) {
            "Input file should have extension $shmFileExtension. Given $input"
        }
    }
}
