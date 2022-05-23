package com.milaboratory.mixcr.trees

import com.milaboratory.util.RangeMap

class MutationsDescription(
    val VMutationsWithoutCDR3: RangeMap<MutationsWithRange>,
    val VMutationsInCDR3WithoutNDN: MutationsWithRange,
    val knownNDN: MutationsWithRange,
    val JMutationsInCDR3WithoutNDN: MutationsWithRange,
    val JMutationsWithoutCDR3: RangeMap<MutationsWithRange>
) {

    fun withKnownNDNMutations(mutations: MutationsWithRange): MutationsDescription = MutationsDescription(
        VMutationsWithoutCDR3,
        VMutationsInCDR3WithoutNDN,
        mutations,
        JMutationsInCDR3WithoutNDN,
        JMutationsWithoutCDR3
    )
}
