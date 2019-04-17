package com.milaboratory.mixcr.basictypes;

import com.milaboratory.primitivio.annotations.Serializable;
import gnu.trove.iterator.TObjectDoubleIterator;
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

    public TagCounter(TagTuple tags, double count) {
        this.tags = new TObjectDoubleHashMap<>();
        this.tags.put(tags, count);
    }

    public TagCounter(TagTuple tags) {
        this(tags, 1.0);
    }

    public double getOrDefault(TagTuple tt, double d) {
        if (!tags.containsKey(tt))
            return d;
        else
            return tags.get(tt);
    }

    public int size() {
        return tags.size();
    }

    public double get(TagTuple tt) {
        return getOrDefault(tt, Double.NaN);
    }

    public TObjectDoubleIterator<TagTuple> iterator() {
        TObjectDoubleIterator<TagTuple> it = tags.iterator();
        return new TObjectDoubleIterator<TagTuple>() {
            @Override
            public TagTuple key() {
                return it.key();
            }

            @Override
            public double value() {
                return it.value();
            }

            @Override
            public double setValue(double val) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void advance() {
                it.advance();
            }

            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
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
