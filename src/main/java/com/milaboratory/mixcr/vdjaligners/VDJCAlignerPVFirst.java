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
package com.milaboratory.mixcr.vdjaligners;

import com.milaboratory.core.PairedEndReadsLayout;
import com.milaboratory.core.Target;
import com.milaboratory.core.alignment.*;
import com.milaboratory.core.alignment.batch.AlignmentHit;
import com.milaboratory.core.alignment.batch.AlignmentHitImpl;
import com.milaboratory.core.alignment.batch.BatchAlignerWithBaseWithFilter;
import com.milaboratory.core.alignment.kaligner1.AbstractKAlignerParameters;
import com.milaboratory.core.alignment.kaligner1.KAlignerParameters;
import com.milaboratory.core.alignment.kaligner2.KAlignerParameters2;
import com.milaboratory.core.io.sequence.SequenceRead;
import com.milaboratory.core.io.sequence.SingleRead;
import com.milaboratory.core.io.sequence.SingleReadImpl;
import com.milaboratory.core.merger.MergerParameters;
import com.milaboratory.core.sequence.NSQTuple;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.HasGene;
import com.milaboratory.mixcr.basictypes.SequenceHistory;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.mixcr.basictypes.tag.TagCount;
import com.milaboratory.mixcr.partialassembler.AlignedTarget;
import com.milaboratory.mixcr.partialassembler.TargetMerger;
import com.milaboratory.util.BitArray;
import io.repseq.core.*;

import java.util.*;

public final class VDJCAlignerPVFirst extends VDJCAlignerAbstract {
    private static final ReferencePoint reqPointR = ReferencePoint.CDR3End.move(-3);
    private static final ReferencePoint reqPointL = ReferencePoint.CDR3Begin.move(+3);
    // Used in case of AMerge
    private VDJCAlignerS sAligner = null;
    private final TargetMerger alignmentsMerger;
    private BatchAlignerWithBaseWithFilter<NucleotideSequence, VDJCGene, AlignmentHit<NucleotideSequence, VDJCGene>>
            vAlignerNotFloatingLeft,
            vAlignerNotFloatingRight;


    public VDJCAlignerPVFirst(VDJCAlignerParameters parameters) {
        super(parameters);
        MergerParameters mp = parameters.getMergerParameters().overrideReadsLayout(PairedEndReadsLayout.CollinearDirect);
        alignmentsMerger = new TargetMerger(mp, parameters, (float) parameters.getMergerParameters().getMinimalIdentity());
    }

    public void setSAligner(VDJCAlignerS sAligner) {
        if (this.sAligner != null)
            throw new IllegalStateException("Single aligner is already set.");
        this.sAligner = sAligner;
    }

    @Override
    protected void init() {
        super.init();
        vAlignerNotFloatingLeft = vAligner.setFloatingLeftBound(false);
        vAlignerNotFloatingRight = vAligner.setFloatingRightBound(false);
    }

    @Override
    protected VDJCAlignments process0(NSQTuple input) {
        Target[] targets = parameters.getReadsLayout().createTargets(input);
        // Creates helper classes for each PTarget
        PAlignmentHelper[] helpers = createInitialHelpers(targets);

        VDJCAlignments alignment = parameters.getAllowPartialAlignments() ?
                processPartial(input, helpers) :
                processStrict(input, helpers);

        // if sAligner == null (which means --no-merge option), no merge will be performed
        if (alignment != null && sAligner != null) {
            final TargetMerger.TargetMergingResult mergeResult = alignmentsMerger.merge(
                    new AlignedTarget(alignment, 0),
                    new AlignedTarget(alignment, 1),
                    false);

            if (mergeResult.failedDueInconsistentAlignments()) {
                GeneType geneType = mergeResult.getFailedMergedGeneType();
                int removeId =
                        alignment.getBestHit(geneType).getAlignment(0).getScore()
                                > alignment.getBestHit(geneType).getAlignment(1).getScore()
                                ? 1 : 0;
                if (listener != null)
                    listener.onTopHitSequenceConflict(alignment, geneType);
                return alignment.removeBestHitAlignment(geneType, removeId);
            } else if (mergeResult.isSuccessful()) {
                assert mergeResult.isUsingAlignments();
                NSequenceWithQuality alignedTarget = mergeResult.getResult().getTarget();
                VDJCAlignments sAlignments = sAligner.process0(new NSQTuple(input.getId(), alignedTarget));
                if (sAlignments == null)
                    return null;

                // Reconstructing full history: paired-end read, merged (AlignmentOverlapped) and aligned
                sAlignments = sAlignments
                        .setHistory(
                                new SequenceHistory[]{
                                        new SequenceHistory.Merge(
                                                SequenceHistory.OverlapType.AlignmentOverlap,
                                                alignment.getHistory(0),
                                                alignment.getHistory(1),
                                                mergeResult.getOffset(),
                                                mergeResult.getMismatched())},
                                parameters.isSaveOriginalSequence() ? new NSQTuple[]{input} : null
                        );
                if (listener != null)
                    listener.onSuccessfulAlignmentOverlap(sAlignments);
                return sAlignments;
            }
        }

        return alignment;
    }

