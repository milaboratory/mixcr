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
package com.milaboratory.mixcr.trees

import com.milaboratory.core.Range
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NucleotideSequence
import io.repseq.core.GeneFeature

class NodeMutationsDescription(
    val VMutationsWithoutCDR3: Map<GeneFeature, CompositeMutations>,
    val VMutationsInCDR3WithoutNDN: CompositeMutations,
    val knownNDN: CompositeMutations,
    val JMutationsInCDR3WithoutNDN: CompositeMutations,
    val JMutationsWithoutCDR3: Map<GeneFeature, CompositeMutations>
)

/**
 * Mutations representation for chained mutations.
 *
 * Parent sequence may be calculated if needed
 */
data class CompositeMutations(
    val grand: NucleotideSequence,
    val mutationsFromGrandToParent: Mutations<NucleotideSequence>,
    val rangeInGrand: Range,
    val mutationsFromParentToThis: Mutations<NucleotideSequence>,
) {
    fun calculateParent(): NucleotideSequence = mutationsFromGrandToParent.mutate(grand)
    val rangeInParent: Range = MutationsUtils.projectRange(mutationsFromGrandToParent, rangeInGrand)
}
