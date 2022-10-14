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
import com.milaboratory.mixcr.qc.SizeParameters
import picocli.CommandLine.Command
import picocli.CommandLine.Option

abstract class CommandExportQc : MiXCRCommandWithOutputs() {
    @Option(
        names = ["--width"],
        description = ["Plot width"],
        paramLabel = "<n>"
    )
    var width = -1

    @Option(
        names = ["--height"],
        description = ["Plot height"],
        paramLabel = "<n>"
    )
    var height = -1

    val sizeParameters: SizeParameters?
        get() = if (width != -1 && height != -1) SizeParameters(width, height) else null

    @Command(
        description = ["Export QC plots."],
        synopsisSubcommandLabel = "COMMAND"
    )
    class CommandExportQcMain
}
