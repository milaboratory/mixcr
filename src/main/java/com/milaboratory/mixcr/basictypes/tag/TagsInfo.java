package com.milaboratory.mixcr.basictypes.tag;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.primitivio.annotations.Serializable;

import java.util.Arrays;
import java.util.Objects;

@Serializable(asJson = true)
public final class TagsInfo {
    @JsonProperty("tags")
    public final TagInfo[] tags;

    @JsonCreator
    public TagsInfo(@JsonProperty("tags") TagInfo... tags) {
        Objects.requireNonNull(tags);
        this.tags = tags;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TagsInfo tagsInfo = (TagsInfo) o;
        return Arrays.equals(tags, tagsInfo.tags);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(tags);
    }
}
