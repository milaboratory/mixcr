/*
 * Copyright (c) 2014-2015, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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
 * commercial purposes should contact the Inventors using one of the following
 * email addresses: chudakovdm@mail.ru, chudakovdm@gmail.com
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
package com.milaboratory.mixcr.vdjaligners;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.core.alignment.AlignmentScoring;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.reference.GeneFeature;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE)
public final class DAlignerParameters extends GeneAlignmentParameters<DAlignerParameters> {
    private float absoluteMinScore, relativeMinScore;
    private int maxHits;
    private AlignmentScoring<NucleotideSequence> scoring;

    @JsonCreator
    public DAlignerParameters(
            @JsonProperty("geneFeatureToAlign") GeneFeature geneFeatureToAlign,
            @JsonProperty("absoluteMinScore") float absoluteMinScore,
            @JsonProperty("relativeMinScore") float relativeMinScore,
            @JsonProperty("maxHits") int maxHits,
            @JsonProperty("scoring") AlignmentScoring scoring) {
        super(geneFeatureToAlign);
        this.absoluteMinScore = absoluteMinScore;
        this.relativeMinScore = relativeMinScore;
        this.maxHits = maxHits;
        this.scoring = scoring;
    }

    public AlignmentScoring getScoring() {
        return scoring;
    }

    public DAlignerParameters setScoring(AlignmentScoring scoring) {
        this.scoring = scoring;
        return this;
    }

    public float getAbsoluteMinScore() {
        return absoluteMinScore;
    }

    public DAlignerParameters setAbsoluteMinScore(float absoluteMinScore) {
        this.absoluteMinScore = absoluteMinScore;
        return this;
    }

    public float getRelativeMinScore() {
        return relativeMinScore;
    }

    public DAlignerParameters setRelativeMinScore(float relativeMinScore) {
        this.relativeMinScore = relativeMinScore;
        return this;
    }

    public int getMaxHits() {
        return maxHits;
    }

    public DAlignerParameters setMaxHits(int maxHits) {
        this.maxHits = maxHits;
        return this;
    }

    @Override
    public DAlignerParameters clone() {
        return new DAlignerParameters(geneFeatureToAlign, absoluteMinScore, relativeMinScore, maxHits, scoring);
    }

    @Override
    public String toString() {
        return "DAlignerParameters{" +
                "absoluteMinScore=" + absoluteMinScore +
                ", relativeMinScore=" + relativeMinScore +
                ", maxHits=" + maxHits +
                ", scoring=" + scoring +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DAlignerParameters)) return false;
        if (!super.equals(o)) return false;

        DAlignerParameters that = (DAlignerParameters) o;

        if (Float.compare(that.absoluteMinScore, absoluteMinScore) != 0) return false;
        if (maxHits != that.maxHits) return false;
        if (Float.compare(that.relativeMinScore, relativeMinScore) != 0) return false;
        if (!scoring.equals(that.scoring)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (absoluteMinScore != +0.0f ? Float.floatToIntBits(absoluteMinScore) : 0);
        result = 31 * result + (relativeMinScore != +0.0f ? Float.floatToIntBits(relativeMinScore) : 0);
        result = 31 * result + maxHits;
        result = 31 * result + scoring.hashCode();
        return result;
    }
}
