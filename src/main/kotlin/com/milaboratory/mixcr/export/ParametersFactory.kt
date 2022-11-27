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

import com.milaboratory.mixcr.basictypes.tag.TagType
import com.milaboratory.mixcr.cli.ValidationException
import com.milaboratory.mixcr.trees.SHMTreeForPostanalysis.Base
import io.repseq.core.GeneFeature
import io.repseq.core.ReferencePoint
import java.util.*

object ParametersFactory {
    const val tagTypeDescription = "Tag type will be used for filtering tags for export."

    fun tagParam(
        sPrefix: String,
        sSuffix: String = ""
    ): CommandArgRequired<String> = CommandArgRequired(
        "<tag_name>",
        { tagName -> tagName }
    ) { tagName -> sPrefix + tagName + sSuffix }

    fun tagTypeParam(
        sPrefix: String = ""
    ): CommandArgRequired<TagType> = CommandArgRequired(
        "<(${TagType.values().joinToString("|")})>",
        { arg ->
            val tagType = TagType.valueOfCaseInsensitiveOrNull(arg)
            ValidationException.require(tagType != null) {
                "$cmdArgName: unexpected arg $arg, expecting one of ${TagType.values().joinToString(", ") { it.name }}"
            }
            tagType
        },
        { sPrefix + it.name }
    )

    fun geneFeatureParam(sPrefix: String): CommandArgRequired<GeneFeature> = CommandArgRequired(
        "<gene_feature>",
        { arg -> GeneFeature.parse(arg) }
    ) { sPrefix + GeneFeature.encode(it) }

    fun relativeGeneFeatureParam(): CommandArgRequired<GeneFeature> = CommandArgRequired(
        "<relative_to_gene_feature>",
        { arg -> GeneFeature.parse(arg) }
    ) { "Relative" + GeneFeature.encode(it) }

    fun referencePointParam(
        sPrefix: String = ""
    ): CommandArgRequired<ReferencePoint> = CommandArgRequired(
        "<reference_point>",
        { arg -> ReferencePoint.parse(arg) },
        { sPrefix + ReferencePoint.encode(it, true) }
    )

    fun referencePointParamOptional(
        meta: String = "<reference_point>",
        sPrefix: String = ""
    ): CommandArgOptional<ReferencePoint?> = CommandArgOptional(
        meta,
        { arg ->
            try {
                ReferencePoint.parse(arg)
                true
            } catch (e: java.lang.IllegalArgumentException) {
                false
            }
        },
        { arg -> ReferencePoint.parse(arg) },
        { sPrefix + ReferencePoint.encode(it, true) }
    )

    fun nodeTypeParam(sPrefix: String, withParent: Boolean = true): CommandArgRequired<Base> = when {
        withParent -> CommandArgRequired(
            "<(${Base.germline}|${Base.mrca}|${Base.parent})>",
            { arg ->
                ValidationException.require(Base.values().any { arg == it.name }) {
                    "$cmdArgName: unexpected arg $arg, expecting one of ${Base.values().joinToString(", ") { it.name }}"
                }
                Base.valueOf(arg)
            },
            { base -> sPrefix + base.name.replaceFirstChar { it.titlecase(Locale.getDefault()) } }
        )

        else -> CommandArgRequired(
            "<(${Base.germline}|${Base.mrca})>",
            { arg ->
                ValidationException.require(arg in arrayOf(Base.germline.name, Base.mrca.name)) {
                    "$cmdArgName: unexpected arg $arg, expecting ${Base.germline} or ${Base.mrca}"
                }
                Base.valueOf(arg)
            },
            { base -> sPrefix + base.name.replaceFirstChar { it.titlecase(Locale.getDefault()) } }
        )
    }

    fun nodeTypeParamOptional(sPrefix: String): CommandArgOptional<Base?> =
        CommandArgOptional(
            "<(${Base.germline}|${Base.mrca}|${Base.parent})>",
            { arg -> arg == "node" || Base.values().any { arg == it.name } },
            { arg ->
                ValidationException.require(arg == "node" || Base.values().any { arg == it.name }) {
                    "$cmdArgName: unexpected arg $arg, expecting ${Base.values().joinToString(", ") { it.name }}"
                }
                when (arg) {
                    "node" -> null
                    else -> Base.valueOf(arg)
                }
            },
            { base ->
                when {
                    base != null -> sPrefix + base.name.replaceFirstChar { it.titlecase(Locale.getDefault()) }
                    else -> ""
                }
            }
        )
}
