package com.milaboratory.mixcr.partialassembler;

import com.milaboratory.core.alignment.*;
import com.milaboratory.core.alignment.batch.AlignmentHit;
import com.milaboratory.core.alignment.batch.AlignmentResult;
import com.milaboratory.core.alignment.batch.BatchAlignerWithBaseWithFilter;
import com.milaboratory.core.alignment.kaligner1.AbstractKAlignerParameters;
import com.milaboratory.core.alignment.kaligner1.KAlignerParameters;
import com.milaboratory.core.alignment.kaligner2.KAlignerParameters2;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerAbstract;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignmentResult;
import io.repseq.core.Chains;
import io.repseq.core.GeneType;
import io.repseq.core.ReferencePoint;
import io.repseq.core.VDJCGene;

import java.util.Arrays;
import java.util.EnumMap;

import static com.milaboratory.mixcr.vdjaligners.VDJCAlignerPVFirst.combine;

/**
 * @author Dmitry Bolotin
 * @author Stanislav Poslavsky
 */
public final class PartialAlignmentsAssemblerAligner extends VDJCAlignerAbstract<VDJCMultiRead> {
    private final ThreadLocal<AlignerCustom.LinearMatrixCache> linearMatrixCache = new ThreadLocal<AlignerCustom.LinearMatrixCache>() {
        @Override
        protected AlignerCustom.LinearMatrixCache initialValue() {
            return new AlignerCustom.LinearMatrixCache();
        }
    };

