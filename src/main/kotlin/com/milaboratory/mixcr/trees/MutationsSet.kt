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

import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.primitivio.PrimitivI
import com.milaboratory.primitivio.PrimitivO
import com.milaboratory.primitivio.Serializer
import com.milaboratory.primitivio.Util
import com.milaboratory.primitivio.annotations.Serializable
import com.milaboratory.primitivio.readMap
import com.milaboratory.primitivio.readObjectRequired
import io.repseq.core.GeneType
import io.repseq.core.GeneType.Joining
import io.repseq.core.GeneType.Variable

/**
 * Describe mutations of node from the root of the tree.
 */
@Serializable(by = MutationsSetSerializer::class)
data class MutationsSet(
    val VMutations: VGeneMutations,
    val NDNMutations: NDNMutations,
    val JMutations: JGeneMutations
) {
    fun getGeneMutations(geneType: GeneType): GeneMutations = when (geneType) {
        Joining -> JMutations
        Variable -> VMutations
        else -> throw IllegalArgumentException()
    }
}

class MutationsSetSerializer : Serializer<MutationsSet> {
    override fun write(output: PrimitivO, obj: MutationsSet) {
        Util.writeMap(obj.VMutations.mutations, output)
        output.writeObject(obj.VMutations.partInCDR3)
        output.writeObject(obj.NDNMutations)
        output.writeObject(obj.JMutations.partInCDR3)
        Util.writeMap(obj.JMutations.mutations, output)
    }

    override fun read(input: PrimitivI): MutationsSet = MutationsSet(
        VGeneMutations(
            input.readMap(),
            input.readObjectRequired()
        ),
        input.readObjectRequired(),
        JGeneMutations(
            input.readObjectRequired(),
            input.readMap()
        )
    )

    override fun isReference(): Boolean = false

    override fun handlesReference(): Boolean = false
}

@Serializable(by = NDNMutationsSerializer::class)
data class NDNMutations(
    val mutations: Mutations<NucleotideSequence>
) {
    fun buildSequence(rootInfo: RootInfo): NucleotideSequence = mutations.mutate(rootInfo.reconstructedNDN)
}

class NDNMutationsSerializer : Serializer<NDNMutations> {
    override fun write(output: PrimitivO, obj: NDNMutations) {
        output.writeObject(obj.mutations)
    }

    override fun read(input: PrimitivI): NDNMutations = NDNMutations(
        input.readObjectRequired()
    )

    override fun isReference(): Boolean = false

    override fun handlesReference(): Boolean = false
}