    private VDJCAlignments processPartial(NSQTuple input, PAlignmentHelper[] helpers) {
        // Calculates which PTarget was aligned with the highest score
        helpers[0].performCDAlignment();
        PAlignmentHelper bestHelper = helpers[0];
        for (int i = 1; i < helpers.length; ++i) {
            helpers[i].performCDAlignment();
            if (bestHelper.score() < helpers[i].score())
                bestHelper = helpers[i];
        }

        if (!bestHelper.hasVOrJHits()) {
            onFailedAlignment(VDJCAlignmentFailCause.NoHits);
            return null;
        }

        // Calculates if this score is bigger then the threshold
        if (bestHelper.score() < parameters.getMinSumScore()) {
            onFailedAlignment(VDJCAlignmentFailCause.LowTotalScore);
            return null;
        }

        // Finally filtering hits inside this helper to meet minSumScore and maxHits limits
        bestHelper.filterHits(parameters.getMinSumScore(), parameters.getMaxHits());

        // TODO do we really need this ?
        if (!bestHelper.hasVOrJHits()) {
            onFailedAlignment(VDJCAlignmentFailCause.LowTotalScore);
            return null;
        }

        VDJCAlignments alignments = bestHelper.createResult(input, this);

        // Final check
        if (!parameters.getAllowNoCDR3PartAlignments()) {
            // CDR3 Begin / End
            boolean containCDR3Parts = false;
            final VDJCHit bestV = alignments.getBestHit(GeneType.Variable);
            final VDJCHit bestJ = alignments.getBestHit(GeneType.Joining);
            for (int i = 0; i < 2; i++) {
                if ((bestV != null && bestV.getAlignment(i) != null
                        && bestV.getAlignment(i).getSequence1Range().getTo()
                        >= bestV.getGene().getPartitioning().getRelativePosition(parameters.getFeatureToAlign(GeneType.Variable), reqPointL))
                        ||
                        (bestJ != null && bestJ.getAlignment(i) != null
                                && bestJ.getAlignment(i).getSequence1Range().getFrom()
                                <= bestJ.getGene().getPartitioning().getRelativePosition(parameters.getFeatureToAlign(GeneType.Joining), reqPointR))) {
                    containCDR3Parts = true;
                    break;
                }
            }

            if (!containCDR3Parts) {
                onFailedAlignment(VDJCAlignmentFailCause.NoCDR3Parts);
                return null;
            }
        }

        // Read successfully aligned

        onSuccessfulAlignment(alignments);
        if (bestHelper.vChimera)
            onSegmentChimeraDetected(GeneType.Variable, alignments);
        if (bestHelper.jChimera)
            onSegmentChimeraDetected(GeneType.Joining, alignments);

        return alignments;
    }

    private VDJCAlignments processStrict(NSQTuple input, PAlignmentHelper[] helpers) {
        // Calculates which PTarget was aligned with the highest score
        PAlignmentHelper bestHelper = helpers[0];
        for (int i = 1; i < helpers.length; ++i)
            if (bestHelper.score() < helpers[i].score())
                bestHelper = helpers[i];

        // If V or J hits are absent
        if (!bestHelper.hasVAndJHits()) {
            if (!bestHelper.hasVOrJHits())
                onFailedAlignment(VDJCAlignmentFailCause.NoHits);
            else if (!bestHelper.hasVHits())
                onFailedAlignment(VDJCAlignmentFailCause.NoVHits);
            else
                onFailedAlignment(VDJCAlignmentFailCause.NoJHits);
            return null;
        }

        // Performing alignment of C and D genes; if corresponding parameters are set include their scores to
        // the total score value
        bestHelper.performCDAlignment();

        // Calculates if this score is bigger then the threshold
        if (bestHelper.score() < parameters.getMinSumScore()) {
            onFailedAlignment(VDJCAlignmentFailCause.LowTotalScore);
            return null;
        }

        // Finally filtering hits inside this helper to meet minSumScore and maxHits limits
        bestHelper.filterHits(parameters.getMinSumScore(), parameters.getMaxHits());

        // If hits for V or J are missing after filtration
        if (!bestHelper.isGoodVJ()) {
            if (!bestHelper.hasVHits())
                onFailedAlignment(VDJCAlignmentFailCause.NoVHits);
            else if (!bestHelper.hasJHits())
                onFailedAlignment(VDJCAlignmentFailCause.NoJHits);
            else if (!bestHelper.hasVJOnTheSameTarget())
                onFailedAlignment(VDJCAlignmentFailCause.VAndJOnDifferentTargets);
            else
                onFailedAlignment(VDJCAlignmentFailCause.LowTotalScore);

            return null;
        }

        // Read successfully aligned

        VDJCAlignments alignments = bestHelper.createResult(input, this);

        onSuccessfulAlignment(alignments);
        if (bestHelper.vChimera)
            onSegmentChimeraDetected(GeneType.Variable, alignments);
        if (bestHelper.jChimera)
            onSegmentChimeraDetected(GeneType.Joining, alignments);

        return alignments;
    }

