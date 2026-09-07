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
import com.milaboratory.util.TempFileDest
import com.milaboratory.util.TempFileManager
import picocli.CommandLine.Option
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

class UseLocalTempOption {
    @Option(
        description = ["Put temporary files, such as sort spills, in the specified folder, creating " +
                "it if needed. In analyze, defaults to the intermediates location when those are " +
                "relocated, and to the system temp folder otherwise. Takes precedence over " +
                "--use-local-temp. Steps that MiXCR delegates to mitool do not take a folder: their " +
                "scratch data sits next to their own output when intermediates are relocated or " +
                "--use-local-temp is given, and in the system temp folder otherwise."],
        names = ["--temp-dir"],
        paramLabel = "<path>",
        order = OptionsOrder.tempDir
    )
    var tempDir: Path? = null

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

    /**
     * Destination for scratch data of a step writing [outputFile].
     *
     * [additionalPrefix] separates the destinations of steps sharing a folder. A destination claims every
     * file whose name starts with its prefix, so the prefix must not be a prefix of unrelated files.
     */
    fun tempDestination(outputFile: Path, additionalPrefix: String): TempFileDest =
        when (val dir = tempDir) {
            null -> TempFileManager.smartTempDestination(outputFile, additionalPrefix, !value)
            else -> {
                if (!dir.exists()) dir.createDirectories()
                TempFileDest.filesWithPrefix(dir, "tmp." + outputFile.fileName + additionalPrefix, true)
            }
        }
}
