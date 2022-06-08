package com.milaboratory.mixcr.basictypes.tag;

public enum TagValueType {
    Enum(null),
    ByteString(null),
    Sequence(null),
    SequenceAndQuality(TagValueType.Sequence);

    private final TagValueType keyType;

    TagValueType(TagValueType keyType) {
        this.keyType = keyType;
    }

    public TagValueType getKeyType() {
        return keyType == null ? this : keyType;
    }
}