    PAlignmentHelper[] createInitialHelpers(Target[] target) {
        PAlignmentHelper[] result = new PAlignmentHelper[target.length];
        for (int i = 0; i < target.length; i++)
            result[i] = alignVThenJ(target[i]);
        return result;
    }

    PairedHit[] sortAndFilterHits(final PairedHit[] hits, final KGeneAlignmentParameters parameters) {
        if (hits.length == 0)
            return hits;

        Arrays.sort(hits, SCORE_COMPARATOR);

        //filter due to minSumScore = score(R1) + score(R2) and due to relativeMinScore
        int minSumScore = Math.max(
                parameters.getMinSumScore(),
                (int) (hits[0].sumScore * parameters.getRelativeMinScore())
        );
        return filterHits(hits, minSumScore, this.parameters.getMaxHits());
    }

    static PairedHit[] filterHits(final PairedHit[] hits, final int minSumScore, final int maxHits) {
        int i = 0;
        while (i < hits.length && i < maxHits && hits[i].sumScore >= minSumScore) {
            ++i;
        }
        return i == hits.length ? hits : Arrays.copyOf(hits, i);
    }

    private final ThreadLocal<AlignerCustom.LinearMatrixCache> linearMatrixCache = new ThreadLocal<AlignerCustom.LinearMatrixCache>() {
        @Override
        protected AlignerCustom.LinearMatrixCache initialValue() {
            return new AlignerCustom.LinearMatrixCache();
        }
    };

    private final ThreadLocal<AlignerCustom.AffineMatrixCache> affineMatrixCache = new ThreadLocal<AlignerCustom.AffineMatrixCache>() {
        @Override
        protected AlignerCustom.AffineMatrixCache initialValue() {
            return new AlignerCustom.AffineMatrixCache();
        }
    };

    //TODO delete when KAlignerParameters abstraction finished
    private static int getAbsoluteMinScore(AbstractKAlignerParameters p) {
        if (p instanceof KAlignerParameters)
            return (int) ((KAlignerParameters) p).getAbsoluteMinScore();
        else if (p instanceof KAlignerParameters2)
            return ((KAlignerParameters2) p).getAbsoluteMinScore();
        else throw new RuntimeException();
    }

    @SuppressWarnings("unchecked")
    boolean checkAndEliminateChimera(List<AlignmentHit<NucleotideSequence, VDJCGene>> al1,
                                     List<AlignmentHit<NucleotideSequence, VDJCGene>> al2,
                                     GeneType gt) {
        if (al1.isEmpty() || al2.isEmpty())
            return false;

        for (List<AlignmentHit<NucleotideSequence, VDJCGene>> all : new List[]{al1, al2}) {
            boolean scoreOk = false;
            for (AlignmentHit<NucleotideSequence, VDJCGene> al : all)
                if (al.getAlignment().getScore() > parameters.getMinChimeraDetectionScore()) {
                    scoreOk = true;
                    break;
                }

            if (!scoreOk)
                return false;
        }

        boolean chimera = true;
        OUT:
        for (AlignmentHit<NucleotideSequence, VDJCGene> a1 : al1)
            for (AlignmentHit<NucleotideSequence, VDJCGene> a2 : al2)
                if (a1.getRecordPayload().equals(a2.getRecordPayload())) {
                    chimera = false;
                    break OUT;
                }

        if (!chimera)
            return false;

        // Comparing top hit positions
        if (gt == GeneType.Variable)
            if (al1.get(0).getAlignment().getSequence1Range().getTo() > al2.get(0).getAlignment().getSequence1Range().getTo())
                al2.clear();
            else
                al1.clear();
        else if (al1.get(0).getAlignment().getSequence1Range().getFrom() > al2.get(0).getAlignment().getSequence1Range().getFrom())
            al1.clear();
        else
            al2.clear();

        return true;
    }

