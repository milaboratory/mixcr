package com.milaboratory.mixcr.trees

import com.google.common.primitives.Bytes
import com.milaboratory.core.Range
import com.milaboratory.core.alignment.Aligner
import com.milaboratory.core.alignment.LinearGapAlignmentScoring
import com.milaboratory.core.mutations.Mutation
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.mutations.MutationsBuilder
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.util.extractAbsoluteMutations
import java.util.stream.Collectors
import java.util.stream.IntStream
import kotlin.random.Random

fun Random.generateMutations(
    parent: NucleotideSequence,
    range: Range = Range(0, parent.size())
): Mutations<NucleotideSequence> {
    val result = MutationsBuilder(NucleotideSequence.ALPHABET)
    val parentChars = parent.sequence.asArray()
    for (i in range.from until range.to - 1) {
        when (nextInt(20)) {
            0 -> {
                val insertionsCount = nextInt(3)
                var i1 = 0
                while (i1 < insertionsCount) {
                    result.append(Mutation.createInsertion(i, nextInt(4).toByte().toInt()))
                    i1++
                }
            }
            1 -> result.append(Mutation.createDeletion(i, parentChars[i].toInt()))
            2, 3, 4 -> {
                val replaceWith = nextInt(4).toByte()
                if (parentChars[i] != replaceWith) {
                    result.append(Mutation.createSubstitution(i, parentChars[i].toInt(), replaceWith.toInt()))
                }
            }
        }
    }
    val child = result.createAndDestroy().mutate(parent)
    return Aligner.alignGlobal(
        LinearGapAlignmentScoring.getNucleotideBLASTScoring(),
        parent,
        child,
        range.lower, parent.size() - range.lower,
        range.lower, child.size() - range.lower
    ).absoluteMutations
}

fun Random.generateSequence(size: Int): NucleotideSequence {
    val chars = IntStream.range(0, size)
        .mapToObj { nextInt(4).toByte() }
        .collect(Collectors.toList())
    return NucleotideSequence(Bytes.toArray(chars))
}

fun buildSequence(
    sequence1: NucleotideSequence,
    mutations: Mutations<NucleotideSequence>,
    range: Range,
    isIncludeFirstInserts: Boolean
): NucleotideSequence = MutationsUtils.buildSequence(
    sequence1,
    mutations.extractAbsoluteMutations(range, isIncludeFirstInserts),
    range
)
