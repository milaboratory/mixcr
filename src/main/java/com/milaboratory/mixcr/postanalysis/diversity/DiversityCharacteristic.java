package com.milaboratory.mixcr.postanalysis.diversity;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.mixcr.postanalysis.*;

import java.util.Arrays;

/**
 *
 */
@JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE
)
public class DiversityCharacteristic<T> extends Characteristic<DiversityMeasure, T> {
    @JsonProperty("measures")
    public final DiversityMeasure[] measures;

    @JsonCreator
    public DiversityCharacteristic(@JsonProperty("name") String name,
                                   @JsonProperty("weight") WeightFunction<T> weight,
                                   @JsonProperty("preproc") SetPreprocessorFactory<T> preprocessor,
                                   @JsonProperty("measures") DiversityMeasure[] measures) {
        super(name, preprocessor, weight);
        this.measures = measures;
    }

    public DiversityCharacteristic(@JsonProperty("name") String name,
                                   @JsonProperty("weight") WeightFunction<T> weight,
                                   @JsonProperty("preproc") SetPreprocessorFactory<T> preprocessor) {
        this(name, weight, preprocessor, DiversityMeasure.basic());
    }

    @Override
    protected Aggregator<DiversityMeasure, T> createAggregator(Dataset<T> dataset) {
        return new DiversityAggregator<>(c -> Math.round(weight.weight(c)), measures);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        DiversityCharacteristic<?> that = (DiversityCharacteristic<?>) o;
        return Arrays.equals(measures, that.measures);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + Arrays.hashCode(measures);
        return result;
    }
}
