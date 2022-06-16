/*
 * Copyright (c) 2014-2022, MiLaboratories Inc. All Rights Reserved
 *
 * Before downloading or accessing the software, please read carefully the
 * License Agreement available at:
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 *
 * By downloading or accessing the software, you accept and agree to be bound
 * by the terms of the License Agreement. If you do not want to agree to the terms
 * of the Licensing Agreement, you must not download or access the software.
 */
package com.milaboratory.mixcr.postanalysis.downsampling;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.postanalysis.WeightFunction;

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
    @JsonProperty("weightFunction")
    public final WeightFunction<Clone> weightFunction;

    @JsonCreator
    public ClonesDownsamplingPreprocessorFactory(@JsonProperty("downsampleValueChooser") DownsampleValueChooser downsampleValueChooser,
                                                 @JsonProperty("dropOutliers") boolean dropOutliers,
                                                 @JsonProperty("weightFunction") WeightFunction<Clone> weightFunction) {
        super(downsampleValueChooser, c -> Math.round(weightFunction.weight(c)), Clone::setCount, dropOutliers);
        this.weightFunction = weightFunction;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClonesDownsamplingPreprocessorFactory that = (ClonesDownsamplingPreprocessorFactory) o;
        return Objects.equals(downsampleValueChooser, that.downsampleValueChooser)
                && Objects.equals(weightFunction, that.weightFunction)
                && Objects.equals(dropOutliers, that.dropOutliers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(downsampleValueChooser, weightFunction, dropOutliers);
    }
}