    @SuppressWarnings("unchecked")
    PAlignmentHelper alignVThenJ(final Target target) {
        /*
         * Step 1: alignment of V gene
         */

        List<AlignmentHit<NucleotideSequence, VDJCGene>> vAl1, vAl2;

        if (parameters.getLibraryStructure() == VDJCLibraryStructure.R1V) {
            vAl1 = vAlignerNotFloatingRight.align(target.targets[0].getSequence()).getHits();
            vAl2 = vAlignerNotFloatingLeft.align(target.targets[1].getSequence()).getHits();
        } else {
            vAl1 = vAligner.align(target.targets[0].getSequence()).getHits();
            vAl2 = vAligner.align(target.targets[1].getSequence()).getHits();
        }

        /*
         * Step 1.4: force floating bounds = false for ---xxx> <---- and ---> <xxx---- topologies
         */

        if (parameters.isSmartForceEdgeAlignments() && parameters.getLibraryStructure() != VDJCLibraryStructure.R1V) {
            boolean forceRightEdgeInLeft = false, forceLeftEdgeInRight = false;
            for (PairedHit vHit : sortAndFilterHits(createPairedHits(vAl1, vAl2), parameters.getVAlignerParameters())) {
                if (vHit.hit0 == null || vHit.hit1 == null)
                    continue;

                Alignment<NucleotideSequence>
                        lAl = vHit.hit0.getAlignment(),
                        rAl = vHit.hit1.getAlignment();

                if (lAl == null || rAl == null)
                    continue;

                if (lAl.getSequence2Range().getTo() != target.targets[0].size() && rAl.getSequence2Range().getFrom() == 0) {
                    //---xxx> <----
                    forceRightEdgeInLeft = true;
                } else if (lAl.getSequence2Range().getTo() == target.targets[0].size() && rAl.getSequence2Range().getFrom() != 0) {
                    //---> <xxx----
                    forceLeftEdgeInRight = true;
                }
            }

            if (forceRightEdgeInLeft)
                vAl1 = vAlignerNotFloatingRight.align(target.targets[0].getSequence()).getHits();
            if (forceLeftEdgeInRight)
                vAl2 = vAlignerNotFloatingLeft.align(target.targets[1].getSequence()).getHits();

            if (this.listener != null)
                this.listener.onRealignmentWithForcedNonFloatingBound(forceLeftEdgeInRight, forceRightEdgeInLeft);
        }

        /*
         * Step 1.5: eliminating conflicting alignments in favor of alignments covering CDR3 edge
         */

        boolean vChimera = checkAndEliminateChimera(vAl1, vAl2, GeneType.Variable);

        PairedHit[] vHits = createPairedHits(vAl1, vAl2);


        /*
         * Step 2: of round of V gene alignment with more precise algorithm
         */

        for (PairedHit vHit : vHits) {
            if (vHit.hit1 == null) {
                AlignmentHit<NucleotideSequence, VDJCGene> leftHit = vHit.hit0;

                // Checking whether alignment touches right read edge (taking to account tolerance)
                if (leftHit.getAlignment().getSequence2Range().getTo()
                        < target.targets[0].size() - parameters.getAlignmentBoundaryTolerance())
                    continue;

                final AlignmentScoring<NucleotideSequence> scoring = parameters.getVAlignerParameters().getParameters().getScoring();
                final NucleotideSequence sequence2 = target.targets[1].getSequence();
                final NucleotideSequence sequence1 = leftHit.getAlignment().getSequence1();
                final int beginFR3 = leftHit.getRecordPayload().getPartitioning().getRelativePosition(parameters.getFeatureToAlign(GeneType.Variable), ReferencePoint.FR3Begin);
                if (beginFR3 == -1)
                    continue;
                Alignment alignment;
                if (scoring instanceof LinearGapAlignmentScoring)
                    alignment = AlignerCustom.alignLinearSemiLocalLeft0(
                            (LinearGapAlignmentScoring) scoring,
                            sequence1, sequence2,
                            beginFR3, sequence1.size() - beginFR3,
                            0, sequence2.size(),
                            false, true,
                            NucleotideSequence.ALPHABET,
                            linearMatrixCache.get());
                else
                    alignment = AlignerCustom.alignAffineSemiLocalLeft0(
                            (AffineGapAlignmentScoring) scoring,
                            sequence1, sequence2,
                            beginFR3, sequence1.size() - beginFR3,
                            0, sequence2.size(),
                            false, true,
                            NucleotideSequence.ALPHABET,
                            affineMatrixCache.get());

                if (alignment.getScore() < getAbsoluteMinScore(parameters.getVAlignerParameters().getParameters()))
                    continue;

                vHit.set(1, new AlignmentHitImpl<NucleotideSequence, VDJCGene>(alignment, leftHit.getRecordPayload()));
                vHit.calculateScore();
            }
        }

        vHits = sortAndFilterHits(vHits, parameters.getVAlignerParameters());

        /*
         * Step 3: alignment of J gene
         */

        List<AlignmentHit<NucleotideSequence, VDJCGene>> jAl1 = performJAlignment(target, vHits, 0),
                jAl2 = performJAlignment(target, vHits, 1);

        /*
         * Step 3.5: eliminating conflicting alignments in favor of alignments covering CDR3 edge
         */

        boolean jChimera = checkAndEliminateChimera(jAl1, jAl2, GeneType.Joining);

        PairedHit[] jHits = createPairedHits(jAl1, jAl2);

        for (PairedHit jHit : jHits) {
            if (jHit.hit0 == null) {
                AlignmentHit<NucleotideSequence, VDJCGene> rightHit = jHit.hit1;

                // Checking whether alignment touches left read edge (taking to account tolerance)
                if (rightHit.getAlignment().getSequence2Range().getFrom() > parameters.getAlignmentBoundaryTolerance())
                    continue;

                final AlignmentScoring<NucleotideSequence> scoring = parameters.getJAlignerParameters().getParameters().getScoring();
                final NucleotideSequence sequence2 = target.targets[0].getSequence();
                final NucleotideSequence sequence1 = rightHit.getAlignment().getSequence1();

                int begin = 0;
                if (vHits.length != 0 && vHits[0].hit0 != null) {
                    begin = vHits[0].hit0.getAlignment().getSequence2Range().getTo() - parameters.getVJOverlapWindow();
                    if (begin < 0)
                        begin = 0;
                }

                Alignment alignment;
                if (scoring instanceof LinearGapAlignmentScoring)
                    alignment = AlignerCustom.alignLinearSemiLocalRight0(
                            (LinearGapAlignmentScoring) scoring,
                            sequence1, sequence2,
                            0, sequence1.size(),
                            begin, sequence2.size() - begin,
                            false, true,
                            NucleotideSequence.ALPHABET,
                            linearMatrixCache.get());
                else
                    alignment = AlignerCustom.alignAffineSemiLocalRight0(
                            (AffineGapAlignmentScoring) scoring,
                            sequence1, sequence2,
                            0, sequence1.size(),
                            begin, sequence2.size() - begin,
                            false, true,
                            NucleotideSequence.ALPHABET,
                            affineMatrixCache.get());

                if (alignment.getScore() < getAbsoluteMinScore(parameters.getJAlignerParameters().getParameters()))
                    continue;

                jHit.set(0, new AlignmentHitImpl<NucleotideSequence, VDJCGene>(alignment, rightHit.getRecordPayload()));
                jHit.calculateScore();
            }
        }

        jHits = sortAndFilterHits(jHits, parameters.getJAlignerParameters());

        /*
         * Step 4: Filter V/J hits with common chain only
         */

        // Check if parameters allow chimeras
        if (!parameters.isAllowChimeras()) {

            // Calculate common chains
            Chains commonChains = getVJCommonChains(vHits, jHits);
            if (!commonChains.isEmpty()) {


                // Filtering V genes

                int filteredSize = 0;
                for (PairedHit hit : vHits)
                    if (hit.getGene().getChains().intersects(commonChains))
                        ++filteredSize;

                // Perform filtering (new array allocation) only if needed
                if (vHits.length != filteredSize) {
                    PairedHit[] newHits = new PairedHit[filteredSize];
                    filteredSize = 0; // Used as pointer
                    for (PairedHit hit : vHits)
                        if (hit.getGene().getChains().intersects(commonChains))
                            newHits[filteredSize++] = hit;

                    assert newHits.length == filteredSize;

                    vHits = newHits;
                }

                // Filtering J genes

                filteredSize = 0;
                for (PairedHit hit : jHits)
                    if (hit.getGene().getChains().intersects(commonChains))
                        ++filteredSize;

                // Perform filtering (new array allocation) only if needed
                if (jHits.length != filteredSize) {
                    PairedHit[] newHits = new PairedHit[filteredSize];
                    filteredSize = 0; // Used as pointer
                    for (PairedHit hit : jHits)
                        if (hit.getGene().getChains().intersects(commonChains))
                            newHits[filteredSize++] = hit;

                    assert newHits.length == filteredSize;

                    jHits = newHits;
                }
            }
        }

        return new PAlignmentHelper(target, vHits, jHits, vChimera, jChimera);
    }

