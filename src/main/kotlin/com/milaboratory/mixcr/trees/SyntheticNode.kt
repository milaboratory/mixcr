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

class SyntheticNode(
    val fromRootToThis: MutationsSet,
) {
    fun mutate(mutations: NodeMutationsDescription): SyntheticNode = SyntheticNode(
        MutationsSet(
            VGeneMutations(
                mutations = fromRootToThis.mutations.V.mutationsOutsideOfCDR3.mapValues { (geneFeature, value) ->
                    value.combineWith(
                        mutations.mutationsOutsideCDR3.V[geneFeature]!!.mutationsFromParentToThis
                    )
                },
                partInCDR3 = fromRootToThis.mutations.V.partInCDR3.copy(
                    mutations = fromRootToThis.mutations.V.partInCDR3.mutations
                        .combineWith(mutations.mutationsInCDR3.V.mutationsFromParentToThis)
                )
            ),
            fromRootToThis.NDNMutations.copy(
                mutations = fromRootToThis.NDNMutations.mutations
                    .combineWith(mutations.knownNDN.mutationsFromParentToThis)
            ),
            JGeneMutations(
                partInCDR3 = fromRootToThis.mutations.J.partInCDR3.copy(
                    mutations = fromRootToThis.mutations.J.partInCDR3.mutations
                        .combineWith(mutations.mutationsInCDR3.J.mutationsFromParentToThis)
                ),
                mutations = fromRootToThis.mutations.J.mutationsOutsideOfCDR3.mapValues { (geneFeature, value) ->
                    value.combineWith(
                        mutations.mutationsOutsideCDR3.J[geneFeature]!!.mutationsFromParentToThis
                    )
                }
            )
        )
    )

}
