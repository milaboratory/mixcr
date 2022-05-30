package com.milaboratory.mixcr.alleles

import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NucleotideSequence

data class CloneDescription(
    val mutations: Mutations<NucleotideSequence>,
    val clusterIdentity: ClusterIdentity
) {
    constructor(mutations: Mutations<NucleotideSequence>, CDR3Length: Int, complimentaryGeneName: String) : this(
        mutations,
        ClusterIdentity(CDR3Length, complimentaryGeneName)
    )

    data class ClusterIdentity(
        private val CDR3Length: Int,
        private val complimentaryGeneName: String
    )
}
