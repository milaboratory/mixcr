package com.milaboratory.mixcr.alleles

import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NucleotideSequence

interface AllelesSearcher {
    fun search(clones: List<CloneDescription>): List<Result>

    data class Result(
        val allele: Mutations<NucleotideSequence>
    )
}
