/*
 * Copyright (c) 2014-2022, MiLaboratories Inc. All Rights Reserved
 *
 * Before downloading or accessing the software, please read carefully the
 * License Agreement available at:
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 *
 * By downloading or accessing the software, you accept and agree to be bound
 * by the terms of the License Agreement. If you do not want to agree to the terms
 * of the Licensing Agreement, you must not download or access the software.
 */
package com.milaboratory.mixcr.assembler;


import com.milaboratory.core.Range;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.SequenceQuality;
import com.milaboratory.core.sequence.quality.QualityAggregationType;
import com.milaboratory.core.sequence.quality.QualityAggregator;
import com.milaboratory.mixcr.assembler.preclone.PreClone;
import com.milaboratory.mixcr.basictypes.ClonalSequence;
import com.milaboratory.mixcr.basictypes.GeneAndScore;
import com.milaboratory.mixcr.basictypes.HasRelativeMinScore;
import com.milaboratory.mixcr.basictypes.tag.TagCountAggregator;
import com.milaboratory.mixcr.basictypes.tag.TagTuple;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCGeneId;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CloneAccumulator {
    VDJCGeneAccumulator geneAccumulator = new VDJCGeneAccumulator();
    Map<GeneType, List<GeneAndScore>> genes;
    private ClonalSequence sequence;
    private final QualityAggregator aggregator;
    private long coreCount = 0, mappedCount = 0, initialCoreCount = -1;
    private volatile int cloneIndex = -1;
    final Range[] nRegions;
    final TagCountAggregator tagAggregator = new TagCountAggregator();
    /** This weight is updated right before clustering, and used in statistical testing during PCR/RT error correction */
    private long weight = -1;
    /** Updated before clustering and used to calculate sample/cell intersection during error correction */
    private Set<TagTuple> sampleAndCellPrefixes = null;

    public CloneAccumulator(ClonalSequence sequence, Range[] nRegions, QualityAggregationType qualityAggregationType) {
        this.sequence = sequence;
        this.nRegions = nRegions;
        this.aggregator = qualityAggregationType.create(sequence.getConcatenated().size());
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

    public TagCountAggregator getTagAggregator() {
        return tagAggregator;
    }

    public long getWeight() {
        assert weight != -1;
        return weight;
    }

    public Set<TagTuple> getSampleAndCellPrefixes() {
        return sampleAndCellPrefixes;
    }

    public void updateWeightAndPrefixes(int sampleCellDepth, boolean hasMoleculeTags) {
        if (hasMoleculeTags)
            weight = tagAggregator.size();
        else
            weight = getCount();

        sampleAndCellPrefixes = tagAggregator.getPrefixSet(sampleCellDepth);
    }

    private static final Set<TagTuple> noTagsSet = Collections.singleton(TagTuple.NO_TAGS);

    public CloneAccumulatorPart subtractTags(int sampleCellDepth, Set<TagTuple> prefixes) {
        if (noTagsSet.equals(prefixes))
            return new CloneAccumulatorPart(this);
        double sumBeforeIntersection = tagAggregator.sum();
        TagCountAggregator cut = tagAggregator.filterKeys(t -> prefixes.contains(t.keyPrefix(sampleCellDepth)));
        if (tagAggregator.isEmpty())
            return new CloneAccumulatorPart(this);
        double cutShare = cut.sum() / sumBeforeIntersection;
        CloneAccumulatorPart cCut = new CloneAccumulatorPart(this, cut,
                (long) (coreCount * cutShare),
                (long) (mappedCount * cutShare));
        coreCount -= cCut.getCoreCount();
        mappedCount -= cCut.getMappedCount();
        return cCut;
    }

    public void mergeCounts(CloneAccumulator acc) {
        coreCount += acc.coreCount;
        mappedCount += acc.mappedCount;
        tagAggregator.add(acc.tagAggregator);
    }

    public void mergeCounts(CloneAccumulatorPart part) {
        coreCount += part.getCoreCount();
        mappedCount += part.getMappedCount();
        tagAggregator.add(part.getTagAggregator());
    }

    public void aggregateGeneInfo(HasRelativeMinScore geneParameters) {
        genes = geneAccumulator.aggregateInformation(geneParameters);
        geneAccumulator = null;
    }

    public synchronized void accumulate(ClonalSequence data, PreClone preClone, boolean mapped) {
        if (geneAccumulator == null)
            throw new IllegalStateException("Gene information already aggregated");

        if (!mapped) { // Core sequence accumulation
            coreCount += preClone.getNumberOfReads();

            // TODO or core tag count ???
            tagAggregator.add(preClone.getFullTagCount());

            // Accumulate information about V-D-J alignments only for strictly clustered reads
            // (only for core clonotypes members)
            geneAccumulator.accumulate(preClone.getGeneScores());

            aggregator.aggregate(data.getConcatenated().getQuality());
        } else // Mapped sequence accumulation
            mappedCount += preClone.getNumberOfReads();
    }
}
