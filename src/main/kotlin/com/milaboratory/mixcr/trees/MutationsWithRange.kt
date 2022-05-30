package com.milaboratory.mixcr.trees

import com.milaboratory.core.Range
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NucleotideSequence

data class MutationsWithRange(
    val sequence1: NucleotideSequence,
    val mutations: Mutations<NucleotideSequence>,
    val range: Range
) {
    private var result: NucleotideSequence? = null

    fun buildSequence(): NucleotideSequence {
        if (result == null) {
            result = MutationsUtils.buildSequence(sequence1, mutations, range)
        }
        return result!!
    }
}
