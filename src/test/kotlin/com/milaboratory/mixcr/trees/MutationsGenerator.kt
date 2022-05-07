package com.milaboratory.mixcr.trees

import com.milaboratory.core.Range
import com.milaboratory.core.alignment.Aligner
import com.milaboratory.core.alignment.LinearGapAlignmentScoring
import com.milaboratory.core.mutations.Mutation
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.mutations.MutationsBuilder
import com.milaboratory.core.sequence.NucleotideSequence
import java.util.*

internal object MutationsGenerator {
    @JvmOverloads
    fun generateMutations(
        parent: NucleotideSequence,
        random: Random,
        range: Range = Range(0, parent.size())
    ): Mutations<NucleotideSequence> {
        val result = MutationsBuilder(NucleotideSequence.ALPHABET)
        val parentChars = parent.sequence.asArray()
        for (i in range.from until range.to - 1) {
            when (random.nextInt(20)) {
                0 -> {
                    val insertionsCount = random.nextInt(3)
                    var i1 = 0
                    while (i1 < insertionsCount) {
                        result.append(Mutation.createInsertion(i, random.nextInt(4).toByte().toInt()))
                        i1++
                    }
                }
                1 -> result.append(Mutation.createDeletion(i, parentChars[i].toInt()))
                2, 3, 4 -> {
                    val replaceWith = random.nextInt(4).toByte()
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
}
