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

import com.milaboratory.core.Range
import com.milaboratory.core.alignment.Alignment
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.basictypes.MultiAlignmentHelper
import com.milaboratory.mixcr.basictypes.VDJCHit
import com.milaboratory.mixcr.basictypes.VDJCObject
import com.milaboratory.mixcr.export.AirrColumns.ComplexReferencePoint
import com.milaboratory.util.BitArray
import com.milaboratory.util.StringUtil
import io.repseq.core.GeneFeature
import io.repseq.core.GeneType
import io.repseq.core.GeneType.Joining
import io.repseq.core.GeneType.VDJC_REFERENCE
import io.repseq.core.GeneType.VDJ_REFERENCE
import io.repseq.core.GeneType.Variable
import io.repseq.core.ReferencePoint
import io.repseq.core.ReferencePoint.FR1Begin
import io.repseq.util.IMGTPadding
import java.util.*
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

object AirrUtil {
    const val PADDING_CHARACTER = '.'
    private const val NULL_PADDING_LENGTH = Int.MIN_VALUE

    /**
     * Pre-calculates AIRR style alignment data
     *
     * @param `object`    source data
     * @param targetId  target to build alignment from (AIRR format has no support for multi target sequence structures)
     * @param vdjRegion if true alignment will be forced to the boundaries of VDJRegion (in IMGT's sense, with FR4End bound to the reading frame)
     * @return pre-calculated alignment data
     */
    fun calculateAirrAlignment(obj: VDJCObject, targetId: Int, vdjRegion: Boolean): AirrAlignment? {
        val target = obj.getTarget(targetId).sequence
        val settings = MultiAlignmentHelper.Settings(
            markMatchWithSpecialLetter = false,
            lowerCaseMatch = false,
            lowerCaseMismatch = false,
            specialMatchChar = ' ',
            outOfRangeChar = ' '
        )
        val alignments = mutableListOf<MultiAlignmentHelper.AlignmentInput<NucleotideSequence>>()
        val actualGeneTypes = mutableListOf<GeneType>()
        var validPaddings = true
        // padding insertion positions are in germline coordinates
        val paddingsByGeneType: MutableMap<GeneType, List<IMGTPadding>> = EnumMap(GeneType::class.java)

        // positive numbers means imgt gaps must be added, negative - letters must be removed from the
        // corresponding side, compared to IMGT's VDJRegion
        // these numbers represent number of nucleotides in the germline sequence to fill up the sequence,
        // IMGTPadding's calculated must additionally be added
        var leftVDJPadding = NULL_PADDING_LENGTH
        var rightVDJPadding = NULL_PADDING_LENGTH
        // for positive values above these sequences represent a germline sequence ranges to be appended to tha alignment
        var leftVDJExtraRange: Range? = null
        var rightVDJExtraRange: Range? = null
        // and the sequences themselves
        var leftVDJExtra: NucleotideSequence? = null
        var rightVDJExtra: NucleotideSequence? = null
        // these positions are initially in germline coordinates, conversion to alignment position happens below
        var cdr3Begin = -1
        var cdr3End = -1
        val bestHits: EnumMap<GeneType, VDJCHit> = EnumMap(GeneType::class.java)
        for (gt in if (vdjRegion) VDJ_REFERENCE else VDJC_REFERENCE) {
            val bestHit = obj.getBestHit(gt) ?: continue
            var al = bestHit.getAlignment(targetId) ?: continue
            val gene = bestHit.gene
            actualGeneTypes.add(gt)
            var refGeneFeature = bestHit.alignedFeature

            // Incomplete V gene feature correction
            if (gt == Variable && FR1Begin < refGeneFeature.firstPoint) {
                val extensionFeature = GeneFeature(FR1Begin, refGeneFeature.firstPoint)
                if (gene.partitioning.isAvailable(extensionFeature)) {
                    val leftExtension = gene.getFeature(extensionFeature)
                    refGeneFeature = GeneFeature(extensionFeature, refGeneFeature)
                    al = Alignment(
                        leftExtension.concatenate(al.sequence1),
                        al.absoluteMutations.move(leftExtension.size()),
                        al.sequence1Range.move(leftExtension.size()),
                        al.sequence2Range,
                        al.score
                    )
                    // TODO update best hit ???
                }
            }
            bestHits[gt] = bestHit
            alignments.add(
                MultiAlignmentHelper.AlignmentInput(
                    bestHit.gene.name,
                    al.invert(target),
                    al.score.toInt(),
                    bestHit.score.toInt()
                )
            )

            // IMGT related code below
            val refGeneSequence = al.sequence1
            if (gt == Variable) {
                cdr3Begin = gene.partitioning.getRelativePosition(refGeneFeature, ReferencePoint.CDR3Begin)
                val fr1Begin = gene.partitioning.getRelativePosition(refGeneFeature, FR1Begin)
                if (fr1Begin >= 0) { // in case gene has no anchor point for the position
                    leftVDJPadding = al.sequence1Range.from - fr1Begin
                    if (leftVDJPadding > 0) {
                        leftVDJExtraRange = Range(fr1Begin, al.sequence1Range.from)
                        leftVDJExtra = refGeneSequence.getRange(leftVDJExtraRange)
                    }
                }
            }
            if (gt == Joining) {
                cdr3End = gene.partitioning.getRelativePosition(refGeneFeature, ReferencePoint.CDR3End)
                val fr4End = gene.partitioning.getRelativePosition(refGeneFeature, ReferencePoint.FR4End)
                if (fr4End >= 0) { // in case gene has no anchor point for the position
                    rightVDJPadding = fr4End - al.sequence1Range.to
                    if (rightVDJPadding > 0) {
                        rightVDJExtraRange = Range(al.sequence1Range.to, fr4End)
                        rightVDJExtra = refGeneSequence.getRange(al.sequence1Range.to, fr4End)
                    }
                }
            }
            if (!validPaddings) continue
            val germlinePaddings = IMGTPadding.calculateForSequence(gene.partitioning, refGeneFeature)
            if (germlinePaddings == null) validPaddings = false
            paddingsByGeneType[gt] = germlinePaddings
        }

        val helper: MultiAlignmentHelper = MultiAlignmentHelper.build(
            settings,
            Range(0, target.size()),
            "",
            target,
            *alignments.toTypedArray()
        )

        // merging alignments
        // output data
        var sequence = helper.subject.content
        // output data
        val germlineBuilder = StringBuilder()
        var size = helper.size()
        // output data
        var germlinePosition = IntArray(size)
        Arrays.fill(germlinePosition, -1)
        // output data
        var geneType = arrayOfNulls<GeneType>(size)
        // output data
        var match = BooleanArray(size)
        var firstAligned = -1
        var lastAligned = 0
        outer@ for (i in 0 until size) {
            for (gti in actualGeneTypes.indices) {
                if (helper.getQuery(gti)[i] != ' ') {
                    val gt = actualGeneTypes[gti]
                    germlineBuilder.append(helper.getQuery(gti)[i])
                    germlinePosition[i] = helper.getAbsQueryPositionAt(gti, i)
                    geneType[i] = gt
                    match[i] = helper.match[gti][i]
                    if (firstAligned == -1) firstAligned = i
                    lastAligned = i
                    continue@outer
                }
            }
            germlineBuilder.append('N')
        }

        // trimming unaligned
        sequence = sequence.substring(firstAligned, lastAligned + 1)
        var germline = germlineBuilder.substring(firstAligned, lastAligned + 1)
        germlinePosition = germlinePosition.copyOfRange(firstAligned, lastAligned + 1)
        geneType = geneType.copyOfRange(firstAligned, lastAligned + 1)
        match = match.copyOfRange(firstAligned, lastAligned + 1)
        size = lastAligned - firstAligned + 1
        assert(sequence.length == size)
        if (vdjRegion) {
            if (leftVDJPadding == NULL_PADDING_LENGTH || rightVDJPadding == NULL_PADDING_LENGTH) {
                // impossible to construct VDJRegion-bound AIRR alignment
                return null
            } else {
                // Processing left side
                if (leftVDJPadding > 0) {
                    assert(leftVDJPadding == leftVDJExtra!!.size())
                    assert(leftVDJPadding == leftVDJExtraRange!!.length())
                    sequence = StringUtil.chars(PADDING_CHARACTER, leftVDJPadding) + sequence
                    germline = leftVDJExtra.toString() + germline
                    val newGermlinePosition = IntArray(leftVDJPadding + size)
                    System.arraycopy(
                        germlinePosition, 0, newGermlinePosition,
                        leftVDJPadding, size
                    )
                    val newGeneType = arrayOfNulls<GeneType>(leftVDJPadding + size)
                    System.arraycopy(
                        geneType, 0, newGeneType,
                        leftVDJPadding, size
                    )
                    val newMatch = BooleanArray(leftVDJPadding + size)
                    System.arraycopy(
                        match, 0, newMatch,
                        leftVDJPadding, size
                    )
                    var p = leftVDJExtraRange.from
                    var i = 0
                    while (p < leftVDJExtraRange.to) {
                        newGermlinePosition[i] = p
                        newGeneType[i] = Variable
                        newMatch[i] = true
                        ++p
                        ++i
                    }
                    germlinePosition = newGermlinePosition
                    geneType = newGeneType
                    match = newMatch
                } else if (leftVDJPadding != 0) {
                    sequence = sequence.substring(-leftVDJPadding)
                    germline = germline.substring(-leftVDJPadding)
                    germlinePosition = germlinePosition.copyOfRange(-leftVDJPadding, germlinePosition.size)
                    geneType = geneType.copyOfRange(-leftVDJPadding, geneType.size)
                    match = match.copyOfRange(-leftVDJPadding, match.size)
                }
                size += leftVDJPadding
                assert(sequence.length == size)
                if (rightVDJPadding > 0) {
                    assert(rightVDJPadding == rightVDJExtra!!.size())
                    assert(rightVDJPadding == rightVDJExtraRange!!.length())
                    sequence += StringUtil.chars(PADDING_CHARACTER, rightVDJPadding)
                    germline += rightVDJExtra
                    germlinePosition = germlinePosition.copyOf(size + rightVDJPadding)
                    geneType = geneType.copyOf(size + rightVDJPadding)
                    match = match.copyOf(size + rightVDJPadding)
                    // size here is "previous size"
                    var p = rightVDJExtraRange.from
                    var i = size
                    while (p < rightVDJExtraRange.to) {
                        germlinePosition[i] = p
                        geneType[i] = Joining
                        match[i] = true
                        ++p
                        ++i
                    }
                } else if (rightVDJPadding != 0) {
                    sequence = sequence.substring(0, size + rightVDJPadding) // note that rightVDJPadding < 0
                    germline = germline.substring(0, size + rightVDJPadding)
                    germlinePosition = germlinePosition.copyOf(size + rightVDJPadding)
                    geneType = geneType.copyOf(size + rightVDJPadding)
                    match = match.copyOf(size + rightVDJPadding)
                }
                size += rightVDJPadding
                assert(germline.length == size)
            }
        }
        val cdr3Length = obj.ntLengthOf(GeneFeature.CDR3)
        var paddings: MutableList<IMGTPadding>? = null
        if (validPaddings) {
            paddings = ArrayList()
            outer@ for ((gt, germlinePaddings) in paddingsByGeneType) {
                for (germlinePadding in germlinePaddings) {
                    val pos = projectPosition(
                        germlinePosition, geneType,
                        germlinePadding.insertionPosition, gt
                    )
                    if (pos == -1) {
                        paddings = null
                        break@outer
                    }
                    paddings!!.add(IMGTPadding(pos, germlinePadding.paddingLength))
                }
            }
            if (paddings == null || cdr3Begin == -1 || cdr3End == -1 || cdr3Length < 0) paddings = null else {
                cdr3Begin = projectPosition(germlinePosition, geneType, cdr3Begin, Variable)
                cdr3End = projectPosition(germlinePosition, geneType, cdr3End, Joining)
                val paddedLength = IMGTPadding.getPaddedLengthNt(GeneFeature.CDR3, cdr3Length)
                if (cdr3Begin == -1 || cdr3End == -1 || paddedLength == -1) paddings = null else {
                    val position = cdr3Begin + IMGTPadding.insertPosition(cdr3End - cdr3Begin)
                    paddings.add(IMGTPadding(position, paddedLength - cdr3Length))
                }
            }
        }
        paddings?.sort()
        return AirrAlignment(bestHits, sequence, germline, geneType, germlinePosition, BitArray(*match), paddings)
    }

