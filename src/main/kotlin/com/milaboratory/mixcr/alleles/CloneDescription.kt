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

import com.milaboratory.core.mutations.Mutation
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.util.asMutations
import com.milaboratory.mixcr.util.asSequence

data class CloneDescription(
    private val cloneId: Int,
    /**
     * Mutations of clone without CDR3.
     */
    val mutations: Mutations<NucleotideSequence>,
    /**
     * Is mutations count of complimentary gene equal to 0.
     */
    val naiveByComplimentaryGeneMutations: Boolean,
    /**
     * Mutations groups of clone without CDR3.
     */
    val clusterIdentity: ClusterIdentity
) {
    val mutationGroups: LinkedHashSet<MutationGroup> = MutationGroup.groupMutationsByPositions(mutations)

    data class ClusterIdentity(
        private val CDR3Length: Int,
        private val complimentaryGeneName: String
    )

    /**
     * Mutations of clone with the same position.
     * Grouping ensures that insertions in one position will be viewed as one mutation.
     */
    data class MutationGroup(
        val mutations: List<Int>
    ) {
        override fun toString(): String = Mutations(NucleotideSequence.ALPHABET, *mutations.toIntArray()).encode()

        companion object {
            fun groupMutationsByPositions(mutations: Mutations<NucleotideSequence>): LinkedHashSet<MutationGroup> {
                var lastPosition = -1
                var lastWasDeletion = false
                val mutationGroups = mutableListOf<MutableList<Int>>()
                mutations.asSequence()
                    .forEach { mutation ->
                        val subsequentDeletion = lastWasDeletion && Mutation.isDeletion(mutation) &&
                                Mutation.getPosition(mutation) == lastPosition + 1
                        if (!subsequentDeletion && lastPosition != Mutation.getPosition(mutation)) {
                            mutationGroups.add(mutableListOf())
                        }
                        lastPosition = Mutation.getPosition(mutation)
                        lastWasDeletion = Mutation.isDeletion(mutation)
                        mutationGroups.last() += mutation
                    }
                val result = mutationGroups.map { MutationGroup(it) }
                return LinkedHashSet(result)
            }
        }
    }
}

fun List<CloneDescription.MutationGroup>.asMutations(): Mutations<NucleotideSequence> = asSequence()
    .flatMap { it.mutations }
    .asMutations(NucleotideSequence.ALPHABET)

