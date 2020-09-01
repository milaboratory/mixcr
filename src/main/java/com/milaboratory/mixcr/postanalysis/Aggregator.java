package com.milaboratory.mixcr.postanalysis;

import java.util.ArrayList;
import java.util.Arrays;

public interface Aggregator<K, T> {
    /** apply for each clone */
    void consume(T obj);

    /** get the result */
    MetricValue<K>[] result();

    static <K, T> Aggregator<K, T> merge(Aggregator<K, T> agg1, Aggregator<K, T> agg2) {
        return new Aggregator<K, T>() {
            @Override
            public void consume(T obj) {
                agg1.consume(obj);
                agg2.consume(obj);
            }

            @Override
            public MetricValue<K>[] result() {
                ArrayList<MetricValue<K>> r = new ArrayList<>();
                r.addAll(Arrays.asList(agg1.result()));
                r.addAll(Arrays.asList(agg2.result()));
                //noinspection unchecked
                return r.toArray(new MetricValue[0]);
            }
        };
    }
}
