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

fun Mutations<NucleotideSequence>.intersection(second: Mutations<NucleotideSequence>): Mutations<NucleotideSequence> {
    if (isEmpty || second.isEmpty) return EMPTY_NUCLEOTIDE_MUTATIONS
    return intersectionAsSequence(second).asMutations(NucleotideSequence.ALPHABET)
}

fun Mutations<NucleotideSequence>.intersectionCount(second: Mutations<NucleotideSequence>): Int {
    if (isEmpty || second.isEmpty) return 0
    return intersectionAsSequence(second).count()
}

private fun Mutations<NucleotideSequence>.intersectionAsSequence(
    second: Mutations<NucleotideSequence>
): kotlin.sequences.Sequence<Int> {
    val builder = sequence {
        val firstIterator = asSequence().iterator()
        val secondIterator = second.asSequence().iterator()
        var mutationOfFirst: Int? = firstIterator.next()
        var mutationOfSecond: Int? = secondIterator.next()
        while (mutationOfFirst != null && mutationOfSecond != null) {
            val positionOfFirst = Mutation.getPosition(mutationOfFirst)
            val positionOfSecond = Mutation.getPosition(mutationOfSecond)
            if (positionOfFirst < positionOfSecond) {
                mutationOfFirst = firstIterator.nextOrNull()
            } else if (positionOfFirst > positionOfSecond) {
                mutationOfSecond = secondIterator.nextOrNull()
            } else {
                //positions are the same
                val positionInBaseSeq = Mutation.getPosition(mutationOfFirst)

                //collect all inserts at this position in sequences. Last mutation will be processed on next step
                val subsetOfFirstBuilder = NucleotideSequence.ALPHABET.createBuilder()
                while (mutationOfFirst != null && Mutation.isInsertion(mutationOfFirst) &&
                    Mutation.getPosition(mutationOfFirst) == positionInBaseSeq
                ) {
                    subsetOfFirstBuilder.append(Mutation.getTo(mutationOfFirst))
                    mutationOfFirst = firstIterator.nextOrNull()
                }
                val subsetOfSecondBuilder = NucleotideSequence.ALPHABET.createBuilder()
                while (mutationOfSecond != null && Mutation.isInsertion(mutationOfSecond) &&
                    Mutation.getPosition(mutationOfSecond) == positionInBaseSeq
                ) {
                    subsetOfSecondBuilder.append(Mutation.getTo(mutationOfSecond))
                    mutationOfSecond = secondIterator.nextOrNull()
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
                        yield(
                            Mutation.createInsertion(
                                positionInBaseSeq,
                                subsetOfFirst.codeAt(position).toInt()
                            )
                        )
                    }
                } else {
                    //it there is substitutions in this position, compare them
                    if (mutationOfFirst != null && mutationOfFirst == mutationOfSecond) {
                        yield(mutationOfFirst)
                    }
                }

                //there were no insertions in this position in one of sequences, roll iterator for next mutation
                if (subsetOfFirstBuilder.size() == 0) {
                    mutationOfFirst = firstIterator.nextOrNull()
                }
                if (subsetOfSecondBuilder.size() == 0) {
                    mutationOfSecond = secondIterator.nextOrNull()
                }
            }
        }
    }
    return builder
}

fun Mutations<NucleotideSequence>.without(second: Mutations<NucleotideSequence>): Mutations<NucleotideSequence> {
    if (second.isEmpty) return this
    val secondIterator = second.asSequence().iterator()
    var mutationOfSecond: Int? = secondIterator.next()
    return asSequence().filter { mutationOfFirst ->
        if (mutationOfSecond == null) {
            true
        } else {
            if (mutationOfFirst == mutationOfSecond) {
                mutationOfSecond = secondIterator.nextOrNull()
                false
            } else {
                val positionOfFirst = Mutation.getPosition(mutationOfFirst)
                while (secondIterator.hasNext() && positionOfFirst > Mutation.getPosition(mutationOfSecond!!)) {
                    mutationOfSecond = secondIterator.next()
                }
                true
            }
        }
    }.asMutations(NucleotideSequence.ALPHABET)
}

private fun <T> Iterator<T>.nextOrNull(): T? = when {
    hasNext() -> next()
    else -> null
}
