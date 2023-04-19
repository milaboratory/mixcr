/*
 * Copyright (c) 2014-2023, MiLaboratories Inc. All Rights Reserved
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
import com.milaboratory.mixcr.cli.CommonDescriptions.DEFAULT_VALUE_FROM_PRESET
import com.milaboratory.mixcr.cli.MiXCRCommand.OptionsOrder
import com.milaboratory.mixcr.export.ExportFieldDescription
import picocli.CommandLine.Option

class ExportDefaultOptions : (List<ExportFieldDescription>) -> List<ExportFieldDescription> {
    @Option(
        description = ["Don't print first header line, print only data", DEFAULT_VALUE_FROM_PRESET],
        names = ["--no-header"],
        order = OptionsOrder.exportOptions + 100
    )
    var noHeader = false

    @Option(
        description = ["Don't export fields from preset."],
        names = ["--drop-default-fields"],
        arity = "0",
        order = OptionsOrder.exportOptions + 200
    )
    private var dropDefaultFields: Boolean = false

    @Option(
        description = ["Added columns will be inserted before default columns. By default columns will be added after default columns"],
        names = ["--prepend-columns"],
        arity = "0",
        order = OptionsOrder.exportOptions + 300
    )
    private var prependColumns: Boolean? = null

    @Option(
        description = ["Export not covered regions as empty text."],
        names = ["--not-covered-as-empty"],
        arity = "0",
        order = OptionsOrder.exportOptions + 400
    )
    var notCoveredAsEmpty: Boolean = false

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
        ValidationException.requireNotEmpty(result) {
            "nothing to export, no fields are specified"
        }
        return result
    }
}
