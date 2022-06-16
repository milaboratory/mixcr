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
package com.milaboratory.mixcr.postanalysis;

import java.util.ArrayList;
import java.util.Arrays;

public interface Aggregator<K, T> {
    /** apply for each dataset element */
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
