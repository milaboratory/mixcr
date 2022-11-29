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

import picocli.CommandLine.Option
import java.nio.file.Path

object DebugDirOption {
    @Option(names = ["--debug-dir"], hidden = true)
    private var value: Path? = null

    @Volatile
    private var initialized = false

    operator fun invoke(function: (Path) -> Unit) {
        value?.let { value ->
            if (!initialized) {
                value.toFile().deleteRecursively()
                value.toFile().mkdirs()
                initialized = true
            }
            function(value)
        }
    }
}
