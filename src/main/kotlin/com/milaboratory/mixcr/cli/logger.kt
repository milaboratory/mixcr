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

@Suppress("ClassName")
object logger {
    var noWarnings = false

    var verbose = false

    fun warn(message: String) {
        val formattedMessage = "WARNING: $message"
        printWarn(formattedMessage)
    }

    fun warnUnfomatted(message: String) {
        printWarn(message)
    }

    private fun printWarn(message: String) {
        if (!noWarnings)
            System.err.println(message)
    }
}
