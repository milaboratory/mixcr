package com.milaboratory.mixcr.trees;

import com.milaboratory.core.Range;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.util.ClonesAlignmentRanges;
import com.milaboratory.mixcr.util.RangeInfo;

import java.util.stream.Collectors;

import static com.milaboratory.core.mutations.Mutations.EMPTY_NUCLEOTIDE_MUTATIONS;

class SyntheticNode {
    private final MutationsDescription fromRootToThis;

    private SyntheticNode(MutationsDescription fromRootToThis) {
        this.fromRootToThis = fromRootToThis;
    }

    public static SyntheticNode createFromMutations(MutationsDescription fromRootToThis) {
        return new SyntheticNode(fromRootToThis);
    }

    public SyntheticNode mutate(MutationsDescription fromParentToThis) {
        return new SyntheticNode(new MutationsDescription(
                MutationsUtils.combine(fromRootToThis.getVMutationsWithoutCDR3(), fromParentToThis.getVMutationsWithoutCDR3()),
                fromRootToThis.getVMutationsInCDR3WithoutNDN().combineWith(fromParentToThis.getVMutationsInCDR3WithoutNDN()),
                fromRootToThis.getKnownNDN().combineWith(fromParentToThis.getKnownNDN()),
                fromRootToThis.getJMutationsInCDR3WithoutNDN().combineWith(fromParentToThis.getJMutationsInCDR3WithoutNDN()),
                MutationsUtils.combine(fromRootToThis.getJMutationsWithoutCDR3(), fromParentToThis.getJMutationsWithoutCDR3())
        ));
    }

    public static SyntheticNode createRoot(ClonesAlignmentRanges VRanges, NucleotideSequence VSequence1, RootInfo rootInfo, ClonesAlignmentRanges JRanges, NucleotideSequence JSequence1) {
        MutationsDescription emptyMutations = new MutationsDescription(
                VRanges.getCommonRanges().stream()
                        //TODO includeFirstInserts for first range
                        .map(it -> new MutationsWithRange(VSequence1, EMPTY_NUCLEOTIDE_MUTATIONS, new RangeInfo(it, false)))
                        .collect(Collectors.toList()),
                new MutationsWithRange(
                        VSequence1,
                        EMPTY_NUCLEOTIDE_MUTATIONS,
                        new RangeInfo(rootInfo.getVRangeInCDR3(), false)
                ),
                new MutationsWithRange(
                        rootInfo.getReconstructedNDN(),
                        EMPTY_NUCLEOTIDE_MUTATIONS,
                        new RangeInfo(new Range(0, rootInfo.getReconstructedNDN().size()), true)
                ),
                new MutationsWithRange(
                        JSequence1,
                        EMPTY_NUCLEOTIDE_MUTATIONS,
                        new RangeInfo(rootInfo.getJRangeInCDR3(), true)
                ),
                JRanges.getCommonRanges().stream()
                        .map(it -> new MutationsWithRange(JSequence1, EMPTY_NUCLEOTIDE_MUTATIONS, new RangeInfo(it, false)))
                        .collect(Collectors.toList())
        );

        return new SyntheticNode(emptyMutations);
    }

    public MutationsDescription getFromRootToThis() {
        return fromRootToThis;
    }

}
