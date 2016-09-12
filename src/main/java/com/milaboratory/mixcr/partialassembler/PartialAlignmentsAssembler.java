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
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsWriter;
import com.milaboratory.mixcr.basictypes.VDJCPartitionedSequence;
import com.milaboratory.mixcr.cli.ReportHelper;
import com.milaboratory.mixcr.cli.ReportWriter;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.set.hash.TLongHashSet;
import io.repseq.core.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static com.milaboratory.mixcr.vdjaligners.VDJCAlignerWithMerge.getMMDescr;

public class PartialAlignmentsAssembler implements AutoCloseable, ReportWriter {
    final TLongObjectHashMap<List<KMerInfo>> kToIndexLeft = new TLongObjectHashMap<>();
    final TLongHashSet leftPartsIds = new TLongHashSet();
    final VDJCAlignmentsWriter writer;
    final int kValue;
    final int kOffset;
    final int minimalVJJunctionOverlap;
    final boolean writePartial, overlappedOnly;
    final TargetMerger targetMerger;
    public final AtomicLong leftParts = new AtomicLong(),
            rightParts = new AtomicLong(),
            noKMer = new AtomicLong(),
            wildCardsInKMer = new AtomicLong(),
            total = new AtomicLong(),
            overlapped = new AtomicLong(),
            totalWritten = new AtomicLong(),
            partialAsIs = new AtomicLong(),
            complexOverlapped = new AtomicLong(),
            containsCDR3 = new AtomicLong();

    public PartialAlignmentsAssembler(PartialAlignmentsAssemblerParameters params, VDJCAlignmentsWriter writer,
                                      boolean writePartial, boolean overlappedOnly) {
        this.kValue = params.getKValue();
        this.kOffset = params.getKOffset();
        this.minimalVJJunctionOverlap = params.getMinimalVJJunctionOverlap();
        this.targetMerger = new TargetMerger(params.getMergerParameters());
        this.writePartial = writePartial;
        this.overlappedOnly = overlappedOnly;
        this.writer = writer;
    }

    public PartialAlignmentsAssembler(PartialAlignmentsAssemblerParameters params, String output,
                                      boolean writePartial, boolean overlappedOnly) throws IOException {
        this(params, new VDJCAlignmentsWriter(output), writePartial, overlappedOnly);
    }

    public void buildLeftPartsIndex(VDJCAlignmentsReader reader) {
        writer.header(reader.getParameters(), reader.getUsedGenes());
        for (VDJCAlignments alignment : CUtils.it(reader)) {
            if (alignment.getFeature(GeneFeature.CDR3) != null)
                continue;
            addLeftToIndex(alignment);
        }
    }

    public void searchOverlaps(VDJCAlignmentsReader reader) {
        final VDJCAlignerParameters alignerParameters = reader.getParameters();
        PartialAlignmentsAssemblerAligner aligner = new PartialAlignmentsAssemblerAligner(alignerParameters);
        targetMerger.setAlignerParameters(alignerParameters);
        for (VDJCGene gene : reader.getUsedGenes())
            aligner.addGene(gene);

        for (VDJCAlignments alignment : CUtils.it(reader)) {
            total.incrementAndGet();

            if (leftPartsIds.contains(alignment.getAlignmentsIndex()))
                continue;

            if (alignment.getFeature(GeneFeature.CDR3) != null) {
                containsCDR3.incrementAndGet();
                if (!overlappedOnly) {
                    totalWritten.incrementAndGet();
                    writer.write(alignment);
                }
                continue;
            }

            VDJCMultiRead mRead = searchOverlaps(alignment, alignerParameters.isAllowChimeras());
            if (mRead == null) {
                if (writePartial && !overlappedOnly) {
                    totalWritten.incrementAndGet();
                    partialAsIs.incrementAndGet();
                    writer.write(alignment);
                }
                continue;
            }

            final VDJCAlignments al = aligner.process(mRead).alignment;

            overlapped.incrementAndGet();
            String[] descriptions = new String[mRead.numberOfReads()];
            for (int i = 0; i < mRead.numberOfReads(); i++)
                descriptions[i] = mRead.getRead(i).getDescription();
            al.setTargetDescriptions(descriptions);
            totalWritten.incrementAndGet();
            writer.write(al);
        }

        if (writePartial && !overlappedOnly)
            for (List<KMerInfo> kMerInfos : kToIndexLeft.valueCollection())
                for (KMerInfo kMerInfo : kMerInfos) {
                    totalWritten.incrementAndGet();
                    partialAsIs.incrementAndGet();
                    writer.write(kMerInfo.getAlignments());
                }

        writer.setNumberOfProcessedReads(reader.getNumberOfReads() - overlapped.get());
    }

