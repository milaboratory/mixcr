@file:Suppress("LocalVariableName")

package com.milaboratory.mixcr.trees

import com.milaboratory.core.alignment.AlignmentScoring
import com.milaboratory.core.sequence.NucleotideSequence

class ScoringSet(
    val VScoring: AlignmentScoring<NucleotideSequence>,
    val NDNScoring: AlignmentScoring<NucleotideSequence>,
    val JScoring: AlignmentScoring<NucleotideSequence>,
)