    /**
     * Preforms J alignment for a single read
     */
    @SuppressWarnings("unchecked")
    List<AlignmentHit<NucleotideSequence, VDJCGene>> performJAlignment(
            final Target target,
            final PairedHit[] vHits,
            final int index) {
        // Getting best V hit
        AlignmentHit<NucleotideSequence, VDJCGene> vHit = vHits.length == 0 ? null : vHits[0].get(index);

        final NucleotideSequence targetSequence = target.targets[index].getSequence();

        BitArray filterForJ = getFilter(GeneType.Joining, vHits);

        if (vHit == null)
            return jAligner.align(targetSequence, 0, targetSequence.size(), filterForJ).getHits();
        //        parameters.getAllowPartialAlignments()
        //                    ? jAligner.align(targetSequence).getHits()
        //                    : Collections.EMPTY_LIST;


        //TODO remove
        if (vHit.getAlignment().getSequence1Range().getTo() <=
                vHit.getRecordPayload().getPartitioning().getRelativePosition(
                        parameters.getFeatureToAlign(GeneType.Variable),
                        ReferencePoint.FR3Begin)
                || vHit.getAlignment().getSequence2Range().getTo() == targetSequence.size())
            return Collections.EMPTY_LIST;

        int jFrom = vHit.getAlignment().getSequence2Range().getTo() - parameters.getVJOverlapWindow();
        jFrom = jFrom < 0 ? 0 : jFrom;
        return jAligner.align(targetSequence,
                jFrom,
                targetSequence.size(), filterForJ).getHits();
    }

    /**
     * Converts two AlignmentResults to an array of paired hits (each paired hit for a particular V of J gene)
     */
    static PairedHit[] createPairedHits(List<AlignmentHit<NucleotideSequence, VDJCGene>>... results) {
        Map<VDJCGeneId, PairedHit> hits = new HashMap<>();
        addHits(hits, results[0], 0);
        addHits(hits, results[1], 1);

        final PairedHit[] pairedHits = hits.values().toArray(new PairedHit[hits.size()]);
        for (PairedHit ph : pairedHits)
            ph.calculateScore();
        return pairedHits;
    }

