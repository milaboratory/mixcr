package com.milaboratory.mixcr.trees

import com.milaboratory.core.Range
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NucleotideSequence

@Suppress("PropertyName")
class MutationsFromVJGermline(
    val VMutations: VGeneMutations,
    val JMutations: JGeneMutations,
    val VMutationsWithoutCDR3: List<MutationsWithRange>,
    val VMutationsInCDR3WithoutNDN: MutationsWithRange,
    val knownVMutationsWithinNDN: Pair<Mutations<NucleotideSequence>, Range>,
    val knownNDN: NucleotideSequence,
    val knownJMutationsWithinNDN: Pair<Mutations<NucleotideSequence>, Range>,
    val JMutationsInCDR3WithoutNDN: MutationsWithRange,
    val JMutationsWithoutCDR3: List<MutationsWithRange>
) {
    val VJMutationsCount: Int
        get() = VMutations.mutationsCount() + JMutations.mutationsCount()
}
