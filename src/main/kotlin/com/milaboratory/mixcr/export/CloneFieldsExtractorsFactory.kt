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

object CloneFieldsExtractorsFactory : FieldExtractorsFactory<Clone>() {
    override fun allAvailableFields(): List<Field<Clone>> =
        VDJCObjectFieldExtractors.vdjcObjectFields(forTreesExport = false) + cloneFields()

    override val defaultPreset: String = "full"

    override val presets: Map<String, List<FieldCommandArgs>> = buildMap {
        this["min"] = listOf(
            FieldCommandArgs("-count")
        ) + VDJCObjectFieldExtractors.presets["min"]!!

        this["fullNoId"] = listOf(
            FieldCommandArgs("-count"),
            FieldCommandArgs("-fraction")
        ) + VDJCObjectFieldExtractors.presets["full"]!!

        this["fullNoIdImputed"] = listOf(
            FieldCommandArgs("-count"),
            FieldCommandArgs("-fraction")
        ) + VDJCObjectFieldExtractors.presets["fullImputed"]!!

        this["full"] = listOf(FieldCommandArgs("-cloneId")) + this["fullNoId"]!!
        this["fullImputed"] = listOf(FieldCommandArgs("-cloneId")) + this["fullNoIdImputed"]!!
    }

    fun cloneFields(): List<Field<Clone>> = buildList {
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
            "cloneCount"
        ) { clone: Clone ->
            clone.count.toString()
        }
        this += FieldParameterless(
            Order.cloneSpecific + 300,
            "-fraction",
            "Export clone fraction",
            "Clone fraction",
            "cloneFraction"
        ) { clone: Clone ->
            clone.fraction.toString()
        }
        this += FieldParameterless(
            Order.tags + 200,
            "-tagFractions",
            "All tags with fractions",
            "All tags",
            "tagFractions"
        ) { clone: Clone ->
            clone.tagFractions.toString()
        }
        this += FieldParameterless(
            Order.tags + 500,
            "-cellGroup",
            "Cell group",
            "Cell group",
            "cellGroup"
        ) { clone: Clone ->
            clone.group.toString()
        }
    }

}
