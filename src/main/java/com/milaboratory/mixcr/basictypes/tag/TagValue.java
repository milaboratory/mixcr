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

import com.milaboratory.primitivio.annotations.CustomSerializer;
import com.milaboratory.primitivio.annotations.Serializable;

@Serializable(custom = {
        @CustomSerializer(id = 1, type = SequenceAndQualityTagValue.class),
        @CustomSerializer(id = 2, type = SequenceTagValue.class),
        @CustomSerializer(id = 3, type = StringTagValue.class),
        @CustomSerializer(id = 4, type = LongTagValue.class)
})
public interface TagValue extends Comparable<TagValue> {
    /**
     * Returns true for tag values that can be used as a grouping key,
     * use {@link #extractKey()} to extract a key value from the tag value.
     */
    boolean isKey();

    /** Extracts TagValue that can be used as a key, basically by ripping off all auxiliary information. */
    TagValue extractKey();

    /** Forces each child class implement custom toString(). Required for textual exporting. */
    String toString();
}
