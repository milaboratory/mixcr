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

import com.milaboratory.primitivio.annotations.Serializable;
import gnu.trove.iterator.TObjectDoubleIterator;
import gnu.trove.map.hash.TObjectDoubleHashMap;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Serializable(by = IO.TagCounterSerializer.class)
public final class TagCount {
    public static final TagCount NO_TAGS_1 = new TagCount(TagTuple.NO_TAGS);

    final TagTuple singletonTuple;
    final double singletonCount;
    final int depth;
    final TObjectDoubleHashMap<TagTuple> tagMap;

    public TagCount(TagTuple singletonTuple) {
        this.singletonTuple = singletonTuple;
        this.singletonCount = 1.0;
        this.depth = singletonTuple.tags.length;
        this.tagMap = null;
    }

    public TagCount(TagTuple singletonTuple, double singletonCount) {
        if (!singletonTuple.isKey() && singletonCount != 1.0)
            throw new IllegalArgumentException("Non-key tuples can be associated only with 1.0 count.");
        this.singletonTuple = singletonTuple;
        this.singletonCount = singletonCount;
        this.depth = singletonTuple.tags.length;
        this.tagMap = null;
    }

    TagCount(TObjectDoubleHashMap<TagTuple> tagMap) {
        if (tagMap.isEmpty())
            throw new IllegalStateException();

        TagTuple sTuple = null;
        double sCount = Double.NaN;
        if (tagMap.size() == 1) {
            TObjectDoubleIterator<TagTuple> it = tagMap.iterator();
            it.advance();
            sTuple = it.key();
            sCount = it.value();
        }

        if (sTuple != null) {
            if (!sTuple.isKey() && sCount != 1.0)
                throw new IllegalArgumentException("Non-key tuples can be associated only with 1.0 count.");
            this.singletonTuple = sTuple;
            this.singletonCount = sCount;
            this.depth = this.singletonTuple.tags.length;
            this.tagMap = null;
        } else {
            this.singletonTuple = null;
            this.singletonCount = Double.NaN;
            TObjectDoubleIterator<TagTuple> it = tagMap.iterator();
            it.advance();
            this.depth = it.key().tags.length;
            this.tagMap = tagMap;
        }
    }

    /**
     * If this is a singleton non-key counter, converts it to a singleton key counter; returns this for all other
     * cases.
     */
    public TagCount ensureIsKey() {
        if (singletonTuple != null && !singletonTuple.isKey()) {
            assert singletonCount == 1.0;
            return new TagCount(singletonTuple.key());
        } else
            return this;
    }

    public TagTuple asKeyPrefixOrNull(int depth) {
        TagTuple fullKey = asKeyOrNull();
        if (fullKey != null)
            return fullKey.keyPrefix(depth);
        Set<TagTuple> keySet = tagMap.keySet().stream()
                .map(t -> t.keyPrefix(depth))
                .collect(Collectors.toSet());
        if (keySet.size() != 1)
            return null;
        return keySet.iterator().next();
    }

    public TagTuple asKeyOrNull() {
        return singletonTuple != null
                ? singletonTuple.key()
                : null;
    }

    public TagTuple asKeyPrefixOrError(int depth) {
        TagTuple result = asKeyPrefixOrNull(depth);
        if (result == null)
            throw new IllegalStateException("Aggregated tag information, single tag tuple expected.");
        return result;
    }

    public TagTuple asKeyOrError() {
        TagTuple result = asKeyOrNull();
        if (result == null)
            throw new IllegalStateException("Aggregated tag information, single tag tuple expected.");
        return result;
    }

    public TagCount keySuffixes(int depth) {
        TagTuple fullKey = asKeyOrNull();
        if (fullKey != null)
            return new TagCount(fullKey.keySuffix(depth), singletonCount);
        TObjectDoubleHashMap<TagTuple> suffixMap = new TObjectDoubleHashMap<>(tagMap.size());
        TObjectDoubleIterator<TagTuple> it = tagMap.iterator();
        while (it.hasNext()) {
            it.advance();
            suffixMap.put(it.key().keySuffix(depth), it.value());
        }
        return new TagCount(suffixMap);
    }

    public TagValue singleOrNull(int idx) {
        if (singletonTuple != null)
            return singletonTuple.get(idx);

        TagValue value = null;
        TObjectDoubleIterator<TagTuple> it = tagMap.iterator();
        while (it.hasNext()) {
            it.advance();
            TagValue tv = it.key().get(idx);
            if (value == null) {
                value = tv;
            } else if (!value.equals(tv))
                return null;
        }
        return value;
    }

    public Set<TagTuple> tuples() {
        if (singletonTuple != null)
            return Collections.singleton(singletonTuple);
        else
            return tagMap.keySet();
    }

