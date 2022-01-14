package com.milaboratory.mixcr.trees;

import com.milaboratory.core.Range;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.sequence.NucleotideSequence;

public class MutationsWithRange {
    private final NucleotideSequence sequence1;
    private final Mutations<NucleotideSequence> mutations;
    private final Range sequence1Range;
    private final boolean includeFirstMutations;
    private final boolean includeLastMutations;
    private NucleotideSequence result;

    public MutationsWithRange(
            NucleotideSequence sequence1,
            Mutations<NucleotideSequence> mutations,
            Range sequence1Range,
            boolean includeFirstMutations,
            boolean includeLastMutations
    ) {
        this.sequence1 = sequence1;
        this.mutations = mutations;
        this.sequence1Range = sequence1Range;
        this.includeFirstMutations = includeFirstMutations;
        this.includeLastMutations = includeLastMutations;
    }

    public boolean isIncludeLastMutations() {
        return includeLastMutations;
    }

    public NucleotideSequence getSequence1() {
        return sequence1;
    }

    public Mutations<NucleotideSequence> getMutations() {
        return mutations;
    }

    public int mutationsCount() {
        return mutations.extractAbsoluteMutationsForRange(sequence1Range).size();
    }

    public Range getSequence1Range() {
        return sequence1Range;
    }

    public boolean isIncludeFirstMutations() {
        return includeFirstMutations;
    }

    public MutationsWithRange withMutations(Mutations<NucleotideSequence> mutations) {
        return new MutationsWithRange(sequence1, mutations, sequence1Range, includeFirstMutations, includeLastMutations);
    }

    public MutationsWithRange addMutations(Mutations<NucleotideSequence> additional) {
        return new MutationsWithRange(sequence1, mutations.combineWith(additional), sequence1Range, includeFirstMutations, includeLastMutations);
    }

    int lengthDelta() {
        Range sequence2Range = MutationsUtils.projectRange(getMutations(), getSequence1Range(), isIncludeFirstMutations(), isIncludeLastMutations());
        return sequence2Range.length() - getSequence1Range().length();
    }

    public MutationsWithRange combineWithMutationsToTheRight(Mutations<NucleotideSequence> mutations, Range range) {
        if (sequence1Range.getUpper() != range.getLower()) {
            throw new IllegalArgumentException();
        }
        return new MutationsWithRange(
                sequence1,
                this.mutations.concat(mutations),
                new Range(sequence1Range.getLower(), range.getUpper()),
                sequence1Range.isEmpty() || includeFirstMutations,
                true
        );
    }

    public MutationsWithRange combineWithMutationsToTheLeft(Mutations<NucleotideSequence> mutations, Range range) {
        if (range.getUpper() != sequence1Range.getLower()) {
            throw new IllegalArgumentException();
        }
        Mutations<NucleotideSequence> mutationsBefore;
        if (sequence1Range.isEmpty()) {
            mutationsBefore = Mutations.EMPTY_NUCLEOTIDE_MUTATIONS;
        } else {
            mutationsBefore = this.mutations.move(mutations.getLengthDelta());
        }
        return new MutationsWithRange(
                sequence1,
                mutations.concat(mutationsBefore),
                new Range(range.getLower(), sequence1Range.getUpper()),
                true,
                sequence1Range.isEmpty() || includeLastMutations
        );
    }

    public MutationsWithRange combineWithTheSameMutationsRight(MutationsWithRange another) {
        if (sequence1Range.getUpper() != another.sequence1Range.getLower()) {
            throw new IllegalArgumentException();
        }
        if (!mutations.equals(another.mutations)) {
            throw new IllegalArgumentException();
        }
        return new MutationsWithRange(
                sequence1,
                mutations,
                new Range(sequence1Range.getLower(), another.sequence1Range.getUpper()),
                includeFirstMutations,
                another.includeLastMutations
        );
    }

    public MutationsWithRange combineWithTheSameMutationsLeft(MutationsWithRange another) {
        if (sequence1Range.getLower() != another.sequence1Range.getUpper()) {
            throw new IllegalArgumentException();
        }
        if (!mutations.equals(another.mutations)) {
            throw new IllegalArgumentException();
        }
        return new MutationsWithRange(
                sequence1,
                mutations,
                new Range(another.sequence1Range.getLower(), sequence1Range.getUpper()),
                another.includeFirstMutations,
                includeLastMutations
        );
    }

    NucleotideSequence buildSequence() {
        if (result == null) {
            result = MutationsUtils.buildSequence(
                    getSequence1(),
                    getMutations(),
                    getSequence1Range(),
                    isIncludeFirstMutations(),
                    isIncludeLastMutations()
            );
        }
        return result;
    }
}
