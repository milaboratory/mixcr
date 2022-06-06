package com.milaboratory.mixcr.assembler.preclone;

import com.milaboratory.core.Range;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.mixcr.basictypes.GeneAndScore;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.mixcr.basictypes.tag.TagCount;
import com.milaboratory.mixcr.basictypes.tag.TagTuple;
import com.milaboratory.primitivio.annotations.Serializable;
import com.milaboratory.util.CollectionUtils;
import io.repseq.core.ExtendedReferencePoints;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCGeneId;

import java.util.*;

@Serializable(by = IO.PreCloneSerializer.class)
public final class PreClone {
    /** Pre-clonotype id */
    final long index;
    /** Core key of the alignments group */
    final TagTuple coreKey;
    /** Tag counter aggregating information about alignments with clonal sequence */
    final TagCount coreTagCount;
    /** Tag counter aggregating information across all alignments assigned to this pre-clone */
    final TagCount fullTagCount;
    /** Assembled clonal sequence */
    final NSequenceWithQuality[] clonalSequence;
    /** Aggregated V, J, C gene scoring and content information */

    final Map<GeneType, List<GeneAndScore>> geneScores;
    /** Reference point for each of the clonal sequences */
    final ExtendedReferencePoints[] referencePoints;

    public PreClone(long index, TagTuple coreKey, TagCount coreTagCount, TagCount fullTagCount,
                    NSequenceWithQuality[] clonalSequence,
                    Map<GeneType, List<GeneAndScore>> geneScores,
                    ExtendedReferencePoints[] referencePoints) {
        this.index = index;
        this.coreKey = Objects.requireNonNull(coreKey);
        this.coreTagCount = Objects.requireNonNull(coreTagCount);
        this.fullTagCount = Objects.requireNonNull(fullTagCount);
        this.clonalSequence = Objects.requireNonNull(clonalSequence);
        this.geneScores = Objects.requireNonNull(geneScores);
        this.referencePoints = Objects.requireNonNull(referencePoints);
    }

    public long getIndex() {
        return index;
    }

    public TagTuple getCoreKey() {
        return coreKey;
    }

    public TagCount getCoreTagCount() {
        return coreTagCount;
    }

    public TagCount getFullTagCount() {
        return fullTagCount;
    }

    public NSequenceWithQuality[] getClonalSequence() {
        return clonalSequence;
    }

    public Map<GeneType, List<GeneAndScore>> getGeneScores() {
        return geneScores;
    }

    public List<GeneAndScore> getGeneScores(GeneType geneType) {
        return geneScores.get(geneType);
    }

    public GeneAndScore getBestHit(GeneType geneType) {
        List<GeneAndScore> gss = geneScores.get(geneType);
        return gss == null || gss.isEmpty()
                ? null
                : gss.get(0);
    }

    public VDJCGeneId getBestGene(GeneType geneType) {
        GeneAndScore gs = getBestHit(geneType);
        return gs == null ? null : gs.geneId;
    }

    public Range getRange(int csIdx, GeneFeature feature) {
        return referencePoints[csIdx].getRange(feature);
    }

    public PreClone withIndex(long index) {
        return new PreClone(index, coreKey, coreTagCount, fullTagCount, clonalSequence, geneScores, referencePoints);
    }

    /** Converts alignment to a pre-clone, given the clonal gene features (assemblingFeatures) */
    public static PreClone fromAlignment(long index, VDJCAlignments alignment, GeneFeature... geneFeatures) {
        NSequenceWithQuality[] clonalSequences = new NSequenceWithQuality[geneFeatures.length];

        for (int i = 0; i < geneFeatures.length; i++)
            clonalSequences[i] = Objects.requireNonNull(alignment.getFeature(geneFeatures[i]));

        Map<GeneType, List<GeneAndScore>> geneScores = new EnumMap<>(GeneType.class);
        EnumMap<GeneType, VDJCHit[]> hitsMap = alignment.getHitsMap();
        for (GeneType gt : GeneType.VDJC_REFERENCE) {
            VDJCHit[] hits = hitsMap.get(gt);
            List<GeneAndScore> gss = new ArrayList<>(hits.length);
            for (VDJCHit hit : hits)
                gss.add(new GeneAndScore(hit.getGene().getId(), hit.getScore()));
            assert CollectionUtils.isSorted(gss);
            geneScores.put(gt, gss);
        }
        return new PreClone(index, TagTuple.NO_TAGS, alignment.getTagCount(), alignment.getTagCount(),
                clonalSequences, geneScores, null);
    }
}
