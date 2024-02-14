/*
 * Copyright (c) 2014-2024, MiLaboratories Inc. All Rights Reserved
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

import com.milaboratory.app.ValidationException
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.nameWithoutExtension

object OutputTemplate {
    const val description = "Output template may contain {file_name} and {file_dir_path},%n" +
            "outputs for '-o /output/folder/{file_name}_suffix.clns input_file.clns input_file2.clns' will be /output/folder/input_file_suffix.clns and /output/folder/input_file2_suffix.clns,%n" +
            "outputs for '-o {file_dir_path}/{file_name}_suffix.clns /some/folder1/input_file.clns /some/folder2/input_file2.clns' will be /seme/folder1/input_file_suffix.clns and /some/folder2/input_file2_suffix.clns%n" +
            "Resulted outputs must be uniq"

    fun calculateOutputs(template: String, inputFiles: List<Path>): List<Path> {
        val outputFiles = inputFiles
            .map { it.toAbsolutePath() }
            .map { path ->
                template
                    .replace(Regex("\\{file_name}"), path.nameWithoutExtension)
                    .replace(Regex("\\{file_dir_path}"), path.parent.toString())
            }
            .map { Paths.get(it) }
            .toList()
        if (outputFiles.distinct().count() < outputFiles.size) {
            var message = "Output files are not uniq: $outputFiles"
            message += "\nTry to use `{file_name}` and/or `{file_dir_path}` in template to get different output paths for every input. See help for more details"
            throw ValidationException(message)
        }
        return outputFiles
    }
}
