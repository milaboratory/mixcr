package com.milaboratory.mixcr.trees

import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NucleotideSequence

class CloneWithMutationsFromReconstructedRoot(
    val mutationsSet: MutationsSet,
    val mutationsFromVJGermline: MutationsFromVJGermline,
    val clone: CloneWrapper
)

class CloneWithMutationsFromVJGermline(
    val mutations: MutationsFromVJGermline,
    val cloneWrapper: CloneWrapper
)

data class MutationsSet(
    val VMutations: VGeneMutations,
    val NDNMutations: NDNMutations,
    val JMutations: JGeneMutations
)

data class NDNMutations(
    val base: NucleotideSequence,
    val mutations: Mutations<NucleotideSequence>
) {
    fun buildSequence(): NucleotideSequence = mutations.mutate(base)
}
