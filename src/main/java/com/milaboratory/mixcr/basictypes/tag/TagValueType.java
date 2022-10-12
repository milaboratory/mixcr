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
import com.milaboratory.core.sequence.NucleotideSequence;

public enum TagValueType {
    Enum(null, java.lang.Enum.class),
    ByteString(null, com.milaboratory.util.ByteString.class),
    Sequence(null, NucleotideSequence.class),
    SequenceAndQuality(TagValueType.Sequence, NSequenceWithQuality.class);

    private final TagValueType keyType;
    private final Class<?> valueClass;

    TagValueType(TagValueType keyType, Class<?> valueClass) {
        this.keyType = keyType;
        this.valueClass = valueClass;
    }

    public TagValueType getKeyType() {
        return keyType == null ? this : keyType;
    }

    public boolean isKey() {
        return keyType == null;
    }

    public Class<?> getValueClass() {
        return valueClass;
    }
}
