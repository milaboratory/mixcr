package com.milaboratory.mixcr.trees

import com.milaboratory.core.Range
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NucleotideSequence

data class MutationsWithRange(
    val sequence1: NucleotideSequence,
    val mutations: Mutations<NucleotideSequence>,
    val range: Range
) {

    fun buildSequence(): NucleotideSequence = MutationsUtils.buildSequence(sequence1, mutations, range)
}
