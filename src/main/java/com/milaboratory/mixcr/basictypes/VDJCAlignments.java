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
import com.milaboratory.mixcr.reference.GeneType;
import com.milaboratory.primitivio.annotations.Serializable;

import java.util.EnumMap;

@Serializable(by = IO.VDJCAlignmentsSerializer.class)
public final class VDJCAlignments extends VDJCObject {
    volatile String[] descriptions;
    final long readId;
    private volatile long alignmentsIndex = -1;

    public VDJCAlignments(long readId, long alignmentsIndex, VDJCAlignments alignments) {
        super(alignments.hits, alignments.targets);
        this.readId = readId;
        this.alignmentsIndex = alignmentsIndex;
        this.descriptions = alignments.descriptions;
    }

    public VDJCAlignments(long readId, EnumMap<GeneType, VDJCHit[]> hits, NSequenceWithQuality target) {
        super(hits, new NSequenceWithQuality[]{target});
        this.readId = readId;
    }

    public VDJCAlignments(long readId, EnumMap<GeneType, VDJCHit[]> hits, NSequenceWithQuality... targets) {
        super(hits, targets);
        this.readId = readId;
    }

    public VDJCAlignments(long readId, VDJCHit[] vHits, VDJCHit[] dHits, VDJCHit[] jHits, VDJCHit[] cHits,
                          NSequenceWithQuality... targets) {
        super(vHits, dHits, jHits, cHits, targets);
        this.readId = readId;
    }

    public long getReadId() {
        return readId;
    }

    public long getAlignmentsIndex() {
        return alignmentsIndex;
    }

    public void setAlignmentsIndex(long alignmentsIndex) {
        this.alignmentsIndex = alignmentsIndex;
    }

    public void setDescriptions(String[] description) {
        this.descriptions = description;
    }

    public String[] getDescriptions() {
        return descriptions;
    }

    /**
     * Returns {@code true} if at least ont V and one J hit among first {@code top} hits have same locus and false
     * otherwise (first {@code top} V hits have different locus from those have first {@code top} J hits).
     *
     * @param top numer of top hits to test
     * @return {@code true} if at least ont V and one J hit among first {@code top} hits have same locus and false
     * otherwise (first {@code top} V hits have different locus from those have first {@code top} J hits)
     */
    public final boolean hasSameVJLoci(final int top) {
        VDJCHit[] vHits = hits.get(GeneType.Variable),
                jHits = hits.get(GeneType.Joining);
        for (int v = 0; v < top && v < vHits.length; ++v)
            for (int j = 0; j < top && j < jHits.length; ++j)
                if (vHits[v].getAllele().getLocus() == jHits[j].getAllele().getLocus())
                    return true;
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VDJCAlignments)) return false;
        if (!super.equals(o)) return false;

        VDJCAlignments that = (VDJCAlignments) o;

        if (readId != that.readId) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (int) (readId ^ (readId >>> 32));
        return result;
    }
}
