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
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader;
import com.milaboratory.mixcr.basictypes.VDJCPartitionedSequence;
import com.milaboratory.mixcr.reference.ReferencePoint;
import gnu.trove.list.array.TLongArrayList;
import gnu.trove.map.hash.TLongIntHashMap;

import java.util.concurrent.atomic.AtomicLong;

public class PartialAlignmentsAssembler {
    volatile long[] index;
    final TLongIntHashMap kToIndexLeft = new TLongIntHashMap();
    final int kValue;
    final int kOffset;
    final AtomicLong leftParts = new AtomicLong(),
            noKMer = new AtomicLong(),
            total = new AtomicLong();

    public PartialAlignmentsAssembler(PartialAlignmentsAssemblerParameters params) {
        this.kValue = params.getKValue();
        this.kOffset = params.getKOffset();
    }

    public void buildLeftPartsIndex(VDJCAlignmentsReader reader) {
        TLongArrayList index = new TLongArrayList();
        reader.setIndexer(index);
        for (VDJCAlignments alignment : CUtils.it(reader))
            addLeftToIndex(alignment);
        this.index = index.toArray();
    }

    private void addLeftToIndex(VDJCAlignments alignment) {
        VDJCPartitionedSequence partitionedSequence = null;
        for (int i = 0; i < alignment.numberOfTargets(); i++) {
            VDJCPartitionedSequence ps = alignment.getPartitionedTarget(i);
            if (ps.getPartitioning().isAvailable(ReferencePoint.VEndTrimmed)) {
                partitionedSequence = ps;
                break;
            }
        }

        if (partitionedSequence == null)
            return;

        NSequenceWithQuality seq = partitionedSequence.getSequence();

        int kFrom = partitionedSequence.getPartitioning().getPosition(ReferencePoint.VEndTrimmed) + kOffset;
        int kTo = kFrom + kValue;

        if (kFrom < 0 || kTo >= seq.size()) {
            noKMer.incrementAndGet();
            return;
        }

        leftParts.incrementAndGet();

        long kmer = kMer(seq.getSequence(), kFrom, kTo);

        if(kmer == -1)
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
}