    static void addHits(Map<VDJCGeneId, PairedHit> hits,
                        List<AlignmentHit<NucleotideSequence, VDJCGene>> result,
                        int index) {
        for (AlignmentHit<NucleotideSequence, VDJCGene> hit : result) {
            VDJCGeneId id = hit.getRecordPayload().getId();
            PairedHit val =
                    index == 0 ?
                            null :
                            hits.get(id);

            if (val == null)
                hits.put(id, val = new PairedHit());

            val.set(index, hit);
        }
    }

    static final PreVDJCHit[] zeroArray = new PreVDJCHit[0];
    @SuppressWarnings("unchecked")
    static final AlignmentHit<NucleotideSequence, VDJCGene>[] zeroKArray = new AlignmentHit[0];

    final class PAlignmentHelper {
        final boolean vChimera, jChimera;
        final Target target;
        PairedHit[] vHits, jHits;
        VDJCHit[] dHits = null, cHits = null;

        PAlignmentHelper(Target target, PairedHit[] vHits, PairedHit[] jHits,
                         boolean vChimera, boolean jChimera) {
            this.target = target;
            this.vHits = vHits;
            this.jHits = jHits;
            this.vChimera = vChimera;
            this.jChimera = jChimera;
        }

        boolean hasVHits() {
            return vHits.length > 0;
        }

        boolean hasJHits() {
            return jHits.length > 0;
        }

        boolean hasVOrJHits() {
            return vHits.length > 0 || jHits.length > 0;
        }

        boolean hasVAndJHits() {
            return vHits.length > 0 && jHits.length > 0;
        }

        boolean isGoodVJ() {
            return hasVAndJHits() && hasVJOnTheSameTarget();
        }

        private boolean hasVJOnTheSameTarget() {
            for (int i = 0; i < 2; i++)
                if (vHits[0].get(i) != null && jHits[0].get(i) != null)
                    return true;
            return false;
        }

        /**
         * Returns sum score for this targets.
         */
        float score() {
            // Adding V score
            float score = vHits.length > 0 ? vHits[0].sumScore : 0.0f;

            // Adding J score
            if (jHits.length > 0)
                score += jHits[0].sumScore;

            // Adding C score
            if (parameters.doIncludeCScore() && cHits != null && cHits.length > 0)
                score += cHits[0].getScore();

            // Adding D score
            if (parameters.doIncludeDScore() && dHits != null && dHits.length > 0)
                score += dHits[0].getScore();

            return score;
        }


        /**
         * Converts this object to a final VDJAlignment object.
         */
        VDJCAlignments createResult(NSQTuple input, VDJCAlignerPVFirst aligner) {
            VDJCHit[] vHits = convert(this.vHits, GeneType.Variable, aligner);
            VDJCHit[] jHits = convert(this.jHits, GeneType.Joining, aligner);

            return new VDJCAlignments(vHits, dHits, jHits, cHits, TagCount.NO_TAGS_1, target.targets,
                    SequenceHistory.RawSequence.of(input.getId(), target),
                    aligner.parameters.isSaveOriginalSequence() ? new NSQTuple[]{input} : null);
        }

        /**
         * Perform final alignment of D and C genes on fully marked-up reads (with by V and J alignments).
         */
        @SuppressWarnings("unchecked")
        void performCDAlignment() {
            PairedHit bestVHit = vHits.length == 0 ? null : vHits[0];
            PairedHit bestJHit = jHits.length == 0 ? null : jHits[0];

            if ((bestVHit == null || bestJHit == null) && !parameters.getAllowPartialAlignments())
                return;

            //Alignment of C gene
            if (cAligner != null) {
                AlignmentHit<NucleotideSequence, VDJCGene>[][] results = new AlignmentHit[2][];
                Arrays.fill(results, zeroKArray);

                boolean calculated = false;
                if (bestVHit == null && bestJHit == null)
                    calculated = true;

                if (!calculated && bestJHit != null) { // If J hit is present somewhere

                    // The following algorithm represents following behaviour:
                    // If there is a J hit in R1 search for C gene in R1 (after J) and R2
                    // If there is a J hit in R2 search for C gene in R2 (after J)

                    for (int i = 0; i < 2; ++i) {
                        Alignment<NucleotideSequence> jAlignment = bestJHit.get(i) == null ? null : bestJHit.get(i).getAlignment();
                        if (i == 0 && jAlignment == null)
                            continue;
                        int from = jAlignment == null ? 0 : jAlignment.getSequence2Range().getTo();
                        List<AlignmentHit<NucleotideSequence, VDJCGene>> temp = cAligner.align(target.targets[i].getSequence(), from,
                                        target.targets[i].size(),
                                        getFilter(GeneType.Constant, vHits, jHits))
                                .getHits();
                        results[i] = temp.toArray(new AlignmentHit[temp.size()]);
                    }
                    calculated = true;
                }

                if (!calculated && bestVHit.get(0) != null && bestVHit.get(1) == null) { // At least one V hit must be present in the first read

                    // Searching for C gene in second read

                    List<AlignmentHit<NucleotideSequence, VDJCGene>> temp = cAligner.align(target.targets[1].getSequence(),
                                    0, target.targets[1].size(),
                                    getFilter(GeneType.Constant, vHits))
                            .getHits();

                    results[1] = temp.toArray(new AlignmentHit[temp.size()]);
                }

                cHits = combine(parameters.getFeatureToAlign(GeneType.Constant), results);
            } else
                cHits = new VDJCHit[0];

            //Alignment of D gene
            if (singleDAligner != null) {
                PreVDJCHit[][] preDHits = new PreVDJCHit[2][];
                Arrays.fill(preDHits, zeroArray);

                if (bestVHit != null && bestJHit != null)
                    for (int i = 0; i < 2; ++i) {
                        Alignment<NucleotideSequence> vAlignment = bestVHit.get(i) == null ? null : bestVHit.get(i).getAlignment();
                        Alignment<NucleotideSequence> jAlignment = bestJHit.get(i) == null ? null : bestJHit.get(i).getAlignment();
                        if (vAlignment == null || jAlignment == null)
                            continue;
                        int from = vAlignment.getSequence2Range().getTo(),
                                to = jAlignment.getSequence2Range().getFrom();
                        if (from >= to)
                            continue;
                        List<PreVDJCHit> temp = singleDAligner.align0(target.targets[i].getSequence(),
                                getPossibleDLoci(vHits, jHits, cHits), from, to);
                        preDHits[i] = temp.toArray(new PreVDJCHit[temp.size()]);
                    }

                dHits = PreVDJCHit.combine(getDGenesToAlign(),
                        parameters.getFeatureToAlign(GeneType.Diversity), preDHits);
            }
        }

