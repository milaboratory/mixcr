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

import com.milaboratory.cli.ACommand

abstract class MiXCRCommand : ACommand("mixcr") {
    fun throwValidationExceptionKotlin(message: String, printHelp: Boolean): Nothing {
        super.throwValidationException(message, printHelp)
        error(message)
    }

    fun throwValidationExceptionKotlin(message: String): Nothing {
        super.throwValidationException(message)
        error(message)
    }

    fun throwExecutionExceptionKotlin(message: String): Nothing {
        super.throwExecutionException(message)
        error(message)
    }
}
