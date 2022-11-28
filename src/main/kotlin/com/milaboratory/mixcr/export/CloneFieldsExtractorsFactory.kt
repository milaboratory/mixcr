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
import com.milaboratory.mixcr.basictypes.tag.TagInfo
import com.milaboratory.mixcr.cli.CommonDescriptions.Labels
import com.milaboratory.mixcr.export.ParametersFactory.tagTypeDescription
import com.milaboratory.mixcr.export.ParametersFactory.tagTypeParam
import com.milaboratory.mixcr.export.ParametersFactory.tagTypeWithDeprecatedTagName
import com.milaboratory.mixcr.export.TagsUtil.checkTagExists
import com.milaboratory.mixcr.export.TagsUtil.checkTagTypeExists

object CloneFieldsExtractorsFactory : FieldExtractorsFactory<Clone>() {
    override fun allAvailableFields(): List<FieldsCollection<Clone>> =
        VDJCObjectFieldExtractors.vdjcObjectFields(forTreesExport = false) +
                cloneFields(forTreesExport = false)

    fun cloneFields(forTreesExport: Boolean): List<FieldsCollection<Clone>> = buildList {
        this += Field(
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
        this += Field(
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
        this += Field(
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
        this += Field(
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
        this += Field(
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
        val uniqueTagFractionField = Field(
            Order.tags + 500,
            "-uniqueTagFraction",
            "Fraction of unique tags (UMI, CELL, etc.) the clone or alignment collected.",
            tagTypeWithDeprecatedTagName(sPrefix = "unique", sSuffix = "Fraction"),
            validateArgs = { header, (tagName, tagType) ->
                tagType?.let { checkTagTypeExists(header, it) }
                tagName?.let { checkTagExists(header, it) }
            },
        ) { clone: Clone, (tagName, tagType) ->
            val tag: TagInfo = when {
                tagType != null -> tagsInfo.lastOrNull { it.type == tagType }
                else -> tagsInfo[tagName]
            } ?: return@Field NULL
            val level = tag.index + 1
            clone.getTagDiversityFraction(level).toString()
        }
        this += uniqueTagFractionField
        this += FieldsCollection(
            Order.tags + 501,
            "-allUniqueTagFractions",
            "Fractions of unique tags (i.e. CELL barcode or UMI sequence) for all available tags in separate columns.%n$tagTypeDescription",
            uniqueTagFractionField,
            tagTypeParam(),
            validateArgs = { header, tagType ->
                checkTagTypeExists(header, tagType)
            },
            deprecation = "`-allUniqueTagFractions ${Labels.TAG_TYPE}` deprecated use `-uniqueTagFraction ${Labels.TAG_TYPE}` instead"
        ) { tagType ->
            tagNamesWithType(tagType).map { arrayOf(it) }
        }


        this += Field(
            Order.tags + 600,
            "-cellGroup",
            "Cell group",
            "cellGroup"
        ) { clone: Clone ->
            clone.group.toString()
        }
    }

}
