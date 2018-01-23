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
import com.milaboratory.core.merger.MergerParameters.IdentityType;
import com.milaboratory.core.merger.MismatchOnlyPairedReadMerger;
import com.milaboratory.core.merger.PairedReadMergingResult;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.SequencesUtils;
import com.milaboratory.mixcr.basictypes.SequenceHistory;
import com.milaboratory.mixcr.basictypes.SequenceHistory.OverlapType;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.mixcr.vdjaligners.KGeneAlignmentParameters;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCGene;
import io.repseq.core.VDJCGeneId;

import java.util.*;

public class TargetMerger {
    final MismatchOnlyPairedReadMerger merger;
    final IdentityType identityType;
    private volatile VDJCAlignerParameters alignerParameters;
    final float minimalAlignmentMergeIdentity;

    public TargetMerger(MergerParameters mergerParameters, float minimalAlignmentMergeIdentity) {
        this.merger = new MismatchOnlyPairedReadMerger(mergerParameters);
        this.identityType = mergerParameters.getIdentityType();
        this.minimalAlignmentMergeIdentity = minimalAlignmentMergeIdentity;
    }

    public void setAlignerParameters(VDJCAlignerParameters alignerParameters) {
        this.alignerParameters = alignerParameters;
    }

    @SuppressWarnings("unchecked")
    public AlignedTarget merge(AlignedTarget targetLeft, AlignedTarget targetRight, int offset, OverlapType overlapType, int nMismatches) {
        if (offset < 0)
            return merge(targetRight, targetLeft, -offset, overlapType, nMismatches);

        final NSequenceWithQuality mergedTarget = merger.overlap(targetLeft.getTarget(), targetRight.getTarget(), offset);

        EnumMap<GeneType, VDJCHit[]> result = new EnumMap<>(GeneType.class);

        for (GeneType geneType : GeneType.VJC_REFERENCE) {
            final BatchAlignerWithBaseParameters bp = ((KGeneAlignmentParameters) alignerParameters.getGeneAlignerParameters(geneType)).getParameters();
            final VDJCHit[] leftHits = targetLeft.getAlignments().getHits(geneType);
            final VDJCHit[] rightHits = targetRight.getAlignments().getHits(geneType);
            GeneFeature alignedFeature = leftHits.length == 0 ? rightHits.length == 0 ? null : rightHits[0].getAlignedFeature() : leftHits[0].getAlignedFeature();

            Map<VDJCGeneId, HitMappingRecord> map = extractHitsMapping(targetLeft, targetRight, geneType);
            ArrayList<VDJCHit> resultingHits = new ArrayList<>();
            for (Map.Entry<VDJCGeneId, HitMappingRecord> mE : map.entrySet()) {
                final VDJCGene gene = mE.getValue().gene;

                Alignment<NucleotideSequence> mergedAl = merge(
                        bp.getScoring(), extractBandedWidth(bp),
                        mergedTarget.getSequence(), offset,
                        mE.getValue().alignments[0], mE.getValue().alignments[1]);
                resultingHits.add(new VDJCHit(gene, mergedAl, alignedFeature));
            }

            Collections.sort(resultingHits);

            //final float relativeMinScore = extractRelativeMinScore(bp);
            //int threshold = (int) (resultingHits.size() > 0 ? resultingHits.get(0).getScore() * relativeMinScore : 0);
            //for (int i = resultingHits.size() - 1; i > 0; --i)
            //    if (resultingHits.get(i).getScore() < threshold)
            //        resultingHits.remove(i);

            result.put(geneType, resultingHits.toArray(new VDJCHit[resultingHits.size()]));
        }


        VDJCAlignments alignments = new VDJCAlignments(result,
                new NSequenceWithQuality[]{mergedTarget},
                new SequenceHistory[]{
                        new SequenceHistory.Merge(overlapType, targetLeft.getHistory(), targetRight.getHistory(), offset, nMismatches)
                },
                VDJCAlignments.mergeOriginalReads(
                        targetLeft.getAlignments(),
                        targetRight.getAlignments())
        );
        AlignedTarget resultTarget = new AlignedTarget(alignments, 0);
        for (BPoint bPoint : BPoint.values()) {
            int leftPoint = targetLeft.getBPoint(bPoint);
            int rightPoint = targetRight.getBPoint(bPoint);
            if (leftPoint != -1 && rightPoint != -1)
                throw new IllegalArgumentException("Same bPoint defined in both input targets.");
            else if (leftPoint != -1)
                resultTarget = resultTarget.setBPoint(bPoint, leftPoint);
            else if (rightPoint != -1)
                resultTarget = resultTarget.setBPoint(bPoint, offset + rightPoint);
        }

        return resultTarget;
    }

