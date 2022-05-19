package com.milaboratory.mixcr.trees

import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.mutations.MutationsBuilder
import com.milaboratory.core.sequence.NucleotideSequence

class MutationsDescription(
    val VMutationsWithoutCDR3: List<MutationsWithRange>,
    val VMutationsInCDR3WithoutNDN: MutationsWithRange,
    val knownNDN: MutationsWithRange,
    val JMutationsInCDR3WithoutNDN: MutationsWithRange,
    val JMutationsWithoutCDR3: List<MutationsWithRange>
) {

    fun withKnownNDNMutations(mutations: MutationsWithRange): MutationsDescription = MutationsDescription(
        VMutationsWithoutCDR3,
        VMutationsInCDR3WithoutNDN,
        mutations,
        JMutationsInCDR3WithoutNDN,
        JMutationsWithoutCDR3
    )

    fun combinedVMutations(): Mutations<NucleotideSequence> {
        val allBasedOnTheSameSequence = VMutationsWithoutCDR3.stream()
            .map { obj -> obj.sequence1 }
            .allMatch { VMutationsInCDR3WithoutNDN.sequence1 == it }
        require(allBasedOnTheSameSequence)
        val mutationsFromRootToBaseBuilder = MutationsBuilder(NucleotideSequence.ALPHABET)
        VMutationsWithoutCDR3.stream()
            .map { obj: MutationsWithRange? -> obj!!.mutationsForRange() }
            .forEach { other -> mutationsFromRootToBaseBuilder.append(other) }
        mutationsFromRootToBaseBuilder.append(VMutationsInCDR3WithoutNDN.mutationsForRange())
        return mutationsFromRootToBaseBuilder.createAndDestroy()
    }

    fun combinedJMutations(): Mutations<NucleotideSequence> {
        val allBasedOnTheSameSequence = JMutationsWithoutCDR3.stream()
            .map { obj -> obj.sequence1 }
            .allMatch { JMutationsInCDR3WithoutNDN.sequence1 == it }
        require(allBasedOnTheSameSequence)
        val mutationsFromRootToBaseBuilder = MutationsBuilder(NucleotideSequence.ALPHABET)
        mutationsFromRootToBaseBuilder.append(JMutationsInCDR3WithoutNDN.mutationsForRange())
        JMutationsWithoutCDR3.stream()
            .map { obj -> obj.mutationsForRange() }
            .forEach { mutationsFromRootToBaseBuilder.append(it) }
        return mutationsFromRootToBaseBuilder.createAndDestroy()
    }
}
