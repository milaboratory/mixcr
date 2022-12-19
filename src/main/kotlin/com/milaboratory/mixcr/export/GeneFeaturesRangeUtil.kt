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

import com.milaboratory.app.logger
import com.milaboratory.mixcr.basictypes.GeneFeatures
import io.repseq.core.GeneFeature
import io.repseq.core.ReferencePoint

object GeneFeaturesRangeUtil {
    fun commonDescriptionForFeatures(
        command: String,
        vararg fields: Field<*>
    ): String {
        val resultFields = arrayOf("FR3", "CDR3", "FR4")
            .flatMap { feature ->
                fields.map { field -> "`${field.cmdArgName} $feature`" }
            }
            .joinToString(", ")
        return "for all gene features between specified reference points (in separate columns).%n" +
                "For example, `$command FR3Begin FR4End` will export $resultFields.%n" +
                "By default, boundaries will be got from analysis parameters if possible or `FR1Begin FR4End` otherwise."
    }

    fun commonDescriptionForReferencePoints(
        command: String,
        nFeatureField: Field<*>
    ): String = "for all reference points between specified (in separate columns).%n" +
            "For example, `$command FR3Begin FR4End` will export `${nFeatureField.cmdArgName} FR3Begin`, ${nFeatureField.cmdArgName} CDR3Begin`, ${nFeatureField.cmdArgName} CDR3End` and `${nFeatureField.cmdArgName} FR4End`.%n" +
            "By default, boundaries will be got from analysis parameters if possible or `FR1Begin FR4End` otherwise."

    fun MetaForExport.geneFeaturesBetweenArgs(
        from: ReferencePoint?,
        to: ReferencePoint?
    ): List<Array<String>> = geneFeaturesBetween(from, to)
        .flatten()
        .map { arrayOf(GeneFeature.encode(it)) }

    fun FieldsCollection<*>.warnIfFeatureNotCovered(
        header: MetaForExport,
        from: ReferencePoint?,
        to: ReferencePoint?
    ) {
        header.geneFeaturesBetween(from, to)
            .flatten()
            .forEach { feature ->
                warnIfFeatureNotCovered(header, feature)
            }
    }

    fun FieldsCollection<*>.warnIfFeatureNotCovered(
        header: MetaForExport,
        feature: GeneFeature
    ) {
        if (header.allFullyCoveredBy != null) {
            if (header.allFullyCoveredBy.intersection(feature) != GeneFeatures(feature)) {
                logger.warn("${GeneFeature.encode(feature)} is not covered ($cmdArgName ${GeneFeature.encode(feature)})")
            }
        }
    }

    private fun MetaForExport.geneFeaturesBetween(
        from: ReferencePoint?,
        to: ReferencePoint?
    ): List<List<GeneFeature>> = referencePointsBetweenOrDefault(from, to)
        .map { it.zipWithNext { a, b -> GeneFeature(a, b) } }

    fun MetaForExport.referencePointsToExport(
        from: ReferencePoint?,
        to: ReferencePoint?
    ): List<Array<String>> = referencePointsBetweenOrDefault(from, to)
        .flatten()
        .map { arrayOf(ReferencePoint.encode(it, true)) }

    private fun MetaForExport.referencePointsBetweenOrDefault(
        from: ReferencePoint?,
        to: ReferencePoint?
    ): List<List<ReferencePoint>> = when {
        from != null && to != null -> listOf(referencePointsBetween(from, to))
        allFullyCoveredBy != null -> allFullyCoveredBy.features
            .map { referencePointsBetween(it.firstPoint, it.lastPoint) }
        else -> listOf(referencePointsBetween(ReferencePoint.FR1Begin, ReferencePoint.FR4End))
    }

    private fun referencePointsBetween(
        from: ReferencePoint,
        to: ReferencePoint
    ): List<ReferencePoint> {
        val referencePointsBetween = referencePointsToExport
            .filter { from < it && it < to }
        return listOf(from) + referencePointsBetween + to
    }

    private val referencePointsToExport = arrayOf(
        ReferencePoint.L1Begin,
        ReferencePoint.L1End,
        ReferencePoint.L2Begin,
        ReferencePoint.FR1Begin,
        ReferencePoint.CDR1Begin,
        ReferencePoint.FR2Begin,
        ReferencePoint.CDR2Begin,
        ReferencePoint.FR3Begin,
        ReferencePoint.CDR3Begin,
        ReferencePoint.FR4Begin,
        ReferencePoint.FR4End
    )
}
