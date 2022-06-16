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

import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NucleotideSequence

class ByNativeCellsSearcher(
    private val minDiversity: Int
) : AllelesSearcher {
    override fun search(clones: List<CloneDescription>): List<AllelesSearcher.Result> = clones.asSequence()
        .map { it.mutations }
        .distinct()
        .mapNotNull { mutations ->
            if (diversity(clones, mutations) >= minDiversity) {
                AllelesSearcher.Result(mutations)
            } else {
                null
            }
        }
        .toList()

    private fun diversity(
        clones: List<CloneDescription>,
        mutations: Mutations<NucleotideSequence>
    ) = clones.asSequence()
        .filter { clone -> clone.mutations == mutations }
        .map { it.clusterIdentity }
        .distinct()
        .count()
}
