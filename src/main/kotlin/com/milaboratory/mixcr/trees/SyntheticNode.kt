@file:Suppress("LocalVariableName")

package com.milaboratory.mixcr.trees

import com.milaboratory.core.mutations.Mutations.EMPTY_NUCLEOTIDE_MUTATIONS
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.util.ClonesAlignmentRanges

class SyntheticNode private constructor(
    val fromRootToThis: MutationsSet,
) {
    fun mutate(mutations: MutationsDescription): SyntheticNode = SyntheticNode(
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
            VSequence1: NucleotideSequence,
            rootInfo: RootInfo,
            JRanges: ClonesAlignmentRanges,
            JSequence1: NucleotideSequence
        ): SyntheticNode = SyntheticNode(
            MutationsSet(
                VGeneMutations(
                    VSequence1,
                    VRanges.commonRanges.associateWith { EMPTY_NUCLEOTIDE_MUTATIONS },
                    PartInCDR3(rootInfo.VRangeInCDR3, EMPTY_NUCLEOTIDE_MUTATIONS)
                ),
                NDNMutations(rootInfo.reconstructedNDN, EMPTY_NUCLEOTIDE_MUTATIONS),
                JGeneMutations(
                    JSequence1,
                    PartInCDR3(rootInfo.JRangeInCDR3, EMPTY_NUCLEOTIDE_MUTATIONS),
                    JRanges.commonRanges.associateWith { EMPTY_NUCLEOTIDE_MUTATIONS }
                )
            )
        )
    }
}
