package com.milaboratory.mixcr.basictypes.tag;

import com.milaboratory.primitivio.annotations.CustomSerializer;
import com.milaboratory.primitivio.annotations.Serializable;

@Serializable(custom = {
        @CustomSerializer(id = 1, type = SequenceAndQualityTagValue.class),
        @CustomSerializer(id = 2, type = SequenceTagValue.class)
})
public interface TagValue extends Comparable<TagValue> {
    /**
     * Returns true for tag values that can be used as a grouping key,
     * use {@link #extractKey()} to extract a key value from the tag value.
     */
    boolean isKey();

    /** Extracts TagValue that can be used as a key, basically by ripping off all auxiliary information. */
    TagValue extractKey();
}
