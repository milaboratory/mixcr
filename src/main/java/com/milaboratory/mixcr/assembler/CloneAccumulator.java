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


import com.milaboratory.core.Range;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.SequenceQuality;
import com.milaboratory.mixcr.basictypes.ClonalSequence;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.mixcr.reference.AlleleId;
import com.milaboratory.mixcr.reference.GeneType;
import gnu.trove.map.hash.TObjectFloatHashMap;

import java.util.EnumMap;

public final class CloneAccumulator {
    final EnumMap<GeneType, TObjectFloatHashMap<AlleleId>> geneScores = new EnumMap<>(GeneType.class);
    final ClonalSequence sequence;
    final byte[] quality;
    long count = 0;
    volatile int cloneIndex = -1;
    final Range[] nRegions;

    public CloneAccumulator(ClonalSequence sequence, Range[] nRegions) {
        this.sequence = sequence;
        this.nRegions = nRegions;
        this.quality = sequence.getConcatenated().getQuality().asArray();
    }

    public ClonalSequence getSequence() {
        return sequence;
    }

    public Range[] getNRegions() {
        return nRegions;
    }

    public void setCloneIndex(int cloneIndex) {
        this.cloneIndex = cloneIndex;
    }

    public int getCloneIndex() {
        // O_o
        while (cloneIndex == -1) ;
        return cloneIndex;
    }

    public long getCount() {
        return count;
    }

    public synchronized void accumulate(ClonalSequence data, VDJCAlignments alignment, boolean mapped) {
        //Increment count
        ++count;

        if (!mapped) {
            // Accumulate information about V-D-J alignments only for strictly clustered reads
            // (only for core clonotypes members)
            float score;

            // Accumulate information about all genes
            for (GeneType geneType : GeneType.values()) {
                TObjectFloatHashMap<AlleleId> alleleScores = geneScores.get(geneType);
                VDJCHit[] hits = alignment.getHits(geneType);
                if (hits.length == 0)
                    continue;
                if (alleleScores == null)
                    geneScores.put(geneType, alleleScores = new TObjectFloatHashMap<>());
                for (VDJCHit hit : hits) {
                    // Calculating sum of natural logarithms of scores
                    score = hit.getScore();
                    alleleScores.adjustOrPutValue(hit.getAllele().getId(), score, score);
                }
            }

            int pointer = 0;
            for (NSequenceWithQuality p : data) {
                for (int i = 0; i < p.size(); ++i) {
                    final SequenceQuality q = p.getQuality();
                    if (quality[pointer] < q.value(i))
                        quality[pointer] = q.value(i);
                    ++pointer;
                }
            }
        }
    }
}
