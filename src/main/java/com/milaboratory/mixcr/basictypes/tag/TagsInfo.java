package com.milaboratory.mixcr.basictypes.tag;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.primitivio.annotations.Serializable;

import java.util.Arrays;
import java.util.Objects;

@Serializable(asJson = true)
public final class TagsInfo {
    @JsonProperty("sorted")
    public final boolean sorted;
    @JsonProperty("tags")
    public final TagInfo[] tags;

    @JsonCreator
    public TagsInfo(@JsonProperty("sorted") boolean sorted, @JsonProperty("tags") TagInfo... tags) {
        Objects.requireNonNull(tags);
        this.sorted = sorted;
        this.tags = tags;
    }

    public TagsInfo setSorted(boolean sorted){
        return new TagsInfo(sorted, tags);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TagsInfo tagsInfo = (TagsInfo) o;
        return sorted == tagsInfo.sorted && Arrays.equals(tags, tagsInfo.tags);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(sorted);
        result = 31 * result + Arrays.hashCode(tags);
        return result;
    }
}
