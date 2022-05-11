package com.milaboratory.mixcr.basictypes.tag;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.primitivio.annotations.Serializable;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@Serializable(asJson = true)
public final class TagInfo implements Comparable<TagInfo> {
    @JsonProperty("type")
    private final TagType type;
    @JsonProperty("valueType")
    private final TagValueType valueType;
    @JsonProperty("name")
    private final String name;
    @JsonProperty("index")
    private final int index;

    @JsonCreator
    public TagInfo(@JsonProperty("type") TagType type,
                   @JsonProperty("valueType") TagValueType valueType,
                   @JsonProperty("name") String name,
                   @JsonProperty("index") int index) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(valueType);
        Objects.requireNonNull(name);
        this.type = type;
        this.valueType = valueType;
        this.name = name;
        this.index = index;
    }

    public TagType getType() {
        return type;
    }

    public TagValueType getValueType() {
        return valueType;
    }

    public String getName() {
        return name;
    }

    public int getIndex() {
        return index;
    }

    public TagInfo withIndex(int idx) {
        return new TagInfo(type, valueType, name, idx);
    }

    @Override
    public int compareTo(@NotNull TagInfo o) {
        int c;
        if ((c = Integer.compare(type.ordinal(), o.type.ordinal())) != 0)
            return c;
        if ((c = name.compareTo(o.name)) != 0)
            return c;
        return Integer.compare(index, o.index);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TagInfo tagInfo = (TagInfo) o;
        return index == tagInfo.index && type == tagInfo.type && valueType == tagInfo.valueType && name.equals(tagInfo.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, valueType, name, index);
    }
}
