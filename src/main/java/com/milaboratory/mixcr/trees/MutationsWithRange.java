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
    private final boolean includeFirstMutations;
    private final boolean includeLastMutations;

    public MutationsWithRange(
            NucleotideSequence sequence1,
            Mutations<NucleotideSequence> fromBaseToParent,
            Mutations<NucleotideSequence> fromParentToThis,
            Range sequence1Range,
            boolean includeFirstMutations,
            boolean includeLastMutations
    ) {
        this.sequence1 = sequence1;
        this.fromBaseToParent = fromBaseToParent;
        this.fromParentToThis = fromParentToThis;
        this.sequence1Range = sequence1Range;
        this.combinedMutations = this.fromBaseToParent.combineWith(this.fromParentToThis);
        this.includeFirstMutations = includeFirstMutations;
        this.includeLastMutations = includeLastMutations;
    }

    public boolean isIncludeLastMutations() {
        return includeLastMutations;
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

    public boolean isIncludeFirstMutations() {
        return includeFirstMutations;
    }

    int lengthDelta() {
        Range sequence2Range = MutationsUtils.projectRange(getCombinedMutations(), getSequence1Range(), isIncludeFirstMutations(), isIncludeLastMutations());
        return sequence2Range.length() - getSequence1Range().length();
    }

    NucleotideSequence buildSequence() {
        return MutationsUtils.buildSequence(
                getSequence1(),
                getCombinedMutations(),
                getSequence1Range(),
                isIncludeFirstMutations(),
                isIncludeLastMutations()
        );
    }

    MutationsWithRange withIncludeLastMutations(boolean includeLastMutations) {
        return new MutationsWithRange(
                sequence1,
                fromBaseToParent,
                fromParentToThis,
                sequence1Range,
                includeFirstMutations,
                includeLastMutations
        );
    }

    MutationsWithRange withIncludeFirstMutations(boolean includeFirstMutations) {
        return new MutationsWithRange(
                sequence1,
                fromBaseToParent,
                fromParentToThis,
                sequence1Range,
                includeFirstMutations,
                includeLastMutations
        );
    }
}
