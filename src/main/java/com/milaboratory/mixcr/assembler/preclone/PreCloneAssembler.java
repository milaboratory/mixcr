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
package com.milaboratory.mixcr.assembler.preclone;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPort;
import cc.redberry.pipe.util.DummyInputPort;
import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.alignment.BandedLinearAligner;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mitool.consensus.ConsensusResult;
import com.milaboratory.mitool.consensus.GConsensusAssembler;
import com.milaboratory.mixcr.assembler.ClonalSequenceExtractionListener;
import com.milaboratory.mixcr.assembler.VDJCGeneAccumulator;
import com.milaboratory.mixcr.basictypes.GeneAndScore;
import com.milaboratory.mixcr.basictypes.HasRelativeMinScore;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.mixcr.basictypes.tag.TagCount;
import com.milaboratory.mixcr.basictypes.tag.TagCountAggregator;
import com.milaboratory.mixcr.basictypes.tag.TagTuple;
import com.milaboratory.util.GroupByPipeKt;
import com.milaboratory.util.GroupOP;
import gnu.trove.iterator.TIntIntIterator;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.iterator.TObjectDoubleIterator;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import io.repseq.core.*;
import kotlin.jvm.functions.Function1;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public final class PreCloneAssembler {
    /**
     * Reference points that will be projected onto the assembled consensus / pre-clone. Important Note: all the points
     * are alignment attached and not checked for continuity.
     */
    private static final ReferencePoint[] ReferencePointsToProject = {
            ReferencePoint.VEndTrimmed,
            ReferencePoint.DBeginTrimmed,
            ReferencePoint.DEndTrimmed,
            ReferencePoint.JBeginTrimmed
    };

    private final PreCloneAssemblerReportBuilder report = new PreCloneAssemblerReportBuilder();
    private final AtomicLong idGenerator = new AtomicLong();

    private final PreCloneAssemblerParameters parameters;
    private final GeneFeature[] assemblingFeatures;
    private final int groupingLevel;

    private final HasRelativeMinScore relativeMinScores;

    private final Function1<VDJCAlignments, TagTuple> groupingFunction;
    private final OutputPort<GroupOP<VDJCAlignments, TagTuple>> alignmentsReader1, alignmentsReader2;
    private ClonalSequenceExtractionListener extractionListener;

    public PreCloneAssembler(PreCloneAssemblerParameters parameters,
                             GeneFeature[] assemblingFeatures,
                             int groupingLevel,
                             OutputPort<VDJCAlignments> alignmentsReader1,
                             OutputPort<VDJCAlignments> alignmentsReader2,
                             HasRelativeMinScore relativeMinScores) {
        Objects.requireNonNull(parameters);
        this.parameters = parameters;
        this.assemblingFeatures = assemblingFeatures;
        this.groupingLevel = groupingLevel;
        this.groupingFunction = groupingFunction(groupingLevel);
        this.alignmentsReader1 = GroupByPipeKt.groupAlreadySorted(alignmentsReader1, groupingFunction);
        this.alignmentsReader2 = GroupByPipeKt.groupAlreadySorted(alignmentsReader2, groupingFunction);
        this.relativeMinScores = relativeMinScores;
    }

    public void setExtractionListener(ClonalSequenceExtractionListener extractionListener) {
        if (this.extractionListener != null)
            throw new IllegalStateException("extractionListener is already set");
        this.extractionListener = extractionListener;
    }

    private static Function1<VDJCAlignments, TagTuple> groupingFunction(int depth) {
        return a -> a.getTagCount().asKeyPrefixOrError(depth);
    }

    public Function1<VDJCAlignments, TagTuple> getGroupingFunction() {
        return groupingFunction;
    }

    public PreCloneAssemblerParameters getParameters() {
        return parameters;
    }

    public PreCloneAssemblerReportBuilder getReport() {
        return report;
    }

    public PreCloneAssemblerResult getForNextGroup() {
        GroupOP<VDJCAlignments, TagTuple> grp1 = alignmentsReader1.take();

        if (grp1 == null)
            return null;

        report.inputGroups.incrementAndGet();

        List<NSequenceWithQuality[]> assemblerInput = new ArrayList<>();
        // Alignment infos for alignments containing all assembling features
        // (i.e. for the rows from assemblerInput)
        List<AlignmentInfo> alignmentInfos = new ArrayList<>();

        // Pre-allocated data row
        NSequenceWithQuality[] row = new NSequenceWithQuality[assemblingFeatures.length];

        // Index inside the group
        int localIdx = -1;

        // Collecting distribution of tag suffixes (i.e. distribution of UMIs inside the CELL-tag groups)
        // for downstream decontamination / filtering
        TagCountAggregator totalSuffixCountAgg = parameters.minTagSuffixShare > 0 ? new TagCountAggregator() : null;

        // Step #1
        // First Pass over the data; Building input dataset for the consensus assembler and collecting
        // required information about alignments for the step #3

        outer:
        for (VDJCAlignments al : CUtils.it(grp1)) {
            localIdx++;
            for (int sr = 0; sr < assemblingFeatures.length; sr++)
                if ((row[sr] = al.getFeature(assemblingFeatures[sr])) == null) {
                    if (extractionListener != null) {
                        extractionListener.onFailedToExtractClonalSequence(al);
                    }
                    continue outer;
                }

            assemblerInput.add(row);
            EnumMap<GeneType, List<GeneAndScore>> geneAndScores = new EnumMap<>(GeneType.class);
            for (GeneType gt : GeneType.VJC_REFERENCE) {
                VDJCHit[] hits = al.getHits(gt);
                List<GeneAndScore> gss = new ArrayList<>(hits.length);
                for (VDJCHit hit : hits) gss.add(hit.getGeneAndScore());
                geneAndScores.put(gt, gss);
            }

            TagCount suffixCount = al.getTagCount().keySuffixes(groupingLevel);
            alignmentInfos.add(new AlignmentInfo(localIdx, al.getAlignmentsIndex(), al.getMinReadId(),
                    suffixCount, geneAndScores));

            // Collecting suffix tag stats
            if (totalSuffixCountAgg != null)
                totalSuffixCountAgg.add(suffixCount);

            // Allocating array for the next data row
            row = new NSequenceWithQuality[assemblingFeatures.length];
        }

        assert alignmentInfos.size() == assemblerInput.size();

        report.inputAssemblingFeatureSequences.addAndGet(alignmentInfos.size());
        report.inputAlignments.addAndGet(localIdx + 1);

        if (assemblerInput.isEmpty()) {
            CUtils.drainWithoutClose(alignmentsReader2.take(), DummyInputPort.INSTANCE);
            report.groupsWithNoAssemblingFeature.incrementAndGet();
            return new PreCloneAssemblerResult(Collections.emptyList(), null);
        }

        // Step #2
        // Building consensuses from the records collected on the step #1, and creating indices for empirical
        // clonotype assignment for alignments left after the previous step

        GConsensusAssembler gAssembler = new GConsensusAssembler(parameters.assembler, assemblerInput);
        List<ConsensusResult> rawConsensuses = gAssembler.calculateConsensuses();

        // Special map to simplify collection of additional information from already assigned alignment
        // -1 = not assigned to any consensus / clonotype
        // 0  = no assembling feature (target for empirical assignment)
        // >0 = assigned to a specific consensus / clonotype (store cloneIdx+1)
        int[] alignmentIdxToCloneIdxP1 = new int[(int) grp1.getCount()];

        // Step #2.5
        // Filtering clonotypes by abundance of tag suffixes
        List<ConsensusInfo> consensuses = new ArrayList<>(rawConsensuses.size());
        TagCount totalSuffixCount = totalSuffixCountAgg == null ? null : totalSuffixCountAgg.createAndDestroy();

        for (ConsensusResult c : rawConsensuses) {
            // Collecting aggregated suffix count of the clonotype
            TagCountAggregator suffixCountAgg = new TagCountAggregator();
            int rIdx = -1;
            while ((rIdx = c.recordsUsed.nextBit(rIdx + 1)) != -1)
                suffixCountAgg.add(alignmentInfos.get(rIdx).suffixCount);

            TagCount suffixCount = suffixCountAgg.createAndDestroy();

            if (totalSuffixCount != null) {
                // Filtering tag suffixes based on
                TagCountAggregator filteredSuffixCount = new TagCountAggregator();
                TObjectDoubleIterator<TagTuple> it = suffixCount.iterator();
                while (it.hasNext()) {
                    it.advance();
                    if (it.value() >= parameters.minTagSuffixShare * totalSuffixCount.get(it.key()))
                        filteredSuffixCount.add(it.key(), it.value());
                }

                if (filteredSuffixCount.isEmpty()) {
                    // Clonotype dropper
                    report.coreClonotypesDroppedByTagSuffix.incrementAndGet();
                    report.coreAlignmentsDroppedByTagSuffix.addAndGet(c.recordsUsed.size());
                    continue;
                }

                suffixCount = filteredSuffixCount.createAndDestroy();
            }

            TIntArrayList records = new TIntArrayList(c.recordsUsed.bitCount());
            while ((rIdx = c.recordsUsed.nextBit(rIdx + 1)) != -1) {
                if (totalSuffixCount == null || suffixCount.containsAll(alignmentInfos.get(rIdx).suffixCount))
                    records.add(rIdx);
                else
                    // Alignments from this consensus with tags filtered as a result of this operation
                    // will be not mapped to any of the clonotypes
                    alignmentIdxToCloneIdxP1[rIdx] = -1;
            }

            consensuses.add(new ConsensusInfo(c, suffixCount, records));
        }

        int numberOfClones = consensuses.size();
        report.clonotypes.addAndGet(numberOfClones);
        report.clonotypesPerGroup.get(numberOfClones).incrementAndGet();
        if (numberOfClones == 0)
            report.assemblingFeatureSequencesInZeroPreClones.addAndGet(alignmentInfos.size());

        // Accumulates V, J and C gene information for each consensus
        // noinspection unchecked
        Map<GeneType, List<GeneAndScore>>[] geneInfos = new Map[numberOfClones];

        // Accumulators of reference points positions
        // rp[cloneIdx][assemblingFeatureIdx][refPointIdx]
        TIntIntHashMap[][][] referencePointStats =
                new TIntIntHashMap[numberOfClones]
                        [assemblingFeatures.length]
                        [ReferencePointsToProject.length];

        // Tag suffixes unambiguously linked to a clonotype
        // will be used for alignments without V and J hits
        // (other more "normal" alignments will be mapped via gatToCloneId map below)
        // (store cloneIdx+1; -1 for ambiguous cases; 0 - not found)
        TObjectIntHashMap<TagTuple> tagSuffixToCloneId = new TObjectIntHashMap<>();
        assert tagSuffixToCloneId.get(1) == 0;
        // V, J and C genes unambiguously linked to a clonotype
        // (store cloneIdx+1; -1 for ambiguous cases; 0 - not found)
        TObjectIntHashMap<VDJCGeneId> vjcGenesToCloneId = new TObjectIntHashMap<>();
        // V, J and C genes + tags unambiguously linked to a clonotype
        // (store cloneIdx+1; -1 for ambiguous cases; 0 - not found)
        TObjectIntHashMap<GeneAndTagSuffix> gatToCloneId = new TObjectIntHashMap<>();

        for (int cIdx = 0; cIdx < numberOfClones; cIdx++) {
            ConsensusInfo c = consensuses.get(cIdx);

            VDJCGeneAccumulator acc = new VDJCGeneAccumulator();
            Set<TagTuple> tagSuffixes = new HashSet<>();

            TIntIterator it = c.records.iterator();
            while (it.hasNext()) {
                AlignmentInfo ai = alignmentInfos.get(it.next());
                acc.accumulate(ai.genesAndScores);
                tagSuffixes.addAll(ai.suffixCount.tuples());
                alignmentIdxToCloneIdxP1[ai.localIdx] = cIdx + 1;
                report.coreAlignments.incrementAndGet();
            }

            geneInfos[cIdx] = acc.aggregateInformation(relativeMinScores);

            for (TagTuple ts : tagSuffixes) {
                int valueInMap = tagSuffixToCloneId.get(ts);
                if (valueInMap > 0) {
                    assert cIdx + 1 != valueInMap;
                    report.umiConflicts.incrementAndGet();
                    tagSuffixToCloneId.put(ts, -1); // ambiguity detected
                } else if (valueInMap == 0)
                    tagSuffixToCloneId.put(ts, cIdx + 1);
            }

            for (GeneType gt : GeneType.VJ_REFERENCE) {
                List<GeneAndScore> gss = geneInfos[cIdx].get(gt);
                if (gss == null)
                    continue;
                for (GeneAndScore gs : gss) {
                    int valueInMap = vjcGenesToCloneId.get(gs.geneId);
                    if (valueInMap > 0) {
                        assert cIdx + 1 != valueInMap;
                        report.geneConflicts.incrementAndGet(gt.ordinal());
                        vjcGenesToCloneId.put(gs.geneId, -1);
                    } else if (valueInMap == 0)
                        vjcGenesToCloneId.put(gs.geneId, cIdx + 1);

                    // In combination with tags
                    for (TagTuple ts : tagSuffixes) {
                        GeneAndTagSuffix gat = new GeneAndTagSuffix(gs.geneId, ts);
                        valueInMap = gatToCloneId.get(gat);
                        if (valueInMap > 0) {
                            assert cIdx + 1 != valueInMap;
                            report.gatConflicts.incrementAndGet();
                            gatToCloneId.put(gat, -1); // ambiguity detected
                        } else if (valueInMap == 0)
                            gatToCloneId.put(gat, cIdx + 1);
                    }
                }
            }
        }

        // Information from the alignments having assembling features, but not assigned to any of the contigs are
        // interpreted as indeed ambiguous
        for (AlignmentInfo ai : alignmentInfos) {
            if (alignmentIdxToCloneIdxP1[ai.localIdx] > 0)
                // This alignment was already assigned to a clone
                continue;

            // Will not participate in empirical alignment assignment
            alignmentIdxToCloneIdxP1[ai.localIdx] = -1;

            report.discardedCoreAlignments.incrementAndGet();

            // TODO add reports here

            for (TagTuple ts : ai.suffixCount.tuples())
                tagSuffixToCloneId.put(ts, -1);

            for (GeneType gt : GeneType.VJ_REFERENCE) {
                List<GeneAndScore> gss = ai.genesAndScores.get(gt);
                if (gss == null || gss.isEmpty())
                    continue;
                for (GeneAndScore gs : gss) {
                    vjcGenesToCloneId.put(gs.geneId, -1);
                    for (TagTuple ts : ai.suffixCount.tuples())
                        gatToCloneId.put(new GeneAndTagSuffix(gs.geneId, ts), -1);
                }
            }
        }

        // Step #3
        // Assigning leftover alignments and collecting reference point positions

        TagCountAggregator[] coreTagCountAggregators = new TagCountAggregator[numberOfClones];
        TagCountAggregator[] fullTagCountAggregators = new TagCountAggregator[numberOfClones];
        for (int cIdx = 0; cIdx < numberOfClones; cIdx++) {
            coreTagCountAggregators[cIdx] = new TagCountAggregator();
            fullTagCountAggregators[cIdx] = new TagCountAggregator();
        }
        int[] cloneCounts = new int[numberOfClones];

        GroupOP<VDJCAlignments, TagTuple> grp2 = alignmentsReader2.take();
        assert grp1.getKey().equals(grp2.getKey()) : "" + grp1.getKey() + " != " + grp2.getKey();

        localIdx = -1;
        for (VDJCAlignments al : CUtils.it(grp2)) {
            localIdx++;

            int cIdxP1 = alignmentIdxToCloneIdxP1[localIdx];

            // Running empirical assignment for the alignments not yet assigned
            if (cIdxP1 == 0) {
                // V and J gene based assignment
                for (GeneType gt : GeneType.VJ_REFERENCE) {
                    for (VDJCHit hit : al.getHits(gt)) {
                        // TagSuffix + V/J-gene based assignment
                        for (TagTuple ts : al.getTagCount().keySuffixes(groupingLevel).tuples()) {
                            int cp1 = gatToCloneId.get(new GeneAndTagSuffix(hit.getGene().getId(), ts));
                            if (cp1 <= 0)
                                continue;
                            if (cIdxP1 == 0) {
                                cIdxP1 = cp1;
                                report.gatEmpiricallyAssignedAlignments.incrementAndGet();
                            } else if (cIdxP1 != cp1)
                                cIdxP1 = -1;
                        }

                        // Back to V and J gene based assignment
                        int cp1 = vjcGenesToCloneId.get(hit.getGene().getId());
                        if (cp1 <= 0)
                            continue;

                        if (cIdxP1 == 0) {
                            cIdxP1 = cp1;
                            report.vjEmpiricallyAssignedAlignments.incrementAndGet();
                        } else if (cIdxP1 != cp1)
                            cIdxP1 = -1;
                    }
                }

                // TagSuffix based assignment
                if (al.getHits(GeneType.Variable).length == 0 && al.getHits(GeneType.Joining).length == 0)
                    for (TagTuple ts : al.getTagCount().keySuffixes(groupingLevel).tuples()) {
                        int cp1 = tagSuffixToCloneId.get(ts);
                        if (cp1 <= 0)
                            continue;
                        if (cIdxP1 == 0) {
                            cIdxP1 = cp1;
                            report.umiEmpiricallyAssignedAlignments.incrementAndGet();
                        } else if (cIdxP1 != cp1)
                            cIdxP1 = -1;
                    }

                // Here cIdxP1 > 0 if any of the empirical methods produced a hit
                // and no conflicts were encountered between methods

                if (cIdxP1 > 0) {
                    // Adding alignment to the clone
                    alignmentIdxToCloneIdxP1[localIdx] = cIdxP1;
                    report.empiricallyAssignedAlignments.incrementAndGet();
                } else if (cIdxP1 == -1)
                    report.empiricalAssignmentConflicts.incrementAndGet();
            } else if (cIdxP1 > 0) {
                int cIdx = cIdxP1 - 1;
                // Using second iteration over the alignments to assemble TagCounters from the alignments assigned to
                // clonotypes based on their contig assignment
                coreTagCountAggregators[cIdx].add(al.getTagCount());

                // and collect statistics on the reference point positions
                GeneFeature[] gfs = assemblingFeatures;
                for (int gfi = 0; gfi < gfs.length; gfi++) {
                    GeneFeature gf = gfs[gfi];
                    NucleotideSequence seq = al.getFeature(gf).getSequence();
                    // Alignment to project alignment positions onto the consensus
                    Alignment<NucleotideSequence> a = BandedLinearAligner.align(
                            parameters.assembler.aAssemblerParameters.scoring,
                            seq.getSequence(),
                            consensuses.get(cIdx).cResult.consensuses[gfi].consensus.getSequence(),
                            parameters.assembler.aAssemblerParameters.bandWidth);
                    for (int rpi = 0; rpi < ReferencePointsToProject.length; rpi++) {
                        ReferencePoint rp = ReferencePointsToProject[rpi];
                        int positionInAlignment = al.getRelativePosition(gf, rp);
                        if (positionInAlignment == -1)
                            continue;
                        int positionInConsensus = a.convertToSeq1Position(positionInAlignment);
                        if (positionInConsensus == -1)
                            continue;
                        if (positionInConsensus < -1)
                            positionInConsensus = -2 - positionInConsensus;
                        TIntIntHashMap map = referencePointStats[cIdx][gfi][rpi];
                        if (map == null) // lazy initialization
                            referencePointStats[cIdx][gfi][rpi] = map = new TIntIntHashMap();
                        map.adjustOrPutValue(positionInConsensus, 1, 1);
                    }
                }
            }

            if (cIdxP1 > 0) {
                int cIdx = cIdxP1 - 1;
                fullTagCountAggregators[cIdx].add(al.getTagCount());
                cloneCounts[cIdx] += al.getNumberOfReads();
            } else
                report.unassignedAlignments.incrementAndGet();
        }

        long cloneIdOffset = idGenerator.getAndAdd(numberOfClones);
        List<PreCloneImpl> result = new ArrayList<>(numberOfClones);
        for (int cIdx = 0; cIdx < numberOfClones; cIdx++) {
            ConsensusResult.SingleConsensus[] cs = consensuses.get(cIdx).cResult.consensuses;
            NSequenceWithQuality[] clonalSequence = new NSequenceWithQuality[cs.length];
            ExtendedReferencePoints[] referencePoints = new ExtendedReferencePoints[cs.length];
            for (int sr = 0; sr < cs.length; sr++) {
                clonalSequence[sr] = cs[sr].consensus;
                ExtendedReferencePointsBuilder rpb = new ExtendedReferencePointsBuilder();
                for (int rpi = 0; rpi < ReferencePointsToProject.length; rpi++) {
                    TIntIntHashMap map = referencePointStats[cIdx][sr][rpi];
                    if (map == null)
                        continue;
                    int maxCount = -1;
                    int maxPosition = -1;
                    TIntIntIterator it = map.iterator();
                    while (it.hasNext()) {
                        it.advance();
                        if (maxCount < it.value()) {
                            maxCount = it.value();
                            maxPosition = it.key();
                        }
                    }
                    rpb.setPosition(ReferencePointsToProject[rpi], maxPosition);
                }
                referencePoints[sr] = rpb.build();
            }

            TagCount fullTagCount = fullTagCountAggregators[cIdx].createAndDestroy();
            assert cloneCounts[cIdx] == fullTagCount.sum();

            result.add(new PreCloneImpl(
                    cloneIdOffset + cIdx,
                    grp1.getKey(),
                    coreTagCountAggregators[cIdx].createAndDestroy(),
                    fullTagCount,
                    clonalSequence,
                    geneInfos[cIdx],
                    referencePoints,
                    cloneCounts[cIdx]));
        }

        long[] resultAlToClone = new long[alignmentIdxToCloneIdxP1.length];
        for (int i = 0; i < resultAlToClone.length; i++)
            resultAlToClone[i] = alignmentIdxToCloneIdxP1[i] <= 0
                    ? -1
                    : cloneIdOffset + alignmentIdxToCloneIdxP1[i] - 1;

        return new PreCloneAssemblerResult(result, resultAlToClone);
    }

    private static final class GeneAndTagSuffix {
        final VDJCGeneId gene;
        final TagTuple tag;

        public GeneAndTagSuffix(VDJCGeneId gene, TagTuple tag) {
            this.gene = gene;
            this.tag = tag;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            GeneAndTagSuffix that = (GeneAndTagSuffix) o;
            return gene.equals(that.gene) && tag.equals(that.tag);
        }

        @Override
        public int hashCode() {
            return Objects.hash(gene, tag);
        }
    }

    private static final class ConsensusInfo {
        final ConsensusResult cResult;
        final TagCount suffixCount;
        final TIntArrayList records;

        public ConsensusInfo(ConsensusResult cResult, TagCount suffixCount, TIntArrayList records) {
            this.cResult = cResult;
            this.suffixCount = suffixCount;
            this.records = records;
        }
    }

    private static final class AlignmentInfo {
        final int localIdx;
        final long alignmentId, minReadId;
        final TagCount suffixCount;
        final EnumMap<GeneType, List<GeneAndScore>> genesAndScores;

        public AlignmentInfo(int localIdx, long alignmentId, long minReadId,
                             TagCount suffixCount, EnumMap<GeneType, List<GeneAndScore>> genesAndScores) {
            this.localIdx = localIdx;
            this.alignmentId = alignmentId;
            this.minReadId = minReadId;
            this.suffixCount = suffixCount;
            this.genesAndScores = genesAndScores;
        }
    }
}
