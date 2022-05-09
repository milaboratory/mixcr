package com.milaboratory.mixcr.util

import com.milaboratory.core.Range
import com.milaboratory.core.mutations.Mutation
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.mutations.MutationsBuilder
import com.milaboratory.core.sequence.NucleotideSequence

data class RangeInfo(
    val range: Range,
    val isIncludeFirstInserts: Boolean
) {

    fun intersection(another: RangeInfo): RangeInfo? {
        val intersection: Range = when (range) {
            another.range -> range
            else -> range.intersection(another.range)
        } ?: return null
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
        val builder = MutationsBuilder(NucleotideSequence.ALPHABET)
        builder.ensureCapacity(mutations.size())
        (0 until mutations.size()).forEach { i ->
            val mutation = mutations.getMutation(i)
            if (this.contains(mutation)) {
                builder.append(mutation)
            }
        }
        return builder.createAndDestroy()
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