    @SuppressWarnings("unchecked")
    private VDJCMultiRead searchOverlaps(final VDJCAlignments rightAl,
                                         final boolean allowChimeras) {
        final Chains jChains = rightAl.getAllChains(GeneType.Joining);

        int rightTargetId = getRightPartitionedSequence(rightAl);
        if (rightTargetId == -1)
            return null;

        rightParts.incrementAndGet();

        final VDJCPartitionedSequence rightTarget = rightAl.getPartitionedTarget(rightTargetId);
        NSequenceWithQuality rightSeqQ = rightTarget.getSequence();
        NucleotideSequence rightSeq = rightSeqQ.getSequence();

        int stop = rightTarget.getPartitioning().getPosition(ReferencePoint.JBeginTrimmed);
        assert stop != -1;

        stop -= kOffset;

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

                // Checking chains compatibility
                if (!allowChimeras && !leftAl.getAllChains(GeneType.Variable).intersects(jChains))
                    continue;

                final NucleotideSequence leftSeq = leftAl.getPartitionedTarget(getLeftPartitionedSequence(leftAl))
                        .getSequence().getSequence();
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

        if (maxOverlap < minimalVJJunctionOverlap)
            return null;

        KMerInfo left = maxOverlapList.remove(maxOverlapIndexInList);
        VDJCAlignments leftAl = left.alignments;

        final long readId = rightAl.getReadId();

        ArrayList<AlignedTarget> leftTargets = extractAlignedTargets(leftAl, true);
        ArrayList<AlignedTarget> rightTargets = extractAlignedTargets(rightAl, false);

        AlignedTarget leftCentral = leftTargets.get(left.targetId);
        AlignedTarget rightCentral = rightTargets.get(rightTargetId);

        AlignedTarget central = targetMerger.merge(readId, leftCentral, rightCentral, maxDelta)
                .overrideDescription("VJOverlap(" + maxOverlap + ") = " + leftCentral.getDescription() + " + " + rightCentral.getDescription());

        final List<AlignedTarget> leftDescriptors = new ArrayList<>(2),
                rightDescriptors = new ArrayList<>(2);

        for (int i = 0; i < left.targetId; ++i)
            leftDescriptors.add(leftTargets.get(i));
        for (int i = left.targetId + 1; i < leftAl.numberOfTargets(); ++i)
            rightDescriptors.add(leftTargets.get(i));
        for (int i = 0; i < rightTargetId; ++i)
            leftDescriptors.add(rightTargets.get(i));
        for (int i = rightTargetId + 1; i < rightAl.numberOfTargets(); ++i)
            rightDescriptors.add(rightTargets.get(i));


        // Merging to VJ junction
        List<AlignedTarget>[] allDescriptors = new List[]{leftDescriptors, rightDescriptors};
        TargetMerger.TargetMergingResult bestResult = null;
        int bestI;

        // Trying to merge left and right reads to central one
        for (List<AlignedTarget> descriptors : allDescriptors)
            do {
                bestI = -1;
                for (int i = 0; i < descriptors.size(); i++) {
                    TargetMerger.TargetMergingResult result = targetMerger.merge(readId, descriptors.get(i), central);
                    if (result != null && (bestResult == null || bestResult.score < result.score)) {
                        bestResult = result;
                        bestI = i;
                    }
                }

                if (bestI != -1) {
                    central = bestResult.result.overrideDescription(
                            central.getDescription() + " / " + mergeTypePrefix(bestResult.usingAlignments) + "MergedFrom" +
                                    (descriptors == leftDescriptors ? "Left" : "Right") +
                                    "(" + getMMDescr(bestResult.matched, bestResult.mismatched) + ") = " +
                                    descriptors.get(bestI).getDescription());
                    descriptors.remove(bestI);
                }
            } while (bestI != -1);


        // Merging left+left / right+right
        outer:
        for (int d = 0; d < allDescriptors.length; d++) {
            List<AlignedTarget> descriptors = allDescriptors[d];
            for (int i = 0; i < descriptors.size(); i++)
                for (int j = i + 1; j < descriptors.size(); j++) {
                    TargetMerger.TargetMergingResult result = targetMerger.merge(readId, descriptors.get(i), descriptors.get(j));
                    if (result != null) {
                        descriptors.set(i, result.result.overrideDescription(
                                mergeTypePrefix(result.usingAlignments) +
                                        "Merged(" + getMMDescr(result.matched, result.mismatched) + ") = " + descriptors.get(i).getDescription() +
                                        " + " + descriptors.get(j).getDescription()));
                        descriptors.remove(j);
                        --d;
                        continue outer;
                    }
                }
        }

        // Creating pre-list of resulting targets
        List<AlignedTarget> result = new ArrayList<>();
        result.addAll(leftDescriptors);
        result.add(central);
        result.addAll(rightDescriptors);

        // Ordering and filtering final targets
        return new VDJCMultiRead(readId, AlignedTarget.orderTargets(result));
    }

