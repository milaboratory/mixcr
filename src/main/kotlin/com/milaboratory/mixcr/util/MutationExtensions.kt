package com.milaboratory.mixcr.util

import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.mutations.MutationsBuilder
import com.milaboratory.core.sequence.Alphabet
import com.milaboratory.core.sequence.Sequence

fun <S : Sequence<S>> Mutations<S>.asSequence(): kotlin.sequences.Sequence<Int> =
    (0 until size()).asSequence().map { getMutation(it) }

fun <S : Sequence<S>> kotlin.sequences.Sequence<Int>.asMutations(alphabet: Alphabet<S>): Mutations<S> {
    val builder = MutationsBuilder(alphabet)
    forEach { builder.append(it) }
    return builder.createAndDestroy()
}
