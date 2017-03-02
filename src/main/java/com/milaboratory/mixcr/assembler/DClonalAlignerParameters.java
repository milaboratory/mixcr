package com.milaboratory.mixcr.assembler;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.core.alignment.AlignmentScoring;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.ClonalUpdatableParameters;
import com.milaboratory.mixcr.vdjaligners.ClonalGeneAlignmentParameters;

/**
 * Some fields of this object might not be set, to indicate that their values must be taken from original alignment
 * parameters (from *.vdjca file)
 *
 * Created by poslavsky on 01/03/2017.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE)
public class DClonalAlignerParameters<T extends DClonalAlignerParameters<T>> implements
        ClonalUpdatableParameters, ClonalGeneAlignmentParameters, java.io.Serializable {
    protected Float relativeMinScore;
    protected Float absoluteMinScore;
    protected Integer maxHits;
    protected AlignmentScoring<NucleotideSequence> scoring;

    @JsonCreator
    public DClonalAlignerParameters(
            @JsonProperty("relativeMinScore") Float relativeMinScore,
            @JsonProperty("absoluteMinScore") Float absoluteMinScore,
            @JsonProperty("maxHits") Integer maxHits,
            @JsonProperty("scoring") AlignmentScoring<NucleotideSequence> scoring) {
        this.relativeMinScore = relativeMinScore;
        this.absoluteMinScore = absoluteMinScore;
        this.maxHits = maxHits;
        this.scoring = scoring;
    }

    @Override
    public void updateFrom(ClonalGeneAlignmentParameters alignerParameters) {
        if (!(alignerParameters instanceof DClonalAlignerParameters))
            throw new IllegalArgumentException();
        DClonalAlignerParameters oth = (DClonalAlignerParameters) alignerParameters;

        if (relativeMinScore == null)
            relativeMinScore = oth.relativeMinScore;
        if (absoluteMinScore == null)
            absoluteMinScore = oth.absoluteMinScore;
        if (maxHits == null)
            maxHits = oth.maxHits;
        if (scoring == null)
            scoring = oth.scoring;
    }

    @Override
    public boolean isComplete() {
        return relativeMinScore != null && absoluteMinScore != null && maxHits != null && scoring != null;
    }

    @Override
    public AlignmentScoring<NucleotideSequence> getScoring() {
        return scoring;
    }


    public T setRelativeMinScore(float relativeMinScore) {
        this.relativeMinScore = relativeMinScore;
        return (T) this;
    }

    @Override
    public float getRelativeMinScore() {
        return relativeMinScore;
    }


    public T setScoring(AlignmentScoring scoring) {
        this.scoring = scoring;
        return (T) this;
    }

    public float getAbsoluteMinScore() {
        return absoluteMinScore;
    }

    public T setAbsoluteMinScore(float absoluteMinScore) {
        this.absoluteMinScore = absoluteMinScore;
        return (T) this;
    }

    public int getMaxHits() {
        return maxHits;
    }

    public T setMaxHits(int maxHits) {
        this.maxHits = maxHits;
        return (T) this;
    }

    @Override
    public DClonalAlignerParameters clone() {
        return new DClonalAlignerParameters(relativeMinScore, absoluteMinScore, maxHits, scoring);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DClonalAlignerParameters that = (DClonalAlignerParameters) o;

        if (relativeMinScore != null ? !relativeMinScore.equals(that.relativeMinScore) : that.relativeMinScore != null)
            return false;
        if (absoluteMinScore != null ? !absoluteMinScore.equals(that.absoluteMinScore) : that.absoluteMinScore != null)
            return false;
        if (maxHits != null ? !maxHits.equals(that.maxHits) : that.maxHits != null) return false;
        return scoring != null ? scoring.equals(that.scoring) : that.scoring == null;
    }

    @Override
    public int hashCode() {
        int result = relativeMinScore != null ? relativeMinScore.hashCode() : 0;
        result = 31 * result + (absoluteMinScore != null ? absoluteMinScore.hashCode() : 0);
        result = 31 * result + (maxHits != null ? maxHits.hashCode() : 0);
        result = 31 * result + (scoring != null ? scoring.hashCode() : 0);
        return result;
    }
}
