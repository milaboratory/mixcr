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
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.SequenceQuality;
import com.milaboratory.core.sequence.quality.QualityAggregationType;
import com.milaboratory.core.sequence.quality.QualityAggregator;
import com.milaboratory.mixcr.basictypes.ClonalSequence;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import gnu.trove.iterator.TObjectFloatIterator;
import gnu.trove.map.hash.TObjectFloatHashMap;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCGeneId;

import java.util.EnumMap;

public final class CloneAccumulator {
    final EnumMap<GeneType, TObjectFloatHashMap<VDJCGeneId>> geneScores = new EnumMap<>(GeneType.class);
    private ClonalSequence sequence;
    final QualityAggregator aggregator;
    long count = 0, countMapped = 0;
    private volatile int cloneIndex = -1;
    final Range[] nRegions;

    public CloneAccumulator(ClonalSequence sequence, Range[] nRegions, QualityAggregationType qualityAggregationType) {
        this.sequence = sequence;
        this.nRegions = nRegions;
        this.aggregator = qualityAggregationType.create(sequence.getConcatenated().size());
        //this.quality = sequence.getConcatenated().getQuality().asArray();
    }

    public ClonalSequence getSequence() {
        return sequence;
    }

    public void rebuildClonalSequence() {
        SequenceQuality newQuality = aggregator.getQuality();
        final NSequenceWithQuality[] updated = new NSequenceWithQuality[sequence.size()];
        int pointer = 0;
        for (int i = 0; i < updated.length; i++) {
            final NucleotideSequence s = this.sequence.get(i).getSequence();
            updated[i] = new NSequenceWithQuality(s, newQuality.getRange(pointer, pointer + s.size()));
            pointer += s.size();
        }
        sequence = new ClonalSequence(updated);
        return;
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

    public long getCoreCount() {
        return count - countMapped;
    }

    public long getMappedCount() {
        return countMapped;
    }

    public void calculateScores(CloneFactoryParameters parameters) {
        for (GeneType geneType : GeneType.VJC_REFERENCE) {
            VJCClonalAlignerParameters vjcParameters = parameters.getVJCParameters(geneType);
            if (vjcParameters == null)
                continue;

            TObjectFloatHashMap<VDJCGeneId> accumulatorAlleleIds = geneScores.get(geneType);
            if (accumulatorAlleleIds == null)
                continue;

            TObjectFloatIterator<VDJCGeneId> iterator = accumulatorAlleleIds.iterator();
            float maxScore = 0;
            while (iterator.hasNext()) {
                iterator.advance();
                float value = iterator.value();
                if (value > maxScore)
                    maxScore = value;
            }

            maxScore = maxScore * vjcParameters.getRelativeMinScore();
            iterator = accumulatorAlleleIds.iterator();
            while (iterator.hasNext()) {
                iterator.advance();
                if (maxScore > iterator.value())
                    iterator.remove();
                else
                    iterator.setValue(iterator.value() / (count - countMapped));
            }
        }
    }

    public synchronized void accumulate(ClonalSequence data, VDJCAlignments alignment, boolean mapped) {
        //Increment count
        ++count;

        if (!mapped) {
            // Accumulate information about V-D-J alignments only for strictly clustered reads
            // (only for core clonotypes members)
            float score;

            // Accumulate information about all genes
            for (GeneType geneType : GeneType.VJC_REFERENCE) {
                TObjectFloatHashMap<VDJCGeneId> alleleScores = geneScores.get(geneType);
                VDJCHit[] hits = alignment.getHits(geneType);
                if (hits.length == 0)
                    continue;
                if (alleleScores == null)
                    geneScores.put(geneType, alleleScores = new TObjectFloatHashMap<>());
                for (VDJCHit hit : hits) {
                    // Calculating sum of natural logarithms of scores
                    score = hit.getScore();
                    alleleScores.adjustOrPutValue(hit.getGene().getId(), score, score);
                }
            }

            aggregator.aggregate(data.getConcatenated().getQuality());

        } else ++countMapped;
    }
}
