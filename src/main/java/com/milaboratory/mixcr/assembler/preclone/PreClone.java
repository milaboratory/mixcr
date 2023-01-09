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
package com.milaboratory.mixcr.assembler.preclone;

import com.milaboratory.core.Range;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.mixcr.basictypes.GeneAndScore;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.mixcr.basictypes.tag.TagCount;
import com.milaboratory.mixcr.basictypes.tag.TagTuple;
import com.milaboratory.util.CollectionUtils;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCGeneId;

import java.util.*;

public interface PreClone {
    /** Pre-clonotype id */
    long getIndex();

    /** Core key of the alignments group */
    TagTuple getCoreKey();

    /** Tag counter aggregating information about alignments with clonal sequence */
    TagCount getCoreTagCount();

    /** Tag counter aggregating information across all alignments assigned to this pre-clone */
    TagCount getFullTagCount();

    /** Assembled clonal sequence */
    NSequenceWithQuality[] getClonalSequence();

    /** Aggregated V, J, C gene scoring and content information */
    Map<GeneType, List<GeneAndScore>> getGeneScores();

    /** Returns number of reads the pre-clone aggregates */
    int getNumberOfReads();

    default List<GeneAndScore> getGeneScores(GeneType geneType) {
        return getGeneScores().get(geneType);
    }

    default GeneAndScore getBestHit(GeneType geneType) {
        List<GeneAndScore> gss = getGeneScores().get(geneType);
        return gss == null || gss.isEmpty()
                ? null
                : gss.get(0);
    }

    default VDJCGeneId getBestGene(GeneType geneType) {
        GeneAndScore gs = getBestHit(geneType);
        return gs == null ? null : gs.geneId;
    }

    /** Provides limited access to the sequence partitioning for clonal sequences */
    Range getRange(int csIdx, GeneFeature feature);

    /** Converts alignment to a pre-clone, given the clonal gene features (assemblingFeatures) */
    static PreClone fromAlignment(long index, VDJCAlignments alignment, GeneFeature... geneFeatures) {
        NSequenceWithQuality[] clonalSequences = new NSequenceWithQuality[geneFeatures.length];
        for (int i = 0; i < geneFeatures.length; i++)
            clonalSequences[i] = Objects.requireNonNull(alignment.getFeature(geneFeatures[i]));
        Map<GeneType, List<GeneAndScore>> geneScores = new EnumMap<>(GeneType.class);
        EnumMap<GeneType, VDJCHit[]> hitsMap = alignment.getHitsMap();
        for (GeneType gt : GeneType.VDJC_REFERENCE) {
            VDJCHit[] hits = hitsMap.get(gt);
            if (hits == null)
                continue;
            List<GeneAndScore> gss = new ArrayList<>(hits.length);
            for (VDJCHit hit : hits)
                gss.add(new GeneAndScore(hit.getGene().getId(), hit.getScore()));
            assert CollectionUtils.isSorted(gss);
            geneScores.put(gt, gss);
        }
        return new PreCloneAbstract(index, TagTuple.NO_TAGS,
                alignment.getTagCount().ensureIsKey(), alignment.getTagCount().ensureIsKey(),
                clonalSequences, geneScores) {
            @Override
            public Range getRange(int csIdx, GeneFeature feature) {
                return alignment.getRelativeRange(geneFeatures[csIdx], feature);
            }

            @Override
            public int getNumberOfReads() {
                return alignment.getNumberOfReads();
            }
        };
    }
}
