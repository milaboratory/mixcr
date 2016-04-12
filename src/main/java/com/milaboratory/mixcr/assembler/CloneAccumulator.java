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
import com.milaboratory.core.mutations.AggregatedMutations;
import com.milaboratory.core.mutations.MutationConsensusBuilder;
import com.milaboratory.core.mutations.Weight;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.SequenceQuality;
import com.milaboratory.mixcr.basictypes.ClonalSequence;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.mixcr.reference.AlleleId;
import com.milaboratory.mixcr.reference.GeneFeature;
import com.milaboratory.mixcr.reference.GeneType;
import com.milaboratory.mixcr.reference.LociLibraryManager;
import gnu.trove.iterator.TObjectFloatIterator;
import gnu.trove.map.hash.TObjectFloatHashMap;

import java.util.EnumMap;
import java.util.Map;

public final class CloneAccumulator {
    final EnumMap<GeneType, TObjectFloatHashMap<AlleleId>> geneScores = new EnumMap<>(GeneType.class);
    final EnumMap<GeneType, AlleleId> topAlleles = new EnumMap<>(GeneType.class);
    final ClonalSequence sequence;
    final byte[] quality;
    long count = 0, countMapped = 0;
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

    EnumMap<GeneType, MutationConsensusBuilder<NucleotideSequence>> aggregators = null;

    public void initializeCoverageAggregator(Map<GeneType, GeneFeature> featuresToAlign) {
        aggregators = new EnumMap<>(GeneType.class);
        for (GeneType geneType : GeneType.VJC_REFERENCE) {
            final AlleleId alleleId = topAlleles.get(geneType);
            if (alleleId == null)
                continue;
            int length = LociLibraryManager.getDefault().getAllele(alleleId).getFeature(featuresToAlign.get(geneType)).size();
            aggregators.put(geneType, new MutationConsensusBuilder<>(NucleotideSequence.ALPHABET, length));
        }
    }

    public void accumulateCoverage(VDJCAlignments alignments) {
        for (GeneType geneType : GeneType.VJC_REFERENCE) {
            final AlleleId reference = topAlleles.get(geneType);
            final VDJCHit[] hits = alignments.getHits(geneType);
            VDJCHit hit = null;
            for (VDJCHit vdjcHit : hits)
                if (vdjcHit.getAllele().getId().equals(reference)) {
                    hit = vdjcHit;
                    break;
                }
            if (hit == null)
                continue;

            for (int i = 0; i < hit.numberOfTargets(); i++)
                aggregators.get(geneType).aggregate(hit.getAlignment(i), Weight.ONE);
        }
    }

    EnumMap<GeneType, AggregatedMutations<NucleotideSequence>> aggregatedMutations = null;

    public AggregatedMutations<NucleotideSequence> getAggregatedMutations(GeneType geneType) {
        if (aggregatedMutations == null) {
            aggregatedMutations = new EnumMap<>(GeneType.class);
            for (GeneType type : GeneType.VJC_REFERENCE)
                aggregatedMutations.put(type, aggregators.get(type).build());
        }
        return aggregatedMutations.get(geneType);
    }

    public void calculateScores(CloneFactoryParameters parameters) {
        for (GeneType geneType : GeneType.VJC_REFERENCE) {
            VJCClonalAlignerParameters vjcParameters = parameters.getVJCParameters(geneType);
            if (vjcParameters == null)
                continue;

            TObjectFloatHashMap<AlleleId> accumulatorAlleleIds = geneScores.get(geneType);
            if (accumulatorAlleleIds == null)
                continue;

            TObjectFloatIterator<AlleleId> iterator = accumulatorAlleleIds.iterator();
            float maxScore = 0;
            AlleleId topAllele = null;
            while (iterator.hasNext()) {
                iterator.advance();
                float value = iterator.value();
                if (value > maxScore) {
                    maxScore = value;
                    topAllele = iterator.key();
                }
            }

            topAlleles.put(geneType, topAllele);

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
        } else ++countMapped;
    }
}
