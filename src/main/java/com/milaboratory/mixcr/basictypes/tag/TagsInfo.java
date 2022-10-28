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

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.primitivio.annotations.Serializable;

import java.util.AbstractCollection;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;

@Serializable(asJson = true)
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
@JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.NON_PRIVATE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE
)
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

    public TagInfo get(String tagName) {
        for (TagInfo ti : this)
            if (ti.getName().equalsIgnoreCase(tagName)) {
                return ti;
            }
        return null;
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

    public TagsInfo sortedPrefix() {
        return new TagsInfo(sortingLevel, Arrays.copyOf(tags, sortingLevel));
    }

    public int indexOf(String tagName) {
        int idx = -1;
        for (TagInfo ti : this)
            if (ti.getName().equalsIgnoreCase(tagName)) {
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
