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
package com.milaboratory.mixcr.export

import com.milaboratory.mixcr.cli.CommonDescriptions.DEFAULT_VALUE_FROM_PRESET
import com.milaboratory.mixcr.cli.ValidationException
import picocli.CommandLine.Option

class ExportDefaultOptions : (List<ExportFieldDescription>) -> List<ExportFieldDescription> {
    @Option(
        description = ["Don't print first header line, print only data", DEFAULT_VALUE_FROM_PRESET],
        names = ["--no-header"]
    )
    var noHeader = false

    @Option(
        description = ["Don't export fields from preset."],
        names = ["--drop-default-fields"],
        arity = "0"
    )
    private var dropDefaultFields: Boolean = false

    @Option(
        description = ["Added columns will be inserted before default columns. By default columns will be added after default columns"],
        names = ["--prepend-columns"],
        arity = "0"
    )
    private var prependColumns: Boolean? = null

    val addedFields: MutableList<ExportFieldDescription> = mutableListOf()

    override fun invoke(defaultFields: List<ExportFieldDescription>): List<ExportFieldDescription> {
        if (prependColumns != null) {
            ValidationException.require(!dropDefaultFields) {
                "--prepend-columns has no meaning if --drop-default-fields is set"
            }
        }
        if (addedFields.isEmpty()) {
            ValidationException.require(prependColumns == null) {
                "--prepend-columns is set but no fields was added"
            }
        }

        val result = when {
            dropDefaultFields -> addedFields
            else -> when (prependColumns) {
                true -> addedFields + defaultFields
                else -> defaultFields + addedFields
            }
        }
        ValidationException.require(result.isNotEmpty()) {
            "nothing to export, no fields are specified"
        }
        return result
    }
}
