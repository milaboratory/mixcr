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

import java.nio.file.Path
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.io.path.extension

class ValidationException(
    override val message: String,
    val printHelp: Boolean = false
) : RuntimeException() {
    companion object {
        fun requireXSV(path: Path?) {
            requireExtension("Require", path, "tsv", "csv")
        }

        fun requireJson(path: Path?) {
            requireExtension("Require", path, "json")
        }

        fun requireExtension(prefix: String, path: Path?, vararg extension: String) {
            require(path == null || path.extension in extension) {
                "$prefix ${extension.joinToString(" or ") { ".$it" }} file extension, got $path"
            }
        }

        @OptIn(ExperimentalContracts::class)
        inline fun require(value: Boolean, printHelp: Boolean = false, lazyMessage: () -> Any) {
            contract {
                returns() implies value
            }
            if (!value) {
                val message = lazyMessage()
                throw ValidationException(message.toString(), printHelp)
            }
        }

        @OptIn(ExperimentalContracts::class)
        inline fun <T : Any> requireNotNull(value: T?, printHelp: Boolean = false, lazyMessage: () -> Any): T {
            contract {
                returns() implies (value != null)
            }

            if (value == null) {
                val message = lazyMessage()
                throw ValidationException(message.toString(), printHelp)
            } else {
                return value
            }
        }
    }
}