    public PartialAlignmentsAssemblerAligner(VDJCAlignerParameters parameters) {
        super(parameters);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected VDJCAlignmentResult<VDJCMultiRead> process0(VDJCMultiRead input) {
        final int nReads = input.numberOfReads();
        EnumMap<GeneType, VDJCHit[]> vdjcHits = new EnumMap<>(GeneType.class);

        NSequenceWithQuality[] targets = new NSequenceWithQuality[nReads];

        Chains currentChains = Chains.ALL;

        // Across all gene types
        int lastAlignedTarget = 0;
        int firstJTarget = -1;
        int lastVTarget = -1;

        for (int g = 0; g < GeneType.VJC_REFERENCE.length; g++) {
            GeneType gt = GeneType.VJC_REFERENCE[g];
            AlignmentHit<NucleotideSequence, VDJCGene>[][] alignmentHits = new AlignmentHit[nReads][];
            Arrays.fill(alignmentHits, new AlignmentHit[0]);
            for (int targetId = lastAlignedTarget; targetId < nReads; targetId++) {
                targets[targetId] = input.getRead(targetId).getData();

                final NucleotideSequence sequence = input.getRead(targetId).getData().getSequence();

                AlignmentResult<AlignmentHit<NucleotideSequence, VDJCGene>> als;

                final BatchAlignerWithBaseWithFilter<NucleotideSequence, VDJCGene,
                        AlignmentHit<NucleotideSequence, VDJCGene>>
                        aligner = getAligner(gt);
                if (aligner != null) {
                    int pointer = 0;
                    if (g != 0) { // Not V gene
                        VDJCHit[] vdjcHits1 = vdjcHits.get(GeneType.VJC_REFERENCE[g - 1]);
                        Alignment<NucleotideSequence> alignment;
                        if (vdjcHits1.length != 0 && (alignment = vdjcHits1[0].getAlignment(targetId)) != null)
                            pointer = alignment.getSequence2Range().getTo();
                    }
                    als = aligner.align(sequence, pointer, sequence.size(), getFilter(gt, currentChains));
                    if (als != null && als.hasHits()) {
                        lastAlignedTarget = targetId;

                        if (g == 0) // V
                            lastVTarget = targetId;

                        if (g == 1) // J
                            firstJTarget = targetId;

                        alignmentHits[targetId] = als.getHits().toArray(new AlignmentHit[als.getHits().size()]);
                    }
                }
            }

            Chains chains = Chains.EMPTY;
            for (AlignmentHit<NucleotideSequence, VDJCGene>[] alignmentHit0 : alignmentHits)
                if (alignmentHit0 != null)
                    for (AlignmentHit<NucleotideSequence, VDJCGene> hit : alignmentHit0)
                        chains = chains.merge(hit.getRecordPayload().getChains());
            currentChains = currentChains.intersection(chains);

            vdjcHits.put(gt, combine(parameters.getFeatureToAlign(gt), alignmentHits));
        }

        boolean fineVAlignmentPerformed = false, fineJAlignmentPerformed = false;

        // Additional (fine) alignment step for V gene

        VDJCHit[] vHits = vdjcHits.get(GeneType.Variable);
        final AlignmentScoring<NucleotideSequence> vScoring = parameters.getVAlignerParameters().getParameters().getScoring();
        if (vHits != null && vHits.length > 0 && !(vScoring instanceof AffineGapAlignmentScoring) && // TODO implement AffineGapAlignmentScoring
                vdjcHits.get(GeneType.Joining) != null && vdjcHits.get(GeneType.Joining).length > 0) {
            int minimalVSpace = getAbsoluteMinScore(parameters.getVAlignerParameters().getParameters()) /
                    vScoring.getMaximalMatchScore();

            // Because vdjcHits.get(GeneType.Joining) != null && vdjcHits.get(GeneType.Joining).length > 0
            // Assert
            if (firstJTarget == -1)
                throw new AssertionError();

            for (int targetId = 1; targetId <= firstJTarget; targetId++) {
                int vSpace;
                final NucleotideSequence sequence2 = targets[targetId].getSequence();
                if (vdjcHits.get(GeneType.Joining)[0].getAlignment(targetId) != null &&
                        (vSpace = vdjcHits.get(GeneType.Joining)[0].getAlignment(targetId).getSequence2Range().getFrom()) >= minimalVSpace) {
                    for (int vHitIndex = 0; vHitIndex < vHits.length; vHitIndex++) {
                        VDJCHit vHit = vHits[vHitIndex];

                        // Perform fine alignment only if target is not already aligned by fast aligner
                        if (vHit.getAlignment(targetId) != null)
                            continue;

                        Alignment<NucleotideSequence> leftAlignment = vHit.getAlignment(targetId - 1);
                        if (leftAlignment == null)
                            continue;

                        final NucleotideSequence sequence1 = leftAlignment.getSequence1();

                        final int beginFR3 = vHit.getGene().getPartitioning().getRelativePosition(parameters.getFeatureToAlign(GeneType.Variable), ReferencePoint.FR3Begin);
                        if (beginFR3 == -1)
                            continue;

                        final Alignment alignment = AlignerCustom.alignLinearSemiLocalLeft0(
                                (LinearGapAlignmentScoring<NucleotideSequence>) vScoring,
                                sequence1, sequence2,
                                beginFR3, sequence1.size() - beginFR3,
                                0, vSpace,
                                false, true,
                                NucleotideSequence.ALPHABET,
                                linearMatrixCache.get());

                        if (alignment.getScore() < getAbsoluteMinScore(parameters.getVAlignerParameters().getParameters()))
                            continue;

                        fineVAlignmentPerformed = true;
                        vHits[vHitIndex] = vHit.setAlignment(targetId, alignment);
                    }
                }
            }
        }
        Arrays.sort(vHits);
        vdjcHits.put(GeneType.Variable, cutRelativeScore(vHits, parameters.getVAlignerParameters().getRelativeMinScore(),
                parameters.getVAlignerParameters().getParameters().getMaxHits()));

        // Additional (fine) alignment step for J gene

        VDJCHit[] jHits = vdjcHits.get(GeneType.Joining);
        final AlignmentScoring<NucleotideSequence> jScoring = parameters.getJAlignerParameters().getParameters().getScoring();
        if (jHits != null && jHits.length > 0 && !(jScoring instanceof AffineGapAlignmentScoring) && // TODO implement AffineGapAlignmentScoring
                vdjcHits.get(GeneType.Variable) != null && vdjcHits.get(GeneType.Variable).length > 0) {
            int minimalJSpace = getAbsoluteMinScore(parameters.getJAlignerParameters().getParameters()) /
                    jScoring.getMaximalMatchScore();

            // Because vdjcHits.get(GeneType.Variable) != null && vdjcHits.get(GeneType.Variable).length > 0
            // Assert
            if (lastVTarget == -1)
                throw new AssertionError();

            for (int targetId = lastVTarget; targetId < nReads - 1; targetId++) {
                int jSpaceBegin;
                final NucleotideSequence sequence2 = targets[targetId].getSequence();
                if (vdjcHits.get(GeneType.Variable)[0].getAlignment(targetId) != null &&
                        (sequence2.size() - (jSpaceBegin = vdjcHits.get(GeneType.Variable)[0].getAlignment(targetId).getSequence2Range().getTo())) >= minimalJSpace) {
                    for (int jHitIndex = 0; jHitIndex < jHits.length; jHitIndex++) {
                        VDJCHit jHit = jHits[jHitIndex];

                        // Perform fine alignment only if target is not already aligned by fast aligner
                        if (jHit.getAlignment(targetId) != null)
                            continue;

                        Alignment<NucleotideSequence> rightAlignment = jHit.getAlignment(targetId + 1);
                        if (rightAlignment == null)
                            continue;

                        final NucleotideSequence sequence1 = rightAlignment.getSequence1();

                        final Alignment alignment = AlignerCustom.alignLinearSemiLocalRight0(
                                (LinearGapAlignmentScoring) jScoring,
                                sequence1, sequence2,
                                0, sequence1.size(),
                                jSpaceBegin, sequence2.size() - jSpaceBegin,
                                false, true,
                                NucleotideSequence.ALPHABET,
                                linearMatrixCache.get());

                        if (alignment.getScore() < getAbsoluteMinScore(parameters.getJAlignerParameters().getParameters()))
                            continue;

                        fineJAlignmentPerformed = true;
                        jHits[jHitIndex] = jHit.setAlignment(targetId, alignment);
                    }
                }
            }
        }
        Arrays.sort(jHits);
        vdjcHits.put(GeneType.Joining, cutRelativeScore(jHits, parameters.getJAlignerParameters().getRelativeMinScore(),
                parameters.getJAlignerParameters().getParameters().getMaxHits()));


        int dGeneTarget = -1;
        VDJCHit[] vResult = vdjcHits.get(GeneType.Variable);
        VDJCHit[] jResult = vdjcHits.get(GeneType.Joining);
        if (vResult.length != 0 && jResult.length != 0)
            for (int i = 0; i < nReads; i++)
                if (vResult[0].getAlignment(i) != null && jResult[0].getAlignment(i) != null) {
                    dGeneTarget = i;
                    break;
                }

        //if (fineVAlignmentPerformed && fineJAlignmentPerformed)
        //    System.out.println("sd");

        VDJCHit[] dResult;
        if (dGeneTarget == -1)
            dResult = new VDJCHit[0];
        else {
            final Alignment<NucleotideSequence> vAl = vResult[0].getAlignment(dGeneTarget);
            final Alignment<NucleotideSequence> jAl = jResult[0].getAlignment(dGeneTarget);
            if (vAl == null || jAl == null || singleDAligner == null)
                dResult = new VDJCHit[0];
            else
                dResult = singleDAligner.align(targets[dGeneTarget].getSequence(), getPossibleDLoci(vResult, jResult),
                        vAl.getSequence2Range().getTo(),
                        jAl.getSequence2Range().getFrom(),
                        dGeneTarget, nReads);
        }

        final VDJCAlignments alignment = new VDJCAlignments(
                vResult,
                dResult,
                jResult,
                cutRelativeScore(vdjcHits.get(GeneType.Constant), parameters.getCAlignerParameters().getRelativeMinScore(), parameters.getMaxHits()),
                targets,
                input.getHistory(),
                input.getOriginalReads()
        );
        return new VDJCAlignmentResult<>(input, alignment);
    }

    private static VDJCHit[] cutRelativeScore(VDJCHit[] hs, float relativeMinScore, int maxHits) {
        if (hs.length == 0)
            return hs;
        float maxScore = hs[0].getScore() * relativeMinScore;
        int j = Math.min(hs.length - 1, maxHits - 1);
        while (j >= 0 && hs[j].getScore() < maxScore)
            --j;
        if (j != hs.length - 1)
            hs = Arrays.copyOf(hs, j + 1);
        return hs;
    }

    ///**
    // * @param target exclusive
    // */
    //private static boolean hasAnyAlignmentsInNextTargets(VDJCHit[] hits, int target, int numberOfTargets) {
    //    for (int t = target + 1; t < numberOfTargets; t++)
    //        if (hasAnyAlignmentsInTarget(hits, t))
    //            return true;
    //    return false;
    //}
    //
    ///**
    // * @param target exclusive
    // */
    //private static boolean hasAnyAlignmentsInPreviousTargets(VDJCHit[] hits, int target) {
    //    for (int t = target - 1; t >= 0; t--)
    //        if (hasAnyAlignmentsInTarget(hits, t))
    //            return true;
    //    return false;
    //}
    //
    //private static boolean hasAnyAlignmentsInTarget(VDJCHit[] hits, int target) {
    //    for (VDJCHit hit : hits)
    //        if (hit.getAlignment(target) != null)
    //            return true;
    //    return false;
    //}
    //
    //private static int getVRightBoundary(VDJCHit hit, int target) {
    //    return hit.getPartitioningForTarget(target).getPosition(ReferencePoint.VEndTrimmed);
    //}
    //
    //private static int getJLeftBoundary(VDJCHit hit, int target) {
    //    return hit.getPartitioningForTarget(target).getPosition(ReferencePoint.JBeginTrimmed);
    //}

    private static int getAbsoluteMinScore(AbstractKAlignerParameters kParameters) {
        if (kParameters instanceof KAlignerParameters)
            return (int) ((KAlignerParameters) kParameters).getAbsoluteMinScore();
        else if (kParameters instanceof KAlignerParameters2)
            return ((KAlignerParameters2) kParameters).getAbsoluteMinScore();
        else
            throw new RuntimeException("Not supported scoring type.");
    }
}
