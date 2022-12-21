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
import io.repseq.core.ReferencePoint.CDR1Begin
import io.repseq.core.ReferencePoint.CDR2Begin
import io.repseq.core.ReferencePoint.CDR3Begin
import io.repseq.core.ReferencePoint.CDR3End
import io.repseq.core.ReferencePoint.FR1Begin
import io.repseq.core.ReferencePoint.FR2Begin
import io.repseq.core.ReferencePoint.FR3Begin
import io.repseq.core.ReferencePoint.FR4Begin
import io.repseq.core.ReferencePoint.FR4End
import io.repseq.core.ReferencePoint.L1Begin
import io.repseq.core.ReferencePoint.L1End
import io.repseq.core.ReferencePoint.L2Begin
import io.repseq.core.ReferencePoint.encode

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
        to: ReferencePoint?,
        withCDR3: Boolean = true
    ): List<Array<String>> = geneFeaturesBetween(from, to, withCDR3)
        .flatten()
        .map { arrayOf(GeneFeature.encode(it)) }

    fun FieldsCollection<*>.warnIfFeatureNotCovered(
        header: MetaForExport,
        from: ReferencePoint?,
        to: ReferencePoint?,
        withCDR3: Boolean = true
    ) {
        header.geneFeaturesBetween(from, to, withCDR3)
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
            val asGeneFeatures = GeneFeatures.fromComposite(feature)
            if (header.allFullyCoveredBy.intersection(asGeneFeatures) != asGeneFeatures) {
                logger.warn("${GeneFeature.encode(feature)} is not covered ($cmdArgName ${GeneFeature.encode(feature)})")
            }
        }
    }

    private fun MetaForExport.geneFeaturesBetween(
        from: ReferencePoint?,
        to: ReferencePoint?,
        withCDR3: Boolean
    ): List<List<GeneFeature>> = referencePointsBetweenOrDefault(from, to, withCDR3)
        .map { it.zipWithNext { a, b -> GeneFeature(a, b) } }

    fun MetaForExport.referencePointsToExport(
        from: ReferencePoint?,
        to: ReferencePoint?
    ): List<Array<String>> = referencePointsBetweenOrDefault(from, to, withCDR3 = true)
        .flatten()
        .map { arrayOf(encode(it, true)) }

    private fun MetaForExport.referencePointsBetweenOrDefault(
        from: ReferencePoint?,
        to: ReferencePoint?,
        withCDR3: Boolean
    ): List<List<ReferencePoint>> = when {
        from != null && to != null -> referencePointsBetween(from, to, withCDR3)
        allFullyCoveredBy != null -> allFullyCoveredBy.features
            .flatMap { referencePointsBetween(it.firstPoint, it.lastPoint, withCDR3) }

        else -> referencePointsBetween(FR1Begin, FR4End, withCDR3)
    }

    private fun referencePointsBetween(
        from: ReferencePoint,
        to: ReferencePoint,
        withCDR3: Boolean
    ): List<List<ReferencePoint>> = when {
        withCDR3 -> listOf(referencePointsToExport.pointsBetween(from, to))
        else -> buildList {
            if (to <= referencePointsToExportBeforeCDR3.last()) {
                this += referencePointsToExportBeforeCDR3.pointsBetween(from, to)
            } else if (from <= referencePointsToExportBeforeCDR3.last()) {
                this += referencePointsToExportBeforeCDR3.pointsBetween(from, referencePointsToExportBeforeCDR3.last())
            }
            if (from >= referencePointsToExportAfterCDR3.first()) {
                this += referencePointsToExportAfterCDR3.pointsBetween(from, to)
            } else if (to >= referencePointsToExportAfterCDR3.first()) {
                this += referencePointsToExportAfterCDR3.pointsBetween(referencePointsToExportAfterCDR3.first(), to)
            }
        }
    }

    private fun Array<ReferencePoint>.pointsBetween(from: ReferencePoint, to: ReferencePoint): List<ReferencePoint> =
        listOf(from) + filter { from < it && it < to } + to

    private val referencePointsToExport = arrayOf(
        L1Begin,
        L1End,
        L2Begin,
        FR1Begin,
        CDR1Begin,
        FR2Begin,
        CDR2Begin,
        FR3Begin,
        CDR3Begin,
        FR4Begin,
        FR4End
    )

    private val referencePointsToExportBeforeCDR3 = arrayOf(
        L1Begin,
        L1End,
        L2Begin,
        FR1Begin,
        CDR1Begin,
        FR2Begin,
        CDR2Begin,
        FR3Begin,
        CDR3Begin
    )

    private val referencePointsToExportAfterCDR3 = arrayOf(
        CDR3End,
        FR4End
    )
}
