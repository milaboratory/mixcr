package com.milaboratory.mixcr.trees

import com.milaboratory.core.Range
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.util.ClonesAlignmentRanges
import com.milaboratory.mixcr.util.RangeInfo
import java.util.stream.Collectors

class SyntheticNode private constructor(
    val fromRootToThis: MutationsDescription
) {

    fun mutate(fromParentToThis: MutationsDescription): SyntheticNode {
        return SyntheticNode(
            MutationsDescription(
                MutationsUtils.combine(
                    fromRootToThis.VMutationsWithoutCDR3,
                    fromParentToThis.VMutationsWithoutCDR3
                ),
                fromRootToThis.VMutationsInCDR3WithoutNDN
                    .combineWith(fromParentToThis.VMutationsInCDR3WithoutNDN),
                fromRootToThis.knownNDN.combineWith(fromParentToThis.knownNDN),
                fromRootToThis.JMutationsInCDR3WithoutNDN
                    .combineWith(fromParentToThis.JMutationsInCDR3WithoutNDN),
                MutationsUtils.combine(
                    fromRootToThis.JMutationsWithoutCDR3,
                    fromParentToThis.JMutationsWithoutCDR3
                )
            )
        )
    }

    companion object {
        fun createFromMutations(fromRootToThis: MutationsDescription): SyntheticNode {
            return SyntheticNode(fromRootToThis)
        }

        fun createRoot(
            VRanges: ClonesAlignmentRanges,
            VSequence1: NucleotideSequence,
            rootInfo: RootInfo,
            JRanges: ClonesAlignmentRanges,
            JSequence1: NucleotideSequence
        ): SyntheticNode {
            val emptyMutations = MutationsDescription(
                VRanges.commonRanges.stream() //TODO includeFirstInserts for first range
                    .map {
                        MutationsWithRange(
                            VSequence1,
                            Mutations.EMPTY_NUCLEOTIDE_MUTATIONS,
                            RangeInfo(it, false)
                        )
                    }
                    .collect(Collectors.toList()),
                MutationsWithRange(
                    VSequence1,
                    Mutations.EMPTY_NUCLEOTIDE_MUTATIONS,
                    RangeInfo(rootInfo.VRangeInCDR3, false)
                ),
                MutationsWithRange(
                    rootInfo.reconstructedNDN,
                    Mutations.EMPTY_NUCLEOTIDE_MUTATIONS,
                    RangeInfo(Range(0, rootInfo.reconstructedNDN.size()), true)
                ),
                MutationsWithRange(
                    JSequence1,
                    Mutations.EMPTY_NUCLEOTIDE_MUTATIONS,
                    RangeInfo(rootInfo.JRangeInCDR3, true)
                ),
                JRanges.commonRanges.stream()
                    .map {
                        MutationsWithRange(
                            JSequence1,
                            Mutations.EMPTY_NUCLEOTIDE_MUTATIONS,
                            RangeInfo(it, false)
                        )
                    }
                    .collect(Collectors.toList())
            )
            return SyntheticNode(emptyMutations)
        }
    }
}
