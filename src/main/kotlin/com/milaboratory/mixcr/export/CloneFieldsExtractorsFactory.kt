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

import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.export.FieldExtractorsFactory.Order

object CloneFieldsExtractorsFactory : FieldExtractorsFactoryNew<Clone>() {
    override fun allAvailableFields(): List<Field<Clone>> =
        VDJCObjectFieldExtractors.vdjcObjectFields(forTreesExport = false) +
                cloneFields(forTreesExport = false)

    fun cloneFields(forTreesExport: Boolean): List<Field<Clone>> = buildList {
        this += FieldParameterless(
            Order.cloneSpecific + 100,
            "-cloneId",
            when {
                forTreesExport -> "Unique clone identifier in source sample file"
                else -> "Unique clone identifier"
            },
            "cloneId"
        ) { clone: Clone ->
            clone.id.toString()
        }
        this += FieldParameterless(
            Order.cloneSpecific + 200,
            "-count",
            when {
                forTreesExport -> "Export clone count in source sample file"
                else -> "Export clone count"
            },
            "cloneCount",
            deprecation = stdDeprecationNote("-count", "-readCount", true),
        ) { clone: Clone ->
            clone.count.toString()
        }
        this += FieldParameterless(
            Order.cloneSpecific + 201,
            "-readCount",
            when {
                forTreesExport -> "Number of reads assigned to the clonotype in source sample file"
                else -> "Number of reads assigned to the clonotype"
            },
            "readCount"
        ) { clone: Clone ->
            clone.count.toString()
        }
        this += FieldParameterless(
            Order.cloneSpecific + 300,
            "-fraction",
            when {
                forTreesExport -> "Export clone fraction in source sample file"
                else -> "Export clone fraction"
            },
            "cloneFraction",
            deprecation = stdDeprecationNote("-fraction", "-readFraction", true),
        ) { clone: Clone ->
            clone.fraction.toString()
        }
        this += FieldParameterless(
            Order.cloneSpecific + 301,
            "-readFraction",
            when {
                forTreesExport -> "Fraction of reads assigned to the clonotype in source sample file"
                else -> "Fraction of reads assigned to the clonotype"
            },
            "readFraction"
        ) { clone: Clone ->
            clone.fraction.toString()
        }
        this += FieldWithParameters(
            Order.tags + 500,
            "-uniqueTagFraction",
            "Fraction of unique tags (UMI, CELL, etc.) the clone or alignment collected.",
            tagParameter("unique", sSuffix = "Fraction"),
            validateArgs = { (tagName, idx) ->
                require(idx != -1) { "No tag with name $tagName" }
            }
        ) { clone: Clone, (_, idx) ->
            val level = idx + 1
            clone.getTagDiversityFraction(level).toString()
        }
        this += FieldParameterless(
            Order.tags + 600,
            "-cellGroup",
            "Cell group",
            "cellGroup"
        ) { clone: Clone ->
            clone.group.toString()
        }
    }

}
