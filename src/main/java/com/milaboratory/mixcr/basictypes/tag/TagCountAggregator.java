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

import gnu.trove.iterator.TObjectDoubleIterator;
import gnu.trove.map.hash.TObjectDoubleHashMap;

/**
 *
 */
public final class TagCountAggregator {
    private TagTuple singletonTuple = null;
    private double singletonCount = Double.NaN;
    private TObjectDoubleHashMap<TagTuple> tagMap = null;
    private boolean destroyed = false;
    private int depth = -1;

    public TagCountAggregator() {
    }

    public TagCountAggregator add(TagTuple tc, double count) {
        if (destroyed)
            throw new IllegalStateException("destroyed");

        if (!tc.isKey() && count != 1.0)
            throw new IllegalArgumentException("count != 1.0 for non-key tuple.");

        if (depth == -1)
            depth = tc.size();
        else if (depth != tc.size())
            throw new IllegalArgumentException("Inconsistent tag depth; depth=" + depth + " tc.size()=" + tc.size());

        if (tagMap == null) {
            if (singletonTuple == null) {
                singletonTuple = tc;
                singletonCount = count;
                return this;
            } else if (singletonTuple.equals(tc)) {
                if (!singletonTuple.isKey())
                    throw new IllegalStateException("Non-trivial accumulation of non-key tag count.");
                singletonCount += count;
                return this;
            } else {
                tagMap = new TObjectDoubleHashMap<>();
                if (!singletonTuple.isKey() || !tc.isKey())
                    throw new IllegalStateException("Non-trivial accumulation of non-key tag count.");
                tagMap.put(singletonTuple, singletonCount);
                tagMap.put(tc, count);
                singletonTuple = null;
                singletonCount = Double.NaN;
                return this;
            }
        } else {
            tagMap.adjustOrPutValue(tc, count, count);
            return this;
        }
    }

    private void add(TObjectDoubleIterator<TagTuple> it) {
        while (it.hasNext()) {
            it.advance();
            add(it.key(), it.value());
        }
    }

    public TagCountAggregator add(TagCount tc) {
        add(tc.iterator());
        return this;
    }

    public TagCountAggregator add(TagCountAggregator tc) {
        if (tc.tagMap == null) {
            if (tc.singletonTuple == null)
                return this;
            add(tc.singletonTuple, tc.singletonCount);
        } else
            add(tc.tagMap.iterator());
        return this;
    }

    public TagCountAggregator add(TObjectDoubleHashMap<TagTuple> tc) {
        if (tc.size() == 0)
            return this;

        add(tc.iterator());

        return this;
    }

    public int size() {
        if (singletonTuple == null) {
            if (tagMap == null)
                return 0;
            else
                return tagMap.size();
        } else
            return 1;
    }

    public boolean isEmpty() {
        return singletonTuple == null && tagMap == null;
    }

    public double intersectionFractionOf(TagCountAggregator minor) {
        if (tagMap == null || minor.tagMap == null)
            throw new IllegalStateException("tag aware clusterization for non-tagged clones.");

        TagCount thisTagCount = createTagCounter();
        TObjectDoubleIterator<TagTuple> it = minor.createTagCounter().iterator();
        double minorTotal = 0, totalIntersection = 0;
        while (it.hasNext()) {
            it.advance();
            TagTuple key = it.key();
            double v = it.value();
            minorTotal += v;
            if (thisTagCount.get(key) > 0)
                totalIntersection += v;
        }
        return totalIntersection / minorTotal;
    }

    private TagCount createTagCounter() {
        if (singletonTuple == null && tagMap == null)
            throw new IllegalStateException("Building empty tag counter.");

        if (singletonTuple != null)
            return new TagCount(singletonTuple, singletonCount);

        return new TagCount(tagMap);
    }

    public TagCount createAndDestroy() {
        if (destroyed)
            throw new IllegalStateException("destroyed");
        destroyed = true;
        return createTagCounter();
    }

    public static TagCount merge(TagCount a, TagCount b) {
        return new TagCountAggregator().add(a).add(b).createAndDestroy();
    }
}
