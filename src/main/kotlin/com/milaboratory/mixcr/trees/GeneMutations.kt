package com.milaboratory.mixcr.trees

import com.milaboratory.core.Range
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.util.RangeMap
import io.repseq.core.GeneFeature

sealed class GeneMutations(
    val sequence1: NucleotideSequence,
    val partInCDR3: PartInCDR3,
    val mutations: RangeMap<Mutations<NucleotideSequence>>
) {
    fun mutationsCount() = partInCDR3.mutations.size() + mutations.values().sumOf { it.size() }
}

class VGeneMutations(
    sequence1: NucleotideSequence,
    mutations: RangeMap<Mutations<NucleotideSequence>>,
    partInCDR3: PartInCDR3
) : GeneMutations(sequence1, partInCDR3, mutations)

class JGeneMutations(
    sequence1: NucleotideSequence,
    partInCDR3: PartInCDR3,
    mutations: RangeMap<Mutations<NucleotideSequence>>
) : GeneMutations(sequence1, partInCDR3, mutations)

data class PartInCDR3(
    val range: Range,
    val mutations: Mutations<NucleotideSequence>
)

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
    VMutations.partInCDR3.let { (range, mutations) ->
        resultSequenceBuilder.append(MutationsUtils.buildSequence(VMutations.sequence1, mutations, range))
    }
    resultSequenceBuilder.append(NDN)
    JMutations.partInCDR3.let { (range, mutations) ->
        resultSequenceBuilder.append(MutationsUtils.buildSequence(JMutations.sequence1, mutations, range))
    }
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
