package com.milaboratory.mixcr.trees

import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.util.RangeInfo

class MutationsWithRange(
    val sequence1: NucleotideSequence,
    val mutations: Mutations<NucleotideSequence>,
    val rangeInfo: RangeInfo
) {
    init {
        try {
            checkRange(mutations, rangeInfo.range)
        } catch (e: Exception) {
            throw e
        }
    }

    private var result: NucleotideSequence? = null

    fun buildSequence(): NucleotideSequence {
        if (result == null) {
            result = MutationsUtils.buildSequence(sequence1, mutations, rangeInfo.range)
        }
        return result!!
    }
}
