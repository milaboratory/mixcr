package com.milaboratory.mixcr.trees

import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NucleotideSequence

class CloneWithMutationsFromReconstructedRoot(
    val mutationsSet: MutationsSet,
    val mutationsFromRoot: MutationsDescription,
    val mutationsFromVJGermline: MutationsFromVJGermline,
    val clone: CloneWrapper
)

class CloneWithMutationsFromVJGermline(
    val mutations: MutationsFromVJGermline,
    val cloneWrapper: CloneWrapper
)

class MutationsSet(
    val VMutations: VGeneMutations,
    val NDNMutations: NDNMutations,
    val JMutations: JGeneMutations
)

class NDNMutations(
    val base: NucleotideSequence,
    val mutations: Mutations<NucleotideSequence>
)
