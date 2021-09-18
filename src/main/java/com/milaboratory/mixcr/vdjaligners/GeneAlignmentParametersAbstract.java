/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 *
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 *
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */
package com.milaboratory.mixcr.vdjaligners;

import com.milaboratory.mixcr.basictypes.ClonalUpdatableParameters;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;

public abstract class GeneAlignmentParametersAbstract<T extends GeneAlignmentParametersAbstract<T>>
        implements  java.io.Serializable, GeneAlignmentParameters {
    protected GeneFeature geneFeatureToAlign;
    protected Float relativeMinScore;

    protected GeneAlignmentParametersAbstract(GeneFeature geneFeatureToAlign, Float relativeMinScore) {
        this.geneFeatureToAlign = geneFeatureToAlign;
        this.relativeMinScore = relativeMinScore;
    }

    public T setRelativeMinScore(float relativeMinScore) {
        this.relativeMinScore = relativeMinScore;
        return (T) this;
    }

    @Override
    public float getRelativeMinScore() {
        return relativeMinScore;
    }

    @Override
    public GeneFeature getGeneFeatureToAlign() {
        return geneFeatureToAlign;
    }

    public T setGeneFeatureToAlign(GeneFeature geneFeatureToAlign) {
        this.geneFeatureToAlign = geneFeatureToAlign;
        return (T) this;
    }

    @Override
    public GeneType getGeneType() {
        return geneFeatureToAlign.getGeneType();
    }

    public abstract T clone();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GeneAlignmentParametersAbstract<?> that = (GeneAlignmentParametersAbstract<?>) o;

        if (Float.compare(that.relativeMinScore, relativeMinScore) != 0) return false;
        return geneFeatureToAlign != null ? geneFeatureToAlign.equals(that.geneFeatureToAlign) : that.geneFeatureToAlign == null;
    }

    @Override
    public int hashCode() {
        int result = geneFeatureToAlign != null ? geneFeatureToAlign.hashCode() : 0;
        result = 31 * result + (relativeMinScore != +0.0f ? Float.floatToIntBits(relativeMinScore) : 0);
        return result;
    }
}
