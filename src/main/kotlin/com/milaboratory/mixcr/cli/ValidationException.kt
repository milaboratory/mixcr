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
import java.nio.file.Paths
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.io.path.exists

class ValidationException(
    override val message: String,
    val printHelp: Boolean = false
) : RuntimeException() {
    companion object {
        fun requireFileExists(input: Path?, message: (Path) -> String = { "Input file $it doesn't exist." }) {
            if (input != null && !input.exists()) {
                throw ValidationException(message(input))
            }
        }

        fun requireFileType(path: Path?, fileType: InputFileType, vararg additional: InputFileType) {
            requireFileType(path, arrayOf(fileType) + additional)
        }

        fun requireFileType(path: Path?, fileTypes: Array<InputFileType>) {
            require(path == null || fileTypes.any { path.matches(it) }) {
                if (fileTypes.size == 1) {
                    "Require ${fileTypes.first().name.lowercase()} file type, got $path"
                } else {
                    "Require one of ${fileTypes.joinToString(", ") { it.name.lowercase() }} file types, got $path"
                }
            }
        }

        fun requireTheSameFileType(first: Path, another: Path, vararg fileTypes: InputFileType) {
            @Suppress("UNCHECKED_CAST")
            requireFileType(first, fileTypes as Array<InputFileType>)
            val fileType = fileTypes.first { it.matches(first) }
            require(another.matches(fileType)) {
                "$another must have the same extension as $first"
            }
        }

        fun requireNoExtension(input: String?) {
            if (input == null) return
            val asPath = Paths.get(input)
            require(InputFileType.values().none { asPath.matches(it) }) {
                "Must have no extension, got $input"
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
