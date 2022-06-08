/*
 * Copyright (c) 2014-2022, MiLaboratories Inc. All Rights Reserved
 *
 * Before downloading or accessing the software, please read carefully the
 * License Agreement available at:
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 *
 * By downloading or accessing the software, you accept and agree to be bound
 * by the terms of the License Agreement. If you do not want to agree to the terms
 * of the Licensing Agreement, you must not download or access the software.
 */
package com.milaboratory.mixcr.basictypes.tag;

import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.primitivio.annotations.Serializable;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@Serializable(by = IO.SequenceTagValueSerializer.class)
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
