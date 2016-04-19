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

import cc.redberry.pipe.CUtils;
import com.milaboratory.core.io.sequence.SingleRead;
import com.milaboratory.core.io.sequence.SingleReadImpl;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mitools.merger.MismatchOnlyPairedReadMerger;
import com.milaboratory.mitools.merger.QualityMergingAlgorithm;
import com.milaboratory.mixcr.basictypes.*;
import com.milaboratory.mixcr.reference.Allele;
import com.milaboratory.mixcr.reference.GeneType;
import com.milaboratory.mixcr.reference.ReferencePoint;
import gnu.trove.map.hash.TLongObjectHashMap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class PartialAlignmentsAssembler implements AutoCloseable {
    final TLongObjectHashMap<List<KMerInfo>> kToIndexLeft = new TLongObjectHashMap<>();
    final VDJCAlignmentsWriter writer;
    final int kValue;
    final int kOffset;
    final int minimalOverlap;
    final int maxScoreValue;
    final QualityMergingAlgorithm qualityMergingAlgorithm;
    public final AtomicLong leftParts = new AtomicLong(),
            noKMer = new AtomicLong(),
            wildCardsInKMer = new AtomicLong(),
            total = new AtomicLong(),
            overlapped = new AtomicLong(),
            complexOverlapped = new AtomicLong(),
            containsVJJunction = new AtomicLong();

    public PartialAlignmentsAssembler(PartialAlignmentsAssemblerParameters params, String output) {
        this.kValue = params.getKValue();
        this.kOffset = params.getKOffset();
        this.minimalOverlap = params.getMinimalOverlap();
        this.maxScoreValue = params.getMaxScoreValue();
        this.qualityMergingAlgorithm = params.getQualityMergingAlgorithm();

        try {
            this.writer = new VDJCAlignmentsWriter(output);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void buildLeftPartsIndex(VDJCAlignmentsReader reader) {
        writer.header(reader.getParameters(), reader.getUsedAlleles());
        out:
        for (VDJCAlignments alignment : CUtils.it(reader)) {
            for (int i = 0; i < alignment.numberOfTargets(); i++) {
                final VDJCHit vHit = alignment.getBestHit(GeneType.Variable),
                        jHit = alignment.getBestHit(GeneType.Joining);
                if (vHit != null && jHit != null && vHit.getAlignment(i) != null && jHit.getAlignment(i) != null)
                    continue out;
            }
            addLeftToIndex(alignment);
        }
    }

    public void searchOverlaps(VDJCAlignmentsReader reader) {
        PartialAlignmentsAssemblerAligner aligner = new PartialAlignmentsAssemblerAligner(reader.getParameters());
        for (Allele allele : reader.getUsedAlleles())
            aligner.addAllele(allele);

        out:
        for (VDJCAlignments alignment : CUtils.it(reader)) {
            total.incrementAndGet();

            for (int i = 0; i < alignment.numberOfTargets(); i++) {
                final VDJCHit vHit = alignment.getBestHit(GeneType.Variable),
                        jHit = alignment.getBestHit(GeneType.Joining);
                if (vHit != null && jHit != null && vHit.getAlignment(i) != null && jHit.getAlignment(i) != null) {
                    containsVJJunction.incrementAndGet();
                    writer.write(alignment);
                    continue out;
                }
            }

            VDJCMultiRead mRead = searchOverlaps(alignment);
            if (mRead == null)
                continue;

            overlapped.incrementAndGet();
            final VDJCAlignments al = aligner.process(mRead).alignment;
            String[] descriptions = new String[mRead.numberOfReads()];
            for (int i = 0; i < mRead.numberOfReads(); i++)
                descriptions[i] = mRead.getRead(i).getDescription();
            al.setDescriptions(descriptions);
            writer.write(al);
        }
    }

    @SuppressWarnings("unchecked")
    public VDJCMultiRead searchOverlaps(VDJCAlignments rightAl) {
        int rightTargetId = getRightPartitionedSequence(rightAl);
        if (rightTargetId == -1)
            return null;

        final VDJCPartitionedSequence rightTarget = rightAl.getPartitionedTarget(rightTargetId);
        NSequenceWithQuality rightSeqQ = rightTarget.getSequence();
        NucleotideSequence rightSeq = rightSeqQ.getSequence();

        int stop = rightTarget.getPartitioning().getPosition(ReferencePoint.JBeginTrimmed) - kOffset;
        assert stop != -1;

        int maxOverlap = -1;
        int maxDelta = -1;
        int maxOverlapIndexInList = -1;
        List<KMerInfo> maxOverlapList = null;
        for (int rFrom = 0; rFrom < stop && rFrom + kValue < rightSeqQ.size(); rFrom++) {
            long kMer = kMer(rightSeqQ.getSequence(), rFrom, kValue);
            List<KMerInfo> match = kToIndexLeft.get(kMer);
            if (match == null)
                continue;

            out:
            for (int i = 0; i < match.size(); i++) {
                final VDJCAlignments leftAl = match.get(i).getAlignments();
                final NucleotideSequence leftSeq = leftAl.getPartitionedTarget(getLeftPartitionedSequence(leftAl)).getSequence().getSequence();
                int lFrom = match.get(i).kMerPositionFrom;

                int delta, begin = delta = lFrom - rFrom;
                if (begin < 0)
                    begin = 0;
                int end = leftSeq.size();
                if (end - delta >= rightSeq.size())
                    end = rightSeq.size() + delta;

                for (int j = begin; j < end; j++)
                    if (leftSeq.codeAt(j) != rightSeq.codeAt(j - delta))
                        continue out;

                int overlap = end - begin;
                if (maxOverlap < overlap) {
                    maxOverlap = overlap;
                    maxOverlapList = match;
                    maxOverlapIndexInList = i;
                    maxDelta = delta;
                }
            }
        }

        if (maxOverlapList == null)
            return null;

        if (maxOverlap < minimalOverlap)
            return null;

        KMerInfo left = maxOverlapList.remove(maxOverlapIndexInList);
        VDJCAlignments leftAl = left.alignments;
        SingleRead[] rData;
        EnumSet<GeneType>[] expectedGenes;

        final long readId = rightAl.getReadId();
        switch ((leftAl.numberOfTargets() << 4) | rightAl.numberOfTargets()) {
            case 0x22:
                if (left.targetId == 1 && rightTargetId == 0) {
                    rData = new SingleRead[3];
                    expectedGenes = new EnumSet[3];
                    rData[0] = makeLeft(readId, leftAl, 0);
                    rData[1] = makeOverlapped(readId, leftAl, rightAl, 1, 0, maxDelta, maxOverlap);
                    rData[2] = makeRight(readId, rightAl, 1);

                    expectedGenes[0] = extractExpectedGenes(0, leftAl);
                    expectedGenes[1] = extractExpectedGenes(1, leftAl);
                    expectedGenes[1].addAll(extractExpectedGenes(0, rightAl));
                    expectedGenes[1].add(GeneType.Diversity);
                    expectedGenes[2] = extractExpectedGenes(1, rightAl);
                } else if (left.targetId == 0 && rightTargetId == 1) {
                    rData = new SingleRead[1];
                    expectedGenes = new EnumSet[1];

                    rData[0] = makeOverlapped(readId, leftAl, rightAl, 0, 1, maxDelta, maxOverlap);

                    expectedGenes[0] = extractExpectedGenes(0, leftAl);
                    expectedGenes[0].addAll(extractExpectedGenes(1, rightAl));
                    expectedGenes[0].add(GeneType.Diversity);

                    complexOverlapped.incrementAndGet();
                } else {
                    assert left.targetId == rightTargetId;
                    int targetId = left.targetId;
                    rData = new SingleRead[1];
                    expectedGenes = new EnumSet[1];

                    rData[0] = makeOverlapped(readId, leftAl, rightAl, targetId, targetId, maxDelta, maxOverlap);

                    expectedGenes[0] = extractExpectedGenes(targetId, leftAl);
                    expectedGenes[0].addAll(extractExpectedGenes(targetId, rightAl));
                    expectedGenes[0].add(GeneType.Diversity);

                    complexOverlapped.incrementAndGet();
                }
                break;
            case 0x12:
                if (rightTargetId == 0) {
                    rData = new SingleRead[2];
                    expectedGenes = new EnumSet[2];
                    rData[0] = makeOverlapped(readId, leftAl, rightAl, 0, 0, maxDelta, maxOverlap);
                    rData[1] = makeRight(readId, rightAl, 1);

                    expectedGenes[0] = extractExpectedGenes(0, leftAl);
                    expectedGenes[0].addAll(extractExpectedGenes(0, rightAl));
                    expectedGenes[0].add(GeneType.Diversity);
                    expectedGenes[1] = extractExpectedGenes(1, rightAl);
                } else {
                    rData = new SingleRead[1];
                    expectedGenes = new EnumSet[1];

                    rData[0] = makeOverlapped(readId, leftAl, rightAl, 0, 1, maxDelta, maxOverlap);

                    expectedGenes[0] = extractExpectedGenes(0, leftAl);
                    expectedGenes[0].addAll(extractExpectedGenes(1, rightAl));
                    expectedGenes[0].add(GeneType.Diversity);

                    complexOverlapped.incrementAndGet();
                }
                break;
            case 0x21:
                if (left.targetId == 1) {
                    rData = new SingleRead[2];
                    expectedGenes = new EnumSet[2];
                    rData[0] = makeLeft(readId, leftAl, 0);
                    rData[1] = makeOverlapped(readId, leftAl, rightAl, 1, 0, maxDelta, maxOverlap);

                    expectedGenes[0] = extractExpectedGenes(0, leftAl);
                    expectedGenes[1] = extractExpectedGenes(1, leftAl);
                    expectedGenes[1].addAll(extractExpectedGenes(0, rightAl));
                    expectedGenes[1].add(GeneType.Diversity);
                } else {
                    rData = new SingleRead[1];
                    expectedGenes = new EnumSet[1];

                    rData[0] = makeOverlapped(readId, leftAl, rightAl, 0, 0, maxDelta, maxOverlap);

                    expectedGenes[0] = extractExpectedGenes(0, leftAl);
                    expectedGenes[0].addAll(extractExpectedGenes(0, rightAl));
                    expectedGenes[0].add(GeneType.Diversity);

                    complexOverlapped.incrementAndGet();
                }
                break;
            case 0x11:
                rData = new SingleRead[1];
                expectedGenes = new EnumSet[1];

                rData[0] = makeOverlapped(readId, leftAl, rightAl, 0, 0, maxDelta, maxOverlap);

                expectedGenes[0] = extractExpectedGenes(0, leftAl);
                expectedGenes[0].addAll(extractExpectedGenes(0, rightAl));
                expectedGenes[0].add(GeneType.Diversity);
                break;
            default:
                throw new RuntimeException();
        }

        return new VDJCMultiRead(rData, expectedGenes);
    }

    private static SingleRead makeLeft(long readId, VDJCAlignments leftAl, int targetId) {
        return new SingleReadImpl(readId, leftAl.getTarget(targetId), "L" + leftAl.getReadId() + "." + targetId);
    }

    private static SingleRead makeRight(long readId, VDJCAlignments rightAl, int targetId) {
        return new SingleReadImpl(readId, rightAl.getTarget(targetId), "R" + rightAl.getReadId() + "." + targetId);
    }

    private SingleRead makeOverlapped(long readId, VDJCAlignments leftAl, VDJCAlignments rightAl,
                                      int leftTargetId, int rightTargetId, int maxDelta, int maxOverlap) {
        return new SingleReadImpl(readId,
                mergeOverlapped(leftAl.getTarget(leftTargetId), rightAl.getTarget(rightTargetId), maxDelta),
                "L" + leftAl.getReadId() + "." + leftTargetId + " x R" + rightAl.getReadId() + "." + rightTargetId + " overlap = " + maxOverlap);
    }

    private EnumSet<GeneType> extractExpectedGenes(int targetId, VDJCAlignments alignments) {
        EnumSet<GeneType> gts = EnumSet.noneOf(GeneType.class);
        for (GeneType geneType : GeneType.VDJC_REFERENCE) {
            boolean present = false;
            for (VDJCHit vdjcHit : alignments.getHits(geneType)) {
                if (vdjcHit.getAlignment(targetId) != null) {
                    present = true;
                    break;
                }
            }
            if (present)
                gts.add(geneType);
        }
        if (gts.contains(GeneType.Variable) && gts.contains(GeneType.Joining))
            gts.add(GeneType.Diversity);
        return gts;
    }

    private NSequenceWithQuality mergeOverlapped(NSequenceWithQuality left, NSequenceWithQuality right, int delta) {
        return MismatchOnlyPairedReadMerger.overlap(left, right, delta, maxScoreValue, qualityMergingAlgorithm);
    }


    private int getLeftPartitionedSequence(VDJCAlignments alignment) {
        for (int i = 0; i < alignment.numberOfTargets(); i++) {
            if (alignment.getBestHit(GeneType.Joining) != null && alignment.getBestHit(GeneType.Joining).getAlignment(i) != null)
                continue;
            VDJCPartitionedSequence ps = alignment.getPartitionedTarget(i);
            if (ps.getPartitioning().isAvailable(ReferencePoint.VEndTrimmed))
                return i;
        }
        return -1;
    }

    private int getRightPartitionedSequence(VDJCAlignments alignment) {
        for (int i = 0; i < alignment.numberOfTargets(); i++) {
            if (alignment.getBestHit(GeneType.Variable) != null && alignment.getBestHit(GeneType.Variable).getAlignment(i) != null)
                continue;
            VDJCPartitionedSequence ps = alignment.getPartitionedTarget(i);
            if (ps.getPartitioning().isAvailable(ReferencePoint.JBeginTrimmed))
                return i;
        }
        return -1;
    }

    private void addLeftToIndex(VDJCAlignments alignment) {
        int leftTargetId = getLeftPartitionedSequence(alignment);
        if (leftTargetId == -1)
            return;

        VDJCPartitionedSequence left = alignment.getPartitionedTarget(leftTargetId);
        NSequenceWithQuality seq = left.getSequence();

        int kFrom = left.getPartitioning().getPosition(ReferencePoint.VEndTrimmed) + kOffset;
        if (kFrom < 0 || kFrom + kValue >= seq.size()) {
            noKMer.incrementAndGet();
            return;
        }

        long kmer = kMer(seq.getSequence(), kFrom, kValue);
        if (kmer == -1) {
            wildCardsInKMer.incrementAndGet();
            return;
        }

        List<KMerInfo> ids = kToIndexLeft.get(kmer);
        if (ids == null)
            kToIndexLeft.put(kmer, ids = new ArrayList<>(1));
        ids.add(new KMerInfo(alignment, kFrom, leftTargetId));
        leftParts.incrementAndGet();
    }

    private static long kMer(NucleotideSequence seq, int from, int length) {
        long kmer = 0;
        for (int j = from; j < from + length; ++j) {
            byte c = seq.codeAt(j);
            if (NucleotideSequence.ALPHABET.isWildcard(c))
                return -1;
            kmer = (kmer << 2 | c);
        }
        return kmer;
    }

    private static final class KMerInfo {
        final VDJCAlignments alignments;
        final int kMerPositionFrom;
        final int targetId;

        public KMerInfo(VDJCAlignments alignments, int kMerPositionFrom, int targetId) {
            this.alignments = alignments;
            this.kMerPositionFrom = kMerPositionFrom;
            this.targetId = targetId;
        }

        public VDJCAlignments getAlignments() {
            return alignments;
        }
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}