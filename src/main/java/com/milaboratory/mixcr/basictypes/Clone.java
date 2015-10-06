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
package com.milaboratory.mixcr.basictypes;

import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.mixcr.reference.GeneFeature;
import com.milaboratory.mixcr.reference.GeneType;
import com.milaboratory.primitivio.annotations.Serializable;

import java.util.EnumMap;

@Serializable(by = IO.CloneSerializer.class)
public final class Clone extends VDJCObject {
    final GeneFeature[] assemblingFeatures;
    final long count;
    final int id;
    CloneSet parent = null;

    public Clone(NSequenceWithQuality[] targets, EnumMap<GeneType, VDJCHit[]> hits, GeneFeature[] assemblingFeatures, long count, int id) {
        super(hits, targets);
        this.assemblingFeatures = assemblingFeatures;
        this.count = count;
        this.id = id;
    }

    public void setParentCloneSet(CloneSet set) {
        if (this.parent != null)
            throw new IllegalStateException("Parent is already set.");
        this.parent = set;
    }

    public double getFraction() {
        if (parent == null)
            throw new NullPointerException("Parent not set yet.");
        return getFraction(parent.getTotalCount());
    }

    public double getFraction(long totalCount) {
        return 1.0 * count / totalCount;
    }

    public GeneFeature[] getAssemblingFeatures() {
        return assemblingFeatures;
    }

    public long getCount() {
        return count;
    }

    @Override
    public NSequenceWithQuality getFeature(GeneFeature geneFeature) {
        for (int i = 0; i < assemblingFeatures.length; ++i)
            if (geneFeature.equals(assemblingFeatures[i]))
                return targets[i];
        return super.getFeature(geneFeature);
    }

    public int getId() {
        return id;
    }

    @Override
    public String toString() {
        return "id: " + id + " " + "count: " + count;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Clone)) return false;
        if (!super.equals(o)) return false;

        Clone clone = (Clone) o;

        if (count != clone.count) return false;
        if (id != clone.id) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (int) (count ^ (count >>> 32));
        result = 31 * result + id;
        return result;
    }
}
