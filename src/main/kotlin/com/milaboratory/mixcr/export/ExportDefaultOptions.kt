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

import com.milaboratory.mixcr.cli.ValidationException
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Option
import picocli.CommandLine.Spec
import picocli.CommandLine.Spec.Target.MIXEE

class ExportDefaultOptions : (List<ExportFieldDescription>) -> List<ExportFieldDescription> {
    @Option(
        description = ["Don't print first header line, print only data"],
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

    @Spec(MIXEE)
    private lateinit var spec: CommandSpec

    override fun invoke(defaultFields: List<ExportFieldDescription>): List<ExportFieldDescription> {
        val addedFields = CloneFieldsExtractorsFactory.parsePicocli(spec.commandLine().parseResult)
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
