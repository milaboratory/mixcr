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
    @JsonProperty("dropOutliers")
    public final boolean dropOutliers;
    public final ToLongFunction<T> getCount;
    public final BiFunction<T, Long, T> setCount;

    public DownsamplingPreprocessorFactory(DownsampleValueChooser downsampleValueChooser,
                                           ToLongFunction<T> getCount,
                                           BiFunction<T, Long, T> setCount,
                                           boolean dropOutliers) {
        this.downsampleValueChooser = downsampleValueChooser;
        this.dropOutliers = dropOutliers;
        this.getCount = getCount;
        this.setCount = setCount;
    }

    @Override
    public String id() {
        return "Downsample " + downsampleValueChooser.id();
    }

    @Override
    public SetPreprocessor<T> newInstance() {
        return new DownsamplingPreprocessor<T>(
                downsampleValueChooser, getCount,
                setCount,
                dropOutliers, id());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DownsamplingPreprocessorFactory<?> that = (DownsamplingPreprocessorFactory<?>) o;
        return Objects.equals(downsampleValueChooser, that.downsampleValueChooser) && Objects.equals(dropOutliers, that.dropOutliers) && Objects.equals(getCount, that.getCount) && Objects.equals(setCount, that.setCount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(downsampleValueChooser, dropOutliers, getCount, setCount);
    }
}
