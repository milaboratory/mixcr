package com.milaboratory.mixcr.basictypes.tag;

import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.primitivio.annotations.Serializable;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@Serializable(by = IO.SequenceAndQualityTagValueSerializer.class)
public final class SequenceAndQualityTagValue implements TagValue {
    public final NSequenceWithQuality data;

    public SequenceAndQualityTagValue(NSequenceWithQuality data) {
        Objects.requireNonNull(data);
        this.data = data;
    }

    @Override
    public boolean isKey() {
        return false;
    }

    @Override
    public TagValue extractKey() {
        return new SequenceTagValue(data.getSequence());
    }

    @Override
    public int compareTo(@NotNull TagValue o) {
        return data.compareTo(((SequenceAndQualityTagValue) o).data);
    }

    @Override
    public String toString() {
        return data.toString();
    }
}
