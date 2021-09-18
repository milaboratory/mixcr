/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 * 
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 * 
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */
package com.milaboratory.mixcr.vdjaligners;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.milaboratory.core.alignment.Aligner;
import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.alignment.AlignmentScoring;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.assembler.DClonalAlignerParameters;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import io.repseq.core.Chains;
import io.repseq.core.GeneFeature;
import io.repseq.core.VDJCGene;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

public final class SingleDAligner {
    private final AlignmentScoring<NucleotideSequence> scoring;
    private final float absoluteMinScore, relativeMinScore;
    private final int maxHits;
    private final List<SequenceWithChains> sequences = new ArrayList<>();
    private final List<VDJCGene> genes;
    private final GeneFeature featureToAlign;

    private final LoadingCache<NucleotideSequence, List<PreVDJCHit>> resultsCache =
            CacheBuilder.newBuilder()
                    .maximumSize(5000)
                    .recordStats()
                    .build(
                            new CacheLoader<NucleotideSequence, List<PreVDJCHit>>() {
                                public List<PreVDJCHit> load(NucleotideSequence key) {
                                    return _align(key);
                                }
                            }
                    );

    public SingleDAligner(DAlignerParameters parameters,
                          List<VDJCGene> genes) {
        this(parameters, parameters.getGeneFeatureToAlign(), genes);
    }

    public SingleDAligner(DClonalAlignerParameters<?> parameters, GeneFeature geneFeature,
                          List<VDJCGene> genes) {
        this.scoring = parameters.getScoring();
        this.absoluteMinScore = parameters.getAbsoluteMinScore();
        this.relativeMinScore = parameters.getRelativeMinScore();
        this.maxHits = parameters.getMaxHits();
        this.featureToAlign = geneFeature;
        for (VDJCGene gene : genes)
            sequences.add(new SequenceWithChains(gene, featureToAlign));
        this.genes = new ArrayList<>(genes);
    }

    List<PreVDJCHit> align0(NucleotideSequence sequence, Chains chains, int from, int to) {
        if (from > to)
            throw new IllegalArgumentException("from > to. from = " + from + " to = " + to + " .");

        NucleotideSequence key = sequence.getRange(from, to);

        try {
            List<PreVDJCHit> cachedResult = resultsCache.get(key);
            List<PreVDJCHit> result = new ArrayList<>(cachedResult.size());

            PreVDJCHit h;
            for (PreVDJCHit hit : cachedResult) {
                //filter non-possible chains
                if (!chains.intersects(sequences.get(hit.id).chains))
                    continue;

                result.add(h = convert(hit, from));

                assert sequence.getRange(h.alignment.getSequence2Range()).equals(
                        h.alignment
                                .getRelativeMutations()
                                .mutate(sequences.get(h.id).sequence
                                        .getRange(h.alignment.getSequence1Range())));
            }

            cutToScore(result);
            return result;
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public VDJCHit[] align(NucleotideSequence sequence, Chains chains, int from, int to,
                           int targetIndex, int numberOfTargets) {
        List<PreVDJCHit> preHits = align0(sequence, chains, from, to);
        return PreVDJCHit.convert(genes, featureToAlign, preHits,
                targetIndex, numberOfTargets);
    }

    private PreVDJCHit convert(PreVDJCHit hit, int from) {
        Alignment<NucleotideSequence> alignment = hit.alignment;
        return new PreVDJCHit(hit.id, new Alignment<>(alignment.getSequence1(),
                alignment.getAbsoluteMutations(),
                alignment.getSequence1Range(),
                alignment.getSequence2Range().move(from),
                alignment.getScore()));
    }

    private List<PreVDJCHit> _align(NucleotideSequence sequence) {
        if (sequence.size() == 0)
            return Collections.EMPTY_LIST;

        List<PreVDJCHit> result = new ArrayList<>();
        Alignment<NucleotideSequence> alignment;
        for (int i = 0; i < sequences.size(); ++i) {
            alignment = Aligner.alignLocal(scoring, sequences.get(i).sequence, sequence);

            if (alignment == null || alignment.getScore() < absoluteMinScore)
                continue;

            result.add(new PreVDJCHit(i, alignment));
        }

        Collections.sort(result, PreVDJCHit.SCORE_COMPARATOR);
        return result;
    }

    private void cutToScore(List<PreVDJCHit> result) {
        if (result.isEmpty())
            return;
        float threshold = Math.max(absoluteMinScore, result.get(0).alignment.getScore() * relativeMinScore);
        for (int i = result.size() - 1; i >= 0; --i)
            if (result.get(i).alignment.getScore() < threshold
                    || i >= maxHits)
                result.remove(i);
    }

    private static final class SequenceWithChains {
        private final NucleotideSequence sequence;
        private final Chains chains;

        public SequenceWithChains(VDJCGene gene, GeneFeature featureToAlign) {
            this.sequence = gene.getFeature(featureToAlign);
            this.chains = gene.getChains();
        }
    }
}
