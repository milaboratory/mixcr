package com.milaboratory.mixcr.trees;

import com.milaboratory.core.Range;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.sequence.NucleotideSequence;

public class MutationsWithRange {
    private final NucleotideSequence sequence1;
    private final Mutations<NucleotideSequence> fromBaseToParent;
    private final Mutations<NucleotideSequence> fromParentToThis;
    private final Mutations<NucleotideSequence> combinedMutations;
    private final Range sequence1Range;

    public MutationsWithRange(
            NucleotideSequence sequence1,
            Mutations<NucleotideSequence> fromBaseToParent,
            Mutations<NucleotideSequence> fromParentToThis,
            Range sequence1Range
    ) {
        this.sequence1 = sequence1;
        this.fromBaseToParent = fromBaseToParent;
        this.fromParentToThis = fromParentToThis;
        this.sequence1Range = sequence1Range;
        this.combinedMutations = fromBaseToParent.combineWith(fromParentToThis);
    }

    public NucleotideSequence getSequence1() {
        return sequence1;
    }

    public Mutations<NucleotideSequence> getCombinedMutations() {
        return combinedMutations;
    }

    public Range getSequence1Range() {
        return sequence1Range;
    }

    public Mutations<NucleotideSequence> getFromBaseToParent() {
        return fromBaseToParent;
    }

    public Mutations<NucleotideSequence> getFromParentToThis() {
        return fromParentToThis;
    }
}
