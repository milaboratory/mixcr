package com.milaboratory.mixcr.postanalysis.preproc;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.mixcr.postanalysis.SetPreprocessor;

import java.util.Map;
import java.util.function.Function;

/**
 *
 */
public class SampleFilterPreprocessor<T> implements SetPreprocessor<T> {
    @JsonProperty("sampleFilters")
    public final Map<String, FilterPreprocessor<T>> sampleFilters;

    @JsonCreator
    public SampleFilterPreprocessor(@JsonProperty("sampleFilters") Map<String, FilterPreprocessor<T>> sampleFilters) {
        this.sampleFilters = sampleFilters;
    }

    @JsonIgnore
    private Map<Iterable<T>, String> sampleNames;

    public void setSampleNames(Map<Iterable<T>, String> sampleNames) {
        this.sampleNames = sampleNames;
    }

    @Override
    public Function<Iterable<T>, Iterable<T>> setup(Iterable<T>[] sets) {
        return set -> sampleFilters.get(sampleNames.get(set)).setup(null).apply(set);
    }
}
