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
