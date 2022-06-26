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
import com.milaboratory.core.alignment.Alignment
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.mutations.MutationsUtil
import com.milaboratory.core.sequence.AminoAcidSequence
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.core.sequence.TranslationParameters
import com.milaboratory.mixcr.util.extractAbsoluteMutations

data class MutationsDescription(
    val sequence1: NucleotideSequence,
    private val allMutations: Mutations<NucleotideSequence>,
    private val range: Range,
    private val translationParameters: TranslationParameters,
    private val isIncludeFirstInserts: Boolean,
    private val maxShiftedTriplets: Int = Int.MAX_VALUE
) {
    val mutations: Mutations<NucleotideSequence> by lazy {
        allMutations.extractAbsoluteMutations(range, isIncludeFirstInserts)
    }

    val aaSequence1: AminoAcidSequence
        get() = AminoAcidSequence.translate(sequence1, translationParameters)

    val targetAASequence: AminoAcidSequence
        get() = AminoAcidSequence.translate(targetNSequence, translationParameters)

    val aaMutations: Mutations<AminoAcidSequence>
        get() = MutationsUtil.nt2aa(sequence1, mutations, translationParameters, maxShiftedTriplets)

    val aaMutationsDetailed: Array<MutationsUtil.MutationNt2AADescriptor>
        get() = MutationsUtil.nt2aaDetailed(sequence1, mutations, translationParameters, maxShiftedTriplets)

    val targetNSequence: NucleotideSequence
        get() = MutationsUtils.buildSequence(sequence1, mutations, range)

    val nAlignment: Alignment<NucleotideSequence>
        get() = Alignment(sequence1, mutations, -1.0f)

    val aaAlignment: Alignment<AminoAcidSequence>
        get() = Alignment(aaSequence1, aaMutations, -1.0f)

    fun differenceWith(comparison: MutationsDescription): MutationsDescription {
        check(sequence1 == comparison.sequence1)
        check(range == comparison.range)
        return copy(
            sequence1 = allMutations.mutate(sequence1),
            allMutations = allMutations.invert().combineWith(comparison.allMutations),
            range = MutationsUtils.projectRange(allMutations, range)
        )
    }
}
