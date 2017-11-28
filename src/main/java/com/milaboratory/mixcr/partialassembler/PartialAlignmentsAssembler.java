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
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.*;
import com.milaboratory.mixcr.cli.ReportHelper;
import com.milaboratory.mixcr.cli.Report;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.set.hash.TLongHashSet;
import io.repseq.core.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static com.milaboratory.mixcr.vdjaligners.VDJCAlignerWithMerge.getMMDescr;

public class PartialAlignmentsAssembler implements AutoCloseable, Report {
    final TLongObjectHashMap<List<KMerInfo>> kToIndexLeft = new TLongObjectHashMap<>();
    final TLongHashSet alreadyMergedIds = new TLongHashSet();
    final TLongHashSet notInLeftIndexIds = new TLongHashSet();
    final VDJCAlignmentsWriter writer;
    final int kValue;
    final int kOffset;
    final int minimalAssembleOverlap;
    final int minimalNOverlap;
    final boolean writePartial, overlappedOnly;
    final TargetMerger targetMerger;
    public final AtomicLong leftParts = new AtomicLong(),
            rightParts = new AtomicLong(),
            noKMer = new AtomicLong(),
            wildcardsInKMer = new AtomicLong(),
            kMerDiversity = new AtomicLong(),
            total = new AtomicLong(),
            overlapped = new AtomicLong(),
            totalWritten = new AtomicLong(),
            partialAsIs = new AtomicLong(),
            overoverlapped = new AtomicLong(),
            droppedSmallOverlapNRegion = new AtomicLong(),
            droppedNoNRegion = new AtomicLong(),
            complexOverlapped = new AtomicLong(),
            containsCDR3 = new AtomicLong();

