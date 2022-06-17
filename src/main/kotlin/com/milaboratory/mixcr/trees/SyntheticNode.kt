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

import com.milaboratory.core.mutations.Mutations.EMPTY_NUCLEOTIDE_MUTATIONS
import com.milaboratory.mixcr.util.ClonesAlignmentRanges

class SyntheticNode private constructor(
    val fromRootToThis: MutationsSet,
) {
    fun mutate(mutations: NodeMutationsDescription): SyntheticNode = SyntheticNode(
        MutationsSet(
            fromRootToThis.VMutations.copy(
                mutations = fromRootToThis.VMutations.mutations.mapValues { (range, value) ->
                    value.combineWith(mutations.VMutationsWithoutCDR3[range]!!.mutations.move(range.lower))
                },
                partInCDR3 = fromRootToThis.VMutations.partInCDR3.copy(
                    mutations = fromRootToThis.VMutations.partInCDR3.mutations
                        .combineWith(mutations.VMutationsInCDR3WithoutNDN.mutations.move(fromRootToThis.VMutations.partInCDR3.range.lower))
                )
            ),
            fromRootToThis.NDNMutations.copy(
                mutations = fromRootToThis.NDNMutations.mutations
                    .combineWith(mutations.knownNDN.mutations)
            ),
            fromRootToThis.JMutations.copy(
                mutations = fromRootToThis.JMutations.mutations.mapValues { (range, value) ->
                    value.combineWith(mutations.JMutationsWithoutCDR3[range]!!.mutations.move(range.lower))
                },
                partInCDR3 = fromRootToThis.JMutations.partInCDR3.copy(
                    mutations = fromRootToThis.JMutations.partInCDR3.mutations
                        .combineWith(mutations.JMutationsInCDR3WithoutNDN.mutations.move(fromRootToThis.JMutations.partInCDR3.range.lower))
                )
            )
        )
    )

    companion object {
        fun createFromMutations(
            fromRootToThis: MutationsSet
        ): SyntheticNode {
            return SyntheticNode(fromRootToThis)
        }

        fun createRoot(
            VRanges: ClonesAlignmentRanges,
            rootInfo: RootInfo,
            JRanges: ClonesAlignmentRanges
        ): SyntheticNode = SyntheticNode(
            MutationsSet(
                VGeneMutations(
                    VRanges.commonRanges.associateWith { EMPTY_NUCLEOTIDE_MUTATIONS },
                    PartInCDR3(rootInfo.VRangeInCDR3, EMPTY_NUCLEOTIDE_MUTATIONS)
                ),
                NDNMutations(EMPTY_NUCLEOTIDE_MUTATIONS),
                JGeneMutations(
                    PartInCDR3(rootInfo.JRangeInCDR3, EMPTY_NUCLEOTIDE_MUTATIONS),
                    JRanges.commonRanges.associateWith { EMPTY_NUCLEOTIDE_MUTATIONS }
                )
            )
        )
    }
}
