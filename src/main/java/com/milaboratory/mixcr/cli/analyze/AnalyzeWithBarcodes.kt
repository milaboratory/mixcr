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

import com.milaboratory.mixcr.cli.CommandCorrectAndSortTags
import picocli.CommandLine.Option

abstract class AnalyzeWithBarcodes : AnalyzeBase() {
    @Option(
        names = ["--correctAndSortTagParameters"],
        description = ["Additional parameters for correctAndSortTagParameters step specified with double quotes."],
        arity = "1"
    )
    var correctAndSortTagsOverrides: List<String> = emptyList()
    val correctAndSortTagsOps = mutableListOf<String>()

    /** run correctAndSortTags */
    fun runCorrectAndSortTags(input: String, output: String): String {
        // reports & commons
        inheritOptions(correctAndSortTagsOps)
        // additional parameters
        correctAndSortTagsOps += correctAndSortTagsOverrides
        // add input output
        correctAndSortTagsOps += "$input $output"

        AnalyzeUtil.runCommand(CommandCorrectAndSortTags(), spec, correctAndSortTagsOps)

        return output
    }
}