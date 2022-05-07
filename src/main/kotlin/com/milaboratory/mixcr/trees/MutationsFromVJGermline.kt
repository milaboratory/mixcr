package com.milaboratory.mixcr.trees

import com.milaboratory.core.Range
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NucleotideSequence

@Suppress("PropertyName")
class MutationsFromVJGermline(
    val VMutationsWithoutCDR3: List<MutationsWithRange>,
    val VMutationsInCDR3WithoutNDN: MutationsWithRange,
    val knownVMutationsWithinNDN: Pair<Mutations<NucleotideSequence>, Range>,
    val knownNDN: NucleotideSequence,
    val knownJMutationsWithinNDN: Pair<Mutations<NucleotideSequence>, Range>,
    val JMutationsInCDR3WithoutNDN: MutationsWithRange,
    val JMutationsWithoutCDR3: List<MutationsWithRange>
) {

    val VJMutationsCount: Int
        get() = VMutationsWithoutCDR3.stream().mapToInt { obj: MutationsWithRange -> obj.mutationsCount() }.sum() +
            VMutationsInCDR3WithoutNDN.mutationsCount() +
            JMutationsWithoutCDR3.stream().mapToInt { obj: MutationsWithRange -> obj.mutationsCount() }.sum() +
            JMutationsInCDR3WithoutNDN.mutationsCount()
    val VMutations: List<MutationsWithRange>
        get() {
            val result: MutableList<MutationsWithRange> = ArrayList(VMutationsWithoutCDR3)
            result.add(VMutationsInCDR3WithoutNDN)
            return result
        }
    val JMutations: List<MutationsWithRange>
        get() {
            val result: MutableList<MutationsWithRange> = ArrayList()
            result.add(JMutationsInCDR3WithoutNDN)
            result.addAll(JMutationsWithoutCDR3)
            return result
        }
}