        /**
         * Filters hit to finally meet maxHit and minScore limits.
         */
        public void filterHits(float minTotalScore, int maxHits) {
            // Calculate this value once to use twice in the code below
            float totalMScore = minTotalScore - score();

            if (vHits != null && vHits.length > 0) {
                float minScore = Math.max(
                        parameters.getVAlignerParameters().getRelativeMinScore() * vHits[0].sumScore,
                        totalMScore + vHits[0].sumScore // = minTotalScore - topJScore - topCScore - topDScore
                );
                this.vHits = VDJCAlignerPVFirst.filterHits(vHits, (int) minScore, maxHits);
                assert vHits.length > 0;
            }

            if (jHits != null && jHits.length > 0) {
                this.jHits = VDJCAlignerPVFirst.filterHits(jHits,
                        (int) (totalMScore + jHits[0].sumScore), // = minTotalScore - topVScore - topCScore - topDScore
                        maxHits);
                assert jHits.length > 0;
            }
        }
    }

    /**
     * Calculates normal "sum" score for each hit and sort hits according to this score.
     */
    static void calculateScoreAndSort(PairedHit[] hits) {
        for (PairedHit hit : hits)
            hit.calculateScore();
        Arrays.sort(hits, SCORE_COMPARATOR);
    }

    /**
     * Internal storage of paired hit. Combines information from two hits for right and left reads of a paired-end
     * read.
     */
    static final class PairedHit implements HasGene {
        public AlignmentHit<NucleotideSequence, VDJCGene> hit0, hit1;
        float sumScore = -1, vEndScore = -1;

        PairedHit() {
        }

        ///**
        // * Calculates alignment score only for FR3 and CDR3 part of V gene.
        // */
        //void calculateVEndScore(VDJCAlignerPVFirst aligner) {
        //    if (hit0 != null)
        //        vEndScore = aligner.calculateVEndScore(hit0);
        //
        //    if (hit1 != null) {
        //        float sc = aligner.calculateVEndScore(hit1);
        //        if (vEndScore < sc)
        //            vEndScore = sc;
        //    }
        //}

        /**
         * Calculates normal "sum" score for this paired hit.
         */
        void calculateScore() {
            sumScore =
                    (hit0 == null ? 0.0f : hit0.getAlignment().getScore()) +
                            (hit1 == null ? 0.0f : hit1.getAlignment().getScore());
        }

        /**
         * To use this hit as an array of two single hits.
         */
        void set(int i, AlignmentHit<NucleotideSequence, VDJCGene> hit) {
            assert i == 0 || i == 1;
            if (i == 0)
                this.hit0 = hit;
            else
                this.hit1 = hit;
            assert hit0 == null || hit1 == null || hit0.getRecordPayload().getId().equals(hit1.getRecordPayload().getId());
        }

        /**
         * To use this hit as an array of two single hits.
         */
        AlignmentHit<NucleotideSequence, VDJCGene> get(int i) {
            assert i == 0 || i == 1;

            if (i == 0)
                return hit0;
            else
                return hit1;
        }

        /**
         * Returns id of reference sequence
         */
        @Override
        public VDJCGene getGene() {
            assert hit0 == null || hit1 == null || hit0.getRecordPayload() == hit1.getRecordPayload();
            return hit0 == null ? hit1.getRecordPayload() : hit0.getRecordPayload();
        }

