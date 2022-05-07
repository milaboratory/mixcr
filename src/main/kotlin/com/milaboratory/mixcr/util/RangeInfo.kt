package com.milaboratory.mixcr.util

import com.milaboratory.core.Range
import com.milaboratory.core.mutations.Mutation
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NucleotideSequence
import java.util.stream.IntStream

data class RangeInfo(
    val range: Range,
    val isIncludeFirstInserts: Boolean
) {

    fun intersection(another: RangeInfo): RangeInfo? {
        val intersection: Range?
        if (range == another.range) {
            intersection = range
        } else {
            intersection = range.intersection(another.range)
            if (intersection == null) {
                return null
            }
        }
        val includeFirstInserts = when (intersection.lower) {
            range.lower -> isIncludeFirstInserts
            another.range.lower -> isIncludeFirstInserts || another.isIncludeFirstInserts
            else -> false
        }
        return RangeInfo(
            intersection,
            includeFirstInserts
        )
    }

    fun extractAbsoluteMutations(mutations: Mutations<NucleotideSequence>): Mutations<NucleotideSequence> {
        val filteredMutations = IntStream.of(*mutations.rawMutations)
            .filter { mutation: Int -> this.contains(mutation) }
            .toArray()
        return Mutations(NucleotideSequence.ALPHABET, *filteredMutations)
    }

    operator fun contains(mutation: Int): Boolean =
        when (val position = Mutation.getPosition(mutation)) {
            range.lower -> when {
                Mutation.isInsertion(mutation) -> isIncludeFirstInserts
                else -> true
            }
            range.upper -> Mutation.isInsertion(mutation)
            else -> range.lower < position && position < range.upper
        }
}