    private fun projectPosition(
        germlinePositions: IntArray, geneTypes: Array<GeneType?>,
        position: Int, geneType: GeneType
    ): Int {
        var onLeft = false
        for (i in germlinePositions.indices) if (geneTypes[i] == geneType) {
            if (germlinePositions[i] < position) onLeft = true
            if (onLeft && germlinePositions[i] >= position) return i
        }
        return -1
    }

    /**
     * Selects the most appropriate target for export in multi-target cases.
     *
     * Selection criteria is the following:
     * - target containing CDR3 wins
     * - if no CDR3 target with the longest total alignment length over best hits wins
     * - if no alignments are present 0 is returned
     */
    fun bestTarget(obj: VDJCObject): Int {
        for (i in 0 until obj.numberOfTargets()) if (obj.getPartitionedTarget(i).partitioning.isAvailable(GeneFeature.CDR3)) return i
        var maxAlignmentLength = -1
        var targetWithMaxAlignmentLength = -1
        for (i in 0 until obj.numberOfTargets()) {
            var alignmentLength = 0
            for (gt in VDJC_REFERENCE) {
                val bh = obj.getBestHit(gt) ?: continue
                val al = bh.getAlignment(i) ?: continue
                alignmentLength += al.sequence2Range.length()
            }
            if (alignmentLength > maxAlignmentLength) {
                maxAlignmentLength = alignmentLength
                targetWithMaxAlignmentLength = i
            }
        }
        return targetWithMaxAlignmentLength
    }

