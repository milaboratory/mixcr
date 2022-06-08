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
