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
package com.milaboratory.mixcr.cli.analyze

import com.milaboratory.mixcr.cli.CommandAssembleContigs
import picocli.CommandLine.Option

abstract class AnalyzeWithBarcodesWithContigs : AnalyzeWithBarcodes() {
    @Option(
        names = ["--assembleContigs"],
        description = ["Additional parameters for assemble contigs step specified with double quotes"],
        arity = "1"
    )
    var assembleContigsOverrides: List<String> = emptyList()
    val assembleContigsOps = mutableListOf<String>()

    /** run assembleContigs */
    fun runAssembleContigs(input: String, output: String): String {
        // reports & commons
        inheritOptions(assembleContigsOps)
        // additional parameters
        assembleContigsOps += assembleContigsOverrides
        // add input output
        assembleContigsOps += "$input $output"

        AnalyzeUtil.runCommand(CommandAssembleContigs(), spec, assembleContigsOps)

        return output
    }
}