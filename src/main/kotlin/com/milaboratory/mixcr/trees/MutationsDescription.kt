package com.milaboratory.mixcr.trees

import com.milaboratory.core.Range

class MutationsDescription(
    val VMutationsWithoutCDR3: Map<Range, MutationsWithRange>,
    val VMutationsInCDR3WithoutNDN: MutationsWithRange,
    val knownNDN: MutationsWithRange,
    val JMutationsInCDR3WithoutNDN: MutationsWithRange,
    val JMutationsWithoutCDR3: Map<Range, MutationsWithRange>
)
