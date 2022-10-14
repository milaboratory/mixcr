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
package com.milaboratory.mixcr.cli.qc

import com.milaboratory.mixcr.cli.MiXCRCommandWithOutputs
import picocli.CommandLine.ArgGroup
import picocli.CommandLine.Command
import picocli.CommandLine.Option

abstract class CommandExportQc : MiXCRCommandWithOutputs() {
    class SizeParameters {
        @Option(
            names = ["--width"],
            description = ["Plot width"],
            paramLabel = "<n>",
            required = true
        )
        var width = -1

        @Option(
            names = ["--height"],
            description = ["Plot height"],
            paramLabel = "<n>",
            required = true
        )
        var height = -1
    }

    @ArgGroup(exclusive = false)
    var sizeParameters: SizeParameters? = null

    @Command(
        description = ["Export QC plots."],
        synopsisSubcommandLabel = "COMMAND"
    )
    class CommandExportQcMain
}
