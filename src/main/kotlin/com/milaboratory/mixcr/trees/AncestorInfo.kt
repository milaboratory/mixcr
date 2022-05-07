package com.milaboratory.mixcr.trees

import com.milaboratory.core.sequence.NucleotideSequence

data class AncestorInfo(
    val sequence: NucleotideSequence,
    val CDR3Begin: Int,
    val CDR3End: Int
)