    public boolean isNonKeySingleton() {
        return isSingleton() && !getSingletonTuple().isKey();
    }

    public boolean isSingleton() {
        return singletonTuple != null;
    }

    public TagTuple getSingletonTuple() {
        return singletonTuple;
    }

    public double getSingletonCount() {
        return singletonCount;
    }

    public boolean isNoTag() {
        return TagTuple.NO_TAGS.equals(getSingletonTuple());
    }

    public int size() {
        return isSingleton()
                ? 1
                : tagMap.size();
    }

    public int depth() {
        return depth;
    }

    /**
     * Reduces tag counts to the specified level, new tag counts will be computed as the number of uniques suffixes.
     */
    public TagCount reduceToLevel(int level) {
        if (level == depth)
            return this;

        TagCountAggregator agg = new TagCountAggregator();
        TObjectDoubleIterator<TagTuple> it = iterator();
        while (it.hasNext()) {
            it.advance();
            agg.add(it.key().prefix(level), 1d);
        }
        return agg.createAndDestroy();
    }

    /** The same ase reduceToLevel(level).size() */
    public int getTagDiversity(int level) {
        if (level == depth)
            return size();

        Set<TagTuple> uniqueTags = new HashSet<>();
        TObjectDoubleIterator<TagTuple> it = iterator();
        while (it.hasNext()) {
            it.advance();
            uniqueTags.add(it.key().prefix(level));
        }

        return uniqueTags.size();
    }

    /** Returns true if all keys in the counter has the provided prefix. */
    public boolean allTagsHasPrefix(TagTuple prefix) {
        TObjectDoubleIterator<TagTuple> it = iterator();
        while (it.hasNext()) {
            it.advance();
            if (!it.key().hasPrefix(prefix))
                return false;
        }
        return true;
    }

    public double get(TagTuple tt) {
        if (isSingleton()) {
            if (tt.equals(singletonTuple))
                return singletonCount;
            return 0.0;
        } else
            return tagMap.get(tt);
    }

    public boolean containsAll(Set<TagTuple> other) {
        if (singletonTuple != null)
            return other.size() == 0 || (other.size() == 1 && singletonTuple.equals(other.iterator().next()));
        else
            return tagMap.keySet().containsAll(other);
    }

    public boolean containsAll(TagCount other) {
        return tuples().containsAll(other.tuples());
    }

    public TObjectDoubleIterator<TagTuple> iterator() {
        if (singletonTuple != null)
            // Singleton iterator
            return new TObjectDoubleIterator<TagTuple>() {
                int position = -1;

                @Override
                public TagTuple key() {
                    return position == 0
                            ? singletonTuple
                            : null;
                }

                @Override
                public double value() {
                    return position == 0
                            ? singletonCount
                            : Double.NaN;
                }

                @Override
                public double setValue(double val) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void advance() {
                    ++position;
                }

                @Override
                public boolean hasNext() {
                    return position == -1;
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException();
                }
            };

        TObjectDoubleIterator<TagTuple> it = tagMap.iterator();
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

    @Override
    public String toString() {
        if (isSingleton())
            return singletonTuple + ": " + singletonCount;
        else
            return tagMap.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TagCount that = (TagCount) o;
        return Double.compare(that.singletonCount, singletonCount) == 0 && Objects.equals(singletonTuple, that.singletonTuple) && Objects.equals(tagMap, that.tagMap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(singletonTuple, singletonCount, tagMap);
    }

    public TagCount filter(Predicate<TagTuple> predicate) {
        TObjectDoubleIterator<TagTuple> it = iterator();
        TagCountAggregator tb = new TagCountAggregator();
        while (it.hasNext()) {
            it.advance();

            TagTuple t = it.key();
            double count = it.value();
            if (!predicate.test(t))
                continue;
            tb.add(t, count);
        }
        return tb.createAndDestroy();
    }

    public TagCount[] splitBy(int level) {
        if (level == 0)
            return new TagCount[]{this};
        if (level > depth)
            throw new IllegalArgumentException();
        Map<TagTuple, TagCountAggregator> map = new HashMap<>();
        TObjectDoubleIterator<TagTuple> it = iterator();
        while (it.hasNext()) {
            it.advance();

            TagTuple t = it.key();
            double count = it.value();

            TagCountAggregator tb = map.computeIfAbsent(t.prefix(level), __ -> new TagCountAggregator());
            tb.add(t, count);
        }
        return map.values().stream().map(TagCountAggregator::createAndDestroy).toArray(TagCount[]::new);
    }

    public double sum() {
        TObjectDoubleIterator<TagTuple> it = iterator();
        double sum = 0;
        while (it.hasNext()) {
            it.advance();
            sum += it.value();
        }
        return sum;
    }
}
