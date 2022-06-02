package com.milaboratory.mixcr.assembler.preclone;

import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.mixcr.basictypes.GeneAndScore;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.mixcr.basictypes.tag.TagCount;
import com.milaboratory.mixcr.basictypes.tag.TagTuple;
import com.milaboratory.primitivio.annotations.Serializable;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;

import java.util.*;

@Serializable(by = IO.PreCloneSerializer.class)
public final class PreClone {
    /** Pre-clonotype id */
    public final long id;
    /** Core key of the alignments group */
    public final TagTuple coreKey;
    /** Tag counter aggregating information about alignments with clonal sequence */
    public final TagCount coreTagCount;
    /** Tag counter aggregating information across all alignments assigned to this pre-clone */
    public final TagCount fullTagCount;
    /** Assembled clonal sequence */
    public final NSequenceWithQuality[] clonalSequence;
    /** Aggregated V, J, C gene scoring and content information */
    public final Map<GeneType, List<GeneAndScore>> geneScores;

    public PreClone(long id, TagTuple coreKey, TagCount coreTagCount, TagCount fullTagCount,
                    NSequenceWithQuality[] clonalSequence,
                    Map<GeneType, List<GeneAndScore>> geneScores) {
        this.id = id;
        this.coreKey = coreKey;
        this.coreTagCount = coreTagCount;
        this.fullTagCount = fullTagCount;
        this.clonalSequence = clonalSequence;
        this.geneScores = geneScores;
    }

    public long getId() {
        return id;
    }

    public PreClone withId(long id) {
        return new PreClone(id, coreKey, coreTagCount, fullTagCount, clonalSequence, geneScores);
    }

    /** Converts alignment to a pre-clone, given the clonal gene features (features to align) */
    public static PreClone fromAlignment(long id, VDJCAlignments alignment, GeneFeature... geneFeatures) {
        NSequenceWithQuality[] clonalSequences = new NSequenceWithQuality[geneFeatures.length];

        for (int i = 0; i < geneFeatures.length; i++)
            clonalSequences[i] = Objects.requireNonNull(alignment.getFeature(geneFeatures[i]));

        Map<GeneType, List<GeneAndScore>> geneScores = new EnumMap<>(GeneType.class);
        EnumMap<GeneType, VDJCHit[]> hitsMap = alignment.getHitsMap();
        for (GeneType gt : GeneType.VDJC_REFERENCE) {
            VDJCHit[] hits = hitsMap.get(gt);
            List<GeneAndScore> gss = new ArrayList<>(hits.length);
            for (int i = 0; i < hits.length; i++) {
                VDJCHit hit = hits[i];
                gss.add(new GeneAndScore(hit.getGene().getId(), hit.getScore()));
            }
            geneScores.put(gt, gss);
        }
        return new PreClone(id, TagTuple.NO_TAGS, alignment.getTagCount(), alignment.getTagCount(),
                clonalSequences, geneScores);
    }
}
