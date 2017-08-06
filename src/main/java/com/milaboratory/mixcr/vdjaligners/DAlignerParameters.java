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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.core.alignment.AlignmentScoring;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.assembler.DClonalAlignerParameters;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;

public final class DAlignerParameters extends DClonalAlignerParameters<DAlignerParameters>
        implements GeneAlignmentParameters, java.io.Serializable {
    private GeneFeature geneFeatureToAlign;

    @JsonCreator
    public DAlignerParameters(
            @JsonProperty("geneFeatureToAlign") GeneFeature geneFeatureToAlign,
            @JsonProperty("relativeMinScore") Float relativeMinScore,
            @JsonProperty("absoluteMinScore") Float absoluteMinScore,
            @JsonProperty("maxHits") Integer maxHits,
            @JsonProperty("scoring") AlignmentScoring<NucleotideSequence> scoring) {
        super(relativeMinScore, absoluteMinScore, maxHits, scoring);
        this.geneFeatureToAlign = geneFeatureToAlign;
    }

    @Override
    public GeneFeature getGeneFeatureToAlign() {
        return geneFeatureToAlign;
    }

    public DAlignerParameters setGeneFeatureToAlign(GeneFeature geneFeatureToAlign) {
        this.geneFeatureToAlign = geneFeatureToAlign;
        return this;
    }

    @Override
    public GeneType getGeneType() {
        return GeneType.Diversity;
    }

    @Override
    public void updateFrom(ClonalGeneAlignmentParameters alignerParameters) {
        throw new IllegalStateException();
    }

    @Override
    public boolean isComplete() {
        throw new IllegalStateException();
    }


    @Override
    public DAlignerParameters clone() {
        return new DAlignerParameters(geneFeatureToAlign, relativeMinScore, absoluteMinScore, maxHits, scoring);
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
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        DAlignerParameters that = (DAlignerParameters) o;

        return geneFeatureToAlign != null ? geneFeatureToAlign.equals(that.geneFeatureToAlign) : that.geneFeatureToAlign == null;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (geneFeatureToAlign != null ? geneFeatureToAlign.hashCode() : 0);
        return result;
    }
}
