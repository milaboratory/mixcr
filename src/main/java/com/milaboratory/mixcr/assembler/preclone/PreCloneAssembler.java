package com.milaboratory.mixcr.assembler.preclone;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPort;
import cc.redberry.pipe.util.DummyInputPort;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.mitool.consensus.ConsensusResult;
import com.milaboratory.mitool.consensus.GConsensusAssembler;
import com.milaboratory.mitool.helpers.GroupOP;
import com.milaboratory.mitool.helpers.PipeKt;
import com.milaboratory.mixcr.basictypes.GeneAndScore;
import com.milaboratory.mixcr.assembler.VDJCGeneAccumulator;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.mixcr.basictypes.tag.TagCountAggregator;
import com.milaboratory.mixcr.basictypes.tag.TagTuple;
import gnu.trove.map.hash.TObjectIntHashMap;
import gnu.trove.set.hash.TIntHashSet;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCGeneId;
import kotlin.jvm.functions.Function1;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class PreCloneAssembler {
    private final PreCloneAssemblerReport report = new PreCloneAssemblerReport();
    private final AtomicInteger idGenerator = new AtomicInteger();
    private final PreCloneAssemblerParameters parameters;
    private final OutputPort<GroupOP<VDJCAlignments, TagTuple>> alignmentsReader1, alignmentsReader2;

    public PreCloneAssembler(PreCloneAssemblerParameters parameters,
                             OutputPort<VDJCAlignments> alignmentsReader1,
                             OutputPort<VDJCAlignments> alignmentsReader2) {
        this.parameters = parameters;
        Function1<VDJCAlignments, TagTuple> gFunction = a -> a.getTagCount().asKeyPrefixOrError(parameters.groupingLevel);
        this.alignmentsReader1 = PipeKt.group(alignmentsReader1, gFunction);
        this.alignmentsReader2 = PipeKt.group(alignmentsReader2, gFunction);
    }

    public PreCloneAssemblerReport getReport() {
        return report;
    }

    public List<PreCloneWithAlignments> getForNextGroup() {
        GroupOP<VDJCAlignments, TagTuple> grp1 = alignmentsReader1.take();

        if (grp1 == null)
            return null;

        report.inputGroups.incrementAndGet();

        List<NSequenceWithQuality[]> assemblerInput = new ArrayList<>();
        // Alignment infos for alignments containing all assembling features
        // (i.e. for the rows from assemblerInput)
        List<AlignmentInfo> alignmentInfos = new ArrayList<>();

        // Pre-allocated data row
        NSequenceWithQuality[] row = new NSequenceWithQuality[parameters.assemblingFeatures.length];

        // Index inside the group
        int localIdx = -1;

        // Step #1
        // First Pass over the data; Building input dataset for the consensus assembler and collecting
        // required information about alignments for the step #3

        outer:
        for (VDJCAlignments al : CUtils.it(grp1)) {
            localIdx++;
            for (int i = 0; i < parameters.assemblingFeatures.length; i++)
                if ((row[i] = al.getFeature(parameters.assemblingFeatures[i])) == null)
                    continue outer;

            assemblerInput.add(row);
            EnumMap<GeneType, GeneAndScore[]> geneAndScores = new EnumMap<>(GeneType.class);
            for (GeneType gt : GeneType.VJC_REFERENCE) {
                VDJCHit[] hits = al.getHits(gt);
                GeneAndScore[] gss = new GeneAndScore[hits.length];
                for (int i = 0; i < hits.length; i++)
                    gss[i] = hits[i].getGeneAndScore();
                geneAndScores.put(gt, gss);
            }
            alignmentInfos.add(new AlignmentInfo(localIdx, al.getAlignmentsIndex(), al.getMinReadId(),
                    al.getTagCount().keySuffixes(parameters.groupingLevel), geneAndScores));

            // Allocating array for the next data row
            row = new NSequenceWithQuality[parameters.assemblingFeatures.length];
        }

        assert alignmentInfos.size() == assemblerInput.size();

        report.inputAlignments.addAndGet(localIdx);

        if (assemblerInput.isEmpty()) {
            CUtils.drainWithoutClose(alignmentsReader2.take(), DummyInputPort.INSTANCE);
            return Collections.emptyList();
        }

        // Step #2
        // Building consensuses from the records collected on the step #1, and creating indices for
        // clonotype assignment of alignments left after the previous step

        GConsensusAssembler gAssembler = new GConsensusAssembler(parameters.assemblerParameters, assemblerInput);
        List<ConsensusResult> consensuses = gAssembler.calculateConsensuses();
        // TODO <-- leave only top consensus for UMI-based analysis
        int numberOfClones = consensuses.size();
        report.clonotypes.addAndGet(numberOfClones);
        report.clonotypesPerGroup.get(numberOfClones).incrementAndGet();

        // Accumulates V, J and C gene information for each consensus
        // noinspection unchecked
        Map<GeneType, List<GeneAndScore>>[] geneInfos = new Map[numberOfClones];
        // Saves local record indices, assigned to each of the consensuses
        TIntHashSet[] contents = new TIntHashSet[numberOfClones];
        // I.e. UMIs for CELL-barcode-only assembly use-case
        // Set<TagTuple>[] tagSuffixess = new Set[numberOfClones];

        // Union of all contents
        // BitArray allAssignedToClonotypes = new BitArray((int) grp1.getCount());

        // Tag suffixes unambiguously linked to a clonotype (store cloneId+1; -1 for ambiguous cases; 0 - not found)
        TObjectIntHashMap<TagTuple> tagSuffixToCloneId = new TObjectIntHashMap<>();
        // V, J and C genes unambiguously linked to a clonotype (store cloneId+1; -1 for ambiguous cases; 0 - not found)
        TObjectIntHashMap<VDJCGeneId> vjcGenesToCloneId = new TObjectIntHashMap<>();

        // Special map to simplify collection of additional information from already assigned alignment
        // -1 = not assigned to any consensus / clonotype
        // 0  = no assembling feature (target for empirical assignment)
        // >0 = assigned to a specific consensus / clonotype (store cloneId+1)
        int[] alignmentIndexToClonotypeIndex = new int[(int) grp1.getCount()];

        for (int cIdx = 0; cIdx < numberOfClones; cIdx++) {
            ConsensusResult c = consensuses.get(cIdx);
            VDJCGeneAccumulator acc = new VDJCGeneAccumulator();
            TIntHashSet content = new TIntHashSet();
            Set<TagTuple> tagSuffixes = new HashSet<>();

            int rIdx = -1;
            while ((rIdx = c.recordsUsed.nextBit(rIdx + 1)) != -1) {
                AlignmentInfo ai = alignmentInfos.get(rIdx);
                acc.accumulate(ai.genesAndScores);
                content.add(ai.localIdx);
                tagSuffixes.addAll(ai.tagSuffixes);
                alignmentIndexToClonotypeIndex[ai.localIdx] = cIdx + 1;
                report.coreAlignments.incrementAndGet();
            }

            // allAssignedToClonotypes.or(content);

            geneInfos[cIdx] = acc.aggregateInformation(parameters.relativeMinScores);
            contents[cIdx] = content;
            // tagSuffixess[cIdx] = tagSuffixes;

            for (TagTuple ts : tagSuffixes)
                if (tagSuffixToCloneId.get(ts) > 0)
                    tagSuffixToCloneId.put(ts, -1); // ambiguity detected
                else
                    tagSuffixToCloneId.put(ts, cIdx + 1);

            for (GeneType gt : GeneType.VJ_REFERENCE) {
                List<GeneAndScore> gss = geneInfos[cIdx].get(gt);
                if (gss == null)
                    continue;
                for (GeneAndScore gs : gss)
                    if (vjcGenesToCloneId.get(gs.geneId) > 0)
                        vjcGenesToCloneId.put(gs.geneId, -1);
                    else
                        vjcGenesToCloneId.put(gs.geneId, cIdx + 1);
            }
        }

        // Information from the alignments with assembling features, but not assigned to any contigs interpreted as
        // ambiguous
        for (AlignmentInfo ai : alignmentInfos) {
            if (alignmentIndexToClonotypeIndex[ai.localIdx] > 0)
                continue;

            alignmentIndexToClonotypeIndex[ai.localIdx] = -1;

            report.discardedCoreAlignments.incrementAndGet();

            for (TagTuple ts : ai.tagSuffixes)
                tagSuffixToCloneId.put(ts, -1);

            for (GeneType gt : GeneType.VJ_REFERENCE) {
                GeneAndScore[] gss = ai.genesAndScores.get(gt);
                if (gss == null)
                    continue;
                for (GeneAndScore gs : gss)
                    vjcGenesToCloneId.put(gs.geneId, -1);
            }
        }

        // Step #3
        // Assigning leftover alignments

        TagCountAggregator[] coreTagCountAggregators = new TagCountAggregator[numberOfClones];
        TagCountAggregator[] fullTagCountAggregators = new TagCountAggregator[numberOfClones];
        for (int cIdx = 0; cIdx < numberOfClones; cIdx++) {
            coreTagCountAggregators[cIdx] = new TagCountAggregator();
            fullTagCountAggregators[cIdx] = new TagCountAggregator();
        }

        GroupOP<VDJCAlignments, TagTuple> grp2 = alignmentsReader2.take();
        assert grp1.getKey().equals(grp2.getKey()) : "" + grp1.getKey() + " != " + grp2.getKey();

        localIdx = -1;
        for (VDJCAlignments al : CUtils.it(grp2)) {
            localIdx++;

            int cIdxP1 = alignmentIndexToClonotypeIndex[localIdx];

            // Running empirical assignment for the alignments not yet assigned
            if (cIdxP1 == 0) {
                // V and J gene based assignment
                for (GeneType gt : GeneType.VJ_REFERENCE)
                    for (VDJCHit hit : al.getHits(gt)) {
                        int cp1 = vjcGenesToCloneId.get(hit.getGene().getId());
                        if (cp1 <= 0)
                            continue;
                        report.vjEmpiricallyAssignedAlignments.incrementAndGet();
                        if (cIdxP1 == 0)
                            cIdxP1 = cp1;
                        else if (cIdxP1 != cp1)
                            cIdxP1 = -1;
                    }

                // TagSuffix based assignment
                for (TagTuple ts : al.getTagCount().keySuffixes(parameters.groupingLevel)) {
                    int cp1 = tagSuffixToCloneId.get(ts);
                    if (cp1 <= 0)
                        continue;
                    report.umiEmpiricallyAssignedAlignments.incrementAndGet();
                    if (cIdxP1 == 0)
                        cIdxP1 = cp1;
                    else if (cIdxP1 != cp1)
                        cIdxP1 = -1;
                }

                if (cIdxP1 > 0) {
                    // Adding alignment to the clone
                    contents[cIdxP1 - 1].add(localIdx);
                } else if (cIdxP1 == -1)
                    report.empiricalAssignmentConflicts.incrementAndGet();
            } else if (cIdxP1 > 0)
                // Using second iteration over the alignments to assemble TagCounters from the alignments assigned to
                // clonotypes based on their contig assignment
                coreTagCountAggregators[cIdxP1 - 1].add(al.getTagCount());

            if (cIdxP1 > 0)
                fullTagCountAggregators[cIdxP1 - 1].add(al.getTagCount());
            else
                report.unassignedAlignments.incrementAndGet();
        }

        List<PreCloneWithAlignments> result = new ArrayList<>();
        for (int cIdx = 0; cIdx < numberOfClones; cIdx++) {
            ConsensusResult.SingleConsensus[] cs = consensuses.get(cIdx).consensuses;
            NSequenceWithQuality[] clonalSequence = new NSequenceWithQuality[cs.length];
            for (int i = 0; i < cs.length; i++)
                clonalSequence[i] = cs[i].consensus;
            result.add(new PreCloneWithAlignments(
                    new PreClone(
                            idGenerator.incrementAndGet(),
                            grp1.getKey(),
                            coreTagCountAggregators[cIdx].createAndDestroy(),
                            fullTagCountAggregators[cIdx].createAndDestroy(),
                            clonalSequence,
                            geneInfos[cIdx]),
                    contents[cIdx]
            ));
        }

        return result;
    }

    private static final class AlignmentInfo {
        final int localIdx;
        final long alignmentId, minReadId;
        final Set<TagTuple> tagSuffixes;
        final EnumMap<GeneType, GeneAndScore[]> genesAndScores;

        public AlignmentInfo(int localIdx, long alignmentId, long minReadId,
                             Set<TagTuple> tagSuffixes, EnumMap<GeneType, GeneAndScore[]> genesAndScores) {
            this.localIdx = localIdx;
            this.alignmentId = alignmentId;
            this.minReadId = minReadId;
            this.tagSuffixes = tagSuffixes;
            this.genesAndScores = genesAndScores;
        }
    }
}
