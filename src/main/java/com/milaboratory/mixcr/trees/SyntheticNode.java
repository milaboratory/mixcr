package com.milaboratory.mixcr.trees;

import com.milaboratory.core.Range;
import com.milaboratory.core.mutations.Mutation;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.mutations.MutationsBuilder;
import com.milaboratory.core.sequence.NucleotideSequence;

import java.util.List;
import java.util.stream.Collectors;

class SyntheticNode {
    private final MutationsDescription fromRootToParent;
    private final MutationsDescription fromParentToThis;
    private final MutationsDescription fromRootToThis;

    public SyntheticNode(MutationsDescription fromRootToParent, MutationsDescription fromParentToThis) {
        this.fromRootToParent = fromRootToParent;
        this.fromParentToThis = fromParentToThis;
        fromRootToThis = new MutationsDescription(
                combine(fromRootToParent.getVMutationsWithoutCDR3(), fromParentToThis.getVMutationsWithoutCDR3()),
                fromRootToParent.getVMutationsInCDR3WithoutNDN().addMutations(fromParentToThis.getVMutationsInCDR3WithoutNDN().getMutations()),
                fromRootToParent.getKnownNDN().addMutations(fromParentToThis.getKnownNDN().getMutations()),
                fromRootToParent.getJMutationsInCDR3WithoutNDN().addMutations(fromParentToThis.getJMutationsInCDR3WithoutNDN().getMutations()),
                combine(fromRootToParent.getJMutationsWithoutCDR3(), fromParentToThis.getJMutationsWithoutCDR3())
        );
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
                        Range intersection = baseMutations.getRangeInfo().getRange().intersection(combineWithMutations.getRangeInfo().getRange());
                        if (intersection != null) {
                            for (int i = 0; i < combineWithMutations.getMutations().size(); i++) {
                                int mutation = combineWithMutations.getMutations().getMutation(i);
                                int position = Mutation.getPosition(mutation);
                                if (intersection.contains(position)) {
                                    builder.append(mutation);
                                }
                            }
                        }
                    }
                    Mutations<NucleotideSequence> andDestroy = builder.createAndDestroy();
                    Mutations<NucleotideSequence> resultMutations = baseMutations.getMutations().combineWith(andDestroy);
                    MutationsWithRange mutationsWithRange = baseMutations.withMutations(resultMutations);
                    mutationsWithRange.buildSequence();
                    return mutationsWithRange;
                })
                .collect(Collectors.toList());
    }
}
