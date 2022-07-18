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

class SyntheticNode private constructor(
    val fromRootToThis: MutationsSet,
) {
    fun mutate(mutations: NodeMutationsDescription): SyntheticNode = SyntheticNode(
        MutationsSet(
            VGeneMutations(
                mutations = fromRootToThis.VMutations.mutations.mapValues { (geneFeature, value) ->
                    value.combineWith(
                        mutations.VMutationsWithoutCDR3[geneFeature]!!.mutationsFromParentToThis
                    )
                },
                partInCDR3 = fromRootToThis.VMutations.partInCDR3.copy(
                    mutations = fromRootToThis.VMutations.partInCDR3.mutations
                        .combineWith(mutations.VMutationsInCDR3WithoutNDN.mutationsFromParentToThis)
                )
            ),
            fromRootToThis.NDNMutations.copy(
                mutations = fromRootToThis.NDNMutations.mutations
                    .combineWith(mutations.knownNDN.mutationsFromParentToThis)
            ),
            JGeneMutations(
                partInCDR3 = fromRootToThis.JMutations.partInCDR3.copy(
                    mutations = fromRootToThis.JMutations.partInCDR3.mutations
                        .combineWith(mutations.JMutationsInCDR3WithoutNDN.mutationsFromParentToThis)
                ),
                mutations = fromRootToThis.JMutations.mutations.mapValues { (geneFeature, value) ->
                    value.combineWith(
                        mutations.JMutationsWithoutCDR3[geneFeature]!!.mutationsFromParentToThis
                    )
                }
            )
        )
    )

    companion object {
        fun createFromMutations(
            fromRootToThis: MutationsSet
        ): SyntheticNode {
            return SyntheticNode(fromRootToThis)
        }

        fun createRoot(rootInfo: RootInfo): SyntheticNode = SyntheticNode(
            MutationsSet(
                VGeneMutations(
                    rootInfo.VAlignFeatures.associateWith { EMPTY_NUCLEOTIDE_MUTATIONS },
                    PartInCDR3(rootInfo.VRangeInCDR3, EMPTY_NUCLEOTIDE_MUTATIONS)
                ),
                NDNMutations(EMPTY_NUCLEOTIDE_MUTATIONS),
                JGeneMutations(
                    PartInCDR3(rootInfo.JRangeInCDR3, EMPTY_NUCLEOTIDE_MUTATIONS),
                    rootInfo.JAlignFeatures.associateWith { EMPTY_NUCLEOTIDE_MUTATIONS }
                )
            )
        )
    }
}
