package com.milaboratory.mixcr.util

import com.milaboratory.core.Range
import com.milaboratory.core.mutations.Mutation
import com.milaboratory.core.mutations.Mutations
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

    fun extractAbsoluteMutations(mutations: Mutations<NucleotideSequence>): Mutations<NucleotideSequence> =
        mutations.asSequence()
            .filter { contains(it) }
            .asMutations(NucleotideSequence.ALPHABET)

    operator fun contains(mutation: Int): Boolean =
        when (val position = Mutation.getPosition(mutation)) {
            range.lower -> when {
                Mutation.isInsertion(mutation) -> isIncludeFirstInserts
                else -> position < range.upper
            }
            range.upper -> Mutation.isInsertion(mutation)
            else -> range.lower < position && position < range.upper
        }
}

fun Mutations<NucleotideSequence>.extractAbsoluteMutations(
    range: Range,
    isIncludeFirstInserts: Boolean
): Mutations<NucleotideSequence> = asSequence()
    .filter { mutation ->
        when (val position = Mutation.getPosition(mutation)) {
            range.lower -> when {
                Mutation.isInsertion(mutation) -> isIncludeFirstInserts
                else -> position < range.upper
            }
            range.upper -> Mutation.isInsertion(mutation)
            else -> position in (range.lower + 1) until range.upper
        }
    }
    .asMutations(NucleotideSequence.ALPHABET)
