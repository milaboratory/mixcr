package com.milaboratory.mixcr.assembler;

import com.milaboratory.mixcr.basictypes.GeneAndScore;
import com.milaboratory.mixcr.basictypes.HasRelativeMinScore;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import gnu.trove.iterator.TObjectFloatIterator;
import gnu.trove.map.hash.TObjectFloatHashMap;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCGeneId;

import java.util.*;

public final class VDJCGeneAccumulator {
    private final int[] observations = new int[GeneType.values().length];
    private final EnumMap<GeneType, TObjectFloatHashMap<VDJCGeneId>> geneScores = new EnumMap<>(GeneType.class);

    public synchronized void accumulate(VDJCAlignments alignment) {
        // Accumulate information about all genes
        for (GeneType geneType : GeneType.VJC_REFERENCE) {
            TObjectFloatHashMap<VDJCGeneId> geneScores = this.geneScores.get(geneType);
            VDJCHit[] hits = alignment.getHits(geneType);
            if (hits.length == 0)
                continue;
            observations[geneType.ordinal()]++;
            if (geneScores == null)
                this.geneScores.put(geneType, geneScores = new TObjectFloatHashMap<>());
            for (VDJCHit hit : hits) {
                // Calculating sum of natural logarithms of scores
                geneScores.adjustOrPutValue(hit.getGene().getId(), hit.getScore(), hit.getScore());
            }
        }
    }

    public synchronized void accumulate(EnumMap<GeneType, GeneAndScore[]> genesAndScores) {
        // Accumulate information about all genes
        for (GeneType geneType : GeneType.VJC_REFERENCE) {
            TObjectFloatHashMap<VDJCGeneId> geneScores = this.geneScores.get(geneType);
            GeneAndScore[] data = genesAndScores.get(geneType);
            if (data == null || data.length == 0)
                continue;
            observations[geneType.ordinal()]++;
            if (geneScores == null)
                this.geneScores.put(geneType, geneScores = new TObjectFloatHashMap<>());
            for (GeneAndScore gs : data) {
                // Calculating sum of natural logarithms of scores
                geneScores.adjustOrPutValue(gs.geneId, gs.score, gs.score);
            }
        }
    }

    public boolean hasInfoFor(GeneType geneType) {
        TObjectFloatHashMap<VDJCGeneId> scores = geneScores.get(geneType);
        return scores != null && !scores.isEmpty();
    }

    public boolean contains(GeneType geneType, VDJCGeneId gene) {
        TObjectFloatHashMap<VDJCGeneId> scores = geneScores.get(geneType);
        return scores != null && scores.contains(gene);
    }

    public GeneAndScore getBestGene(GeneType geneType) {
        TObjectFloatHashMap<VDJCGeneId> scores = geneScores.get(geneType);
        if (scores == null)
            return null;
        float maxScore = 0;
        VDJCGeneId maxAllele = null;
        TObjectFloatIterator<VDJCGeneId> iterator = scores.iterator();
        while (iterator.hasNext()) {
            iterator.advance();
            if (maxAllele == null || maxScore < iterator.value()) {
                maxAllele = iterator.key();
                maxScore = iterator.value();
            }
        }
        return new GeneAndScore(maxAllele, maxScore);
    }

    public Map<GeneType, List<GeneAndScore>> aggregateInformation(HasRelativeMinScore hasRelativeMinScore) {
        EnumMap<GeneType, List<GeneAndScore>> result = new EnumMap<>(GeneType.class);
        for (GeneType geneType : GeneType.VJC_REFERENCE) {
            float relativeMinScore = hasRelativeMinScore.getRelativeMinScore(geneType);
            if (Float.isNaN(relativeMinScore))
                continue;

            TObjectFloatHashMap<VDJCGeneId> accumulatorGeneIds = geneScores.get(geneType);
            if (accumulatorGeneIds == null)
                continue;

            TObjectFloatIterator<VDJCGeneId> iterator = accumulatorGeneIds.iterator();
            float maxScore = 0;
            while (iterator.hasNext()) {
                iterator.advance();
                float value = iterator.value();
                if (value > maxScore)
                    maxScore = value;
            }

            maxScore = maxScore * relativeMinScore;

            List<GeneAndScore> geneList = new ArrayList<>();
            iterator = accumulatorGeneIds.iterator();
            while (iterator.hasNext()) {
                iterator.advance();
                if (iterator.value() > maxScore)
                    geneList.add(new GeneAndScore(iterator.key(),
                            Math.round(iterator.value() * 10f / observations[geneType.ordinal()]) / 10f));
            }
            Collections.sort(geneList);
            result.put(geneType, geneList);
        }

        return result;
    }
}
