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

import com.milaboratory.core.alignment.Alignment
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.export.AirrUtil.AirrAlignment
import io.repseq.core.GeneFeature
import io.repseq.core.GeneType
import io.repseq.core.ReferencePoint
import io.repseq.core.SequencePartitioning
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object AirrColumns {
    private fun airrStr(str: String?): String = str ?: ""

    private fun airrBoolean(value: Boolean?): String = if (value == null) "" else if (value) "T" else "F"

    class CloneId : FieldExtractor<AirrVDJCObjectWrapper> {
        override val header = "sequence_id"

        override fun extractValue(meta: RowMetaForExport, obj: AirrVDJCObjectWrapper): String =
            "clone." + obj.asClone().id
    }

    class AlignmentId : FieldExtractor<AirrVDJCObjectWrapper> {
        override val header = "sequence_id"

        override fun extractValue(meta: RowMetaForExport, obj: AirrVDJCObjectWrapper): String =
            "read." + obj.asAlignment().minReadId
    }

    class Sequence(private val targetId: Int) : FieldExtractor<AirrVDJCObjectWrapper> {
        override val header = "sequence"

        override fun extractValue(meta: RowMetaForExport, obj: AirrVDJCObjectWrapper): String {
            val resolvedTargetId = if (targetId == -1) obj.bestTarget else targetId
            return obj.`object`.getTarget(resolvedTargetId).sequence.toString()
        }
    }

    class RevComp : FieldExtractor<AirrVDJCObjectWrapper> {
        override val header = "rev_comp"

        override fun extractValue(meta: RowMetaForExport, obj: AirrVDJCObjectWrapper): String = "F"
    }

    class Productive : FieldExtractor<AirrVDJCObjectWrapper> {
        override val header = "productive"

        override fun extractValue(meta: RowMetaForExport, obj: AirrVDJCObjectWrapper): String {
            val cdr3nt = obj.`object`.getFeature(GeneFeature.CDR3)
            val cdr3aa = obj.`object`.getAAFeature(GeneFeature.CDR3)
            return airrBoolean(
                cdr3nt != null && cdr3aa != null && cdr3nt.size() % 3 == 0 && !cdr3aa.containStops()
            )
        }
    }

    class VDJCCalls(private val gt: GeneType) : FieldExtractor<AirrVDJCObjectWrapper> {
        override val header = gt.letterLowerCase.toString() + "_call"

        override fun extractValue(meta: RowMetaForExport, obj: AirrVDJCObjectWrapper): String =
            obj.`object`.getHits(gt).joinToString(",") { it.gene.name }
    }

    abstract class AirrAlignmentExtractor(private val targetId: Int, protected val withPadding: Boolean) :
        FieldExtractor<AirrVDJCObjectWrapper> {
        abstract fun extractValue(obj: AirrAlignment): String?
        override fun extractValue(meta: RowMetaForExport, obj: AirrVDJCObjectWrapper): String {
            val resolvedTargetId = if (targetId == -1) obj.bestTarget else targetId
            val alignment = obj.getAirrAlignment(resolvedTargetId, withPadding) ?: return ""
            return airrStr(extractValue(alignment))
        }
    }

    class SequenceAlignment(targetId: Int, withPadding: Boolean) : AirrAlignmentExtractor(targetId, withPadding) {
        override val header = "sequence_alignment"

        override fun extractValue(obj: AirrAlignment): String? = obj.getSequence(withPadding)
    }

    class GermlineAlignment(targetId: Int, withPadding: Boolean) : AirrAlignmentExtractor(targetId, withPadding) {
        override val header = "germline_alignment"

        override fun extractValue(obj: AirrAlignment): String? = obj.getGermline(withPadding)
    }

    interface ComplexReferencePoint {
        fun getPosition(partitioning: SequencePartitioning, referenceFeature: GeneFeature?): Int
    }

    class Single(private val point: ReferencePoint) : ComplexReferencePoint {
        override fun getPosition(partitioning: SequencePartitioning, referenceFeature: GeneFeature?): Int =
            if (referenceFeature == null) partitioning.getPosition(point) else partitioning.getRelativePosition(
                referenceFeature,
                point
            )

        override fun toString(): String = point.toString()
    }

    class Rightmost(vararg points: ReferencePoint) : ComplexReferencePoint {
        private val points: Array<ComplexReferencePoint> = Array(points.size) { i -> Single(points[i]) }

        override fun getPosition(partitioning: SequencePartitioning, referenceFeature: GeneFeature?): Int {
            var result = -1
            for (rp in points) {
                val position = rp.getPosition(partitioning, referenceFeature)
                if (position < 0) continue
                result = max(result, position)
            }
            return result
        }

        override fun toString(): String = "Rightmost{" + points.contentToString() + '}'
    }

    class Leftmost(
        vararg val points: ComplexReferencePoint
    ) : ComplexReferencePoint {

        constructor(vararg points: ReferencePoint) : this(*Array(points.size) { i ->
            Single(points[i])
        })

        override fun getPosition(partitioning: SequencePartitioning, referenceFeature: GeneFeature?): Int {
            var result = Int.MAX_VALUE
            for (rp in points) {
                val position = rp.getPosition(partitioning, referenceFeature)
                if (position < 0) continue
                result = min(result, position)
            }
            return if (result == Int.MAX_VALUE) -1 else result
        }

        override fun toString(): String = "Leftmost{" + points.contentToString() + '}'
    }

    class NFeatureFromAlign(
        targetId: Int, withPadding: Boolean,
        private val from: ComplexReferencePoint, private val to: ComplexReferencePoint,
        override val header: String
    ) : AirrAlignmentExtractor(targetId, withPadding) {

        override fun extractValue(obj: AirrAlignment): String? = obj.getSequence(from, to, withPadding)
    }

    abstract class NFeatureAbstract private constructor(
        /**
         * Used only for complex features
         */
        protected val targetId: Int,

        /**
         * Not null for simple gene features
         */
        protected val feature: GeneFeature?,

        /**
         * Not null for complex gene features.
         */
        protected val from: ComplexReferencePoint?,
        protected val to: ComplexReferencePoint?,
        final override val header: String
    ) : FieldExtractor<AirrVDJCObjectWrapper> {

        constructor(
            targetId: Int,
            from: ComplexReferencePoint, to: ComplexReferencePoint,
            header: String
        ) : this(targetId, null, from, to, header)

        constructor(feature: GeneFeature, header: String) : this(
            -1, // will not be used
            feature,
            null, null,
            header
        )

        override fun extractValue(meta: RowMetaForExport, obj: AirrVDJCObjectWrapper): String = when {
            feature != null -> when (val feature = obj.`object`.getFeature(feature)) {
                null -> ""
                else -> extractValue(feature.sequence)
            }

            else -> {
                val resolvedTargetId = if (targetId == -1) obj.bestTarget else targetId
                check(from != null && to != null)
                val partitioning = obj.`object`.getPartitionedTarget(resolvedTargetId).partitioning
                val fromPosition = from.getPosition(partitioning, null)
                val toPosition = to.getPosition(partitioning, null)
                when {
                    fromPosition < 0 -> ""
                    toPosition < 0 -> ""
                    else -> extractValue(
                        when {
                            fromPosition < toPosition -> obj.`object`.getTarget(resolvedTargetId)
                                .sequence.getRange(fromPosition, toPosition)
                            else -> NucleotideSequence.EMPTY
                        }
                    )
                }
            }
        }

        protected abstract fun extractValue(feature: NucleotideSequence): String
    }

    class NFeature : NFeatureAbstract {
        constructor(
            targetId: Int,
            from: ComplexReferencePoint, to: ComplexReferencePoint,
            header: String
        ) : super(targetId, from, to, header)

        constructor(feature: GeneFeature, header: String) : super(feature, header)

        override fun extractValue(feature: NucleotideSequence): String = feature.toString()
    }

    class NFeatureLength : NFeatureAbstract {
        constructor(
            targetId: Int,
            from: ComplexReferencePoint, to: ComplexReferencePoint,
            header: String
        ) : super(targetId, from, to, header)

        constructor(feature: GeneFeature, header: String) : super(feature, header)

        override fun extractValue(feature: NucleotideSequence): String = "" + feature.size()
    }

    class AAFeature(private val feature: GeneFeature, override val header: String) :
        FieldExtractor<AirrVDJCObjectWrapper> {

        override fun extractValue(meta: RowMetaForExport, obj: AirrVDJCObjectWrapper): String {
            val feature = obj.`object`.getAAFeature(feature) ?: return ""
            return feature.toString()
        }
    }

    abstract class AlignmentColumn(protected val targetId: Int, protected val geneType: GeneType) :
        FieldExtractor<AirrVDJCObjectWrapper> {
        override fun extractValue(meta: RowMetaForExport, obj: AirrVDJCObjectWrapper): String {
            val resolvedTargetId = if (targetId == -1) obj.bestTarget else targetId
            val bestHit = obj.`object`.getBestHit(geneType) ?: return ""
            val alignment = bestHit.getAlignment(resolvedTargetId) ?: return ""
            return extractValue(alignment)
        }

        abstract fun extractValue(alignment: Alignment<NucleotideSequence>): String
    }

    /**
     * Scoring for actual alignment in actual target, in contrast with normal MiXCR output where score shows the sum for
     * all targets, or an aggregated value for clonotypes.
     */
    class AlignmentScoring(targetId: Int, geneType: GeneType) : AlignmentColumn(targetId, geneType) {
        override val header = geneType.letterLowerCase.toString() + "_score"

        override fun extractValue(alignment: Alignment<NucleotideSequence>): String = "" + alignment.score
    }

    class AlignmentCigar(targetId: Int, geneType: GeneType) : AlignmentColumn(targetId, geneType) {
        override val header = geneType.letterLowerCase.toString() + "_cigar"

        override fun extractValue(alignment: Alignment<NucleotideSequence>): String = alignment.getCigarString(true)
    }

    class SequenceAlignmentBoundary(targetId: Int, geneType: GeneType, val start: Boolean, val germline: Boolean) :
        AlignmentColumn(targetId, geneType) {
        override val header = geneType.letterLowerCase.toString() + "_" +
                (if (germline) "germline" else "sequence") + "_" +
                if (start) "start" else "end"

        override fun extractValue(alignment: Alignment<NucleotideSequence>): String {
            val range = if (germline) alignment.sequence1Range else alignment.sequence2Range
            return "" + if (start) range.lower + 1 else range.upper
        }
    }

    class AirrAlignmentBoundary(
        targetId: Int,
        withPadding: Boolean,
        private val geneType: GeneType,
        private val start: Boolean
    ) : AirrAlignmentExtractor(targetId, withPadding) {
        override val header = geneType.letterLowerCase.toString() + "_alignment_" +
                if (start) "start" else "end"

        override fun extractValue(obj: AirrAlignment): String {
            val range = obj.getRange(geneType, withPadding) ?: return ""
            return "" + if (start) range.lower + 1 else range.upper
        }
    }

    class CloneCount : FieldExtractor<AirrVDJCObjectWrapper> {
        override val header = "duplicate_count"

        override fun extractValue(meta: RowMetaForExport, obj: AirrVDJCObjectWrapper): String =
            "" + obj.asClone().count.roundToInt()
    }

    class CompleteVDJ(private val targetId: Int) : FieldExtractor<AirrVDJCObjectWrapper> {
        override val header = "complete_vdj"

        override fun extractValue(meta: RowMetaForExport, obj: AirrVDJCObjectWrapper): String {
            val resolvedTargetId = if (targetId == -1) obj.bestTarget else targetId
            val partitionedTarget = obj.`object`.getPartitionedTarget(resolvedTargetId)
            return airrBoolean(partitionedTarget.partitioning.isAvailable(GeneFeature.VDJRegion))
        }
    }
}
