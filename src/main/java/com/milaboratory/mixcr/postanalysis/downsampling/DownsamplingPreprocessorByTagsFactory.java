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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.postanalysis.SetPreprocessor;
import com.milaboratory.mixcr.postanalysis.SetPreprocessorFactory;

import java.util.Objects;

public class DownsamplingPreprocessorByTagsFactory implements SetPreprocessorFactory<Clone> {
    @JsonProperty("tagLevel")
    public final int tagLevel;
    @JsonProperty("tagName")
    public final String tagName;
    @JsonProperty("downsampleValueChooser")
    public final DownsampleValueChooser downsampleValueChooser;
    @JsonProperty("dropOutliers")
    public final boolean dropOutliers;

    @JsonCreator
    public DownsamplingPreprocessorByTagsFactory(
            @JsonProperty("tagLevel") int tagLevel,
            @JsonProperty("tagName") String tagName,
            @JsonProperty("downsampleValueChooser") DownsampleValueChooser downsampleValueChooser,
            @JsonProperty("dropOutliers") boolean dropOutliers) {
        this.tagLevel = tagLevel;
        this.tagName = tagName;
        this.downsampleValueChooser = downsampleValueChooser;
        this.dropOutliers = dropOutliers;
    }

    @Override
    public SetPreprocessor<Clone> newInstance() {
        return new DownsamplingPreprocessorByTags(
                tagLevel,
                downsampleValueChooser,
                dropOutliers,
                id()
        );
    }

    @Override
    public String id() {
        return "Downsample by " + tagName + " " + downsampleValueChooser.id();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DownsamplingPreprocessorByTagsFactory that = (DownsamplingPreprocessorByTagsFactory) o;
        return tagLevel == that.tagLevel && dropOutliers == that.dropOutliers && Objects.equals(tagName, that.tagName) && Objects.equals(downsampleValueChooser, that.downsampleValueChooser);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tagLevel, tagName, downsampleValueChooser, dropOutliers);
    }
}
