/*
 * Copyright (c) 2014-2019, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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
 * commercial purposes should contact MiLaboratory LLC, which owns exclusive
 * rights for distribution of this program for commercial purposes, using the
 * following email address: licensing@milaboratory.com.
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
import com.milaboratory.mixcr.basictypes.GeneAndScore;
import com.milaboratory.mixcr.basictypes.HasRelativeMinScore;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.tag.TagCountAggregator;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCGeneId;

import java.util.List;
import java.util.Map;

public final class CloneAccumulator {
    VDJCGeneAccumulator geneAccumulator = new VDJCGeneAccumulator();
    Map<GeneType, List<GeneAndScore>> genes;
    private ClonalSequence sequence;
    private final QualityAggregator aggregator;
    private long coreCount = 0, mappedCount = 0, initialCoreCount = -1;
    private volatile int cloneIndex = -1;
    final Range[] nRegions;
    final TagCountAggregator tagBuilder = new TagCountAggregator();

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
    }

    public void onBeforeMapping() {
        initialCoreCount = coreCount;
    }

    public float getBestScore(GeneType geneType) {
        if (genes == null)
            throw new IllegalStateException("Gene information not aggregated");
        List<GeneAndScore> genes = this.genes.get(geneType);
        return genes == null || genes.isEmpty() ? Float.NaN : genes.get(0).score;
    }

    public VDJCGeneId getBestGene(GeneType geneType) {
        if (genes == null)
            throw new IllegalStateException("Gene information not aggregated");
        List<GeneAndScore> genes = this.genes.get(geneType);
        return genes == null || genes.isEmpty() ? null : genes.get(0).geneId;
    }

    public boolean hasInfoFor(GeneType geneType) {
        if (genes == null)
            throw new IllegalStateException("Gene information not aggregated");
        List<GeneAndScore> genes = this.genes.get(geneType);
        return genes != null && !genes.isEmpty();
    }

    public boolean hasInfoFor(GeneType geneType, VDJCGeneId gene) {
        if (genes == null)
            throw new IllegalStateException("Gene information not aggregated");
        List<GeneAndScore> genes = this.genes.get(geneType);
        if (genes == null)
            return false;
        for (GeneAndScore gs : genes)
            if (gs.geneId.equals(gene))
                return true;
        return false;
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

    public long getInitialCoreCount() {
        return initialCoreCount;
    }

    public long getCount() {
        return coreCount + mappedCount;
    }

    public long getCoreCount() {
        return coreCount;
    }

    public long getMappedCount() {
        return mappedCount;
    }

    public void mergeCounts(CloneAccumulator acc) {
        coreCount += acc.coreCount;
        mappedCount += acc.mappedCount;
        tagBuilder.add(acc.tagBuilder);
    }

    public void aggregateGeneInfo(HasRelativeMinScore geneParameters) {
        genes = geneAccumulator.aggregateInformation(geneParameters);
        geneAccumulator = null;
    }

    public synchronized void accumulate(ClonalSequence data, VDJCAlignments alignment, boolean mapped) {
        if (geneAccumulator == null)
            throw new IllegalStateException("Gene information already aggregated");

        if (!mapped) { // Core sequence accumulation
            coreCount += alignment.getNumberOfReads();

            tagBuilder.add(alignment.getTagCount());

            // Accumulate information about V-D-J alignments only for strictly clustered reads
            // (only for core clonotypes members)
            geneAccumulator.accumulate(alignment);

            aggregator.aggregate(data.getConcatenated().getQuality());
        } else // Mapped sequence accumulation
            mappedCount += alignment.getNumberOfReads();
    }
}
