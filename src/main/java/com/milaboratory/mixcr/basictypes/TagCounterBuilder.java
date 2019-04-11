package com.milaboratory.mixcr.basictypes;

import gnu.trove.impl.Constants;
import gnu.trove.iterator.TObjectDoubleIterator;
import gnu.trove.map.hash.TObjectDoubleHashMap;

/**
 *
 */
public class TagCounterBuilder {
    private final TObjectDoubleHashMap<TagTuple> agg;
    private boolean destroyed = false;

    public TagCounterBuilder() {
        this.agg = new TObjectDoubleHashMap<>(Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, 0.0);
    }

    public TagCounterBuilder add(TagCounter tc) {
        return add(tc.tags);
    }

    public TagCounterBuilder add(TagCounterBuilder tc) {
        return add(tc.agg);
    }

    public TagCounterBuilder add(TObjectDoubleHashMap<TagTuple> tc) {
        if (destroyed)
            throw new IllegalStateException("destroyed");

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

    public TagCounter createAndDestroy() {
        if (destroyed)
            throw new IllegalStateException("destroyed");

        TagCounter r = new TagCounter(new TObjectDoubleHashMap<>(agg));
        destroyed = true;
        return r;
    }

    public static TagCounter merge(TagCounter a, TagCounter b) {
        return new TagCounterBuilder().add(a).add(b).createAndDestroy();
    }
}
