package com.milaboratory.mixcr.postanalysis.diversity;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.mixcr.postanalysis.*;

/**
 *
 */
@JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE
)
public class DiversityCharacteristic<T> extends Characteristic<DiversityMeasure, T> {
    @JsonCreator
    public DiversityCharacteristic(@JsonProperty("name") String name,
                                   @JsonProperty("weight") WeightFunction<T> weight,
                                   @JsonProperty("preproc") SetPreprocessor<T> preprocessor) {
        super(name, preprocessor, weight);
    }

    @Override
    protected Aggregator<DiversityMeasure, T> createAggregator(Dataset<T> dataset) {
        return new DiversityAggregator<>(c -> Math.round(weight.weight(c)));
    }
}
