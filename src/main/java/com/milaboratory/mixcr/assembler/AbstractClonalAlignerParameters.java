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
package com.milaboratory.mixcr.assembler;

import io.repseq.core.GeneFeature;

public abstract class AbstractClonalAlignerParameters<T extends AbstractClonalAlignerParameters<T>>
        implements java.io.Serializable {
    protected float relativeMinScore;
    protected GeneFeature featureToAlign;

    protected AbstractClonalAlignerParameters() {
    }

    protected AbstractClonalAlignerParameters(GeneFeature featureToAlign, float relativeMinScore) {
        this.relativeMinScore = relativeMinScore;
        this.featureToAlign = featureToAlign;
    }

    public float getRelativeMinScore() {
        return relativeMinScore;
    }

    public T setRelativeMinScore(float relativeMinScore) {
        this.relativeMinScore = relativeMinScore;
        return (T) this;
    }

    public GeneFeature getFeatureToAlign() {
        return featureToAlign;
    }

    public T setFeatureToAlign(GeneFeature featureToAlign) {
        this.featureToAlign = featureToAlign;
        return (T) this;
    }

    public abstract T clone();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AbstractClonalAlignerParameters)) return false;

        AbstractClonalAlignerParameters that = (AbstractClonalAlignerParameters) o;

        if (Float.compare(that.relativeMinScore, relativeMinScore) != 0) return false;
        if (!featureToAlign.equals(that.featureToAlign)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = (relativeMinScore != +0.0f ? Float.floatToIntBits(relativeMinScore) : 0);
        result = 31 * result + featureToAlign.hashCode();
        return result;
    }
}
