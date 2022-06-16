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
package com.milaboratory.mixcr.alleles

import com.milaboratory.core.alignment.AlignmentScoring
import com.milaboratory.core.sequence.NucleotideSequence

internal class CombinedAllelesSearcher(
    private val parameters: FindAllelesParameters,
    private val scoring: AlignmentScoring<NucleotideSequence>,
    private val sequence1: NucleotideSequence
) : AllelesSearcher {
    override fun search(clones: List<CloneDescription>): List<AllelesSearcher.Result> {
        return ByNativeCellsSearcher(parameters.minDiversityForNativeAlleles).search(clones)
    }

}
