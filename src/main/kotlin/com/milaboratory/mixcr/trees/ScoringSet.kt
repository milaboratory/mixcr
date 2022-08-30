/*
 * Copyright (c) 2014-2022, MiLaboratories Inc. All Rights Reserved
 *
 * Before downloading or accessing the software, please read carefully the
 * License Agreement available at:
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 *
 * By downloading or accessing the software, you accept and agree to be bound
 * by the terms of the License Agreement. If you do not want to agree to the terms
 * of the Licensing Agreement, you must not download or access the software.
 */
@file:Suppress("LocalVariableName", "PropertyName", "FunctionName")

package com.milaboratory.mixcr.trees

import com.milaboratory.core.alignment.Aligner
import com.milaboratory.core.alignment.AlignmentScoring
import com.milaboratory.core.alignment.AlignmentUtils
import com.milaboratory.core.sequence.NucleotideSequence
import kotlin.math.max
import kotlin.math.min

class ScoringSet(
    VScoring: AlignmentScoring<NucleotideSequence>,
    NDNScoring: AlignmentScoring<NucleotideSequence>,
    JScoring: AlignmentScoring<NucleotideSequence>,
) {
    val V = PenaltyCalculator(VScoring)
    val NDN = PenaltyCalculator(NDNScoring)
    val J = PenaltyCalculator(JScoring)

    /**
     * Penalty by length of scoring between NDN segments
     *
     * Thoughts:
     * - Try to calculate probabilities of length of N segments and align center to D if possible.
     * Use different penalties in N and D segments comparing to probability of generating the same NDN sequence
     */
    fun NDNDistance(firstNDN: NucleotideSequence, secondNDN: NucleotideSequence): Double =
        NDN.normalizedPenalties(firstNDN, secondNDN)

    class PenaltyCalculator(
        val scoring: AlignmentScoring<NucleotideSequence>
    ) {
        fun normalizedPenalties(
            first: NucleotideSequence,
            second: NucleotideSequence
        ): Double {
            val score = Aligner.alignGlobal(
                scoring,
                first,
                second
            ).score
            val maxScore = scoring.maxScore(max(first.size(), second.size()))
            return (maxScore - score) / min(first.size(), second.size()).toDouble()
        }

        fun penalties(mutations: Collection<CompositeMutations>): Int =
            mutations.sumOf { penalties(it) }

        fun penalties(mutations: CompositeMutations): Int {
            val maxScore = scoring.maxScore(mutations.rangeInParent.length())
            val score = AlignmentUtils.calculateScore(
                mutations.calculateParent(),
                mutations.rangeInParent,
                mutations.mutationsFromParentToThis,
                scoring
            )
            return maxScore - score
        }
    }
}

private fun AlignmentScoring<NucleotideSequence>.maxScore(sequenceSize: Int): Int =
    sequenceSize * maximalMatchScore

