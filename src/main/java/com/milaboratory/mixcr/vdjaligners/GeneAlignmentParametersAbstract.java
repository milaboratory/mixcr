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
