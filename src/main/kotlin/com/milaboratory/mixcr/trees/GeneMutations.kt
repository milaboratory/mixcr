package com.milaboratory.mixcr.trees

import com.milaboratory.core.Range
import com.milaboratory.core.mutations.Mutation
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.util.asMutations
import com.milaboratory.mixcr.util.asSequence
import com.milaboratory.util.RangeMap
import io.repseq.core.GeneFeature

sealed interface GeneMutations {
    val sequence1: NucleotideSequence
    val partInCDR3: PartInCDR3
    val mutations: RangeMap<Mutations<NucleotideSequence>>

    fun mutationsCount() = partInCDR3.mutations.size() + mutations.values().sumOf { it.size() }
    fun combinedMutations(): Mutations<NucleotideSequence>

    fun buildPartInCDR3(): NucleotideSequence =
        MutationsUtils.buildSequence(sequence1, partInCDR3.mutations, partInCDR3.range)
}

data class VGeneMutations(
    override val sequence1: NucleotideSequence,
    override val mutations: RangeMap<Mutations<NucleotideSequence>>,
    override val partInCDR3: PartInCDR3
) : GeneMutations {
    init {
        mutations.entrySet().forEach { (range, mutations) ->
            checkRange(mutations, range)
        }
    }

    override fun combinedMutations(): Mutations<NucleotideSequence> =
        (mutations.values().asSequence().flatMap { it.asSequence() } + partInCDR3.mutations.asSequence())
            .asMutations(NucleotideSequence.ALPHABET)
}

data class JGeneMutations(
    override val sequence1: NucleotideSequence,
    override val partInCDR3: PartInCDR3,
    override val mutations: RangeMap<Mutations<NucleotideSequence>>
) : GeneMutations {
    init {
        mutations.entrySet().forEach { (range, mutations) ->
            checkRange(mutations, range)
        }
    }

    override fun combinedMutations(): Mutations<NucleotideSequence> =
        (partInCDR3.mutations.asSequence() + mutations.values().asSequence().flatMap { it.asSequence() })
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

//TODO remove
fun checkRange(mutations: Mutations<NucleotideSequence>, range: Range) {
    check(mutations.asSequence().all { mutation ->
        val position = Mutation.getPosition(mutation)
        range.contains(position) || (Mutation.isInsertion(mutation) && range.upper == position)
    })
}

//TODO remove
@Suppress("LocalVariableName")
fun assertClone(
    cloneWrapper: CloneWrapper,
    VMutations: VGeneMutations,
    JMutations: JGeneMutations,
    NDN: NucleotideSequence
) {
    val resultSequenceBuilder = NucleotideSequence.ALPHABET.createBuilder()
    VMutations.mutations.entrySet()
        .map { (range, mutations) -> MutationsUtils.buildSequence(VMutations.sequence1, mutations, range) }
        .forEach { resultSequenceBuilder.append(it) }
    val CDR3Begin = resultSequenceBuilder.size()
    resultSequenceBuilder.append(VMutations.buildPartInCDR3())
    resultSequenceBuilder.append(NDN)
    resultSequenceBuilder.append(JMutations.buildPartInCDR3())
    val CDR3End = resultSequenceBuilder.size()
    JMutations.mutations.entrySet()
        .map { (range, mutations) -> MutationsUtils.buildSequence(JMutations.sequence1, mutations, range) }
        .forEach { resultSequenceBuilder.append(it) }
    val resultSequence = resultSequenceBuilder.createAndDestroy()
    val resultCDR3 = resultSequence.getRange(CDR3Begin, CDR3End)
    if (cloneWrapper.clone.getFeature(GeneFeature.CDR3).sequence != resultCDR3) {
        error("${cloneWrapper.clone.getFeature(GeneFeature.CDR3).sequence} - $resultCDR3")
    }
    if (cloneWrapper.clone.getTarget(0).sequence != resultSequence) {
        error("${cloneWrapper.clone.getTarget(0).sequence} - $resultSequence")
    }
}
