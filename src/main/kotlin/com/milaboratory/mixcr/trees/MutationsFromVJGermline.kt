package com.milaboratory.mixcr.trees

import com.milaboratory.core.Range
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NucleotideSequence

@Suppress("PropertyName")
class MutationsFromVJGermline(
    val VMutations: VGeneMutations,
    val knownVMutationsWithinNDN: Pair<Mutations<NucleotideSequence>, Range>,
    val knownNDN: NucleotideSequence,
    val knownJMutationsWithinNDN: Pair<Mutations<NucleotideSequence>, Range>,
    val JMutations: JGeneMutations
) {
    val VJMutationsCount: Int
        get() = VMutations.mutationsCount() + JMutations.mutationsCount()
}
