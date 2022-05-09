package com.milaboratory.mixcr.postanalysis.downsampling;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.postanalysis.SetPreprocessor;
import com.milaboratory.mixcr.postanalysis.SetPreprocessorFactory;

import java.util.Objects;

/**
 *
 */
@JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE
)
public class ClonesDownsamplingPreprocessorFactory implements SetPreprocessorFactory<Clone> {
    @JsonProperty("downsampleValueChooser")
    public final DownsampleValueChooser downsampleValueChooser;
    @JsonProperty("seed")
    public final long seed;

    @JsonCreator
    public ClonesDownsamplingPreprocessorFactory(@JsonProperty("downsampleValueChooser") DownsampleValueChooser downsampleValueChooser,
                                                 @JsonProperty("seed") long seed) {
        this.downsampleValueChooser = downsampleValueChooser;
        this.seed = seed;
    }

    @Override
    public String id() {
        return "Downsample " + downsampleValueChooser.id();
    }

    @Override
    public SetPreprocessor<Clone> newInstance() {
        return new DownsamplingPreprocessor<>(
                c -> Math.round(c.getCount()),
                Clone::setCount, downsampleValueChooser, seed, id());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClonesDownsamplingPreprocessorFactory that = (ClonesDownsamplingPreprocessorFactory) o;
        return Objects.equals(downsampleValueChooser, that.downsampleValueChooser)
                && Objects.equals(seed, that.seed);
    }

    @Override
    public int hashCode() {
        return Objects.hash(downsampleValueChooser, seed);
    }
}
