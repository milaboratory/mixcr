package com.milaboratory.mixcr.basictypes.tag;

import com.milaboratory.core.sequence.NucleotideSequence;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class SequenceTagValue implements TagValue {
    public final NucleotideSequence sequence;

    public SequenceTagValue(NucleotideSequence sequence) {
        Objects.requireNonNull(sequence);
        this.sequence = sequence;
    }

    @Override
    public boolean isKey() {
        return true;
    }

    @Override
    public TagValue extractKey() {
        return this;
    }

    @Override
    public int compareTo(@NotNull TagValue o) {
        return sequence.compareTo(((SequenceTagValue) o).sequence);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SequenceTagValue that = (SequenceTagValue) o;
        return sequence.equals(that.sequence);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sequence);
    }

    @Override
    public String toString() {
        return sequence.toString();
    }
}
