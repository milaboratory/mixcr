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

        fun requireTSV(path: Path?) {
            requireExtension("Require", path, "tsv")
        }

        fun requireJson(path: Path?) {
            requireExtension("Require", path, "json")
        }

        fun requireExtension(prefix: String, path: Path?, vararg extension: String) {
            require(path == null || path.extension in extension) {
                "$prefix ${extension.joinToString(" or ") { ".$it" }} file extension, got $path"
            }
        }

        fun requireDistinct(collection: Collection<Any?>, lazyMessage: () -> String) {
            check(collection.isNotEmpty())
            val different = collection.distinct()
            require(different.size == 1) {
                lazyMessage() + ", got $different"
            }
        }

        @OptIn(ExperimentalContracts::class)
        inline fun require(value: Boolean, printHelp: Boolean = false, lazyMessage: () -> String) {
            contract {
                returns() implies value
            }
            if (!value) {
                throw ValidationException(lazyMessage(), printHelp)
            }
        }

        @OptIn(ExperimentalContracts::class)
        inline fun <T : Any> requireNotNull(value: T?, printHelp: Boolean = false, lazyMessage: () -> String): T {
            contract {
                returns() implies (value != null)
            }

            if (value == null) {
                throw ValidationException(lazyMessage(), printHelp)
            } else {
                return value
            }
        }
    }
}
