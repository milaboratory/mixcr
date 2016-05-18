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
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
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

    public TargetMerger(MergerParameters mergerParameters) {
        this.merger = new MismatchOnlyPairedReadMerger(mergerParameters);
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
            final VDJCHit[] leftHits = targetLeft.getAlignments().getHits(geneType);
            final VDJCHit[] rightHits = targetRight.getAlignments().getHits(geneType);
            GeneFeature alignedFeature = leftHits.length == 0 ? rightHits.length == 0 ? null : rightHits[0].getAlignedFeature() : leftHits[0].getAlignedFeature();

            Map<Allele, Alignment<NucleotideSequence>[]> map = new HashMap<>();
            for (VDJCHit l : leftHits) {
                final Allele allele = l.getAllele();
                map.put(allele, new Alignment[]{l.getAlignment(targetLeft.getTargetId()), null});
            }
            for (VDJCHit r : rightHits) {
                final Allele allele = r.getAllele();
                final Alignment<NucleotideSequence> alignment = r.getAlignment(targetRight.getTargetId());
                final Alignment<NucleotideSequence>[] als = map.get(allele);
                if (als == null)
                    map.put(allele, new Alignment[]{null, alignment});
                else
                    als[1] = alignment;
            }

            List<VDJCHit> resultingHits = new ArrayList<>();
            for (Map.Entry<Allele, Alignment<NucleotideSequence>[]> mE : map.entrySet()) {
                final Allele allele = mE.getKey();
                final BatchAlignerWithBaseParameters bp = ((KGeneAlignmentParameters) alignerParameters.getGeneAlignerParameters(geneType)).getParameters();
                Alignment<NucleotideSequence> mergedAl = merge(
                        bp.getScoring(), extractBandedWidth(bp),
                        mergedTarget.getSequence(), offset,
                        mE.getValue()[0], mE.getValue()[1]);
                resultingHits.add(new VDJCHit(allele, mergedAl, alignedFeature));
            }

            Collections.sort(resultingHits);

            result.put(geneType, resultingHits.toArray(new VDJCHit[resultingHits.size()]));
        }

        return new AlignedTarget(new VDJCAlignments(readId, result, mergedTarget), 0);
    }


    static int extractBandedWidth(BatchAlignerWithBaseParameters bp) {
        if (bp instanceof KAlignerParameters)
            return ((KAlignerParameters) bp).getMaxAdjacentIndels();
        else if (bp instanceof KAlignerParameters2)
            return ((KAlignerParameters2) bp).getMapperMaxClusterIndels();
        else throw new RuntimeException();
    }

    static Alignment<NucleotideSequence> merge(AlignmentScoring<NucleotideSequence> scoring,
                                               int bandedWidth,
                                               NucleotideSequence seq, int offset,
                                               Alignment<NucleotideSequence> left,
                                               Alignment<NucleotideSequence> right) {
        assert left != null || right != null;
        assert offset >= 0;
        assert left == null || right == null || left.getSequence1() == right.getSequence1();

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


        final Alignment<NucleotideSequence> al = BandedAligner.alignGlobal(scoring, left == null ? right.getSequence1() : left.getSequence1(),
                seq, seq1From, seq1To - seq1From, seq2From, seq2To - seq2From, bandedWidth);

        return al;
    }

    public TargetMergingResult merge(AlignedTarget target1, AlignedTarget target2) {
        return null;
    }

    public static class TargetMergingResult {
        public final AlignedTarget result;
        public final int score;

        public TargetMergingResult(AlignedTarget result, int score) {
            this.result = result;
            this.score = score;
        }
    }
}
