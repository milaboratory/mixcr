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
package com.milaboratory.mixcr.assembler;

import com.fasterxml.jackson.annotation.*;
import com.milaboratory.core.alignment.AffineGapAlignmentScoring;
import com.milaboratory.core.alignment.AlignmentScoring;
import com.milaboratory.core.alignment.BandedAlignerParameters;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.ClonalUpdatableParameters;
import com.milaboratory.mixcr.vdjaligners.ClonalGeneAlignmentParameters;

/**
 * Some fields of this object might not be set, to indicate that their values must be taken from original alignment
 * parameters (from *.vdjca file)
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE)
@JsonIgnoreProperties({"featureToAlign", "alignmentParameters"})
public final class VJCClonalAlignerParameters
        implements ClonalGeneAlignmentParameters, java.io.Serializable, ClonalUpdatableParameters {
    Float relativeMinScore;
    AlignmentScoring<NucleotideSequence> scoring;
    @JsonIgnore
    int maxAlignmentWidthLinear;
    @JsonIgnore
    int maxAlignmentWidthAffine;

    public VJCClonalAlignerParameters(
            float relativeMinScore,
            AlignmentScoring<NucleotideSequence> scoring,
            int maxAlignmentWidth) {
        this.relativeMinScore = relativeMinScore;
        this.scoring = scoring;
        this.maxAlignmentWidthLinear = maxAlignmentWidth;
        this.maxAlignmentWidthAffine = maxAlignmentWidth;
    }

    @JsonCreator
    public VJCClonalAlignerParameters(
            @JsonProperty("relativeMinScore") Float relativeMinScore,
            @JsonProperty("scoring") AlignmentScoring<NucleotideSequence> scoring,
            @JsonProperty("maxAlignmentWidth") Integer maxAlignmentWidth,
            @JsonProperty("maxAlignmentWidthLinear") Integer maxAlignmentWidthLinear,
            @JsonProperty("maxAlignmentWidthAffine") Integer maxAlignmentWidthAffine) {
        this.relativeMinScore = relativeMinScore;
        this.scoring = scoring;

        if (maxAlignmentWidth == null && (maxAlignmentWidthAffine == null || maxAlignmentWidthLinear == null))
            throw new IllegalArgumentException("maxAlignmentWidth or maxAlignmentWidthAffine and maxAlignmentWidthLinear are not specified");

        this.maxAlignmentWidthLinear = maxAlignmentWidth != null ? maxAlignmentWidth : maxAlignmentWidthLinear;
        this.maxAlignmentWidthAffine = maxAlignmentWidth != null ? maxAlignmentWidth : maxAlignmentWidthAffine;
    }

    public BandedAlignerParameters<NucleotideSequence> getAlignmentParameters() {
        if (!isComplete())
            throw new IllegalStateException();
        return new BandedAlignerParameters<>(scoring, getMaxAlignmentWidth(), Integer.MIN_VALUE);
    }

    @Override
    public void updateFrom(ClonalGeneAlignmentParameters alignerParameters) {
        if (scoring == null)
            scoring = alignerParameters.getScoring();
        if (relativeMinScore == null)
            relativeMinScore = alignerParameters.getRelativeMinScore();
    }

    @Override
    public boolean isComplete() {
        return relativeMinScore != null && scoring != null;
    }

    @Override
    public AlignmentScoring<NucleotideSequence> getScoring() {
        return scoring;
    }

    public VJCClonalAlignerParameters setScoring(AlignmentScoring<NucleotideSequence> scoring) {
        this.scoring = scoring;
        return this;
    }

    @Override
    public float getRelativeMinScore() {
        return relativeMinScore;
    }

    public VJCClonalAlignerParameters setRelativeMinScore(Float relativeMinScore) {
        this.relativeMinScore = relativeMinScore;
        return this;
    }

    public int getMaxAlignmentWidth() {
        return scoring instanceof AffineGapAlignmentScoring ? maxAlignmentWidthAffine : maxAlignmentWidthLinear;
    }

    @JsonProperty("maxAlignmentWidth")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Integer getMaxAlignmentWidthJSON() {
        return (maxAlignmentWidthAffine == maxAlignmentWidthLinear) ? maxAlignmentWidthAffine : null;
    }

    @JsonProperty("maxAlignmentWidthLinear")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Integer getMaxAlignmentWidthLinearJSON() {
        return (maxAlignmentWidthAffine != maxAlignmentWidthLinear) ? maxAlignmentWidthLinear : null;
    }

    @JsonProperty("maxAlignmentWidthAffine")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Integer getMaxAlignmentWidthAffineJSON() {
        return (maxAlignmentWidthAffine != maxAlignmentWidthLinear) ? maxAlignmentWidthAffine : null;
    }

    @Override
    public VJCClonalAlignerParameters clone() {
        return new VJCClonalAlignerParameters(relativeMinScore, scoring, null, maxAlignmentWidthLinear, maxAlignmentWidthAffine);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        VJCClonalAlignerParameters that = (VJCClonalAlignerParameters) o;

        if (maxAlignmentWidthLinear != that.maxAlignmentWidthLinear) return false;
        if (maxAlignmentWidthAffine != that.maxAlignmentWidthAffine) return false;
        if (relativeMinScore != null ? !relativeMinScore.equals(that.relativeMinScore) : that.relativeMinScore != null)
            return false;
        return scoring != null ? scoring.equals(that.scoring) : that.scoring == null;
    }

    @Override
    public int hashCode() {
        int result = relativeMinScore != null ? relativeMinScore.hashCode() : 0;
        result = 31 * result + (scoring != null ? scoring.hashCode() : 0);
        result = 31 * result + maxAlignmentWidthLinear;
        result = 31 * result + maxAlignmentWidthAffine;
        return result;
    }
}
