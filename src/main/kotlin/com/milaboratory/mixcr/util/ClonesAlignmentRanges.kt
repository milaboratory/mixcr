@file:Suppress("FunctionName", "LocalVariableName")

package com.milaboratory.mixcr.util

import com.milaboratory.core.Range
import com.milaboratory.core.alignment.Alignment
import com.milaboratory.core.mutations.Mutation
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.basictypes.VDJCHit
import com.milaboratory.mixcr.trees.CloneWrapper
import io.repseq.core.GeneType
import io.repseq.core.ReferencePoint
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

class ClonesAlignmentRanges(
    val commonRanges: Array<Range>,
    private val geneType: GeneType
) {
    fun cutRange(range: Range): Range = commonRanges.first { it.intersectsWith(range) }

    fun containsMutation(mutation: Int): Boolean {
        val position = Mutation.getPosition(mutation)
        return commonRanges.any { it.contains(position) }
    }

    fun containsCloneWrapper(clone: CloneWrapper): Boolean =
        clone.getHit(geneType).alignments
            .all { alignment -> commonRanges.any { other -> other in alignment.sequence1Range } }

    fun containsClone(clone: Clone): Boolean =
        clone.getBestHit(geneType).alignments
            .all { alignment -> commonRanges.any { other -> other in alignment.sequence1Range } }

    companion object {
        fun CDR3Sequence1Range(hit: VDJCHit, alignment: Alignment<NucleotideSequence>): Range? {
            var from = getRelativePosition(hit, ReferencePoint.CDR3Begin)
            var to = getRelativePosition(hit, ReferencePoint.CDR3End)
            if (from == -1 && to == -1) {
                return null
            }
            if (from == -1) {
                from = alignment.sequence1Range.lower
            }
            if (to == -1) {
                to = alignment.sequence1Range.upper
            }
            return Range(from, to)
        }

        private fun getRelativePosition(hit: VDJCHit, referencePoint: ReferencePoint): Int =
            hit.gene.partitioning.getRelativePosition(hit.alignedFeature, referencePoint)

        fun <T> commonAlignmentRanges(
            clones: List<T>,
            minPortionOfClones: Double,
            geneType: GeneType,
            hitSupplier: (T) -> VDJCHit
        ): ClonesAlignmentRanges {
            require(minPortionOfClones >= 0.5) { "if minPortionOfClones < 0.5 than there may be ranges with intersections that exist in all clones" }
            // allow excluding minimum one clone in small clusters, but not breaking algorithm condition to filtering on more than a half of clones
            val threshold = max(floor(clones.size * minPortionOfClones).toInt(), ceil(clones.size / 2.0).toInt())
            val rangeCounts = clones
                .flatMap { clone: T ->
                    val bestHit = hitSupplier(clone)
                    bestHit.alignments.map { alignment ->
                        when (val CDR3Range = CDR3Sequence1Range(bestHit, alignment)) {
                            null -> alignment.sequence1Range
                            else -> {
                                val withoutCDR3 = alignment.sequence1Range.without(CDR3Range)
                                when (withoutCDR3.size) {
                                    1 -> withoutCDR3[0]
                                    else -> throw IllegalStateException()
                                }
                            }
                        }
                    }
                }
                .groupingBy { it }.eachCount()
            val rangesWithMinPortionOfClones = rangeCounts
                .mapValues { (range) ->
                    range to rangeCounts
                        .filterKeys { key -> range in key }
                        .values
                        .sum()
                }
                .filterValues { it.second >= threshold }
                .keys
            val result = rangesWithMinPortionOfClones.filter { range ->
                (rangesWithMinPortionOfClones - range).none { other -> range in other }
            }
            val noIntersections = result.none { range ->
                (result - range).any { other -> range.intersectsWith(other) }
            }
            check(noIntersections) { "there are intersections" }
            return ClonesAlignmentRanges(result.toTypedArray(), geneType)
        }
    }
}
