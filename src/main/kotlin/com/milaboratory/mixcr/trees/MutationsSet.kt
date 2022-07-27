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
import com.milaboratory.mixcr.util.VJPair
import com.milaboratory.primitivio.*
import com.milaboratory.primitivio.annotations.Serializable

/**
 * Describe mutations of node from the root of the tree.
 */
@Suppress("DataClassPrivateConstructor")
@Serializable(by = MutationsSetSerializer::class)
data class MutationsSet private constructor(
    val mutations: VJPair<GeneMutations>,
    val NDNMutations: NDNMutations
) {
    constructor(
        VMutations: VGeneMutations,
        NDNMutations: NDNMutations,
        JMutations: JGeneMutations
    ) : this(VJPair(VMutations, JMutations), NDNMutations)
}

class MutationsSetSerializer : Serializer<MutationsSet> {
    override fun write(output: PrimitivO, obj: MutationsSet) {
        Util.writeMap(obj.mutations.V.mutationsOutsideOfCDR3, output)
        output.writeObject(obj.mutations.V.partInCDR3)
        output.writeObject(obj.NDNMutations)
        output.writeObject(obj.mutations.J.partInCDR3)
        Util.writeMap(obj.mutations.J.mutationsOutsideOfCDR3, output)
    }

    override fun read(input: PrimitivI): MutationsSet = MutationsSet(
        VGeneMutations(
            input.readMap(),
            input.readObjectRequired()
        ),
        input.readObjectRequired(),
        JGeneMutations(
            input.readObjectRequired(),
            input.readMap(),
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
