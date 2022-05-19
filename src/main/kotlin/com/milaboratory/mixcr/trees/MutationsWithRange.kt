package com.milaboratory.mixcr.trees

import com.milaboratory.core.Range
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.util.RangeInfo

class MutationsWithRange(
    val sequence1: NucleotideSequence,
    val mutations: Mutations<NucleotideSequence>,
    val rangeInfo: RangeInfo
) {
    private var result: NucleotideSequence? = null
    fun differenceWith(comparison: MutationsWithRange): MutationsWithRange {
        require(rangeInfo.range == comparison.rangeInfo.range)
        require(sequence1 == comparison.sequence1)
        val sequence1 = buildSequence()
        return MutationsWithRange(
            sequence1,
            mutationsForRange().invert()
                .combineWith(comparison.mutationsForRange())
                .move(-rangeInfo.range.lower),
            RangeInfo(Range(0, sequence1.size()), true)
        )
    }

    fun combineWith(next: MutationsWithRange): MutationsWithRange {
        require(next.sequence1.getRange(next.rangeInfo.range) == buildSequence())
        return MutationsWithRange(
            sequence1,
            mutationsForRange().combineWith(next.mutationsForRange().move(rangeInfo.range.lower)),
            RangeInfo(rangeInfo.range, true)
        )
    }

    fun combineWithMutationsToTheRight(mutations: Mutations<NucleotideSequence>, range: Range): MutationsWithRange {
        require(rangeInfo.range.upper == range.lower)
        return MutationsWithRange(
            sequence1,
            this.mutations.concat(mutations),
            RangeInfo(
                rangeInfo.range.setUpper(range.upper),
                rangeInfo.range.isEmpty
            )
        )
    }

    fun combineWithMutationsToTheLeft(mutations: Mutations<NucleotideSequence>, range: Range): MutationsWithRange {
        require(range.upper == rangeInfo.range.lower)
        return MutationsWithRange(
            sequence1,
            mutations.concat(this.mutations),
            RangeInfo(
                rangeInfo.range.setLower(range.lower),
                true
            )
        )
    }

    fun buildSequence(): NucleotideSequence {
        if (result == null) {
            result = MutationsUtils.buildSequence(sequence1, mutations, rangeInfo)
        }
        return result!!
    }

    fun mutationsForRange(): Mutations<NucleotideSequence> = rangeInfo.extractAbsoluteMutations(mutations)
}
