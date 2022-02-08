package com.milaboratory.mixcr.trees;

import com.milaboratory.core.Range;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.util.RangeInfo;

public class MutationsWithRange {
    private final NucleotideSequence sequence1;
    private final Mutations<NucleotideSequence> mutations;
    private final RangeInfo rangeInfo;
    private NucleotideSequence result;

    public MutationsWithRange(
            NucleotideSequence sequence1,
            Mutations<NucleotideSequence> mutations,
            RangeInfo rangeInfo
    ) {
        this.sequence1 = sequence1;
        this.mutations = mutations;
        this.rangeInfo = rangeInfo;
    }

    public RangeInfo getRangeInfo() {
        return rangeInfo;
    }

    public NucleotideSequence getSequence1() {
        return sequence1;
    }

    public Mutations<NucleotideSequence> getMutations() {
        return mutations;
    }

    public int mutationsCount() {
        return mutationsForRange().size();
    }

    MutationsWithRange differenceWith(MutationsWithRange comparison) {
        if (!getRangeInfo().getRange().equals(comparison.getRangeInfo().getRange())) {
            throw new IllegalArgumentException();
        }
        NucleotideSequence sequence1 = buildSequence();
        return new MutationsWithRange(
                sequence1,
                mutationsForRange().invert()
                        .combineWith(comparison.mutationsForRange())
                        .move(-getRangeInfo().getRange().getLower()),
                new RangeInfo(new Range(0, sequence1.size()), true)
        );
    }

    MutationsWithRange combineWith(MutationsWithRange next) {
        return new MutationsWithRange(
                sequence1,
                mutationsForRange().combineWith(next.mutationsForRange().move(getRangeInfo().getRange().getLower())),
                new RangeInfo(rangeInfo.getRange(), true)
        );
    }

    int lengthDelta() {
        return projectedRange().getRange().length() - getRangeInfo().getRange().length();
    }

    public RangeInfo projectedRange() {
        return new RangeInfo(
                MutationsUtils.projectRange(mutations, rangeInfo),
                rangeInfo.isIncludeFirstInserts()
        );
    }

    public MutationsWithRange combineWithMutationsToTheRight(Mutations<NucleotideSequence> mutations, Range range) {
        if (rangeInfo.getRange().getUpper() != range.getLower()) {
            throw new IllegalArgumentException();
        }
        return new MutationsWithRange(
                sequence1,
                this.mutations.concat(mutations),
                new RangeInfo(
                        rangeInfo.getRange().setUpper(range.getUpper()),
                        rangeInfo.getRange().isEmpty()
                )
        );
    }

    public MutationsWithRange combineWithMutationsToTheLeft(Mutations<NucleotideSequence> mutations, Range range) {
        if (range.getUpper() != rangeInfo.getRange().getLower()) {
            throw new IllegalArgumentException();
        }
        Mutations<NucleotideSequence> mutationsBefore = mutationsForRange();
        return new MutationsWithRange(
                sequence1,
                mutations.concat(mutationsBefore),
                new RangeInfo(
                        rangeInfo.getRange().setLower(range.getLower()),
                        true
                )
        );
    }

    public MutationsWithRange combineWithTheSameMutationsRight(MutationsWithRange another) {
        if (rangeInfo.getRange().getUpper() != another.rangeInfo.getRange().getLower()) {
            throw new IllegalArgumentException();
        }
        if (!mutations.equals(another.mutations)) {
            throw new IllegalArgumentException();
        }
        return new MutationsWithRange(
                sequence1,
                mutations,
                new RangeInfo(
                        rangeInfo.getRange().setUpper(another.rangeInfo.getRange().getUpper()),
                        false
                )
        );
    }

    public MutationsWithRange combineWithTheSameMutationsLeft(MutationsWithRange another) {
        if (rangeInfo.getRange().getLower() != another.rangeInfo.getRange().getUpper()) {
            throw new IllegalArgumentException();
        }
        if (!mutations.equals(another.mutations)) {
            throw new IllegalArgumentException();
        }
        return new MutationsWithRange(
                sequence1,
                mutations,
                new RangeInfo(
                        rangeInfo.getRange().setLower(another.rangeInfo.getRange().getLower()),
                        another.getRangeInfo().isIncludeFirstInserts()
                )
        );
    }

    NucleotideSequence buildSequence() {
        if (result == null) {
            result = MutationsUtils.buildSequence(sequence1, mutations, rangeInfo);
        }
        return result;
    }

    Mutations<NucleotideSequence> mutationsForRange() {
        return rangeInfo.extractAbsoluteMutations(mutations);
    }
}
