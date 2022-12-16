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
@file:Suppress("LocalVariableName")

package com.milaboratory.mixcr.trees

import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.util.VJPair
import com.milaboratory.mixcr.util.plus
import com.milaboratory.primitivio.PrimitivI
import com.milaboratory.primitivio.PrimitivO
import com.milaboratory.primitivio.Serializer
import com.milaboratory.primitivio.annotations.Serializable
import com.milaboratory.primitivio.readMap
import com.milaboratory.primitivio.readObjectRequired
import com.milaboratory.primitivio.writeMap
import io.repseq.core.GeneFeature

/**
 * Describe mutations of node from the root of the tree.
 */
@Suppress("DataClassPrivateConstructor")
@Serializable(by = MutationsSet.SerializerImpl::class)
data class MutationsSet private constructor(
    val mutations: VJPair<GeneMutations>,
    val NDNMutations: NDNMutations
) {
    constructor(
        VMutations: VGeneMutations,
        NDNMutations: NDNMutations,
        JMutations: JGeneMutations
    ) : this(VJPair(VMutations, JMutations), NDNMutations)

    fun buildCDR3(rootInfo: RootInfo) = MutationsUtils.buildSequence(
        rootInfo.sequence1.V,
        mutations.V.partInCDR3.mutations,
        rootInfo.rangeInCDR3.V
    ) + NDNMutations.mutations.mutate(rootInfo.reconstructedNDN) +
            MutationsUtils.buildSequence(
                rootInfo.sequence1.J,
                mutations.J.partInCDR3.mutations,
                rootInfo.rangeInCDR3.J
            )

    class SerializerImpl : Serializer<MutationsSet> {
        override fun write(output: PrimitivO, obj: MutationsSet) {
            output.writeMap(obj.mutations.V.mutationsOutsideOfCDR3, { writeObject(it) }, { writeObject(it) })
            output.writeObject(obj.mutations.V.partInCDR3)
            output.writeObject(obj.NDNMutations)
            output.writeObject(obj.mutations.J.partInCDR3)
            output.writeMap(obj.mutations.J.mutationsOutsideOfCDR3, { writeObject(it) }, { writeObject(it) })
        }

        override fun read(input: PrimitivI): MutationsSet {
            val VMutations: Map<GeneFeature, Mutations<NucleotideSequence>> =
                input.readMap({ readObjectRequired() }, { readObjectRequired() })
            val VPartInCDR3 = input.readObjectRequired<PartInCDR3>()
            val NDNMutations = input.readObjectRequired<NDNMutations>()
            val JPartInCDR3 = input.readObjectRequired<PartInCDR3>()
            val JMutations: Map<GeneFeature, Mutations<NucleotideSequence>> =
                input.readMap({ readObjectRequired() }, { readObjectRequired() })
            return MutationsSet(
                VGeneMutations(
                    VMutations,
                    VPartInCDR3
                ),
                NDNMutations,
                JGeneMutations(
                    JPartInCDR3,
                    JMutations,
                )
            )
        }
    }
}

@Serializable(by = NDNMutations.SerializerImpl::class)
data class NDNMutations(
    val mutations: Mutations<NucleotideSequence>
) {
    fun buildSequence(rootInfo: RootInfo): NucleotideSequence = mutations.mutate(rootInfo.reconstructedNDN)

    class SerializerImpl : Serializer<NDNMutations> {
        override fun write(output: PrimitivO, obj: NDNMutations) {
            output.writeObject(obj.mutations)
        }

        override fun read(input: PrimitivI): NDNMutations {
            val mutations = input.readObjectRequired<Mutations<NucleotideSequence>>()
            return NDNMutations(
                mutations
            )
        }
    }
}
