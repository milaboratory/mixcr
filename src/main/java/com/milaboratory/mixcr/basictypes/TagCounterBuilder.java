package com.milaboratory.mixcr.basictypes;

import gnu.trove.impl.Constants;
import gnu.trove.iterator.TObjectDoubleIterator;
import gnu.trove.map.hash.TObjectDoubleHashMap;

/**
 *
 */
public class TagCounterBuilder {
    private TObjectDoubleHashMap<TagTuple> agg;
    private boolean destroyed = false;

    public TagCounterBuilder() {}

    public TagCounterBuilder add(TagTuple tc, double count) {
        return add(new TagCounter(tc, count));
    }

    public TagCounterBuilder add(TagCounter tc) {
        return add(tc.tags);
    }

    public TagCounterBuilder add(TagCounterBuilder tc) {
        if (tc.agg == null)
            return this;
        return add(tc.agg);
    }

    private void ensureInitialized() {
        if (agg == null)
            agg = new TObjectDoubleHashMap<>(Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, 0.0);
    }

    public TagCounterBuilder add(TObjectDoubleHashMap<TagTuple> tc) {
        if (destroyed)
            throw new IllegalStateException("destroyed");

        if (tc.size() == 0)
            return this;

        ensureInitialized();

        if (agg.isEmpty()) {
            agg.putAll(tc);
            return this;
        }

        TObjectDoubleIterator<TagTuple> it = tc.iterator();
        while (it.hasNext()) {
            it.advance();
            TagTuple k = it.key();
            double v = agg.get(k); // 0.0 for no entry value, see constructor
            agg.put(k, v + it.value());
        }

        return this;
    }

    public double intersectionFractionOf(TagCounterBuilder minor) {
        if (agg == null || minor.agg == null)
            throw new IllegalStateException("tag aware clusterization for non-tagged clones.");

        TObjectDoubleIterator<TagTuple> it = minor.agg.iterator();
        double minorTotal = 0, totalIntersection = 0;
        while (it.hasNext()) {
            it.advance();
            TagTuple key = it.key();
            double v = it.value();
            minorTotal += v;
            if (agg.containsKey(key))
                totalIntersection += v;
        }
        return totalIntersection / minorTotal;
    }

    public TagCounter createAndDestroy() {
        if (destroyed)
            throw new IllegalStateException("destroyed");

        if (agg == null)
            return TagCounter.EMPTY;

        TagCounter r = new TagCounter(new TObjectDoubleHashMap<>(agg));
        destroyed = true;
        return r;
    }

    public static TagCounter merge(TagCounter a, TagCounter b) {
        return new TagCounterBuilder().add(a).add(b).createAndDestroy();
    }
}