    static final class HitMappingRecord {
        final VDJCGene gene;
        final Alignment<NucleotideSequence>[] alignments;

        public HitMappingRecord(VDJCGene gene, Alignment<NucleotideSequence>[] alignments) {
            this.gene = gene;
            this.alignments = alignments;
        }
    }

    static boolean hasAlignments(AlignedTarget target, GeneType geneType) {
        for (VDJCHit l : target.getAlignments().getHits(geneType))
            if (l.getAlignment(target.getTargetId()) != null)
                return true;
        return false;
    }

    @SuppressWarnings("unchecked")
    static List<HitMappingRecord> extractSortedHits(AlignedTarget targetLeft, AlignedTarget targetRight, GeneType geneType) {
        // Fast calculation for targets from the same PE-read (or multi-read)
        if (targetLeft.getAlignments() == targetRight.getAlignments()) {
            VDJCHit[] hits = targetLeft.getAlignments().getHits(geneType);
            List<HitMappingRecord> mRecords = new ArrayList<>(hits.length);
            for (VDJCHit hit : hits)
                mRecords.add(new HitMappingRecord(hit.getGene(), new Alignment[]{
                        hit.getAlignment(targetLeft.getTargetId()), hit.getAlignment(targetRight.getTargetId())}));

            // Don't resort mRecords because "hits" were already sorted. Sorting may be different from
            // Collections.sort(mRecords, ...), if initial Alignments object contain more than two targets,
            // however soring in "hits" is considered here to be more accurate because it was supported by
            // other parts of the multi-read object
            return mRecords;
        }

        // Full recalculation for targets form two different Alignments objects
        Map<VDJCGeneId, HitMappingRecord> map = extractHitsMapping(targetLeft, targetRight, geneType);
        List<HitMappingRecord> mRecords = new ArrayList<>(map.values());
        Collections.sort(mRecords, new Comparator<HitMappingRecord>() {
            @Override
            public int compare(HitMappingRecord o1, HitMappingRecord o2) {
                return Integer.compare(sumScore(o2.alignments), sumScore(o1.alignments));
            }
        });
        return mRecords;
    }

