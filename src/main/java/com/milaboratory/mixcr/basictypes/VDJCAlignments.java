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

import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.io.sequence.SequenceRead;
import com.milaboratory.core.io.sequence.SequenceReadUtil;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.primitivio.annotations.Serializable;
import io.repseq.core.GeneType;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;

@Serializable(by = IO.VDJCAlignmentsSerializer.class)
public final class VDJCAlignments extends VDJCObject {
    final SequenceHistory[] history;
    final SequenceRead[] originalReads;
    private volatile long alignmentsIndex = -1;

    public VDJCAlignments(long alignmentsIndex,
                          EnumMap<GeneType, VDJCHit[]> hits,
                          NSequenceWithQuality[] targets,
                          SequenceHistory[] history,
                          SequenceRead[] originalReads) {
        super(hits, targets);
        this.alignmentsIndex = alignmentsIndex;
        this.history = history;
        this.originalReads = originalReads;
    }

    public VDJCAlignments(EnumMap<GeneType, VDJCHit[]> hits,
                          NSequenceWithQuality[] targets,
                          SequenceHistory[] history,
                          SequenceRead[] originalReads) {
        this(-1, hits, targets, history, originalReads);
    }

    public VDJCAlignments(VDJCHit[] vHits, VDJCHit[] dHits, VDJCHit[] jHits, VDJCHit[] cHits,
                          NSequenceWithQuality[] targets,
                          SequenceHistory[] history,
                          SequenceRead[] originalReads) {
        this(-1, createHits(vHits, dHits, jHits, cHits), targets, history, originalReads);
    }

    public VDJCAlignments shiftReadId(long newAlignmentIndex, long shift) {
        return new VDJCAlignments(newAlignmentIndex, hits, targets, shift(history, shift), shift(originalReads, shift));
    }

    private static SequenceHistory[] shift(SequenceHistory[] data, long shift) {
        SequenceHistory[] r = new SequenceHistory[data.length];
        for (int i = 0; i < data.length; i++)
            r[i] = data[i].shiftReadId(shift);
        return r;
    }

    private static SequenceRead[] shift(SequenceRead[] data, long shift) {
        if (data == null)
            return null;
        SequenceRead[] r = new SequenceRead[data.length];
        for (int i = 0; i < data.length; i++)
            r[i] = SequenceReadUtil.setReadId(data[i].getId() + shift, data[i]);
        return r;
    }

    public SequenceHistory getHistory(int targetId) {
        return history[targetId];
    }

    public List<SequenceRead> getOriginalReads() {
        return originalReads == null ? null : Collections.unmodifiableList(Arrays.asList(originalReads));
    }

    public VDJCAlignments setHistory(SequenceHistory[] history, SequenceRead[] originalReads) {
        return new VDJCAlignments(alignmentsIndex, hits, targets, history, originalReads);
    }

    public VDJCAlignments removeBestHitAlignment(GeneType geneType, int targetId) {
        if (getBestHit(geneType) == null)
            return this;
        EnumMap<GeneType, VDJCHit[]> hits = new EnumMap<>(this.hits);
        VDJCHit[] gHits = hits.get(geneType).clone();
        Alignment<NucleotideSequence>[] als = gHits[0].getAlignments().clone();
        als[targetId] = null;
        gHits[0] = new VDJCHit(gHits[0].getGene(), als, gHits[0].getAlignedFeature());
        Arrays.sort(gHits);
        hits.put(geneType, gHits);
        return new VDJCAlignments(alignmentsIndex, hits, targets, history, originalReads);
    }

    public boolean hasNoHitsInTarget(int i) {
        for (VDJCHit[] vdjcHits : hits.values()) {
            if (vdjcHits == null)
                continue;
            for (VDJCHit hit : vdjcHits)
                if (hit.getAlignment(i) != null)
                    return false;
        }
        return true;
    }

    private volatile long minReadId = -1;

    public long getMinReadId() {
        if (minReadId != -1)
            return minReadId;
        long min = Long.MAX_VALUE;
        for (SequenceHistory s : history)
            min = Math.min(s.minReadId(), min);
        return minReadId = min;
    }

    public long getAlignmentsIndex() {
        return alignmentsIndex;
    }

    public void setAlignmentsIndex(long alignmentsIndex) {
        this.alignmentsIndex = alignmentsIndex;
    }

    /**
     * Returns {@code true} if at least one V and one J hit among first {@code top} hits have same chain and false
     * otherwise (first {@code top} V hits have different chain from those have first {@code top} J hits).
     *
     * @param top numer of top hits to test
     * @return {@code true} if at least one V and one J hit among first {@code top} hits have same chain and false
     * otherwise (first {@code top} V hits have different chain from those have first {@code top} J hits)
     */
    public final boolean hasSameVJLoci(final int top) {
        final VDJCHit[] vHits = hits.get(GeneType.Variable),
                jHits = hits.get(GeneType.Joining),
                cHits = hits.get(GeneType.Constant);

        if (vHits.length > 0 && jHits.length > 0 && cHits.length > 0) {
            for (int v = 0; v < actualTop(vHits, top); ++v)
                for (int j = 0; j < actualTop(jHits, top); ++j)
                    for (int c = 0; c < actualTop(cHits, top); ++c)
                        if (hasCommonChain(vHits[v], jHits[j]) && hasCommonChain(vHits[v], cHits[c]))
                            return true;
            return false;
        }

        if (vHits.length > 0 && jHits.length > 0) {
            for (int v = 0; v < actualTop(vHits, top); ++v)
                for (int j = 0; j < actualTop(jHits, top); ++j)
                    if (hasCommonChain(vHits[v], jHits[j]))
                        return true;
            return false;
        }

        if (vHits.length > 0 && cHits.length > 0) {
            for (int v = 0; v < actualTop(vHits, top); ++v)
                for (int c = 0; c < actualTop(cHits, top); ++c)
                    if (hasCommonChain(vHits[v], cHits[c]))
                        return true;
            return false;
        }

        if (cHits.length > 0 && jHits.length > 0) {
            for (int c = 0; c < actualTop(cHits, top); ++c)
                for (int j = 0; j < actualTop(jHits, top); ++j)
                    if (hasCommonChain(cHits[c], jHits[j]))
                        return true;
            return false;
        }

        return true;
    }

    private static boolean hasCommonChain(VDJCHit g1, VDJCHit g2) {
        return g1.getGene().getChains().intersects(g2.getGene().getChains());
    }

    private static int actualTop(VDJCHit[] hits, int top) {
        if (hits.length <= top)
            return hits.length;
        float score = hits[top].getScore() - Float.MIN_VALUE;
        while (top < hits.length &&
                hits[top].getScore() >= score) {
            ++top;
        }
        return top;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        VDJCAlignments that = (VDJCAlignments) o;

        return Arrays.equals(history, that.history);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + Arrays.hashCode(history);
        return result;
    }
}
