package com.milaboratory.mixcr.trees;

import com.milaboratory.core.mutations.MutationsBuilder;
import com.milaboratory.core.sequence.NucleotideSequence;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

class SyntheticNode {
    private final MutationsDescription fromRootToParent;
    private final MutationsDescription fromParentToThis;
    private final MutationsDescription fromRootToThis;

    public SyntheticNode(MutationsDescription fromRootToParent, MutationsDescription fromParentToThis) {
        this(fromRootToParent, fromParentToThis, new MutationsDescription(
                combine(fromRootToParent.getVMutationsWithoutCDR3(), fromParentToThis.getVMutationsWithoutCDR3()),
                fromRootToParent.getVMutationsInCDR3WithoutNDN().combineWithMutations(fromParentToThis.getVMutationsInCDR3WithoutNDN().getMutations()),
                fromRootToParent.getKnownNDN().combineWithMutations(fromParentToThis.getKnownNDN().getMutations()),
                fromRootToParent.getJMutationsInCDR3WithoutNDN().combineWithMutations(fromParentToThis.getJMutationsInCDR3WithoutNDN().getMutations()),
                combine(fromRootToParent.getJMutationsWithoutCDR3(), fromParentToThis.getJMutationsWithoutCDR3())
        ));
    }

    public SyntheticNode(MutationsDescription fromRootToParent, MutationsDescription fromParentToThis, MutationsDescription fromRootToThis) {
        this.fromRootToParent = fromRootToParent;
        this.fromParentToThis = fromParentToThis;
        this.fromRootToThis = fromRootToThis;
    }

    public MutationsDescription getFromRootToParent() {
        return fromRootToParent;
    }

    public MutationsDescription getFromParentToThis() {
        return fromParentToThis;
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
