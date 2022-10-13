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

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

class ApplicationException(
    override val message: String,
    val printHelp: Boolean = false
) : RuntimeException() {

    companion object {
        @OptIn(ExperimentalContracts::class)
        inline fun check(value: Boolean, printHelp: Boolean = false, lazyMessage: () -> Any) {
            contract {
                returns() implies value
            }
            if (!value) {
                val message = lazyMessage()
                throw ApplicationException(message.toString(), printHelp)
            }
        }
    }
}
