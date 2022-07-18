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
package com.milaboratory.mixcr.util

import com.milaboratory.core.Range
import com.milaboratory.core.alignment.Aligner
import com.milaboratory.core.alignment.LinearGapAlignmentScoring
import com.milaboratory.core.mutations.Mutation
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.mutations.Mutations.EMPTY_NUCLEOTIDE_MUTATIONS
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

fun <S : Sequence<S>> Mutations<S>.extractAbsoluteMutations(
    range: Range,
    isIncludeFirstInserts: Boolean
): Mutations<S> = asSequence()
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
    .asMutations(alphabet)

fun Mutations<NucleotideSequence>.intersection(second: Mutations<NucleotideSequence>): Mutations<NucleotideSequence> {
    if (isEmpty || second.isEmpty) return EMPTY_NUCLEOTIDE_MUTATIONS
    val builder = MutationsBuilder(NucleotideSequence.ALPHABET)
    findIntersections(second) {
        builder.append(it)
    }
    return builder.createAndDestroy()
}

fun Mutations<NucleotideSequence>.intersectionCount(second: Mutations<NucleotideSequence>): Int {
    if (isEmpty || second.isEmpty) return 0
    var count = 0
    findIntersections(second) {
        count++
    }
    return count
}

private inline fun Mutations<NucleotideSequence>.findIntersections(
    second: Mutations<NucleotideSequence>,
    callback: (Int) -> Unit
) {
    val firstIterator = MutationsIterator(this)
    val secondIterator = MutationsIterator(second)
    var mutationOfFirst: Int = firstIterator.nextOrMinusOne()
    var mutationOfSecond: Int = secondIterator.nextOrMinusOne()
    while (mutationOfFirst != -1 && mutationOfSecond != -1) {
        val positionOfFirst = Mutation.getPosition(mutationOfFirst)
        val positionOfSecond = Mutation.getPosition(mutationOfSecond)
        if (positionOfFirst < positionOfSecond) {
            mutationOfFirst = firstIterator.nextOrMinusOne()
        } else if (positionOfFirst > positionOfSecond) {
            mutationOfSecond = secondIterator.nextOrMinusOne()
        } else {
            //positions are the same
            val positionInBaseSeq = Mutation.getPosition(mutationOfFirst)

            //collect all inserts at this position in sequences. Last mutation will be processed on next step
            val subsetOfFirstBuilder = NucleotideSequence.ALPHABET.createBuilder()
            while (mutationOfFirst != -1 && Mutation.isInsertion(mutationOfFirst) &&
                Mutation.getPosition(mutationOfFirst) == positionInBaseSeq
            ) {
                subsetOfFirstBuilder.append(Mutation.getTo(mutationOfFirst))
                mutationOfFirst = firstIterator.nextOrMinusOne()
            }
            val subsetOfSecondBuilder = NucleotideSequence.ALPHABET.createBuilder()
            while (mutationOfSecond != -1 && Mutation.isInsertion(mutationOfSecond) &&
                Mutation.getPosition(mutationOfSecond) == positionInBaseSeq
            ) {
                subsetOfSecondBuilder.append(Mutation.getTo(mutationOfSecond))
                mutationOfSecond = secondIterator.nextOrMinusOne()
            }

            if (subsetOfFirstBuilder.size() != 0 && subsetOfSecondBuilder.size() != 0) {
                val subsetOfFirst = subsetOfFirstBuilder.createAndDestroy()
                val subsetOfSecond = subsetOfSecondBuilder.createAndDestroy()
                val mutationsBetweenInsertedSubsets = Aligner.alignGlobal(
                    LinearGapAlignmentScoring.getNucleotideBLASTScoring(),
                    subsetOfFirst,
                    subsetOfSecond
                ).absoluteMutations
                val changedPositionsOfFirstSubset = mutationsBetweenInsertedSubsets.asSequence()
                    .filter { !Mutation.isInsertion(it) }
                    .map { Mutation.getPosition(it) }
                    .toSet()
                val notChangedPositions = (0 until subsetOfFirst.size()) - changedPositionsOfFirstSubset
                notChangedPositions.forEach { position ->
                    callback(
                        Mutation.createInsertion(
                            positionInBaseSeq,
                            subsetOfFirst.codeAt(position).toInt()
                        )
                    )
                }
            } else {
                //it there is substitutions in this position, compare them
                if (mutationOfFirst != -1 && mutationOfFirst == mutationOfSecond) {
                    callback(mutationOfFirst)
                }
            }

            //there were no insertions in this position in one of sequences, roll iterator for next mutation
            if (subsetOfFirstBuilder.size() == 0) {
                mutationOfFirst = firstIterator.nextOrMinusOne()
            }
            if (subsetOfSecondBuilder.size() == 0) {
                mutationOfSecond = secondIterator.nextOrMinusOne()
            }
        }
    }
}

fun Mutations<NucleotideSequence>.without(second: Mutations<NucleotideSequence>): Mutations<NucleotideSequence> {
    if (second.isEmpty) return this
    val secondIterator = MutationsIterator(second)
    var mutationOfSecond: Int = secondIterator.nextOrMinusOne()
    return asSequence().filter { mutationOfFirst ->
        if (mutationOfSecond == -1) {
            true
        } else {
            if (mutationOfFirst == mutationOfSecond) {
                mutationOfSecond = secondIterator.nextOrMinusOne()
                false
            } else {
                val positionOfFirst = Mutation.getPosition(mutationOfFirst)
                while (secondIterator.hasNext() && positionOfFirst > Mutation.getPosition(mutationOfSecond)) {
                    mutationOfSecond = secondIterator.nextOrMinusOne()
                }
                true
            }
        }
    }.asMutations(NucleotideSequence.ALPHABET)
}

private class MutationsIterator(
    private val mutations: Mutations<NucleotideSequence>
) {
    private var i = 0;

    fun hasNext() = i < mutations.size()

    fun nextOrMinusOne(): Int =
        if (i == mutations.size()) {
            -1
        } else {
            mutations.getMutation(i++)
        }
}
