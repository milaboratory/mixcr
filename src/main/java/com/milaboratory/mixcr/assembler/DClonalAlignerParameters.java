/*
 * Copyright (c) 2014-2019, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
 * (here and after addressed as Inventors)
 * All Rights Reserved
 *
 * Permission to use, copy, modify and distribute any part of this program for
 * educational, research and non-profit purposes, by non-profit institutions
 * only, without fee, and without a written agreement is hereby granted,
 * provided that the above copyright notice, this paragraph and the following
 * three paragraphs appear in all copies.
 *
 * Those desiring to incorporate this work into commercial products or use for
 * commercial purposes should contact MiLaboratory LLC, which owns exclusive
 * rights for distribution of this program for commercial purposes, using the
 * following email address: licensing@milaboratory.com.
 *
 * IN NO EVENT SHALL THE INVENTORS BE LIABLE TO ANY PARTY FOR DIRECT, INDIRECT,
 * SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING LOST PROFITS,
 * ARISING OUT OF THE USE OF THIS SOFTWARE, EVEN IF THE INVENTORS HAS BEEN
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * THE SOFTWARE PROVIDED HEREIN IS ON AN "AS IS" BASIS, AND THE INVENTORS HAS
 * NO OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR
 * MODIFICATIONS. THE INVENTORS MAKES NO REPRESENTATIONS AND EXTENDS NO
 * WARRANTIES OF ANY KIND, EITHER IMPLIED OR EXPRESS, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY OR FITNESS FOR A
 * PARTICULAR PURPOSE, OR THAT THE USE OF THE SOFTWARE WILL NOT INFRINGE ANY
 * PATENT, TRADEMARK OR OTHER RIGHTS.
 */
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