    @SuppressWarnings("unchecked")
    static Map<VDJCGeneId, HitMappingRecord> extractHitsMapping(AlignedTarget targetLeft, AlignedTarget targetRight, GeneType geneType) {
        Map<VDJCGeneId, HitMappingRecord> map = new HashMap<>();
        for (VDJCHit l : targetLeft.getAlignments().getHits(geneType)) {
            final VDJCGene gene = l.getGene();
            final Alignment<NucleotideSequence> al = l.getAlignment(targetLeft.getTargetId());
            if (al != null)
                map.put(gene.getId(), new HitMappingRecord(gene, new Alignment[]{al, null}));
        }
        for (VDJCHit r : targetRight.getAlignments().getHits(geneType)) {
            final VDJCGene gene = r.getGene();
            final Alignment<NucleotideSequence> alignment = r.getAlignment(targetRight.getTargetId());
            if (alignment == null)
                continue;
            final HitMappingRecord als = map.get(gene.getId());
            if (als == null)
                map.put(gene.getId(), new HitMappingRecord(gene, new Alignment[]{null, alignment}));
            else {
                assert als.alignments[1] == null;
                als.alignments[1] = alignment;
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
            if (left.convertToSeq2Position(right.getSequence1Range().getFrom()) != right.getSequence2Range().getFrom() + offset) {
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

    public TargetMergingResult merge(AlignedTarget targetLeft, AlignedTarget targetRight) {
        return merge(targetLeft, targetRight, true);
    }

    /**
     * @param targetLeft       left sequence
     * @param targetRight      right sequence
     * @param trySequenceMerge whether to try merging using sequence overlap (if alignment overlap failed)
     */
    public TargetMergingResult merge(AlignedTarget targetLeft, AlignedTarget targetRight,
                                     boolean trySequenceMerge) {
        for (GeneType geneType : GeneType.VJC_REFERENCE) {
            if (!hasAlignments(targetLeft, geneType) || !hasAlignments(targetRight, geneType))
                continue;

            List<HitMappingRecord> als = extractSortedHits(targetLeft, targetRight, geneType);

            Alignment<NucleotideSequence>[] topHits = als.get(0).alignments;

            if (topHits[0] != null && topHits[1] != null) {
                final Alignment<NucleotideSequence> left = topHits[0];
                final Alignment<NucleotideSequence> right = topHits[1];

                final int from = Math.max(left.getSequence1Range().getFrom(), right.getSequence1Range().getFrom());
                final int to = Math.min(left.getSequence1Range().getTo(), right.getSequence1Range().getTo());

                if (to <= from)
                    continue;

                int delta = left.convertToSeq2Position(from) - right.convertToSeq2Position(from);
                if (delta != left.convertToSeq2Position(to) - right.convertToSeq2Position(to))
                    continue;

                int seq1Offset = delta > 0 ? delta : 0;
                int seq2Offset = delta > 0 ? 0 : -delta;
                int overlap = Math.min(targetLeft.getTarget().size() - seq1Offset, targetRight.getTarget().size() - seq2Offset);

                int mismatches = SequencesUtils.mismatchCount(
                        targetLeft.getTarget().getSequence(), seq1Offset,
                        targetRight.getTarget().getSequence(), seq2Offset,
                        overlap);

                double identity = MismatchOnlyPairedReadMerger.identity(identityType,
                        targetLeft.getTarget(), seq1Offset,
                        targetRight.getTarget(), seq2Offset,
                        overlap);

                if (identity < minimalAlignmentMergeIdentity)
                    return new TargetMergingResult(geneType);

                final AlignedTarget merge = merge(targetLeft, targetRight, delta, OverlapType.AlignmentOverlap, mismatches);
                return new TargetMergingResult(true, null, merge,
                        PairedReadMergingResult.MATCH_SCORE * (overlap - mismatches) +
                                PairedReadMergingResult.MISMATCH_SCORE * mismatches, overlap, mismatches, delta);
            }
        }

        if (!trySequenceMerge)
            return new TargetMergingResult();

        final PairedReadMergingResult merge = merger.merge(targetLeft.getTarget(), targetRight.getTarget());
        if (!merge.isSuccessful())
            return new TargetMergingResult();
        return new TargetMergingResult(false, null,
                merge(targetLeft, targetRight, merge.getOffset(), OverlapType.SequenceOverlap, merge.getErrors()),
                merge.score(), merge.getOverlap(), merge.getErrors(), merge.getOffset());
    }

    private static int sumScore(Alignment[] als) {
        int r = 0;
        for (Alignment al : als) {
            if (al != null)
                r += al.getScore();
        }
        return r;
    }

    public static final TargetMergingResult FAILED_RESULT = new TargetMergingResult();

    public final static class TargetMergingResult {
        private final boolean usingAlignments;
        private final GeneType failedMergedGeneType;

        private final AlignedTarget result;
        private final int score;
        private final int matched, mismatched;
        private final int offset;

//        public TargetMergingResult(AlignedTarget result, int score, boolean usingAlignments, int matched, int mismatched) {
//            this.result = result;
//            this.score = score;
//            this.usingAlignments = usingAlignments;
//            this.matched = matched;
//            this.mismatched = mismatched;
//        }

        private TargetMergingResult() {
            this(false, null, null, 0, 0, 0, 0);
        }

        private TargetMergingResult(GeneType failedGeneType) {
            this(true, failedGeneType, null, 0, 0, 0, 0);
        }

        private TargetMergingResult(boolean usingAlignments, GeneType failedMergedGeneType, AlignedTarget result, int score, int matched, int mismatched, int offset) {
            this.usingAlignments = usingAlignments;
            this.failedMergedGeneType = failedMergedGeneType;
            this.result = result;
            this.score = score;
            this.matched = matched;
            this.mismatched = mismatched;
            this.offset = offset;
        }

        public boolean isSuccessful() {
            return result != null;
        }

        public boolean isUsingAlignments() {
            return usingAlignments;
        }

        public boolean failedDueInconsistentAlignments() {
            return failedMergedGeneType != null;
        }

        public GeneType getFailedMergedGeneType() {
            return failedMergedGeneType;
        }

        private void checkSuccessful() {
            if (!isSuccessful())
                throw new IllegalStateException();
        }

        public AlignedTarget getResult() {
            checkSuccessful();
            return result;
        }

        public int getScore() {
            return score;
        }

        public int getMatched() {
            return matched;
        }

        public int getMismatched() {
            checkSuccessful();
            return mismatched;
        }

        public int getOffset() {
            return offset;
        }
    }
}
