package com.milaboratory.mixcr.trees;

import com.milaboratory.core.Range;
import com.milaboratory.core.mutations.MutationsBuilder;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.util.RangeInfo;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.milaboratory.core.mutations.Mutations.EMPTY_NUCLEOTIDE_MUTATIONS;

class SyntheticNode {
    private final MutationsDescription fromRootToThis;

    private SyntheticNode(MutationsDescription fromRootToThis) {
        this.fromRootToThis = fromRootToThis;
    }

    public static SyntheticNode createFromMutations(MutationsDescription fromRootToThis) {
        return new SyntheticNode(fromRootToThis);
    }

    public static SyntheticNode createFromParentAndDiffOfParentAndChild(MutationsDescription fromRootToParent, MutationsDescription fromParentToThis) {
        return new SyntheticNode(new MutationsDescription(
                combine(fromRootToParent.getVMutationsWithoutCDR3(), fromParentToThis.getVMutationsWithoutCDR3()),
                fromRootToParent.getVMutationsInCDR3WithoutNDN().combineWithMutations(fromParentToThis.getVMutationsInCDR3WithoutNDN().getMutations()),
                fromRootToParent.getKnownNDN().combineWithMutations(fromParentToThis.getKnownNDN().getMutations()),
                fromRootToParent.getJMutationsInCDR3WithoutNDN().combineWithMutations(fromParentToThis.getJMutationsInCDR3WithoutNDN().getMutations()),
                combine(fromRootToParent.getJMutationsWithoutCDR3(), fromParentToThis.getJMutationsWithoutCDR3())
        ));
    }

    public static SyntheticNode createRoot(List<Range> VRanges, NucleotideSequence VSequence1, RootInfo rootInfo, List<Range> JRanges, NucleotideSequence JSequence1) {
        MutationsDescription emptyMutations = new MutationsDescription(
                VRanges.stream()
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
                JRanges.stream()
                        .map(it -> new MutationsWithRange(JSequence1, EMPTY_NUCLEOTIDE_MUTATIONS, new RangeInfo(it, false)))
                        .collect(Collectors.toList())
        );

        return new SyntheticNode(emptyMutations);
    }

    public MutationsDescription getFromRootToThis() {
        return fromRootToThis;
    }

    private static List<MutationsWithRange> combine(List<MutationsWithRange> base, List<MutationsWithRange> combineWith) {
        return base.stream()
                .map(baseMutations -> {
                    MutationsBuilder<NucleotideSequence> builder = new MutationsBuilder<>(NucleotideSequence.ALPHABET);
                    for (MutationsWithRange combineWithMutations : combineWith) {
                        RangeInfo intersection = baseMutations.getRangeInfo().intersection(combineWithMutations.getRangeInfo());
                        if (intersection != null) {
                            int[] filteredMutations = IntStream.of(combineWithMutations.getMutations().getRAWMutations())
                                    .filter(intersection::contains)
                                    .toArray();
                            builder.append(filteredMutations);
                        }
                    }
                    return baseMutations.combineWithMutations(builder.createAndDestroy());
                })
                .collect(Collectors.toList());
    }
}
