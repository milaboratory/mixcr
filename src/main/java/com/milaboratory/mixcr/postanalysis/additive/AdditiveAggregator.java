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
package com.milaboratory.mixcr.postanalysis.additive;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.mixcr.postanalysis.Aggregator;
import com.milaboratory.mixcr.postanalysis.MetricValue;
import com.milaboratory.mixcr.postanalysis.WeightFunction;
import gnu.trove.iterator.TObjectDoubleIterator;
import gnu.trove.map.hash.TObjectDoubleHashMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 *
 */
@JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE
)
public class AdditiveAggregator<K, T> implements Aggregator<K, T> {
    @JsonProperty("key")
    public final KeyFunction<K, T> keyFunction;
    @JsonProperty("metric")
    public final AdditiveMetric<T> metric;
    @JsonProperty("weight")
    public final WeightFunction<T> weight;
    @JsonProperty("norm")
    public final Normalization normalization;

    /**
     * Single key value
     */
    private K singleKey;
    /**
     * Aggregated values for single key
     */
    double metricSum, weightSum;
    /**
     * Number of elements
     */
    int nElements;
    /**
     * Aggregated values for other keys ( != null )
     */
    final TObjectDoubleHashMap<K>
            metricSums = new TObjectDoubleHashMap<>(),
            weightSums = new TObjectDoubleHashMap<>();

    @JsonCreator
    public AdditiveAggregator(@JsonProperty("key") KeyFunction<K, T> keyFunction,
                              @JsonProperty("metric") AdditiveMetric<T> metric,
                              @JsonProperty("weight") WeightFunction<T> weight,
                              @JsonProperty("norm") Normalization normalization) {
        this.keyFunction = keyFunction;
        this.metric = metric;
        this.weight = weight;
        this.normalization = normalization;
    }

    @Override
    public void consume(T obj) {
        K key = keyFunction.getKey(obj);
        if (key == null)
            return;

        double metricValue = metric.compute(obj);
        if (Double.isNaN(metricValue)) {
            metricValue = 0.0;
        }

        nElements += 1;
        double weightValue = weight.weight(obj);
        weightSum += weightValue;

        if (metricSums.size() != 0) {
            double weightedValue = metricValue * weightValue;
            metricSums.adjustOrPutValue(key, weightedValue, weightedValue);
            if (normalization == Normalization.DivideByTotalForKey)
                weightSums.adjustOrPutValue(key, weightValue, weightValue);

            return;
        }

        if (singleKey == null)
            singleKey = key;

        if (Objects.equals(key, singleKey))
            metricSum += metricValue * weightValue;
        else {
            metricSums.put(singleKey, metricSum);
            weightSum -= weightValue;
            metricSum = 0;
            if (normalization == Normalization.DivideByTotalForKey)
                weightSums.put(singleKey, weightSum);
            singleKey = null;
            consume(obj);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public MetricValue<K>[] result() {
        List<MetricValue<K>> result = new ArrayList<>();
        TObjectDoubleIterator<K> it = metricSums.iterator();
        while (it.hasNext()) {
            it.advance();
            result.add(new MetricValue<>(it.key(), it.value()));
        }

        if (singleKey != null) {
            assert result.isEmpty();
            result.add(new MetricValue<>(singleKey, metricSum));
        }

        if (result.isEmpty())
            return new MetricValue[0];

        switch (normalization) {
            case DivideByTotal:
                result = result.stream()
                        .map(tuple -> tuple.divide(weightSum))
                        .collect(Collectors.toList());
                break;
            case DivideByTotalForKey:
                result = result.stream()
                        .map(tuple ->
                                tuple.key == null
                                        ? tuple.divide(weightSum)
                                        : tuple.divide(weightSums.get(tuple.key))
                        )
                        .collect(Collectors.toList());
                break;
        }

        return result.toArray(new MetricValue[0]);
    }
}
