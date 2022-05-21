package com.milaboratory.mixcr.postanalysis.downsampling;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.mixcr.postanalysis.SetPreprocessor;
import com.milaboratory.mixcr.postanalysis.SetPreprocessorFactory;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.ToLongFunction;

/**
 *
 */
public class DownsamplingPreprocessorFactory<T> implements SetPreprocessorFactory<T> {
    @JsonProperty("downsampleValueChooser")
    public final DownsampleValueChooser downsampleValueChooser;
    @JsonProperty("seed")
    public final long seed;
    public final ToLongFunction<T> getCount;
    public final BiFunction<T, Long, T> setCount;

    public DownsamplingPreprocessorFactory(DownsampleValueChooser downsampleValueChooser,
                                           long seed,
                                           ToLongFunction<T> getCount,
                                           BiFunction<T, Long, T> setCount) {
        this.downsampleValueChooser = downsampleValueChooser;
        this.seed = seed;
        this.getCount = getCount;
        this.setCount = setCount;
    }

    @Override
    public String id() {
        return "Downsample " + downsampleValueChooser.id();
    }

    @Override
    public SetPreprocessor<T> newInstance() {
        return new DownsamplingPreprocessor<>(
                getCount,
                setCount, downsampleValueChooser, seed, id());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DownsamplingPreprocessorFactory<?> that = (DownsamplingPreprocessorFactory<?>) o;
        return seed == that.seed && Objects.equals(downsampleValueChooser, that.downsampleValueChooser) && Objects.equals(getCount, that.getCount) && Objects.equals(setCount, that.setCount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(downsampleValueChooser, seed, getCount, setCount);
    }
}
