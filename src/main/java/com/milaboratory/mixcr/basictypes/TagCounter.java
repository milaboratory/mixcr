package com.milaboratory.mixcr.basictypes;

import com.milaboratory.primitivio.annotations.Serializable;
import gnu.trove.map.hash.TObjectDoubleHashMap;

/**
 *
 */
@Serializable(by = IO.TagCounterSerializer.class)
public final class TagCounter {
    public static final TagCounter EMPTY = new TagCounter(new TObjectDoubleHashMap<>());

    final TObjectDoubleHashMap<TagTuple> tags;

    TagCounter(TObjectDoubleHashMap<TagTuple> tags) {
        this.tags = tags;
    }

    public TagCounter(TagTuple tags) {
        this.tags = new TObjectDoubleHashMap<>();
        this.tags.put(tags, 1.0);
    }

    public boolean isEmpty() {
        return this.equals(EMPTY);
    }

    @Override
    public String toString() {
        return tags.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TagCounter that = (TagCounter) o;
        return tags.equals(that.tags);
    }

    @Override
    public int hashCode() {
        return tags.hashCode();
    }
}
