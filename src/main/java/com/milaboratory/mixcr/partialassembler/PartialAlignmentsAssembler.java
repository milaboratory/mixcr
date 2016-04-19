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
import gnu.trove.list.array.TLongArrayList;
import gnu.trove.map.hash.TLongObjectHashMap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class PartialAlignmentsAssembler implements AutoCloseable {
    volatile long[] index;
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
        TLongArrayList index = new TLongArrayList();
        reader.setIndexer(index);
        out:
        for (VDJCAlignments alignment : CUtils.it(reader)) {
            for (int i = 0; i < alignment.numberOfTargets(); i++) {
                final VDJCHit vHit = alignment.getBestHit(GeneType.Variable),
                        jHit = alignment.getBestHit(GeneType.Joining);
                if (vHit != null && jHit != null && vHit.getAlignment(i) != null && jHit.getAlignment(i) != null) {
                    containsVJJunction.incrementAndGet();
                    writer.write(alignment);
                    continue out;
                }
            }
            addLeftToIndex(alignment);
        }
        this.index = index.toArray();
    }

    public void searchOverlaps(String file, VDJCAlignmentsReader reader) {
        PartialAlignmentsAssemblerAligner aligner = new PartialAlignmentsAssemblerAligner(reader.getParameters());
        for (Allele allele : reader.getUsedAlleles())
            aligner.addAllele(allele);

        for (VDJCAlignments alignment : CUtils.it(reader)) {
            VDJCAlignments r = searchOverlaps(alignment, aligner);
            if (r != null)
                writer.write(r);
        }
    }

    public VDJCAlignments searchOverlaps(VDJCAlignments rightAl, PartialAlignmentsAssemblerAligner aligner) {
        LeftRightInfo right = getRightPartitionedSequence(rightAl);
        if (right == null)
            return null;

        NSequenceWithQuality rightNSeq = right.ps.getSequence();
        NucleotideSequence rightSeq = rightNSeq.getSequence();

        int stop = rightAl.getBestHit(GeneType.Joining).getAlignment(right.targetId).getSequence2Range().getFrom();

        int maxOverlap = -1;
        int maxOverlapIndex = -1;
        List<KMerInfo> maxOverlapList = null;
        for (int rFrom = 0; rFrom < stop; rFrom++) {
            int kTo = rFrom + kValue;
            long kMer = kMer(rightNSeq.getSequence(), rFrom, kTo);
            List<KMerInfo> match = kToIndexLeft.get(kMer);
            if (match == null)
                continue;

            out:
            for (int i = 0; i < match.size(); i++) {
                final VDJCAlignments al = match.get(i).getAlignments();
                final VDJCPartitionedSequence leftNSeq = getLeftPartitionedSequence(al).ps;
                final NucleotideSequence leftSeq = leftNSeq.getSequence().getSequence();
                int lFrom = match.get(i).kMerPositionFrom;

                int overlap = rFrom + leftSeq.size() - lFrom;
                for (int j = 0; j < rightSeq.size() && lFrom - rFrom + j < leftSeq.size(); j++)
                    if (rightSeq.codeAt(j) != leftSeq.codeAt(lFrom - rFrom + j))
                        continue out;

                if (maxOverlap < overlap) {
                    maxOverlap = overlap;
                    maxOverlapList = match;
                    maxOverlapIndex = i;
                }
            }
        }

        if (maxOverlapList == null)
            return null;

        if (maxOverlap < minimalOverlap)
            return null;

        final KMerInfo left = maxOverlapList.remove(maxOverlapIndex);
        overlapped.incrementAndGet();

        final VDJCAlignments leftAl = left.alignments;
        SingleRead[] rData;
        EnumSet<GeneType>[] expectedGenes;

        int switchcase = (leftAl.numberOfTargets() << 4) & rightAl.numberOfTargets();
        switch (switchcase) {
            case 0x22:
                if (left.targetId == 1 && right.targetId == 0) {
                    rData = new SingleRead[3];
                    expectedGenes = new EnumSet[3];
                    rData[0] = new SingleReadImpl(leftAl.getReadId(), leftAl.getTarget(0), "");
                    rData[1] = new SingleReadImpl(leftAl.getReadId(), mergeOverlapped(leftAl.getTarget(1), rightAl.getTarget(0), maxOverlap), "");
                    rData[2] = new SingleReadImpl(leftAl.getReadId(), rightAl.getTarget(1), "");

                    expectedGenes[0] = extractExpectedGenes(0, leftAl);
                    expectedGenes[1] = extractExpectedGenes(1, leftAl);
                    expectedGenes[1].addAll(extractExpectedGenes(0, rightAl));
                    expectedGenes[1].add(GeneType.Diversity);
                    expectedGenes[2] = extractExpectedGenes(1, rightAl);
                } else if (left.targetId == 0 && right.targetId == 1) {
                    rData = new SingleRead[1];
                    expectedGenes = new EnumSet[1];

                    rData[0] = new SingleReadImpl(leftAl.getReadId(), mergeOverlapped(leftAl.getTarget(0), rightAl.getTarget(1), maxOverlap), "");

                    expectedGenes[0] = extractExpectedGenes(0, leftAl);
                    expectedGenes[0].addAll(extractExpectedGenes(1, rightAl));
                    expectedGenes[0].add(GeneType.Diversity);

                    complexOverlapped.incrementAndGet();
                } else {
                    assert left.targetId == right.targetId;
                    int targetId = left.targetId;
                    rData = new SingleRead[1];
                    expectedGenes = new EnumSet[1];

                    rData[0] = new SingleReadImpl(leftAl.getReadId(), mergeOverlapped(leftAl.getTarget(targetId), rightAl.getTarget(targetId), maxOverlap), "");

                    expectedGenes[0] = extractExpectedGenes(targetId, leftAl);
                    expectedGenes[0].addAll(extractExpectedGenes(targetId, rightAl));
                    expectedGenes[0].add(GeneType.Diversity);

                    complexOverlapped.incrementAndGet();
                }
                break;
            case 0x12:
                if (right.targetId == 0) {
                    rData = new SingleRead[2];
                    expectedGenes = new EnumSet[2];
                    rData[0] = new SingleReadImpl(leftAl.getReadId(), mergeOverlapped(leftAl.getTarget(0), rightAl.getTarget(0), maxOverlap), "");
                    rData[1] = new SingleReadImpl(leftAl.getReadId(), rightAl.getTarget(1), "");

                    expectedGenes[0] = extractExpectedGenes(0, leftAl);
                    expectedGenes[0].addAll(extractExpectedGenes(0, rightAl));
                    expectedGenes[0].add(GeneType.Diversity);
                    expectedGenes[1] = extractExpectedGenes(1, rightAl);
                } else {
                    rData = new SingleRead[1];
                    expectedGenes = new EnumSet[1];

                    rData[0] = new SingleReadImpl(leftAl.getReadId(), mergeOverlapped(leftAl.getTarget(0), rightAl.getTarget(1), maxOverlap), "");

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
                    rData[0] = new SingleReadImpl(leftAl.getReadId(), leftAl.getTarget(0), "");
                    rData[1] = new SingleReadImpl(leftAl.getReadId(), mergeOverlapped(leftAl.getTarget(1), rightAl.getTarget(0), maxOverlap), "");

                    expectedGenes[0] = extractExpectedGenes(0, leftAl);
                    expectedGenes[1] = extractExpectedGenes(1, leftAl);
                    expectedGenes[1].addAll(extractExpectedGenes(0, rightAl));
                    expectedGenes[1].add(GeneType.Diversity);
                } else {
                    rData = new SingleRead[1];
                    expectedGenes = new EnumSet[1];

                    rData[0] = new SingleReadImpl(leftAl.getReadId(), mergeOverlapped(leftAl.getTarget(0), rightAl.getTarget(0), maxOverlap), "");

                    expectedGenes[0] = extractExpectedGenes(0, leftAl);
                    expectedGenes[0].addAll(extractExpectedGenes(0, rightAl));
                    expectedGenes[0].add(GeneType.Diversity);

                    complexOverlapped.incrementAndGet();
                }
                break;
            case 0x11:
                rData = new SingleRead[1];
                expectedGenes = new EnumSet[1];

                rData[0] = new SingleReadImpl(leftAl.getReadId(), mergeOverlapped(leftAl.getTarget(0), rightAl.getTarget(0), maxOverlap), "");

                expectedGenes[0] = extractExpectedGenes(0, leftAl);
                expectedGenes[0].addAll(extractExpectedGenes(0, rightAl));
                expectedGenes[0].add(GeneType.Diversity);
                break;
            default:
                throw new RuntimeException();
        }

        VDJCMultiRead mRead = new VDJCMultiRead(rData, expectedGenes);
        return aligner.process(mRead).alignment;
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

    private NSequenceWithQuality mergeOverlapped(NSequenceWithQuality left, NSequenceWithQuality right, int overlap) {
        return MismatchOnlyPairedReadMerger.overlap(left, right, left.size() - overlap, maxScoreValue, qualityMergingAlgorithm);
    }


    private LeftRightInfo getLeftPartitionedSequence(VDJCAlignments alignment) {
        for (int i = 0; i < alignment.numberOfTargets(); i++) {
            if (alignment.getBestHit(GeneType.Joining) != null && alignment.getBestHit(GeneType.Joining).getAlignment(i) != null)
                continue;
            VDJCPartitionedSequence ps = alignment.getPartitionedTarget(i);
            if (ps.getPartitioning().isAvailable(ReferencePoint.VEndTrimmed))
                return new LeftRightInfo(i, ps);
        }
        return null;
    }

    private LeftRightInfo getRightPartitionedSequence(VDJCAlignments alignment) {
        for (int i = 0; i < alignment.numberOfTargets(); i++) {
            if (alignment.getBestHit(GeneType.Variable) != null && alignment.getBestHit(GeneType.Variable).getAlignment(i) != null)
                continue;
            VDJCPartitionedSequence ps = alignment.getPartitionedTarget(i);
            if (ps.getPartitioning().isAvailable(ReferencePoint.JBeginTrimmed))
                return new LeftRightInfo(i, ps);
        }
        return null;
    }

    private static final class LeftRightInfo {
        final int targetId;
        final VDJCPartitionedSequence ps;

        public LeftRightInfo(int targetId, VDJCPartitionedSequence ps) {
            this.targetId = targetId;
            this.ps = ps;
        }
    }

    private void addLeftToIndex(VDJCAlignments alignment) {
        final LeftRightInfo lrInfo = getLeftPartitionedSequence(alignment);
        VDJCPartitionedSequence left = lrInfo.ps;
        if (left == null)
            return;

        NSequenceWithQuality seq = left.getSequence();

        int kFrom = left.getPartitioning().getPosition(ReferencePoint.VEndTrimmed) + kOffset;
        int kTo = kFrom + kValue;

        if (kFrom < 0 || kTo >= seq.size()) {
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
            kToIndexLeft.put(kmer, ids = new ArrayList<>());
        final long alIndex = alignment.getAlignmentsIndex();
        if (alIndex >= (long) Integer.MAX_VALUE)
            throw new RuntimeException("Too much alignments");
        ids.add(new KMerInfo(alignment, kFrom, lrInfo.targetId));
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