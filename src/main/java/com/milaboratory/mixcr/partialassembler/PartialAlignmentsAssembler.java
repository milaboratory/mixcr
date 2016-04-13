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
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.*;
import com.milaboratory.mixcr.reference.GeneType;
import com.milaboratory.mixcr.reference.LociLibraryManager;
import com.milaboratory.mixcr.reference.ReferencePoint;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.list.array.TLongArrayList;
import gnu.trove.map.hash.TLongObjectHashMap;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

public class PartialAlignmentsAssembler implements AutoCloseable {
    volatile long[] index;
    final TLongObjectHashMap<TIntArrayList> kToIndexLeft = new TLongObjectHashMap<>();
    final VDJCAlignmentsWriter writer;
    final int kValue;
    final int kOffset;
    final int minimalOverlap;
    public final AtomicLong leftParts = new AtomicLong(),
            noKMer = new AtomicLong(),
            wildCardsInKMer = new AtomicLong(),
            total = new AtomicLong(),
            overlapped = new AtomicLong(),
            containsVJJunction = new AtomicLong();

    public PartialAlignmentsAssembler(PartialAlignmentsAssemblerParameters params, String output) {
        this.kValue = params.getKValue();
        this.kOffset = params.getKOffset();
        this.minimalOverlap = params.getMinimalOverlap();
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
        RandomAccessVDJCAReader rReader = new RandomAccessVDJCAReader(file, index, LociLibraryManager.getDefault());
        for (VDJCAlignments alignment : CUtils.it(reader)) {
            searchOverlaps(alignment, rReader);
        }
    }

    public VDJCAlignments searchOverlaps(VDJCAlignments alignment, RandomAccessVDJCAReader rReader) {
        RightInfo right = getRightPartitionedSequence(alignment);
        if (right == null)
            return null;

        NSequenceWithQuality rightNSeq = right.ps.getSequence();
        NucleotideSequence rightSeq = rightNSeq.getSequence();

        int stop = alignment.getBestHit(GeneType.Joining).getAlignment(right.al).getSequence2Range().getFrom();


        VDJCAlignments bestLeft;
        int maxOverlap = -1;
        int maxOverlapIndex = -1;
        TIntArrayList maxOverlapList = null;
        for (int kFrom = 0; kFrom < stop; kFrom++) {
            int kTo = kFrom + kValue;
            long kMer = kMer(rightNSeq.getSequence(), kFrom, kTo);
            TIntArrayList match = kToIndexLeft.get(kMer);
            if (match == null)
                continue;

            for (int i = 0; i < match.size(); i++) {
                final VDJCAlignments al = rReader.get(match.get(i));
                final VDJCPartitionedSequence leftNSeq = getLeftPartitionedSequence(al);
                final NucleotideSequence leftSeq = leftNSeq.getSequence().getSequence();

                int overlap = 0;
                for (; ; overlap++)
                    if (rightSeq.codeAt(rightSeq.size() - overlap) != leftSeq.codeAt(overlap))
                        break;

                if (maxOverlap < overlap) {
                    maxOverlap = overlap;
                    bestLeft = al;
                    maxOverlapList = match;
                    maxOverlapIndex = i;
                }
            }
        }

        if (maxOverlap < minimalOverlap)
            return null;

        if (maxOverlapList != null)
            maxOverlapList.removeAt(maxOverlapIndex);


        overlapped.incrementAndGet();
        return null;
    }


    private VDJCPartitionedSequence getLeftPartitionedSequence(VDJCAlignments alignment) {
        for (int i = 0; i < alignment.numberOfTargets(); i++) {
            if (alignment.getBestHit(GeneType.Joining) != null && alignment.getBestHit(GeneType.Joining).getAlignment(i) != null)
                continue;
            VDJCPartitionedSequence ps = alignment.getPartitionedTarget(i);
            if (ps.getPartitioning().isAvailable(ReferencePoint.VEndTrimmed))
                return ps;
        }
        return null;
    }

    private RightInfo getRightPartitionedSequence(VDJCAlignments alignment) {
        for (int i = 0; i < alignment.numberOfTargets(); i++) {
            if (alignment.getBestHit(GeneType.Variable) != null && alignment.getBestHit(GeneType.Variable).getAlignment(i) != null)
                continue;
            VDJCPartitionedSequence ps = alignment.getPartitionedTarget(i);
            if (ps.getPartitioning().isAvailable(ReferencePoint.JBeginTrimmed))
                return new RightInfo(i, ps);
        }
        return null;
    }

    private static final class RightInfo {
        final int al;
        final VDJCPartitionedSequence ps;

        public RightInfo(int al, VDJCPartitionedSequence ps) {
            this.al = al;
            this.ps = ps;
        }
    }

    private void addLeftToIndex(VDJCAlignments alignment) {
        VDJCPartitionedSequence left = getLeftPartitionedSequence(alignment);
        if (left == null)
            return;

        NSequenceWithQuality seq = left.getSequence();

        int kFrom = left.getPartitioning().getPosition(ReferencePoint.VEndTrimmed) + kOffset;
        int kTo = kFrom + kValue;

        if (kFrom < 0 || kTo >= seq.size()) {
            noKMer.incrementAndGet();
            return;
        }

        long kmer = kMer(seq.getSequence(), kFrom, kTo);
        if (kmer == -1) {
            wildCardsInKMer.incrementAndGet();
            return;
        }
        TIntArrayList ids = kToIndexLeft.get(kmer);
        if (ids == null)
            kToIndexLeft.put(kmer, ids = new TIntArrayList());
        final long alIndex = alignment.getAlignmentsIndex();
        if (alIndex >= (long) Integer.MAX_VALUE)
            throw new RuntimeException("Too much alignments");
        ids.add((int) alIndex);
        leftParts.incrementAndGet();
    }

    private static long kMer(NucleotideSequence seq, int from, int to) {
        long kmer = 0;
        for (int j = from; j < to; ++j) {
            byte c = seq.codeAt(j);
            if (NucleotideSequence.ALPHABET.isWildcard(c))
                return -1;
            kmer = (kmer << 2 | c);
        }
        return kmer;
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}