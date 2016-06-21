/*
 * Copyright (c) 2014-2016, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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
package com.milaboratory.mixcr.partialassembler;

import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.alignment.AlignmentScoring;
import com.milaboratory.core.alignment.BandedAligner;
import com.milaboratory.core.alignment.batch.BatchAlignerWithBaseParameters;
import com.milaboratory.core.alignment.kaligner1.KAlignerParameters;
import com.milaboratory.core.alignment.kaligner2.KAlignerParameters2;
import com.milaboratory.core.merger.MergerParameters;
import com.milaboratory.core.merger.MismatchOnlyPairedReadMerger;
import com.milaboratory.core.merger.PairedReadMergingResult;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.SequencesUtils;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.mixcr.reference.Allele;
import com.milaboratory.mixcr.reference.GeneFeature;
import com.milaboratory.mixcr.reference.GeneType;
import com.milaboratory.mixcr.vdjaligners.KGeneAlignmentParameters;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;

import java.util.*;

public class TargetMerger {
    final MismatchOnlyPairedReadMerger merger;
    private volatile VDJCAlignerParameters alignerParameters;
    final double minimalIdentity;

    public TargetMerger(MergerParameters mergerParameters) {
        this.merger = new MismatchOnlyPairedReadMerger(mergerParameters);
        this.minimalIdentity = mergerParameters.getMinimalIdentity();
    }

    public void setAlignerParameters(VDJCAlignerParameters alignerParameters) {
        this.alignerParameters = alignerParameters;
    }

    @SuppressWarnings("unchecked")
    public AlignedTarget merge(long readId, AlignedTarget targetLeft, AlignedTarget targetRight, int offset) {
        if (offset < 0)
            return merge(readId, targetRight, targetLeft, -offset);

        final NSequenceWithQuality mergedTarget = merger.overlap(targetLeft.getTarget(), targetRight.getTarget(), offset);

        EnumMap<GeneType, VDJCHit[]> result = new EnumMap<>(GeneType.class);

        for (GeneType geneType : GeneType.VJC_REFERENCE) {
            final BatchAlignerWithBaseParameters bp = ((KGeneAlignmentParameters) alignerParameters.getGeneAlignerParameters(geneType)).getParameters();
            final VDJCHit[] leftHits = targetLeft.getAlignments().getHits(geneType);
            final VDJCHit[] rightHits = targetRight.getAlignments().getHits(geneType);
            GeneFeature alignedFeature = leftHits.length == 0 ? rightHits.length == 0 ? null : rightHits[0].getAlignedFeature() : leftHits[0].getAlignedFeature();

            Map<Allele, Alignment<NucleotideSequence>[]> map = extractHitsMapping(targetLeft, targetRight, geneType);
            ArrayList<VDJCHit> resultingHits = new ArrayList<>();
            for (Map.Entry<Allele, Alignment<NucleotideSequence>[]> mE : map.entrySet()) {
                final Allele allele = mE.getKey();

                Alignment<NucleotideSequence> mergedAl = merge(
                        bp.getScoring(), extractBandedWidth(bp),
                        mergedTarget.getSequence(), offset,
                        mE.getValue()[0], mE.getValue()[1]);
                resultingHits.add(new VDJCHit(allele, mergedAl, alignedFeature));
            }

            Collections.sort(resultingHits);
            final float relativeMinScore = extractRelativeMinScore(bp);

            int threshold = (int) (resultingHits.size() > 0 ? resultingHits.get(0).getScore() * relativeMinScore : 0);
            for (int i = resultingHits.size() - 1; i > 0; --i)
                if (resultingHits.get(i).getScore() < threshold)
                    resultingHits.remove(i);

            result.put(geneType, resultingHits.toArray(new VDJCHit[resultingHits.size()]));
        }

        return new AlignedTarget(new VDJCAlignments(readId, result, mergedTarget), 0);
    }

    @SuppressWarnings("unchecked")
    static Map<Allele, Alignment<NucleotideSequence>[]> extractHitsMapping(AlignedTarget targetLeft, AlignedTarget targetRight, GeneType geneType) {
        Map<Allele, Alignment<NucleotideSequence>[]> map = new HashMap<>();
        for (VDJCHit l : targetLeft.getAlignments().getHits(geneType)) {
            final Allele allele = l.getAllele();
            final Alignment<NucleotideSequence> al = l.getAlignment(targetLeft.getTargetId());
            if (al != null)
                map.put(allele, new Alignment[]{al, null});
        }
        for (VDJCHit r : targetRight.getAlignments().getHits(geneType)) {
            final Allele allele = r.getAllele();
            final Alignment<NucleotideSequence> alignment = r.getAlignment(targetRight.getTargetId());
            if (alignment == null)
                continue;
            final Alignment<NucleotideSequence>[] als = map.get(allele);
            if (als == null)
                map.put(allele, new Alignment[]{null, alignment});
            else {
                assert als[1] == null;
                als[1] = alignment;
            }
        }

        return map;
    }

    static int extractBandedWidth(BatchAlignerWithBaseParameters bp) {
        if (bp instanceof KAlignerParameters)
            return ((KAlignerParameters) bp).getMaxAdjacentIndels();
        else if (bp instanceof KAlignerParameters2)
            return ((KAlignerParameters2) bp).getMapperMaxClusterIndels();
        else throw new RuntimeException();
    }

    static float extractRelativeMinScore(BatchAlignerWithBaseParameters bp) {
        if (bp instanceof KAlignerParameters)
            return ((KAlignerParameters) bp).getRelativeMinScore();
        else if (bp instanceof KAlignerParameters2)
            return ((KAlignerParameters2) bp).getRelativeMinScore();
        else throw new RuntimeException();
    }

    static Alignment<NucleotideSequence> merge(AlignmentScoring<NucleotideSequence> scoring,
                                               int bandedWidth,
                                               NucleotideSequence seq, int offset,
                                               Alignment<NucleotideSequence> left,
                                               Alignment<NucleotideSequence> right) {
        assert left != null || right != null;
        assert offset >= 0;
        assert left == null || right == null || left.getSequence1().equals(right.getSequence1());

        int seq1From = -1, seq2From = -1, seq1To = -1, seq2To = -1;

        if (left != null && right != null) {
            if (left.convertPosition(right.getSequence1Range().getFrom()) != right.getSequence2Range().getFrom() + offset) {
                if (left.getScore() > right.getScore())
                    right = null;
                else
                    left = null;
            } else {
                if (left.getSequence1Range().getFrom() < right.getSequence1Range().getFrom()) {
                    seq1From = left.getSequence1Range().getFrom();
                    seq2From = left.getSequence2Range().getFrom();
                } else {
                    seq1From = right.getSequence1Range().getFrom();
                    seq2From = right.getSequence2Range().getFrom() + offset;
                }

                if (left.getSequence1Range().getTo() > right.getSequence1Range().getTo()) {
                    seq1To = left.getSequence1Range().getTo();
                    seq2To = left.getSequence2Range().getTo();
                } else {
                    seq1To = right.getSequence1Range().getTo();
                    seq2To = right.getSequence2Range().getTo() + offset;
                }
            }
        }

        if (left == null) {
            seq1From = right.getSequence1Range().getFrom();
            seq1To = right.getSequence1Range().getTo();

            seq2From = right.getSequence2Range().getFrom() + offset;
            seq2To = right.getSequence2Range().getTo() + offset;
        } else if (right == null) {
            seq1From = left.getSequence1Range().getFrom();
            seq1To = left.getSequence1Range().getTo();

            seq2From = left.getSequence2Range().getFrom();
            seq2To = left.getSequence2Range().getTo();
        }

        return BandedAligner.alignGlobal(scoring, left == null ? right.getSequence1() : left.getSequence1(),
                seq, seq1From, seq1To - seq1From, seq2From, seq2To - seq2From, bandedWidth);
    }

    public TargetMergingResult merge(long readId, AlignedTarget targetLeft, AlignedTarget targetRight) {
        for (GeneType geneType : GeneType.VJC_REFERENCE) {

            Map<Allele, Alignment<NucleotideSequence>[]> map = extractHitsMapping(targetLeft, targetRight, geneType);
            if (map.isEmpty())
                continue;
            List<Alignment<NucleotideSequence>[]> als = new ArrayList<>(map.values());

            Collections.sort(als, new Comparator<Alignment<NucleotideSequence>[]>() {
                @Override
                public int compare(Alignment<NucleotideSequence>[] o1, Alignment<NucleotideSequence>[] o2) {
                    return Integer.compare(sumScore(o2), sumScore(o1));
                }
            });

            Alignment<NucleotideSequence>[] topHits = als.get(0);

            if (topHits[0] != null && topHits[1] != null) {
                final Alignment<NucleotideSequence> left = topHits[0];
                final Alignment<NucleotideSequence> right = topHits[1];

                final int from = Math.max(left.getSequence1Range().getFrom(), right.getSequence1Range().getFrom());
                final int to = Math.min(left.getSequence1Range().getTo(), right.getSequence1Range().getTo());

                if (to <= from)
                    continue;

                int delta = left.convertPosition(from) - right.convertPosition(from);
                if (delta != left.convertPosition(to) - right.convertPosition(to))
                    continue;


                int seq1Offset = delta > 0 ? delta : 0;
                int seq2Offset = delta > 0 ? 0 : -delta;
                int overlap = Math.min(targetLeft.getTarget().size() - seq1Offset, targetRight.getTarget().size() - seq2Offset);

                int mismatches = SequencesUtils.mismatchCount(
                        targetLeft.getTarget().getSequence(), seq1Offset,
                        targetRight.getTarget().getSequence(), seq2Offset,
                        overlap);

                if (1.0 - 1.0 * mismatches / overlap < minimalIdentity)
                    continue;

                final AlignedTarget merge = merge(readId, targetLeft, targetRight, delta);
                return new TargetMergingResult(merge,
                        PairedReadMergingResult.MATCH_SCORE * (overlap - mismatches) +
                                PairedReadMergingResult.MISMATCH_SCORE * mismatches, true, overlap, mismatches);
            }
        }

        final PairedReadMergingResult merge = merger.merge(targetLeft.getTarget(), targetRight.getTarget());
        if (!merge.isSuccessful())
            return null;
        return new TargetMergingResult(merge(readId, targetLeft, targetRight, merge.getOffset()), merge.score(), false, merge.getOverlap(), merge.getErrors());
    }

    private static int sumScore(Alignment[] als) {
        int r = 0;
        for (Alignment al : als) {
            if (al != null)
                r += al.getScore();
        }
        return r;
    }

    public static class TargetMergingResult {
        public final AlignedTarget result;
        public final int score;
        public final boolean usingAlignments;
        public final int matched, mismatched;

        public TargetMergingResult(AlignedTarget result, int score, boolean usingAlignments, int matched, int mismatched) {
            this.result = result;
            this.score = score;
            this.usingAlignments = usingAlignments;
            this.matched = matched;
            this.mismatched = mismatched;
        }
    }
}