    class AirrAlignment(
        bestHits: EnumMap<GeneType, VDJCHit>,
        sequence: String, germline: String,
        geneType: Array<GeneType?>, germlinePosition: IntArray,
        match: BitArray, paddings: List<IMGTPadding>?
    ) {
        private val bestHits: EnumMap<GeneType, VDJCHit>
        private val sequence: String
        private val germline: String
        private val geneType: Array<GeneType?>
        private val germlinePosition: IntArray
        private val match: BitArray
        private val ranges: EnumMap<GeneType, Range>
        private val paddings: List<IMGTPadding>?

        init {
            require(sequence.length == germline.length)
            require(germline.length == geneType.size)
            require(geneType.size == germlinePosition.size)
            require(germlinePosition.size == match.size())
            this.bestHits = bestHits
            this.sequence = sequence
            this.germline = germline
            this.geneType = geneType
            this.germlinePosition = germlinePosition
            this.match = match
            val ranges = EnumMap<GeneType, Range>(
                GeneType::class.java
            )
            for (gt in VDJC_REFERENCE) {
                var min = -1
                var max = -1
                for (i in geneType.indices) if (geneType[i] == gt) {
                    if (min == -1) min = i
                    max = i
                }
                if (min != -1) ranges[gt] = Range(min, max + 1)
            }
            this.ranges = ranges
            this.paddings = paddings
        }

        private fun padded(input: String): String? {
            return if (paddings == null) null else IMGTPadding.applyPadding(paddings, PADDING_CHARACTER, input)
        }

        private fun projectToPadded(unpaddedPosition: Int): Int {
            if (paddings == null) return -1
            var result = unpaddedPosition
            for (p in paddings) result += if (p.insertionPosition >= unpaddedPosition) return result else p.paddingLength
            return result
        }

        fun getSequence(withPadding: Boolean): String? {
            return if (withPadding) padded(sequence) else sequence
        }

        fun getGermline(withPadding: Boolean): String? {
            return if (withPadding) padded(germline) else germline
        }

        private fun projectGermlinePosition(gt: GeneType, position: Int, withPadding: Boolean): Int {
            var resultPosition = position
            resultPosition = projectPosition(germlinePosition, geneType, resultPosition, gt)
            if (resultPosition == -1) return -1
            return if (withPadding) projectToPadded(resultPosition) else resultPosition
        }

        fun getPosition(point: ComplexReferencePoint, withPadding: Boolean): Int {
            for (gt in VDJC_REFERENCE) {
                val hit = bestHits[gt] ?: continue
                val position = point.getPosition(hit.gene.partitioning, hit.alignedFeature)
                if (position == -1) continue
                return projectGermlinePosition(gt, position, withPadding)
            }
            return -1
        }

        fun getSequence(from: ComplexReferencePoint, to: ComplexReferencePoint, withPadding: Boolean): String? {
            val fromPosition = getPosition(from, withPadding)
            val toPosition = getPosition(to, withPadding)
            return if (fromPosition == -1 || toPosition == -1) null else getSequence(withPadding)!!.substring(
                fromPosition,
                toPosition
            )
        }

        fun getRange(gt: GeneType, withPadding: Boolean): Range? {
            val range = ranges[gt] ?: return null
            return if (withPadding) {
                if (paddings == null) null else Range(
                    projectToPadded(range.lower),
                    projectToPadded(range.upper)
                )
            } else range
        }
    }
}
