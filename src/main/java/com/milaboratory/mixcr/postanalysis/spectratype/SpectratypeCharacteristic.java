package com.milaboratory.mixcr.postanalysis.spectratype;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.postanalysis.*;

import java.util.Objects;

/**
 *
 */
public class SpectratypeCharacteristic extends Characteristic<SpectratypeKey<String>, Clone> {
    @JsonProperty("nTopClonotypes")
    public final int nTopClonotypes;
    @JsonProperty("keyFunction")
    public final SpectratypeKeyFunction<String, Clone> keyFunction;

    @JsonCreator
    public SpectratypeCharacteristic(@JsonProperty("name") String name,
                                     @JsonProperty("preprocessor") SetPreprocessor<Clone> preprocessor,
                                     @JsonProperty("nTopClonotypes") int nTopClonotypes,
                                     @JsonProperty("keyFunction") SpectratypeKeyFunction<String, Clone> keyFunction) {
        super(name, preprocessor, new WeightFunctions.Count());
        this.nTopClonotypes = nTopClonotypes;
        this.keyFunction = keyFunction;
    }

    @Override
    protected Aggregator<SpectratypeKey<String>, Clone> createAggregator(Dataset<Clone> dataset) {
        return new SpectratypeAggregator<>(nTopClonotypes, keyFunction, weight);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        SpectratypeCharacteristic that = (SpectratypeCharacteristic) o;
        return nTopClonotypes == that.nTopClonotypes &&
                Objects.equals(keyFunction, that.keyFunction);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), nTopClonotypes, keyFunction);
    }
}
