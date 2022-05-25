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
