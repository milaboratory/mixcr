package com.milaboratory.mixcr.postanalysis.additive;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.mixcr.postanalysis.*;

import java.util.Arrays;
import java.util.Objects;

/**
 *
 */
@JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE
)
public class AdditiveCharacteristic<K, T> extends Characteristic<K, T> {
    @JsonProperty("key")
    public final KeyFunction<K, T> keyFunction;
    @JsonProperty("metric")
    public final AdditiveMetric<T> metric;
    @JsonProperty("agg")
    public final AggregationType aggType;
    @JsonProperty("normalizeByKey")
    public final boolean normalizeByKey;

    @JsonCreator
    public AdditiveCharacteristic(@JsonProperty("name") String name,
                                  @JsonProperty("preproc") SetPreprocessor<T> preprocessor,
                                  @JsonProperty("weight") WeightFunction<T> weight,
                                  @JsonProperty("key") KeyFunction<K, T> keyFunction,
                                  @JsonProperty("metric") AdditiveMetric<T> metric,
                                  @JsonProperty("agg") AggregationType aggType,
                                  @JsonProperty("normalizeByKey") boolean normalizeByKey) {
        super(name, preprocessor, weight);
        this.keyFunction = keyFunction;
        this.metric = metric;
        this.aggType = aggType;
        this.normalizeByKey = normalizeByKey;
    }

    public AdditiveCharacteristic<K, T> setName(String name) {
        return new AdditiveCharacteristic<>(name, preprocessor, weight, keyFunction, metric, aggType, normalizeByKey);
    }

    private Normalization meanNorm() {
        return normalizeByKey ? Normalization.DivideByTotalForKey : Normalization.DivideByTotal;
    }

    @Override
    protected Aggregator<K, T> createAggregator() {
        switch (aggType) {
            case Sum:
                return new AdditiveAggregator<>(keyFunction, metric, weight, Normalization.None);
            case Mean:
                return new AdditiveAggregator<>(keyFunction, metric, weight, meanNorm());
            case Std:
                AdditiveAggregator<K, T> meanAgg = new AdditiveAggregator<>(keyFunction, metric, weight, meanNorm());
                AdditiveAggregator<K, T> meanSquareAgg = new AdditiveAggregator<>(keyFunction, AdditiveMetric.square(metric), weight, meanNorm());
                return new Aggregator<K, T>() {
                    @Override
                    public void consume(T obj) {
                        meanAgg.consume(obj);
                        meanSquareAgg.consume(obj);
                    }

                    @Override
                    public MetricValue<K>[] result() {
                        MetricValue<K>[] mean = meanAgg.result();
                        MetricValue<K>[] meanSquare = meanSquareAgg.result();
                        MetricValue<K>[] result = new MetricValue[mean.length];

                        Arrays.sort(mean);
                        Arrays.sort(meanSquare);

                        for (int i = 0; i < mean.length; ++i) {
                            MetricValue<K> m = mean[i];
                            if (meanAgg.nElements == 1) {
                                result[i] = new MetricValue<>(m.key, 0.0);
                                continue;
                            }
                            MetricValue<K> m2 = meanSquare[i];
                            if (!m.key.equals(m2.key))
                                throw new IllegalArgumentException();

                            result[i] = new MetricValue<>(m.key, Math.sqrt(
                                    meanAgg.weightSum / (meanAgg.weightSum - 1)
                                            * (m2.value - m.value * m.value)));
                        }

                        return result;
                    }
                };
            default:
                throw new RuntimeException();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        AdditiveCharacteristic<?, ?> that = (AdditiveCharacteristic<?, ?>) o;
        return normalizeByKey == that.normalizeByKey &&
                Objects.equals(keyFunction, that.keyFunction) &&
                Objects.equals(metric, that.metric) &&
                aggType == that.aggType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), keyFunction, metric, aggType, normalizeByKey);
    }
}