    public PartialAlignmentsAssembler(PartialAlignmentsAssemblerParameters params, VDJCAlignmentsWriter writer,
                                      boolean writePartial, boolean overlappedOnly) {
        this.kValue = params.getKValue();
        this.kOffset = params.getKOffset();
        this.minimalAssembleOverlap = params.getMinimalAssembleOverlap();
        this.minimalNOverlap = params.getMinimalNOverlap();
        this.targetMerger = new TargetMerger(params.getMergerParameters(), params.getMinimalAlignmentMergeIdentity());
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
            if (!addLeftToIndex(alignment))
                notInLeftIndexIds.add(alignment.getAlignmentsIndex());
        }
    }

    public void searchOverlaps(VDJCAlignmentsReader reader) {
        final VDJCAlignerParameters alignerParameters = reader.getParameters();
        PartialAlignmentsAssemblerAligner aligner = new PartialAlignmentsAssemblerAligner(alignerParameters);
        targetMerger.setAlignerParameters(alignerParameters);
        for (VDJCGene gene : reader.getUsedGenes())
            aligner.addGene(gene);

        for (final VDJCAlignments alignment : CUtils.it(reader)) {
            total.incrementAndGet();

            if (alignment.getFeature(GeneFeature.CDR3) != null) {
                containsCDR3.incrementAndGet();
                if (!overlappedOnly) {
                    totalWritten.incrementAndGet();
                    writer.write(alignment);
                }
                continue;
            }

            if (alreadyMergedIds.contains(alignment.getAlignmentsIndex()))
                continue;

            final OverlapSearchResult searchResult = searchOverlaps(alignment, alignerParameters.isAllowChimeras());

            // Common procedure to cancel processing of current input alignment if it fails to pass some good
            // overlap filtering criterion
            final Runnable cancelCurrentResult = new Runnable() {
                @Override
                public void run() {
                    if (searchResult != null)
                        searchResult.cancel();
                    if (writePartial && !overlappedOnly &&
                            notInLeftIndexIds.contains(alignment.getAlignmentsIndex())) {
                        totalWritten.incrementAndGet();
                        partialAsIs.incrementAndGet();
                        writer.write(alignment);
                    }
                }
            };

            if (searchResult == null) {
                cancelCurrentResult.run();
                continue;
            }
            List<AlignedTarget> mergedTargets = searchResult.result;
            VDJCMultiRead mRead = new VDJCMultiRead(mergedTargets);

            final VDJCAlignments mAlignment = aligner.process(mRead).alignment;

            // Checking number of overlapped non-template (NRegion) letters
            int overlapTargetId = -1;
            Range overlapRange = null;
            for (int i = 0; i < mergedTargets.size(); i++) {
                overlapRange = AlignedTarget.getOverlapRange(mergedTargets.get(i));
                if (overlapRange != null) {
                    overlapTargetId = i;
                    break;
                }
            }

            if (overlapTargetId == -1) {
                // No alignments for Best V Hit and Best J Hit in central (overlapped) target
                cancelCurrentResult.run();
                continue;
            }

            int targetLength = mergedTargets.get(overlapTargetId).getTarget().size();
            VDJCHit bestVHit = mAlignment.getBestHit(GeneType.Variable),
                    bestJHit = mAlignment.getBestHit(GeneType.Joining);

            if (bestVHit == null || bestJHit == null ||
                    bestVHit.getAlignment(overlapTargetId) == null ||
                    bestJHit.getAlignment(overlapTargetId) == null) {
                cancelCurrentResult.run();
                continue;
            }

            int ndnRegionBegin = 0;
            int ndnRegionEnd = targetLength;
            Alignment<NucleotideSequence> vAlignment = bestVHit.getAlignment(overlapTargetId);
            if (vAlignment != null)
                ndnRegionBegin = vAlignment.getSequence2Range().getTo();

            Alignment<NucleotideSequence> jAlignment = bestJHit.getAlignment(overlapTargetId);
            if (jAlignment != null)
                ndnRegionEnd = jAlignment.getSequence2Range().getFrom();

            RangeSet nRegion = ndnRegionBegin >= ndnRegionEnd ?
                    RangeSet.EMPTY :
                    RangeSet.create(ndnRegionBegin, ndnRegionEnd);

            Range dRange = mAlignment.getPartitionedTarget(overlapTargetId).getPartitioning().getRange(GeneFeature.DRegionTrimmed);
            if (dRange != null)
                nRegion = nRegion.subtract(dRange);

            RangeSet nRegionInOverlap = nRegion.intersection(overlapRange);

            int actualNRegionLength = nRegion.totalLength();
            int minimalN = Math.min(minimalNOverlap, actualNRegionLength);

            if (nRegionInOverlap.totalLength() < minimalN) {
                droppedSmallOverlapNRegion.incrementAndGet();
                cancelCurrentResult.run();
                continue;
            }

            // Checking for dangerous false-positive overlap case:
            // VVVVVVVVVVDDDDDDDDDDDDD
            //                  DDDDDDDDDJJJJJJJJJJJJJJJ
            if (minimalN == 0 &&
                    (!overlapRange.contains(ndnRegionBegin - 1) || !overlapRange.contains(ndnRegionEnd))) {
                droppedNoNRegion.incrementAndGet();
                cancelCurrentResult.run();
                continue;
            }

            overlapped.incrementAndGet();
            totalWritten.incrementAndGet();
            writer.write(mAlignment);

            // Saving alignment that where merge to prevent it's use as left part
            alreadyMergedIds.add(alignment.getAlignmentsIndex());
            alreadyMergedIds.add(searchResult.KMerInfo.alignments.getAlignmentsIndex());
        }

        if (writePartial && !overlappedOnly)
            for (List<KMerInfo> kMerInfos : kToIndexLeft.valueCollection())
                for (KMerInfo kMerInfo : kMerInfos)
                    if (alreadyMergedIds.add(kMerInfo.alignments.getAlignmentsIndex())) {
                        totalWritten.incrementAndGet();
                        partialAsIs.incrementAndGet();
                        writer.write(kMerInfo.getAlignments());
                    }

        writer.setNumberOfProcessedReads(reader.getNumberOfReads() - overlapped.get());
    }


    static class OverlapSearchResult {
        final List<KMerInfo> originKMerList;
        final KMerInfo KMerInfo;
        final List<AlignedTarget> result;

        public OverlapSearchResult(List<KMerInfo> originKMerList, KMerInfo KMerInfo, List<AlignedTarget> result) {
            this.originKMerList = originKMerList;
            this.KMerInfo = KMerInfo;
            this.result = result;
        }

        void cancel() {
            originKMerList.add(KMerInfo);
        }
    }

    @SuppressWarnings("unchecked")
    private OverlapSearchResult searchOverlaps(final VDJCAlignments rightAl,
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
        if (stop == -1)
            stop = rightTarget.getSequence().size();
        else
            stop -= kOffset;

        // black list of left parts failed due to inconsistent overlapped alignments (failed AMerge)
        TLongHashSet blackList = new TLongHashSet();
        SEARCH_LEFT_PARTS:
        while (true) {
            int maxOverlap = -1, maxDelta = -1,
                    maxOverlapIndexInList = -1,
                    maxBegin = -1, maxEnd = -1;
            List<KMerInfo> maxOverlapList = null;
            boolean isMaxOverOverlapped = false;
            for (int rFrom = 0; rFrom < stop && rFrom + kValue < rightSeqQ.size(); rFrom++) {
                long kMer = kMer(rightSeqQ.getSequence(), rFrom, kValue);
                List<KMerInfo> match = kToIndexLeft.get(kMer);
                if (match == null)
                    continue;

                out:
                for (int i = 0; i < match.size(); i++) {
                    boolean isOverOverlapped = false;
                    final VDJCAlignments leftAl = match.get(i).getAlignments();

                    if (blackList.contains(leftAl.getAlignmentsIndex()))
                        continue;

                    if (leftAl.getAlignmentsIndex() == rightAl.getAlignmentsIndex() || // You shall not merge with yourself
                            alreadyMergedIds.contains(leftAl.getAlignmentsIndex()))
                        continue;

                    // Checking chains compatibility
                    if (!allowChimeras && jChains != null && !leftAl.getAllChains(GeneType.Variable).intersects(jChains))
                        continue;

                    // Check for the same V
                    if (leftAl.getBestHit(GeneType.Variable) != null
                            && rightAl.getBestHit(GeneType.Variable) != null
                            && !leftAl.hasCommonGenes(GeneType.Variable, rightAl))
                        continue;

                    final NucleotideSequence leftSeq = leftAl.getPartitionedTarget(getLeftPartitionedSequence(leftAl))
                            .getSequence().getSequence();
                    int lFrom = match.get(i).kMerPositionFrom;

                    // begin and end in the coordinates of left
                    int delta, begin = delta = lFrom - rFrom;
                    if (begin < 0) {
                        begin = 0;
                        isOverOverlapped = true;
                    }
                    int end = leftSeq.size();
                    if (end >= rightSeq.size() + delta) {
                        end = rightSeq.size() + delta;
                        isOverOverlapped = true;
                    }

                    for (int j = begin; j < end; j++)
                        if (leftSeq.codeAt(j) != rightSeq.codeAt(j - delta))
                            continue out;

                    int overlap = end - begin;
                    if (maxOverlap < overlap) {
                        maxOverlap = overlap;
                        maxOverlapList = match;
                        maxOverlapIndexInList = i;
                        maxDelta = delta;
                        maxBegin = begin;
                        maxEnd = end;
                        isMaxOverOverlapped = isOverOverlapped;
                    }
                }
            }

            if (maxOverlapList == null)
                return null;

            if (maxOverlap < minimalAssembleOverlap)
                return null;

            final KMerInfo left = maxOverlapList.remove(maxOverlapIndexInList);
            VDJCAlignments leftAl = left.alignments;

            //final long readId = rightAl.getReadId();

            ArrayList<AlignedTarget> leftTargets = extractAlignedTargets(leftAl);
            ArrayList<AlignedTarget> rightTargets = extractAlignedTargets(rightAl);

            AlignedTarget leftCentral = leftTargets.get(left.targetId);
            AlignedTarget rightCentral = rightTargets.get(rightTargetId);

            AlignedTarget central = targetMerger.merge(leftCentral, rightCentral, maxDelta, SequenceHistory.OverlapType.CDR3Overlap, 0);

            // Setting overlap position
            central = AlignedTarget.setOverlapRange(central, maxBegin, maxEnd);

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
            TargetMerger.TargetMergingResult bestResult = TargetMerger.FAILED_RESULT;
            int bestI;

            // Trying to merge left and right reads to central one
            for (List<AlignedTarget> descriptors : allDescriptors)
                do {
                    bestI = -1;
                    for (int i = 0; i < descriptors.size(); i++) {
                        TargetMerger.TargetMergingResult result = targetMerger.merge(descriptors.get(i), central);
                        if (result.failedDueInconsistentAlignments()) {
                            // Inconsistent alignments -> retry
                            blackList.add(leftAl.getAlignmentsIndex());
                            continue SEARCH_LEFT_PARTS;
                        }
                        if (bestResult.getScore() < result.getScore()) {
                            bestResult = result;
                            bestI = i;
                        }
                    }

                    if (bestI != -1) {
                        assert bestResult != TargetMerger.FAILED_RESULT;
                        central = bestResult.getResult();
                        descriptors.remove(bestI);
                    }
                } while (bestI != -1);


            // Merging left+left / right+right
            outer:
            for (int d = 0; d < allDescriptors.length; d++) {
                List<AlignedTarget> descriptors = allDescriptors[d];
                for (int i = 0; i < descriptors.size(); i++)
                    for (int j = i + 1; j < descriptors.size(); j++) {
                        TargetMerger.TargetMergingResult result = targetMerger.merge(descriptors.get(i), descriptors.get(j));
                        if (result.failedDueInconsistentAlignments()) {
                            // Inconsistent alignments -> retry
                            blackList.add(leftAl.getAlignmentsIndex());
                            continue SEARCH_LEFT_PARTS;
                        }
                        if (result.isSuccessful()) {
                            descriptors.set(i, result.getResult());
                            descriptors.remove(j);
                            --d;
                            continue outer;
                        }
                    }
            }

            if (isMaxOverOverlapped)
                overoverlapped.incrementAndGet();

            // Creating pre-list of resulting targets
            List<AlignedTarget> result = new ArrayList<>();
            result.addAll(leftDescriptors);
            result.add(central);
            result.addAll(rightDescriptors);

            // Ordering and filtering final targets
            return new OverlapSearchResult(maxOverlapList, left, AlignedTarget.orderTargets(result));
        }
    }

    private static String mergeTypePrefix(boolean usingAlignment) {
        return usingAlignment ? "A" : "S";
    }

    @JsonProperty("totalProcessed")
    public long getTotalProcessed() {
        return total.get();
    }

    @JsonProperty("outputAlignments")
    public long getOutputAlignments() {
        return totalWritten.get();
    }

    @JsonProperty("withCDR3")
    public long getContainsCDR3() {
        return containsCDR3.get();
    }

    @JsonProperty("overlapped")
    public long getOverlapped() {
        return overlapped.get();
    }

    @JsonProperty("leftTooShortNRegion")
    public long getLeftShortNRegion() {
        return noKMer.get();
    }

    @JsonProperty("kMerDiversity")
    public long getKMerDiversity() {
        return kMerDiversity.get();
    }

    @JsonProperty("droppedWildcardsInKMer")
    public long getWildcardsInKMer() {
        return wildcardsInKMer.get();
    }

    @JsonProperty("droppedSmallOverlapNRegion")
    public long getDroppedSmallOverlapNRegion() {
        return droppedSmallOverlapNRegion.get();
    }

    @JsonProperty("droppedNoNRegion")
    public long getDroppedNoNRegion() {
        return droppedNoNRegion.get();
    }

    @JsonProperty("leftParts")
    public long getLeftParts() {
        return leftParts.get();
    }

    @JsonProperty("rightParts")
    public long getRightParts() {
        return rightParts.get();
    }

    @JsonProperty("complexOverlaps")
    public long getComplexOverlaps() {
        return complexOverlapped.get();
    }

    @JsonProperty("overOverlaps")
    public long getOverOverlapped() {
        return overoverlapped.get();
    }

    @JsonProperty("partialAlignmentsAsIs")
    public long getPartialAlignmentsAsIs() {
        return partialAsIs.get();
    }

    @Override
    public void writeReport(ReportHelper helper) {
        long total = this.total.get();
        helper.writeField("Total alignments analysed", total);
        helper.writePercentAndAbsoluteField("Number of output alignments", totalWritten, total);
        helper.writePercentAndAbsoluteField("Alignments already with CDR3 (no overlapping is performed)", containsCDR3, total);
        helper.writePercentAndAbsoluteField("Successfully overlapped alignments", overlapped, total);
        helper.writePercentAndAbsoluteField("Left parts with too small N-region (failed to extract k-mer)", noKMer, total);
        helper.writeField("Extracted k-mer diversity", kMerDiversity);
        helper.writePercentAndAbsoluteField("Dropped due to wildcard in k-mer", wildcardsInKMer, total);
        helper.writePercentAndAbsoluteField("Dropped due to too short NRegion parts in overlap", droppedSmallOverlapNRegion, total);
        helper.writePercentAndAbsoluteField("Dropped overlaps with empty N region due to no complete NDN coverage", droppedNoNRegion, total);
        helper.writePercentAndAbsoluteField("Number of left-side alignments", leftParts, total);
        helper.writePercentAndAbsoluteField("Number of right-side alignments", rightParts, total);
        helper.writePercentAndAbsoluteField("Complex overlaps", complexOverlapped, total);
        helper.writePercentAndAbsoluteField("Over-overlaps", overoverlapped, total);
        helper.writePercentAndAbsoluteField("Partial alignments written to output", partialAsIs, total);
        if (!writePartial && !overlappedOnly && totalWritten.get() != overlapped.get() + partialAsIs.get() + containsCDR3.get())
            throw new AssertionError();
    }

    private int getLeftPartitionedSequence(VDJCAlignments alignment) {
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
        for (int i = 0; i < alignment.numberOfTargets(); i++) {
            if (alignment.getBestHit(GeneType.Variable) != null &&
                    alignment.getBestHit(GeneType.Variable)
                            .getPartitioningForTarget(i).isAvailable(ReferencePoint.CDR3Begin))
                continue;
            VDJCPartitionedSequence ps = alignment.getPartitionedTarget(i);
            if (ps.getPartitioning().isAvailable(ReferencePoint.JBeginTrimmed))
                return i;
        }

        if (alignment.numberOfTargets() != 2)
            return -1;


        if (getAlignmentLength(alignment, GeneType.Variable, 0) == alignment.getTarget(0).size()
                && alignment.hasNoHitsInTarget(1))
            return 1;

        if (getAlignmentLength(alignment, GeneType.Constant, 1)
                + getAlignmentLength(alignment, GeneType.Joining, 1) == alignment.getTarget(1).size()
                && alignment.hasNoHitsInTarget(0))
            return 0;

        return -1;
    }

    private static int getAlignmentLength(VDJCAlignments alignment, GeneType gt, int id) {
        VDJCHit bh = alignment.getBestHit(gt);
        if (bh == null)
            return 0;
        Alignment<NucleotideSequence> al = bh.getAlignment(id);
        if (al == null)
            return 0;
        return al.getSequence2Range().length();
    }


    private boolean addLeftToIndex(VDJCAlignments alignment) {
        int leftTargetId = getLeftPartitionedSequence(alignment);
        if (leftTargetId == -1)
            return false;

        VDJCPartitionedSequence left = alignment.getPartitionedTarget(leftTargetId);
        NSequenceWithQuality seq = left.getSequence();

        int kFromFirst = left.getPartitioning().getPosition(ReferencePoint.VEndTrimmed) + kOffset;
        if (kFromFirst < 0 || kFromFirst + kValue >= seq.size()) {
            noKMer.incrementAndGet();
            return false;
        }

        for (int kFrom = kFromFirst; kFrom < seq.size() - kValue; ++kFrom) {
            long kmer = kMer(seq.getSequence(), kFrom, kValue);
            if (kmer == -1) {
                wildcardsInKMer.incrementAndGet();
                continue;
            }

            List<KMerInfo> ids = kToIndexLeft.get(kmer);
            if (ids == null) {
                kToIndexLeft.put(kmer, ids = new ArrayList<>(1));
                kMerDiversity.incrementAndGet();
            }
            ids.add(new KMerInfo(alignment, kFrom, leftTargetId));
        }

        leftParts.incrementAndGet();
        return true;
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

    public static ArrayList<AlignedTarget> extractAlignedTargets(VDJCAlignments alignments) {
        ArrayList<AlignedTarget> targets = new ArrayList<>(alignments.numberOfTargets());
        for (int i = 0; i < alignments.numberOfTargets(); i++)
            targets.add(new AlignedTarget(alignments, i));
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

