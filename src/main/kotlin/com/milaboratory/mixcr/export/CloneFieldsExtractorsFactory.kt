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
            "Unique clone identifier",
            "Clone ID",
            "cloneId"
        ) { clone: Clone ->
            clone.id.toString()
        }
        this += FieldParameterless(
            Order.cloneSpecific + 200,
            "-count",
            "Export clone count",
            "Clone count",
            "cloneCount",
            deprecation = stdDeprecationNote("-count", "-readCount", true),
        ) { clone: Clone ->
            clone.count.toString()
        }
        this += FieldParameterless(
            Order.cloneSpecific + 201,
            "-readCount",
            "Number of reads assigned to the clonotype",
            "Read Count",
            "readCount"
        ) { clone: Clone ->
            clone.count.toString()
        }
        if (!forTreesExport) {
            this += FieldParameterless(
                Order.cloneSpecific + 300,
                "-fraction",
                "Export clone fraction",
                "Clone fraction",
                "cloneFraction",
                deprecation = stdDeprecationNote("-fraction", "-readFraction", true),
            ) { clone: Clone ->
                clone.fraction.toString()
            }
            this += FieldParameterless(
                Order.cloneSpecific + 301,
                "-readFraction",
                "Fraction of reads assigned to the clonotype",
                "Read fraction",
                "readFraction"
            ) { clone: Clone ->
                clone.fraction.toString()
            }
        }
        this += FieldWithParameters(
            Order.tags + 500,
            "-uniqueTagFraction",
            "Fraction of unique tags (UMI, CELL, etc.) the clone or alignment collected.",
            tagParameter("Unique ", "unique", hSuffix = " fraction", sSuffix = "Fraction"),
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
            "Cell group",
            "cellGroup"
        ) { clone: Clone ->
            clone.group.toString()
        }
    }

}
