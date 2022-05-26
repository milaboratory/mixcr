package com.milaboratory.mixcr.assembler;

import com.milaboratory.mixcr.basictypes.HasRelativeMinScore;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import gnu.trove.iterator.TObjectFloatIterator;
import gnu.trove.map.hash.TObjectFloatHashMap;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCGeneId;

import java.util.EnumMap;

public final class VDJCGeneScoreAccumulator {
    int observations = 0;
    final EnumMap<GeneType, TObjectFloatHashMap<VDJCGeneId>> geneScores = new EnumMap<>(GeneType.class);

    public synchronized void accumulate(VDJCAlignments alignment) {
        if (observations == -1)
            throw new IllegalStateException("Already aggregated");

        // Accumulate information about all genes
        for (GeneType geneType : GeneType.VJC_REFERENCE) {
            TObjectFloatHashMap<VDJCGeneId> geneScores = this.geneScores.get(geneType);
            VDJCHit[] hits = alignment.getHits(geneType);
            if (hits.length == 0)
                continue;
            if (geneScores == null)
                this.geneScores.put(geneType, geneScores = new TObjectFloatHashMap<>());
            for (VDJCHit hit : hits) {
                // Calculating sum of natural logarithms of scores
                geneScores.adjustOrPutValue(hit.getGene().getId(), hit.getScore(), hit.getScore());
            }
        }
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

    public void aggregateInformation(HasRelativeMinScore hasRelativeMinScore) {
        if (observations == -1)
            throw new IllegalStateException("Already aggregated");

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
            iterator = accumulatorGeneIds.iterator();
            while (iterator.hasNext()) {
                iterator.advance();
                if (maxScore > iterator.value())
                    iterator.remove();
                else
                    iterator.setValue(Math.round(iterator.value() * 10f / observations) / 10f);
            }
        }

        // To prevent double aggregation
        observations = -1;
    }

    public static final class GeneAndScore {
        public final VDJCGeneId geneId;
        public final float score;

        public GeneAndScore(VDJCGeneId geneId, float score) {
            this.geneId = geneId;
            this.score = score;
        }
    }
}
