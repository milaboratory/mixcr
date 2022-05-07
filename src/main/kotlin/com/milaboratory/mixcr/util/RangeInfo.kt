package com.milaboratory.mixcr.util;

import com.milaboratory.core.Range;
import com.milaboratory.core.mutations.Mutation;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.sequence.NucleotideSequence;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.stream.IntStream;

public class RangeInfo {
    private final Range range;
    private final boolean includeFirstInserts;

    public RangeInfo(Range range, boolean includeFirstInserts) {
        this.range = range;
        this.includeFirstInserts = includeFirstInserts;
    }

    public Range getRange() {
        return range;
    }

    public boolean isIncludeFirstInserts() {
        return includeFirstInserts;
    }

    @Nullable
    public RangeInfo intersection(RangeInfo another) {
        Range intersection;
        if (getRange().equals(another.getRange())) {
            intersection = getRange();
        } else {
            intersection = getRange().intersection(another.getRange());
            if (intersection == null) {
                return null;
            }
        }
        boolean includeFirstInserts;
        if (intersection.getLower() == getRange().getLower()) {
            includeFirstInserts = isIncludeFirstInserts();
        } else if (intersection.getLower() == another.getRange().getLower()) {
            includeFirstInserts = isIncludeFirstInserts() || another.isIncludeFirstInserts();
        } else {
            includeFirstInserts = false;
        }
        return new RangeInfo(
                intersection,
                includeFirstInserts
        );
    }

    @Override
    public String toString() {
        return "RangeInfo{" +
                "range=" + range +
                ", includeFirstInserts=" + includeFirstInserts +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RangeInfo rangeInfo = (RangeInfo) o;
        return includeFirstInserts == rangeInfo.includeFirstInserts && range.equals(rangeInfo.range);
    }

    @Override
    public int hashCode() {
        return Objects.hash(range, includeFirstInserts);
    }

    public Mutations<NucleotideSequence> extractAbsoluteMutations(Mutations<NucleotideSequence> mutations) {
        int[] filteredMutations = IntStream.of(mutations.getRAWMutations())
                .filter(this::contains)
                .toArray();
        return new Mutations<>(NucleotideSequence.ALPHABET, filteredMutations);
    }

    public boolean contains(int mutation) {
        int position = Mutation.getPosition(mutation);
        if (position == range.getLower()) {
            if (Mutation.isInsertion(mutation)) {
                return includeFirstInserts;
            } else {
                return true;
            }
        } else if (position == range.getUpper()) {
            return Mutation.isInsertion(mutation);
        } else {
            return range.getLower() < position && position < range.getUpper();
        }
    }
}
