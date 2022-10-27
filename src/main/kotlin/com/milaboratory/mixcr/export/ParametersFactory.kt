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

import io.repseq.core.GeneFeature
import io.repseq.core.ReferencePoint

object ParametersFactory {
    fun tagParameter(
        sPrefix: String,
        sSuffix: String = ""
    ) = CommandArgRequired(
        "<tag_name>",
        { header, tagName -> tagName to header.tagsInfo.indexOf(tagName) }
    ) { (tagName, _) -> sPrefix + tagName + sSuffix }

    fun geneFeatureParam(sPrefix: String): CommandArgRequired<GeneFeature> = CommandArgRequired(
        "<gene_feature>",
        { _, arg -> GeneFeature.parse(arg) }
    ) { sPrefix + GeneFeature.encode(it) }

    fun relativeGeneFeatureParam(): CommandArgRequired<GeneFeature> = CommandArgRequired(
        "<relative_to_gene_feature>",
        { _, arg -> GeneFeature.parse(arg) }
    ) { "Relative" + GeneFeature.encode(it) }

    fun referencePointParam(
        meta: String = "<reference_point>",
        sPrefix: (String) -> String = { it }
    ): CommandArgRequired<ReferencePoint> = CommandArgRequired(
        meta,
        { _, arg -> ReferencePoint.parse(arg) },
        { sPrefix(ReferencePoint.encode(it, true)) }
    )

    fun referencePointParamOptional(
        meta: String = "<reference_point>",
        sPrefix: (String) -> String = { it }
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
        { _, arg -> ReferencePoint.parse(arg) },
        { sPrefix(ReferencePoint.encode(it, true)) }
    )
}
