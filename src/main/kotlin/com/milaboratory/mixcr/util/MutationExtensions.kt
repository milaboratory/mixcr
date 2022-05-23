package com.milaboratory.mixcr.util

import com.milaboratory.core.Range
import com.milaboratory.core.mutations.Mutation
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.mutations.MutationsBuilder
import com.milaboratory.core.sequence.Alphabet
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.core.sequence.Sequence

fun <S : Sequence<S>> Mutations<S>.asSequence(): kotlin.sequences.Sequence<Int> =
    (0 until size()).asSequence().map { getMutation(it) }

fun <S : Sequence<S>> kotlin.sequences.Sequence<Int>.asMutations(alphabet: Alphabet<S>): Mutations<S> {
    val builder = MutationsBuilder(alphabet)
    forEach { builder.append(it) }
    return builder.createAndDestroy()
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
