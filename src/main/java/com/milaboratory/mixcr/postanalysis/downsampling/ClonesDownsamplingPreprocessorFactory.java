package com.milaboratory.mixcr.postanalysis.downsampling;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.mixcr.basictypes.Clone;

import java.util.Objects;

/**
 *
 */
@JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE
)
public class ClonesDownsamplingPreprocessorFactory extends DownsamplingPreprocessorFactory<Clone> {
    @JsonCreator
    public ClonesDownsamplingPreprocessorFactory(@JsonProperty("downsampleValueChooser") DownsampleValueChooser downsampleValueChooser,
                                                 @JsonProperty("dropOutliers") boolean dropOutliers) {
        super(downsampleValueChooser, c -> Math.round(c.getCount()), Clone::setCount, dropOutliers);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClonesDownsamplingPreprocessorFactory that = (ClonesDownsamplingPreprocessorFactory) o;
        return Objects.equals(downsampleValueChooser, that.downsampleValueChooser);
    }

    @Override
    public int hashCode() {
        return Objects.hash(downsampleValueChooser);
    }
}
