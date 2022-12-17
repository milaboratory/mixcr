/*
 * Copyright (c) 2014-2022, MiLaboratories Inc. All Rights Reserved
 *
 * Before downloading or accessing the software, please read carefully the
 * License Agreement available at:
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 *
 * By downloading or accessing the software, you accept and agree to be bound
 * by the terms of the License Agreement. If you do not want to agree to the terms
 * of the Licensing Agreement, you must not download or access the software.
 */
package com.milaboratory.mixcr.partialassembler;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.InputPort;
import cc.redberry.pipe.OutputPort;
import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.SequenceHistory;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.mixcr.basictypes.VDJCPartitionedSequence;
import com.milaboratory.mixcr.basictypes.tag.TagCount;
import com.milaboratory.mixcr.basictypes.tag.TagCountAggregator;
import com.milaboratory.mixcr.cli.AbstractCommandReportBuilder;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.set.hash.TLongHashSet;
import io.repseq.core.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class PartialAlignmentsAssembler extends AbstractCommandReportBuilder<PartialAlignmentsAssembler> {
    final TLongObjectHashMap<List<KMerInfo>> kToIndexLeft = new TLongObjectHashMap<>();
    final TLongHashSet alreadyMergedIds = new TLongHashSet();
    final TLongHashSet notInLeftIndexIds = new TLongHashSet();
    final InputPort<VDJCAlignments> output;
    final int kValue;
    final int kOffset;
    final int minimalAssembleOverlap;
    final int minimalNOverlap;
    final float minimalNOverlapShare;
    final boolean writePartial, overlappedOnly;
    final VDJCAlignerParameters alignerParameters;
    final TargetMerger targetMerger;
    final List<VDJCGene> usedGenes;
    final PartialAlignmentsAssemblerAligner aligner;
    final Set<GeneType> geneTypesToShiftIndels;
    final long maxLeftParts;
    final int maxLeftMatches;

    public final AtomicLong
            independentRuns = new AtomicLong(),
            leftParts = new AtomicLong(),
            rightParts = new AtomicLong(),
            leftTooShortNRegion = new AtomicLong(),
            droppedWildcardsInKMer = new AtomicLong(),
            kMerDiversity = new AtomicLong(),
            totalProcessed = new AtomicLong(),
            overlapped = new AtomicLong(),
            outputAlignments = new AtomicLong(),
            partialAlignmentsAsIs = new AtomicLong(),
            overoverlapped = new AtomicLong(),
            droppedSmallOverlapNRegion = new AtomicLong(),
            droppedNoNRegion = new AtomicLong(),
            complexOverlapped = new AtomicLong(),
            withCDR3 = new AtomicLong();

    public PartialAlignmentsAssembler(PartialAlignmentsAssemblerParameters params,
                                      VDJCAlignerParameters alignerParameters,
                                      List<VDJCGene> usedGenes,
                                      boolean writePartial, boolean overlappedOnly,
                                      InputPort<VDJCAlignments> output) {
        this.kValue = params.getKValue();
        if (kValue >= 32)
            throw new IllegalArgumentException("kValue should be less than 32");
        this.kOffset = params.getKOffset();
        this.minimalAssembleOverlap = params.getMinimalAssembleOverlap();
        this.minimalNOverlap = params.getMinimalNOverlap();
        this.minimalNOverlapShare = params.getMinimalNOverlapShare();
        this.alignerParameters = alignerParameters;
        this.targetMerger = new TargetMerger(params.getMergerParameters(), alignerParameters, params.getMinimalAlignmentMergeIdentity());
        this.usedGenes = usedGenes;
        this.geneTypesToShiftIndels = alignerParameters.getGeneTypesWithLinearScoring();
        this.maxLeftParts = params.getMaxLeftParts();
        this.maxLeftMatches = params.getMaxLeftMatches();
        this.writePartial = writePartial;
        this.overlappedOnly = overlappedOnly;
        this.output = output;

        this.aligner = new PartialAlignmentsAssemblerAligner(alignerParameters);
        for (VDJCGene gene : usedGenes)
            this.aligner.addGene(gene);
    }

    public boolean leftPartsLimitReached() {
        return maxLeftParts <= leftParts.get();
    }

    private boolean maxRightMatchesLimitReached = false;

    public boolean maxRightMatchesLimitReached() {
        return maxRightMatchesLimitReached;
    }

    public void buildLeftPartsIndex(OutputPort<VDJCAlignments> input) {
        // Resetting internal state if this object was reused
        kToIndexLeft.clear();
        alreadyMergedIds.clear();
        notInLeftIndexIds.clear();

        independentRuns.incrementAndGet();

        for (VDJCAlignments alignment : CUtils.it(input)) {
            if (alignment.getFeature(GeneFeature.CDR3) != null)
                continue;
            if (!addLeftToIndex(alignment))
                notInLeftIndexIds.add(alignment.getAlignmentsIndex());
            if (leftParts.get() == maxLeftParts)
                break;
        }
    }

    public void searchOverlaps(OutputPort<VDJCAlignments> input) {
        for (final VDJCAlignments alignment : CUtils.it(input)) {
            totalProcessed.incrementAndGet();

            if (alignment.getFeature(GeneFeature.CDR3) != null) {
                withCDR3.incrementAndGet();
                if (!overlappedOnly) {
                    outputAlignments.incrementAndGet();
                    output.put(alignment);
                }
                continue;
            }

            if (alreadyMergedIds.contains(alignment.getAlignmentsIndex()))
                continue;

            final OverlapSearchResult searchResult = searchOverlaps(alignment, alignerParameters.isAllowChimeras());

            // Common procedure to cancel processing of current input alignment if it fails to pass some good
            // overlap filtering criterion
            final Runnable cancelCurrentResult = () -> {
                if (searchResult != null)
                    searchResult.cancel();
                if (writePartial && !overlappedOnly &&
                        notInLeftIndexIds.contains(alignment.getAlignmentsIndex())) {
                    outputAlignments.incrementAndGet();
                    partialAlignmentsAsIs.incrementAndGet();
                    output.put(alignment);
                }
            };

            if (searchResult == null) {
                cancelCurrentResult.run();
                continue;
            }
            List<AlignedTarget> mergedTargets = searchResult.result;
            VDJCMultiRead mRead = new VDJCMultiRead(mergedTargets);

            final VDJCAlignments mAlignment = aligner.process(mRead.toTuple(), mRead)
                    .setTagCount(searchResult.tagCount) // note: both parts have the same tag tuple
                    .setHistory(mRead.getHistory(), mRead.getOriginalSequences())
                    .setOriginalReads(mRead.getOriginalReads());

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
            int actualNRegionLengthInOverlap = nRegionInOverlap.totalLength();

            if (actualNRegionLengthInOverlap < minimalN ||
                    1.0f * actualNRegionLengthInOverlap / actualNRegionLength < minimalNOverlapShare) {
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
            outputAlignments.incrementAndGet();

            output.put(mAlignment.shiftIndelsAtHomopolymers(geneTypesToShiftIndels));

            // Saving alignment that where merge to prevent its use as left part
            alreadyMergedIds.add(alignment.getAlignmentsIndex());
            alreadyMergedIds.add(searchResult.KMerInfo.alignments.getAlignmentsIndex());
        }

        if (writePartial && !overlappedOnly)
            for (List<KMerInfo> kMerInfos : kToIndexLeft.valueCollection())
                for (KMerInfo kMerInfo : kMerInfos)
                    if (alreadyMergedIds.add(kMerInfo.alignments.getAlignmentsIndex())) {
                        outputAlignments.incrementAndGet();
                        partialAlignmentsAsIs.incrementAndGet();
                        output.put(kMerInfo.getAlignments());
                    }
    }

    static class OverlapSearchResult {
        final List<KMerInfo> originKMerList;
        final KMerInfo KMerInfo;
        final List<AlignedTarget> result;
        final TagCount tagCount;

        public OverlapSearchResult(List<KMerInfo> originKMerList, KMerInfo KMerInfo, List<AlignedTarget> result, TagCount tagCount) {
            this.originKMerList = originKMerList;
            this.KMerInfo = KMerInfo;
            this.result = result;
            this.tagCount = tagCount;
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

        TagCount rightTagCount = rightAl.getTagCount();

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

                if (match.size() > maxLeftMatches)
                    maxRightMatchesLimitReached = true;

                out:
                for (int i = 0, size = Math.min(maxLeftMatches, match.size()); i < size; i++) {
                    boolean isOverOverlapped = false;
                    final VDJCAlignments leftAl = match.get(i).getAlignments();

                    // if (!Objects.equals(extractTagTuple(leftAl), tagTuple))
                    //     continue;

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

            TagCount leftTagCount = leftAl.getTagCount();

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

            TagCountAggregator tcBuilder = new TagCountAggregator();
            tcBuilder.add(leftTagCount);
            tcBuilder.add(rightTagCount);

            // Ordering and filtering final targets
            return new OverlapSearchResult(maxOverlapList, left, AlignedTarget.orderTargets(result), tcBuilder.createAndDestroy());
        }
    }

    private static String mergeTypePrefix(boolean usingAlignment) {
        return usingAlignment ? "A" : "S";
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

    // private static TagTuple extractTagTuple(VDJCAlignments alignments) {
    //     TagCounter tagCounter = alignments.getTagCounter();
    //     if (tagCounter.size() > 1)
    //         throw new IllegalArgumentException();
    //     if (tagCounter.size() == 0)
    //         return null;
    //     final TObjectDoubleIterator<TagTuple> it = tagCounter.iterator();
    //     it.advance();
    //     return it.key();
    // }

    private boolean addLeftToIndex(VDJCAlignments alignment) {
        int leftTargetId = getLeftPartitionedSequence(alignment);
        if (leftTargetId == -1)
            return false;
        VDJCPartitionedSequence left = alignment.getPartitionedTarget(leftTargetId);
        NSequenceWithQuality seq = left.getSequence();

        int kFromFirst = left.getPartitioning().getPosition(ReferencePoint.VEndTrimmed) + kOffset;
        if (kFromFirst < 0 || kFromFirst + kValue >= seq.size()) {
            leftTooShortNRegion.incrementAndGet();
            return false;
        }

        // TagTuple tagTuple = extractTagTuple(alignment);
        for (int kFrom = kFromFirst; kFrom < seq.size() - kValue; ++kFrom) {
            long kmer = kMer(seq.getSequence(), kFrom, kValue);
            if (kmer == -1) {
                droppedWildcardsInKMer.incrementAndGet();
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

    @Override
    protected PartialAlignmentsAssembler that() {
        return this;
    }

    @Override
    public PartialAlignmentsAssemblerReport buildReport() {
        if (!writePartial && !overlappedOnly && outputAlignments.get() != overlapped.get() + partialAlignmentsAsIs.get() + withCDR3.get())
            throw new AssertionError();

        return new PartialAlignmentsAssemblerReport(
                getDate(),
                getCommandLine(),
                getInputFiles(),
                getOutputFiles(),
                getExecutionTimeMillis(),
                getVersion(),
                independentRuns.get(),
                totalProcessed.get(),
                outputAlignments.get(),
                withCDR3.get(),
                overlapped.get(),
                leftTooShortNRegion.get(),
                kMerDiversity.get(),
                droppedWildcardsInKMer.get(),
                droppedSmallOverlapNRegion.get(),
                droppedNoNRegion.get(),
                leftParts.get(),
                rightParts.get(),
                complexOverlapped.get(),
                overoverlapped.get(),
                partialAlignmentsAsIs.get()
        );
    }

    private static long kMer(NucleotideSequence seq, int from, int length) {
        long kmer = 0; // tagTuple == null ? 0 : tagTuple.hashCode();
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
}

// vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv     jjjjjjjjjjjjjjjjjjjjjcccccccccccccccccccccccc
//                        -------------------->             <--------------------
//        ----------------->            <--------------------

//   ------>            -------------------->                    <---------------
//            -------->                  <--------------------

//  ------------------>   -------------------->            <--------------------
//        ----------------->     <--------------------

