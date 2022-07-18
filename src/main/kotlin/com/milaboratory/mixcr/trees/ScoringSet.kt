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
    fun NDNDistance(firstNDN: NucleotideSequence, secondNDN: NucleotideSequence): Double {
        val score = Aligner.alignGlobal(
            NDN.scoring,
            firstNDN,
            secondNDN
        ).score
        val maxScore = max(
            maxScore(firstNDN.size(), NDN.scoring),
            maxScore(secondNDN.size(), NDN.scoring)
        )
        return (maxScore - score) / min(firstNDN.size(), secondNDN.size()).toDouble()
    }

    class PenaltyCalculator(
        val scoring: AlignmentScoring<NucleotideSequence>
    ) {
        fun penalties(mutations: Collection<CompositeMutations>): Int =
            mutations.sumOf { penalties(it) }

        fun penalties(mutations: CompositeMutations): Int {
            val maxScore = maxScore(mutations.rangeInParent.length(), scoring)
            //AlignmentUtils.calculateScore use sequence only for positions without mutations.
            //Calculating parent is expensive, so use grand if possible
            val base = if (mutations.mutationsFromParentToThis.lastMutationPosition() > mutations.grand.size()) {
                mutations.calculateParent()
            } else {
                mutations.grand
            }
            val score = AlignmentUtils.calculateScore(base, mutations.mutationsFromParentToThis, scoring)
            return maxScore - score
        }
    }
}

private fun maxScore(sequenceSize: Int, scoring: AlignmentScoring<NucleotideSequence>): Int =
    sequenceSize * scoring.maximalMatchScore

