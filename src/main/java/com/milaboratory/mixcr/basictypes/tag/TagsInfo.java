package com.milaboratory.mixcr.basictypes.tag;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.primitivio.annotations.Serializable;

import java.util.*;

@Serializable(asJson = true)
public final class TagsInfo extends AbstractCollection<TagInfo> {
    public static final TagsInfo NO_TAGS = new TagsInfo(0);

    @JsonProperty("sortingLevel")
    final int sortingLevel;
    @JsonProperty("tags")
    final TagInfo[] tags;

    @JsonCreator
    public TagsInfo(@JsonProperty("sortingLevel") int sortingLevel, @JsonProperty("tags") TagInfo... tags) {
        Objects.requireNonNull(tags);
        this.sortingLevel = sortingLevel;
        this.tags = tags;
    }

    public boolean hasTagsWithType(TagType groupingLevel) {
        for (TagInfo tag : tags)
            if (tag.getType() == groupingLevel)
                return true;
        return false;
    }

    public int getDepthFor(TagType groupingLevel) {
        for (int i = 0; i < tags.length; i++)
            if (tags[i].getType().ordinal() > groupingLevel.ordinal())
                return i;
        return tags.length;
    }

    public TagInfo get(int idx) {
        return tags[idx];
    }

    @Override
    public int size() {
        return tags.length;
    }

    public int getSortingLevel() {
        return sortingLevel;
    }

    public boolean hasNoTags() {
        return tags.length == 0;
    }

    public TagsInfo setSorted(int sortedLevel) {
        return new TagsInfo(sortedLevel, tags);
    }

    public int indexOf(String tagName){
        int idx = -1;
        for (TagInfo ti : this)
            if (ti.getName().equals(tagName)) {
                idx = ti.getIndex();
                break;
            }
        return idx;
    }

    @Override
    @SuppressWarnings("NullableProblems")
    public Iterator<TagInfo> iterator() {
        return Arrays.asList(tags).iterator();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TagsInfo tagsInfo = (TagsInfo) o;
        return sortingLevel == tagsInfo.sortingLevel && Arrays.equals(tags, tagsInfo.tags);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(sortingLevel);
        result = 31 * result + Arrays.hashCode(tags);
        return result;
    }
}
