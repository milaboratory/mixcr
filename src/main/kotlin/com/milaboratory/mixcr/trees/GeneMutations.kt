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
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mitool.pattern.search.BasicSerializer
import com.milaboratory.mixcr.util.asMutations
import com.milaboratory.mixcr.util.asSequence
import com.milaboratory.primitivio.PrimitivI
import com.milaboratory.primitivio.PrimitivO
import com.milaboratory.primitivio.annotations.Serializable
import com.milaboratory.primitivio.readObjectRequired
import io.repseq.core.GeneFeature
import java.util.*

sealed class GeneMutations(
    /**
     * Mutations within CDR3 in coordinates of V or J sequence1
     */
    val partInCDR3: PartInCDR3,

    /**
     * Mutations outside CDR3 in coordinates of V or J sequence1.
     * Key - geneFeature from corresponded hit.
     * In every cluster keys of different nodes will be equal.
     * There will be several entries if there are holes in alignments, but this holes must be consistent.
     */
    val mutationsOutsideOfCDR3: SortedMap<GeneFeature, Mutations<NucleotideSequence>>
) {

    fun mutationsCount() = partInCDR3.mutations.size() + mutationsOutsideOfCDR3.values.sumOf { it.size() }
    abstract fun combinedMutations(): Mutations<NucleotideSequence>

    abstract fun buildPartInCDR3(rootInfo: RootInfo): NucleotideSequence
}

class VGeneMutations(
    mutations: Map<GeneFeature, Mutations<NucleotideSequence>>,
    partInCDR3: PartInCDR3
) : GeneMutations(partInCDR3, mutations.toSortedMap()) {
    override fun combinedMutations(): Mutations<NucleotideSequence> =
        (mutationsOutsideOfCDR3.values.asSequence().flatMap { it.asSequence() } + partInCDR3.mutations.asSequence())
            .asMutations(NucleotideSequence.ALPHABET)

    override fun buildPartInCDR3(rootInfo: RootInfo): NucleotideSequence =
        MutationsUtils.buildSequence(rootInfo.sequence1.V, partInCDR3.mutations, rootInfo.rangeInCDR3.V)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return true
    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }
}

class JGeneMutations(
    partInCDR3: PartInCDR3,
    mutations: Map<GeneFeature, Mutations<NucleotideSequence>>
) : GeneMutations(partInCDR3, mutations.toSortedMap()) {
    override fun combinedMutations(): Mutations<NucleotideSequence> =
        (partInCDR3.mutations.asSequence() + mutationsOutsideOfCDR3.values.asSequence().flatMap { it.asSequence() })
            .asMutations(NucleotideSequence.ALPHABET)

    override fun buildPartInCDR3(rootInfo: RootInfo): NucleotideSequence =
        MutationsUtils.buildSequence(rootInfo.sequence1.J, partInCDR3.mutations, rootInfo.rangeInCDR3.J)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return true
    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }
}

@Serializable(by = PartInCDR3.SerializerImpl::class)
data class PartInCDR3(
    val range: Range,
    val mutations: Mutations<NucleotideSequence>
) {
    class SerializerImpl : BasicSerializer<PartInCDR3>() {
        override fun write(output: PrimitivO, obj: PartInCDR3) {
            output.writeObject(obj.range)
            output.writeObject(obj.mutations)
        }

        override fun read(input: PrimitivI): PartInCDR3 {
            val range = input.readObjectRequired<Range>()
            val mutations = input.readObjectRequired<Mutations<NucleotideSequence>>()
            return PartInCDR3(
                range,
                mutations
            )
        }
    }
}
