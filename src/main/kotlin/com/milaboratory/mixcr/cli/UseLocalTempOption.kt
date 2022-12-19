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

import com.milaboratory.app.logger
import com.milaboratory.mixcr.cli.MiXCRCommand.OptionsOrder
import picocli.CommandLine.Option

class UseLocalTempOption {
    @Option(
        description = ["Put temporary files in the same folder as the output files."],
        names = ["--use-local-temp"],
        order = OptionsOrder.localTemp
    )
    var value = false

    @Suppress("unused", "UNUSED_PARAMETER")
    @Option(
        description = ["Use system temp folder for temporary files."],
        names = ["--use-system-temp"],
        hidden = true
    )
    fun useSystemTemp(value: Boolean) {
        logger.warn(
            "--use-system-temp is deprecated, it is now enabled by default, use --use-local-temp to invert the " +
                    "behaviour and place temporary files in the same folder as the output file."
        )
    }
}
