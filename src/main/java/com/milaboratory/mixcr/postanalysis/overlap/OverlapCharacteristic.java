package com.milaboratory.mixcr.postanalysis.overlap;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.mixcr.postanalysis.Aggregator;
import com.milaboratory.mixcr.postanalysis.Characteristic;
import com.milaboratory.mixcr.postanalysis.SetPreprocessor;
import com.milaboratory.mixcr.postanalysis.WeightFunction;

import java.util.Objects;

/**
 *
 */
public class OverlapCharacteristic<T> extends Characteristic<OverlapKey<OverlapType>, OverlapGroup<T>> {
    @JsonProperty("i1")
    public final int i1;
    @JsonProperty("i2")
    public final int i2;
    @JsonProperty("weight")
    public final WeightFunction<T> weight;

    @JsonCreator
    public OverlapCharacteristic(@JsonProperty("name") String name,
                                 @JsonProperty("weight") WeightFunction<T> weight,
                                 @JsonProperty("preprocessor") SetPreprocessor<OverlapGroup<T>> preprocessor,
                                 @JsonProperty("i1") int i1,
                                 @JsonProperty("i2") int i2) {
        super(name, preprocessor, __ -> 1L);
        this.i1 = i1;
        this.i2 = i2;
        this.weight = weight;
    }

    @Override
    protected Aggregator<OverlapKey<OverlapType>, OverlapGroup<T>> createAggregator() {
        return new OverlapAggregator<>(weight, i1, i2);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        OverlapCharacteristic<?> that = (OverlapCharacteristic<?>) o;
        return i1 == that.i1 &&
                i2 == that.i2 &&
                Objects.equals(weight, that.weight);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), i1, i2, weight);
    }
}
