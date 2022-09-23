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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.mixcr.postanalysis.SetPreprocessor;
import com.milaboratory.mixcr.postanalysis.SetPreprocessorFactory;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.ToLongFunction;

/**
 *
 */
public class DownsamplingPreprocessorMVHGFactory<T> implements SetPreprocessorFactory<T> {
    @JsonProperty("downsampleValueChooser")
    public final DownsampleValueChooser downsampleValueChooser;
    @JsonProperty("dropOutliers")
    public final boolean dropOutliers;
    public final ToLongFunction<T> getCount;
    public final BiFunction<T, Long, T> setCount;

    public DownsamplingPreprocessorMVHGFactory(DownsampleValueChooser downsampleValueChooser,
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
        return "Downsample by reads " + downsampleValueChooser.id();
    }

    @Override
    public SetPreprocessor<T> newInstance() {
        return new DownsamplingPreprocessorMVHG<T>(
                downsampleValueChooser, getCount,
                setCount,
                dropOutliers, id());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DownsamplingPreprocessorMVHGFactory<?> that = (DownsamplingPreprocessorMVHGFactory<?>) o;
        return Objects.equals(downsampleValueChooser, that.downsampleValueChooser) && Objects.equals(dropOutliers, that.dropOutliers) && Objects.equals(getCount, that.getCount) && Objects.equals(setCount, that.setCount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(downsampleValueChooser, dropOutliers, getCount, setCount);
    }
}
