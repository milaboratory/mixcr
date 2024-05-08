/*
 * Copyright (c) 2014-2024, MiLaboratories Inc. All Rights Reserved
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

import com.milaboratory.mitool.cli.main
import picocli.CommandLine.Command
import picocli.CommandLine.Unmatched

@Command(
    hidden = true,
    description = ["MiTool"]
)
class CommandMiToolDelegate : Runnable {
    @Unmatched
    val allParameters: List<String> = mutableListOf()


    override fun run() = main(allParameters.toTypedArray())
}
