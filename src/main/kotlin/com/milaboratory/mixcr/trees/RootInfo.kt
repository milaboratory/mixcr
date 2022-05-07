package com.milaboratory.mixcr.trees

import com.milaboratory.core.Range
import com.milaboratory.core.sequence.NucleotideSequence

data class RootInfo(
    val VRangeInCDR3: Range,
    val reconstructedNDN: NucleotideSequence,
    val JRangeInCDR3: Range,
    val VJBase: VJBase
)