    private static String mergeTypePrefix(boolean usingAlignment) {
        return usingAlignment ? "A" : "S";
    }

    @Override
    public void writeReport(ReportHelper helper) {
        long total = this.total.get();
        helper.writeField("total", total);
        helper.writePercentAndAbsoluteField("totalWritten", totalWritten, total);
        helper.writePercentAndAbsoluteField("noKMer", noKMer, total);
        helper.writePercentAndAbsoluteField("wildCardsInKMer", wildCardsInKMer, total);
        helper.writePercentAndAbsoluteField("leftParts", leftParts, total);
        helper.writePercentAndAbsoluteField("rightParts", rightParts, total);
        helper.writePercentAndAbsoluteField("containsCDR3", containsCDR3, total);
        helper.writePercentAndAbsoluteField("overlapped", overlapped, total);
        helper.writePercentAndAbsoluteField("complexOverlapped", complexOverlapped, total);
        helper.writePercentAndAbsoluteField("partialAsIs", partialAsIs, total);
        if (!writePartial && !overlappedOnly && totalWritten.get() != overlapped.get() + partialAsIs.get() + containsCDR3.get())
            throw new AssertionError();
    }

    private int getLeftPartitionedSequence(VDJCAlignments alignment) {
        if (alignment.numberOfTargets() > 2)
            return -1;
        for (int i = 0; i < alignment.numberOfTargets(); i++) {
            if (alignment.getBestHit(GeneType.Joining) != null &&
                    alignment.getBestHit(GeneType.Joining)
                            .getPartitioningForTarget(i).isAvailable(ReferencePoint.CDR3End))
                continue;
            VDJCPartitionedSequence ps = alignment.getPartitionedTarget(i);
            if (ps.getPartitioning().isAvailable(ReferencePoint.VEndTrimmed))
                return i;
        }
        return -1;
    }

    private int getRightPartitionedSequence(VDJCAlignments alignment) {
        if (alignment.numberOfTargets() > 2)
            return -1;
        for (int i = 0; i < alignment.numberOfTargets(); i++) {
            if (alignment.getBestHit(GeneType.Variable) != null &&
                    alignment.getBestHit(GeneType.Variable)
                            .getPartitioningForTarget(i).isAvailable(ReferencePoint.CDR3Begin))
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
        leftPartsIds.add(alignment.getAlignmentsIndex());
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

    private static AlignedTarget overrideDescription(AlignedTarget target, boolean isLeft) {
        String descr = (isLeft ? "L" : "R") + target.getAlignments().getReadId() + "." + target.getTargetId();
        String oldDescr = target.getDescription();
        if (oldDescr != null)
            descr += "[" + oldDescr + "]";
        return target.overrideDescription(descr);
    }

    public static ArrayList<AlignedTarget> extractAlignedTargets(VDJCAlignments alignments, boolean isLeft) {
        ArrayList<AlignedTarget> targets = new ArrayList<>(alignments.numberOfTargets());
        for (int i = 0; i < alignments.numberOfTargets(); i++)
            targets.add(overrideDescription(new AlignedTarget(alignments, i), isLeft));
        return targets;
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}

// vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv     jjjjjjjjjjjjjjjjjjjjjcccccccccccccccccccccccc
//                        -------------------->             <--------------------
//        ----------------->            <--------------------

//   ------>            -------------------->                    <---------------
//            -------->                  <--------------------


//  ------------------>   -------------------->            <--------------------

//        ----------------->     <--------------------