        /**
         * Converts this object to a VDJCHit
         */
        @SuppressWarnings("unchecked")
        VDJCHit convert(GeneType geneType, VDJCAlignerPVFirst aligner) {
            Alignment<NucleotideSequence>[] alignments = new Alignment[2];

            VDJCGene gene = null;
            if (hit0 != null) {
                gene = hit0.getRecordPayload();
                alignments[0] = hit0.getAlignment();
            }
            if (hit1 != null) {
                assert gene == null ||
                        gene == hit1.getRecordPayload();
                gene = hit1.getRecordPayload();
                alignments[1] = hit1.getAlignment();
            }

            return new VDJCHit(gene, alignments,
                    aligner.getParameters().getFeatureToAlign(geneType));
        }
    }

    private static VDJCHit[] convert(PairedHit[] preHits,
                                     GeneType geneType,
                                     VDJCAlignerPVFirst aligner) {
        VDJCHit[] hits = new VDJCHit[preHits.length];
        for (int i = 0; i < preHits.length; i++)
            hits[i] = preHits[i].convert(geneType, aligner);
        return hits;
    }

    //    /**
    //     * Calculates alignment score only for FR3 and CDR3 part of V gene.
    //     */
    //    float calculateVEndScore(AlignmentHit<NucleotideSequence, VDJCGene> hit) {
    //        final VDJCGene gene = hit.getRecordPayload();
    //        final int boundary = gene.getPartitioning().getRelativePosition(
    //                parameters.getFeatureToAlign(GeneType.Variable),
    //                ReferencePoint.FR3Begin);
    //        final Alignment<NucleotideSequence> alignment = hit.getAlignment();
    //
    //        if (alignment.getSequence1Range().getUpper() <= boundary)
    //            return 0.0f;
    //
    //        if (alignment.getSequence1Range().getLower() >= boundary)
    //            return alignment.getScore();
    //
    //        final Range range = new Range(boundary, alignment.getSequence1Range().getUpper());
    //        Mutations<NucleotideSequence> vEndMutations = alignment.getAbsoluteMutations()
    //                .extractMutationsForRange(range);
    //
    //        return AlignmentUtils.calculateScore(
    //                alignment.getSequence1().getRange(range),
    //                vEndMutations,
    //                parameters.getVAlignerParameters().getParameters().getScoring());
    //
    ////        return AlignmentUtils.calculateScore(
    ////                parameters.getVAlignerParameters().getParameters().getScoring(),
    ////                range.length(), vEndMutations);
    //    }

    static final Comparator<PairedHit> V_END_SCORE_COMPARATOR = new Comparator<PairedHit>() {
        @Override
        public int compare(PairedHit o1, PairedHit o2) {
            return Float.compare(o2.vEndScore, o1.vEndScore);
        }
    };

    static final Comparator<PairedHit> SCORE_COMPARATOR = new Comparator<PairedHit>() {
        @Override
        public int compare(PairedHit o1, PairedHit o2) {
            return Float.compare(o2.sumScore, o1.sumScore);
        }
    };

    @SuppressWarnings("unchecked")
    public static VDJCHit[] combine(final GeneFeature feature, final AlignmentHit<NucleotideSequence, VDJCGene>[][] hits) {
        for (int i = 0; i < hits.length; i++)
            Arrays.sort(hits[i], GENE_ID_COMPARATOR);
        ArrayList<VDJCHit> result = new ArrayList<>();

        // Sort-join-like algorithm
        int i;
        VDJCGene minGene;
        Alignment<NucleotideSequence>[] alignments;
        final int[] pointers = new int[hits.length];
        while (true) {
            minGene = null;
            for (i = 0; i < pointers.length; ++i)
                if (pointers[i] < hits[i].length && (minGene == null || minGene.getId().compareTo(
                        hits[i][pointers[i]].getRecordPayload().getId()) > 0))
                    minGene = hits[i][pointers[i]].getRecordPayload();

            // All pointers > hits.length
            if (minGene == null)
                break;

            // Collecting alignments for minAllele
            alignments = new Alignment[hits.length];
            for (i = 0; i < pointers.length; ++i)
                if (pointers[i] < hits[i].length && minGene == hits[i][pointers[i]].getRecordPayload()) {
                    alignments[i] = hits[i][pointers[i]].getAlignment();
                    ++pointers[i];
                }

            // Collecting results
            result.add(new VDJCHit(minGene, alignments, feature));
        }
        VDJCHit[] vdjcHits = result.toArray(new VDJCHit[result.size()]);
        Arrays.sort(vdjcHits);
        return vdjcHits;
    }

    public static final Comparator<AlignmentHit<NucleotideSequence, VDJCGene>> GENE_ID_COMPARATOR =
            new Comparator<AlignmentHit<NucleotideSequence, VDJCGene>>() {
                @Override
                public int compare(AlignmentHit<NucleotideSequence, VDJCGene> o1, AlignmentHit<NucleotideSequence, VDJCGene> o2) {
                    return o1.getRecordPayload().getId().compareTo(o2.getRecordPayload().getId());
                }
            };
}
