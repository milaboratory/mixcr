package com.milaboratory.mixcr.trees

import com.milaboratory.core.Range
import com.milaboratory.core.mutations.Mutation
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.util.asMutations
import com.milaboratory.mixcr.util.asSequence

sealed interface GeneMutations {
    val sequence1: NucleotideSequence
    val partInCDR3: PartInCDR3
    val mutations: Map<Range, Mutations<NucleotideSequence>>

    fun mutationsCount() = partInCDR3.mutations.size() + mutations.values.sumOf { it.size() }
    fun combinedMutations(): Mutations<NucleotideSequence>

    fun buildPartInCDR3(): NucleotideSequence =
        MutationsUtils.buildSequence(sequence1, partInCDR3.mutations, partInCDR3.range)
}

data class VGeneMutations(
    override val sequence1: NucleotideSequence,
    override val mutations: Map<Range, Mutations<NucleotideSequence>>,
    override val partInCDR3: PartInCDR3
) : GeneMutations {
    override fun combinedMutations(): Mutations<NucleotideSequence> =
        (mutations.values.asSequence().flatMap { it.asSequence() } + partInCDR3.mutations.asSequence())
            .asMutations(NucleotideSequence.ALPHABET)
}

data class JGeneMutations(
    override val sequence1: NucleotideSequence,
    override val partInCDR3: PartInCDR3,
    override val mutations: Map<Range, Mutations<NucleotideSequence>>
) : GeneMutations {
    override fun combinedMutations(): Mutations<NucleotideSequence> =
        (partInCDR3.mutations.asSequence() + mutations.values.asSequence().flatMap { it.asSequence() })
            .asMutations(NucleotideSequence.ALPHABET)
}

data class PartInCDR3(
    val range: Range,
    val mutations: Mutations<NucleotideSequence>
) {

    fun withoutLeftInsert() = copy(
        mutations = mutations.asSequence()
            .filter { mutation ->
                Mutation.getPosition(mutation) != range.lower || !Mutation.isInsertion(mutation)
            }
            .asMutations(NucleotideSequence.ALPHABET)
    )

    fun combineWithMutationsToTheRight(
        rightRange: Range,
        mutationsToAdd: Mutations<NucleotideSequence>
    ) = PartInCDR3(
        range.setUpper(rightRange.upper),
        mutations.concat(mutationsToAdd)
    )

    fun combineWithMutationsToTheLeft(
        leftRange: Range,
        mutationsToAdd: Mutations<NucleotideSequence>
    ) = PartInCDR3(
        range.setLower(leftRange.lower),
        mutationsToAdd.concat(mutations)
    )
}
