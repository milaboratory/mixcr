/*
 * Copyright (c) 2014-2019, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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
 * commercial purposes should contact MiLaboratory LLC, which owns exclusive
 * rights for distribution of this program for commercial purposes, using the
 * following email address: licensing@milaboratory.com.
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
package com.milaboratory.mixcr.assembler.fullseq;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPort;
import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.*;
import com.milaboratory.core.alignment.BandedAffineAligner.MatrixCache;
import com.milaboratory.core.mutations.MutationsBuilder;
import com.milaboratory.core.sequence.*;
import com.milaboratory.core.sequence.quality.QualityTrimmer;
import com.milaboratory.mixcr.assembler.CloneFactory;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.mixcr.basictypes.VDJCPartitionedSequence;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import gnu.trove.impl.Constants;
import gnu.trove.iterator.TIntIntIterator;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.map.hash.TObjectFloatHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import gnu.trove.set.hash.TIntHashSet;
import io.repseq.core.*;
import io.repseq.gen.VDJCGenes;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static io.repseq.core.GeneType.Joining;
import static io.repseq.core.GeneType.Variable;

/**
 *
 */
public final class FullSeqAssembler {
    private static int ABSENT_PACKED_VARIANT_INFO = -1;
    /** number of letters to the left of reference V gene in the global coordinate grid */
    private static final int N_LEFT_DUMMIES = 1024; // fixme
    /** clone factory */
    final CloneFactory cloneFactory;
    /** initial clone */
    final Clone clone;
    /** clone assembled feature (must cover CDR3) */
    final GeneFeature assemblingFeature;
    /** top hit genes */
    final VDJCGenes genes;
    /** whether V/J genes are aligned */
    final boolean hasV, hasJ; // always trues for now
    /** length of aligned part of reference V gene */
    final int lengthV;
    /** length of aligned part of reference J gene */
    final int jLength;
    /** position of assembling feature in global grid (just "one letter") */
    final int positionOfAssemblingFeature;
    /** variant id of assembling feature of the target clonotype */
    final int clonalAssemblingFeatureVariantIndex;
    /** length of assembling feature in the clone */
    final int assemblingFeatureLength; // = 1
    /** begin of the aligned J part in the reference J gene */
    final int jOffset;
    /** end of alignment of V gene in the global coordinate grid */
    final int rightAssemblingFeatureBound;
    /** splitting region in global coordinates */
    final Range splitRegion;
    /** parameters */
    final FullSeqAssemblerParameters parameters;
    /** minimal sum quality, even for decisive sum quality */
    final long requiredMinimalSumQuality;
    /** aligner parameters */
    final VDJCAlignerParameters alignerParameters;
    /** nucleotide sequence -> its integer index */
    final TObjectIntHashMap<NucleotideSequence> sequenceToVariantId =
            new TObjectIntHashMap<>(Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, -1);
    /** integer index -> nucleotide sequence */
    final TIntObjectHashMap<NucleotideSequence> variantIdToSequence = new TIntObjectHashMap<>();
    /** base hits */
    final VDJCHit vHit, jHit;

    public FullSeqAssembler(CloneFactory cloneFactory,
                            FullSeqAssemblerParameters parameters,
                            Clone clone,
                            VDJCAlignerParameters alignerParameters) {
        this(cloneFactory, parameters, clone, alignerParameters,
                clone.getBestHit(Variable), clone.getBestHit(Joining));
    }

    public FullSeqAssembler(CloneFactory cloneFactory,
                            FullSeqAssemblerParameters parameters,
                            Clone clone,
                            VDJCAlignerParameters alignerParameters,
                            VDJCHit baseVHit,
                            VDJCHit baseJHit) {
        this.vHit = baseVHit;
        this.jHit = baseJHit;

        // Checking parameters
        if (parameters.outputMinimalSumQuality > parameters.branchingMinimalSumQuality)
            throw new IllegalArgumentException("Wrong parameters. (branchingMinimalSumQuality must be greater than outputMinimalSumQuality)");

        this.cloneFactory = cloneFactory;
        this.parameters = parameters;
        this.clone = clone;

        this.alignerParameters = alignerParameters;
        GeneFeature[] assemblingFeatures = clone.getParentCloneSet().getAssemblingFeatures();
        if (assemblingFeatures.length != 1)
            throw new IllegalArgumentException("Supports only singular assemblingFeature.");

        if (assemblingFeatures[0].isComposite())
            throw new IllegalArgumentException("Supports only non-composite gene features as an assemblingFeature.");

        this.assemblingFeature = assemblingFeatures[0];
        this.genes = new VDJCGenes(baseVHit.getGene(), null, baseJHit.getGene(), null); // clone.getBestHitGenes();

        this.clonalAssemblingFeatureVariantIndex = initVariantMappings(clone.getFeature(this.assemblingFeature).getSequence());

        this.requiredMinimalSumQuality = Math.round(parameters.minimalMeanNormalizedQuality * clone.getCount());

        ReferencePoint
                start = assemblingFeature.getFirstPoint(),
                end = assemblingFeature.getLastPoint();

        this.hasV = start.getGeneType() == Variable;
        this.hasJ = end.getGeneType() == Joining;


        //  N_LEFT_DUMMIES     assemblingFeatureLength
        //  ------|--------------|--------------|------------------------>
        //        ↓              ↓              ↓
        //  0000000vvvvvvvvvvvvvvCDR3CDR3CDR3CDR3jjjjjjjjjjjjjjjjCCCCCCCCC
        //      ------- Type A
        //          -------- Type B


        int splitRegionBegin = -1, splitRegionEnd = -1;
        if (hasV) {
            GeneFeature vFeature = baseVHit.getAlignedFeature();
            VDJCGene gene = baseVHit.getGene();
            this.lengthV =
                    gene.getPartitioning().getLength(vFeature)
                            - gene.getFeature(GeneFeature.intersection(assemblingFeature, vFeature)).size();
            if (parameters.subCloningRegion != null) {
                int p = gene.getPartitioning().getRelativePosition(vFeature, parameters.subCloningRegion.getFirstPoint());
                if (p != -1)
                    splitRegionBegin = N_LEFT_DUMMIES + p;

                p = gene.getPartitioning().getRelativePosition(vFeature, parameters.subCloningRegion.getLastPoint());
                if (p != -1)
                    splitRegionEnd = N_LEFT_DUMMIES + p;
            }
        } else
            this.lengthV = 0;

        this.positionOfAssemblingFeature = N_LEFT_DUMMIES + lengthV;
        this.assemblingFeatureLength = 1;

        if (hasJ) {
            VDJCGene gene = baseJHit.getGene();
            GeneFeature jFeature = baseJHit.getAlignedFeature();
            this.jOffset = gene.getPartitioning().getRelativePosition(jFeature, assemblingFeature.getLastPoint());
            this.jLength = gene.getPartitioning().getLength(jFeature) - jOffset;

            if (parameters.subCloningRegion != null) {
                int p = gene.getPartitioning().getRelativePosition(jFeature, parameters.subCloningRegion.getLastPoint());
                if (p != -1)
                    splitRegionEnd = N_LEFT_DUMMIES + lengthV + assemblingFeatureLength - jOffset + p;

                p = gene.getPartitioning().getRelativePosition(jFeature, parameters.subCloningRegion.getFirstPoint());
                if (p != -1)
                    splitRegionBegin = N_LEFT_DUMMIES + lengthV + assemblingFeatureLength - jOffset + p;
            }
        } else {
            this.jOffset = 0;
            this.jLength = 0;
        }

        if (splitRegionBegin != -1 && splitRegionEnd != -1)
            this.splitRegion = new Range(splitRegionBegin, splitRegionEnd);
        else
            this.splitRegion = null;

        this.rightAssemblingFeatureBound = N_LEFT_DUMMIES + lengthV + assemblingFeatureLength;
    }

    FullSeqAssemblerReport report = null;

    public void setReport(FullSeqAssemblerReport report) {
        this.report = report;
    }

    public FullSeqAssemblerReport getReport() {
        return report;
    }

    private EnumMap<GeneType, TObjectFloatHashMap<VDJCGeneId>> originalGeneScores = null;

    private EnumMap<GeneType, TObjectFloatHashMap<VDJCGeneId>> getOriginalGeneScores() {
        if (originalGeneScores == null) {
            originalGeneScores = new EnumMap<>(GeneType.class);
            for (GeneType gt : GeneType.VDJC_REFERENCE) {
                TObjectFloatHashMap<VDJCGeneId> scores = new TObjectFloatHashMap<>();
                for (VDJCHit hit : clone.getHits(gt))
                    scores.put(hit.getGene().getId(), hit.getScore());
                originalGeneScores.put(gt, scores);
            }
        }
        return originalGeneScores;
    }

    /* ======================================== Find variants ============================================= */

    public Clone[] callVariants(RawVariantsData data) {
        OutputPort<int[]> port = data.createPort();
        List<VariantBranch> branches = new ArrayList<>();
        BitSet allReads = new BitSet();
        allReads.set(0, data.nReads);
        branches.add(new VariantBranch(clone.getCount(), allReads));
        for (int i = 0; i < data.points.length; ++i) {
            int[] variantInfos = port.take();
            List<VariantBranch> newBranches = new ArrayList<>();
            for (VariantBranch branch : branches) {
                List<Variant> variants = callVariantsForPoint(variantInfos, branch.reads, data.points[i] == positionOfAssemblingFeature);
                if (variants.size() == 1 && variants.get(0).variantInfo == ABSENT_PACKED_VARIANT_INFO)
                    newBranches.add(branch.addAbsentVariant());
                else {
                    int sumSignificant = 0;
                    for (Variant variant : variants)
                        sumSignificant += variant.nSignificant;
                    for (Variant variant : variants)
                        newBranches.add(branch.addVariant(variant, sumSignificant));
                }
            }
            branches = newBranches;
        }

        if (report != null)
            report.onVariantsCreated(branches);

        // VariantBranch[] branchesBeforeClustering = branches.stream().map(VariantBranch::clone).toArray(VariantBranch[]::new);

        clusterizeBranches(data.points, branches);

        Clone[] result = branches.stream()
                .map(branch -> assembleBranchSequences(data.points, branch))
                .filter(Objects::nonNull)
                .map(branch -> buildClone(clean(branch)))
                .toArray(Clone[]::new);

        if (result.length == 0) {
            // In case assemble procedure failed to assemble even a single clonotype, returning original
            // clonotype, to prevent diversity losses
            report.onEmptyOutput(clone);
            result = new Clone[]{clone};
        }

        if (report != null)
            report.afterVariantsClustered(clone, result);

        return result;
    }

    private void clusterizeBranches(int[] points, List<VariantBranch> branches) {
        branches.sort(Comparator.comparingDouble(c -> c.count));

        TIntHashSet[] observedVariants = new TIntHashSet[points.length];
        for (int i = 0; i < observedVariants.length; i++)
            observedVariants[i] = new TIntHashSet();

        for (int i = branches.size() - 1; i >= 0; --i) {
            VariantBranch branch = branches.get(i);

            boolean newBranch = false;
            for (int j = 0; j < branch.pointStates.length; j++)
                if (observedVariants[j].add(branch.pointStates[j] >>> 8))
                    newBranch = true;

            if (newBranch)
                continue;

            double sumWeight = 0;
            double[] weights = new double[branches.size() - i - 1];
            for (int j = i + 1; j < branches.size(); ++j) {
                VariantBranch cluster = branches.get(j);

                double sumQuality = 0;
                for (int k = 0; k < branch.pointStates.length; ++k)
                    if (branch.pointStates[k] >>> 8 != cluster.pointStates[k] >>> 8)
                        sumQuality += Math.min(branch.pointStates[k] & 0xFF, cluster.pointStates[k] & 0xFF);

                weights[j - i - 1] = Math.pow(10.0, -sumQuality / 10.0);
                sumWeight += weights[j - i - 1];
            }

            if (sumWeight != 0.0)
                for (int j = i + 1; j < branches.size(); ++j) {
                    VariantBranch cluster = branches.get(j);
                    cluster.count += branch.count * weights[j - i - 1] / sumWeight;
                }

            if (report != null)
                report.onVariantClustered(branch);
            branches.remove(i);
        }

        branches.sort(Comparator.comparingDouble(c -> -c.count));
    }

    static class VariantBranch {
        double count; // non-final, since will be modified
        final int[] pointStates;
        final BitSet reads;

        VariantBranch(double count, BitSet reads) {
            this(count, new int[0], reads);
        }

        VariantBranch(double count, int[] pointStates, BitSet reads) {
            this.count = count;
            this.pointStates = pointStates;
            this.reads = reads;
        }

        VariantBranch addAbsentVariant() {
            int[] newStates = Arrays.copyOf(pointStates, pointStates.length + 1);
            newStates[newStates.length - 1] = ABSENT_PACKED_VARIANT_INFO;
            return new VariantBranch(count, newStates, reads);
        }

        VariantBranch addVariant(Variant variant, int sumSignificant) {
            int[] newStates = Arrays.copyOf(pointStates, pointStates.length + 1);
            newStates[newStates.length - 1] = variant.variantInfo;
            return new VariantBranch(count * variant.nSignificant / sumSignificant, newStates, variant.reads);
        }

        protected VariantBranch clone() {
            return new VariantBranch(count, pointStates, reads);
        }
    }

    /**
     * Performs final sequence cleanup. Removes very short sub-targets, performes quality trimming.
     */
    BranchSequences clean(BranchSequences seq) {
        if (parameters.trimmingParameters != null)
            for (int i = seq.ranges.length - 1; i >= 0; --i)
                if (i == seq.assemblingFeatureTargetId) {
                    final Range[] ranges = QualityTrimmer.calculateIslandsFromInitialRange(seq.sequences[i].getQuality(),
                            parameters.trimmingParameters,
                            new Range(seq.assemblingFeatureOffset, seq.assemblingFeatureOffset + seq.assemblingFeatureLength));
                    seq = seq.splitCut(i, ranges);
                } else {
                    // This also completely removes regions with low quality
                    final Range[] ranges = QualityTrimmer.calculateAllIslands(seq.sequences[i].getQuality(),
                            parameters.trimmingParameters);
                    seq = seq.splitCut(i, ranges);
                }
        for (int i = seq.ranges.length - 1; i >= 0; --i)
            if (seq.sequences[i].size() < parameters.minimalContigLength && i != seq.assemblingFeatureTargetId)
                seq = seq.without(i);
        return seq;
    }

    /**
     * Assemble branch sequence (intermediate object with the sequence and meta information on the positions of the
     * assembled targets in global coordinates)
     *
     * @param points positions
     * @param branch variant branch
     */
    BranchSequences assembleBranchSequences(int[] points, VariantBranch branch) {
        // Co-sorting branch data with position (restoring original nucleotides order)
        long[] positionedStates = new long[points.length];
        for (int i = 0; i < points.length; i++)
            positionedStates[i] = ((long) points[i]) << 32 | branch.pointStates[i];
        Arrays.sort(positionedStates);

        // Building sequences
        List<NSequenceWithQuality> sequences = new ArrayList<>();
        List<Range> ranges = new ArrayList<>();
        List<TIntArrayList> positionMaps = new ArrayList<>();
        NSequenceWithQualityBuilder sequenceBuilder = new NSequenceWithQualityBuilder();
        TIntArrayList positionMap = new TIntArrayList();
        int blockStartPosition = -1;
        int assemblingFeatureTargetId = -1;
        int assemblingFeatureOffset = -1;
        int assemblingFeatureLength = -1;
        for (int i = 0; i < positionedStates.length; ++i) {
            if (isAbsent(positionedStates[i]))
                continue;

            if (blockStartPosition == -1)
                blockStartPosition = extractPosition(positionedStates[i]);

            int currentPosition = extractPosition(positionedStates[i]);

            int nextPosition = i == positionedStates.length - 1
                    ? Integer.MAX_VALUE
                    : isAbsent(positionedStates[i + 1])
                    ? Integer.MAX_VALUE
                    : extractPosition(positionedStates[i + 1]);

            assert currentPosition != nextPosition;

            int variantId = ((int) (positionedStates[i] >>> 8)) & 0xFFFFFF;

            NSequenceWithQuality seq = new NSequenceWithQuality(
                    variantIdToSequence.get(variantId),
                    (byte) positionedStates[i]);

            if (currentPosition == positionOfAssemblingFeature) {
                assert assemblingFeatureTargetId == -1;

                // Current implementation can work only with variants having the sequence in the assemblingFeature
                // region exactly equal to the clonal sequence
                if (variantId != clonalAssemblingFeatureVariantIndex)
                    // Terminating sequence assembling process, and returning null result
                    return null;

                assemblingFeatureTargetId = ranges.size();
                assemblingFeatureOffset = sequenceBuilder.size();
                assemblingFeatureLength = seq.size();
            }

            sequenceBuilder.append(seq);
            for (int pp = 0; pp < seq.size(); pp++)
                positionMap.add(currentPosition);

            // Condition met when:
            //   - contiguous sequence region break (not assembled gap)
            //   - last position (nextPosition == Integer.MAX_VALUE)
            if (currentPosition != nextPosition - 1) {
                sequences.add(sequenceBuilder.createAndDestroy());
                positionMaps.add(positionMap);

                // Naive:
                //   ranges.add(new Range(blockStartPosition, currentPosition + 1));
                // Eliminate edge deletions:
                ranges.add(
                        positionMap.isEmpty()
                                ? new Range(blockStartPosition, currentPosition + 1)
                                : new Range(positionMap.get(0), positionMap.get(positionMap.size() - 1) + 1));

                sequenceBuilder = new NSequenceWithQualityBuilder();
                positionMap = new TIntArrayList();
                blockStartPosition = nextPosition;
            }
        }

        assert blockStartPosition != -1;
        assert assemblingFeatureTargetId != -1;

        return new BranchSequences(
                branch.count,
                assemblingFeatureTargetId,
                assemblingFeatureOffset,
                assemblingFeatureLength,
                ranges.toArray(new Range[ranges.size()]),
                positionMaps.toArray(new TIntArrayList[positionMaps.size()]),
                sequences.toArray(new NSequenceWithQuality[sequences.size()]));
    }

    private static int extractPosition(long positionedState) {
        return (int) (positionedState >>> 32);
    }

    private static boolean isAbsent(long positionedState) {
        return (int) (positionedState & 0xFFFFFFFF) == ABSENT_PACKED_VARIANT_INFO;
    }

    private final class BranchSequences {
        /**
         * Count from VariantBranch
         */
        final double count;
        /**
         * Id of the target containing assemblingFeature
         */
        final int assemblingFeatureTargetId;
        /**
         * Offset of the assembling feature inside assemblingFeatureTargetId
         */
        final int assemblingFeatureOffset;
        /**
         * Length of an assembling feature inside assemblingFeatureTargetId, may be different from the original
         * assemblingFeatureLength as a result of assembly
         */
        final int assemblingFeatureLength;
        /**
         * Ranges of assembled contigs in global coordinates
         */
        final Range[] ranges;
        /**
         * Position maps for the assembled contigs (from contig position -> to global position). Used in trimming, to
         * correctly adjust ranges.
         */
        final TIntArrayList[] positionMaps;
        /**
         * Contigs
         */
        final NSequenceWithQuality[] sequences;

        BranchSequences(double count,
                        int assemblingFeatureTargetId, int assemblingFeatureOffset, int assemblingFeatureLength,
                        Range[] ranges, TIntArrayList[] positionMaps, NSequenceWithQuality[] sequences) {
            this.count = count;
            this.assemblingFeatureTargetId = assemblingFeatureTargetId;
            this.assemblingFeatureOffset = assemblingFeatureOffset;
            this.assemblingFeatureLength = assemblingFeatureLength;
            this.ranges = ranges;
            this.positionMaps = positionMaps;
            this.sequences = sequences;
            assert check();
        }

        boolean check() {
            if (sequences[assemblingFeatureTargetId].size() < assemblingFeatureOffset + assemblingFeatureLength)
                throw new IllegalArgumentException();
            if (ranges.length != positionMaps.length && ranges.length != sequences.length)
                throw new IllegalArgumentException();
            for (int i = 0; i < ranges.length; i++) {
                if (positionMaps[i].isEmpty())
                    continue;
                if (positionMaps[i].get(0) != ranges[i].getFrom() || positionMaps[i].get(positionMaps[i].size() - 1) != ranges[i].getTo() - 1)
                    throw new IllegalArgumentException();
                if (positionMaps[i].size() != sequences[i].size())
                    throw new IllegalArgumentException();
            }
            return true;
        }

        /**
         * Returns BranchSequences without i-th contig.
         */
        BranchSequences without(int i) {
            if (i == assemblingFeatureTargetId)
                throw new IllegalArgumentException();
            int newLength = ranges.length - 1;
            Range[] newRanges = new Range[newLength];
            System.arraycopy(ranges, 0, newRanges, 0, i);
            System.arraycopy(ranges, i + 1, newRanges, i, newLength - i);
            TIntArrayList[] newPositionMaps = new TIntArrayList[newLength];
            System.arraycopy(positionMaps, 0, newPositionMaps, 0, i);
            System.arraycopy(positionMaps, i + 1, newPositionMaps, i, newLength - i);
            NSequenceWithQuality[] newSequences = new NSequenceWithQuality[newLength];
            System.arraycopy(sequences, 0, newSequences, 0, i);
            System.arraycopy(sequences, i + 1, newSequences, i, newLength - i);
            return new BranchSequences(
                    count,
                    i < assemblingFeatureTargetId ? assemblingFeatureTargetId - 1 : assemblingFeatureTargetId,
                    assemblingFeatureOffset,
                    assemblingFeatureLength,
                    newRanges,
                    newPositionMaps,
                    newSequences);
        }

        /**
         * Returns new BranchSequences with i-th target splitted according to the rangesToCut.
         *
         * @param i           target id
         * @param rangesToCut ranges in local target coordinates (not global)
         */
        BranchSequences splitCut(int i, Range... rangesToCut) {
            if (rangesToCut.length == 0)
                return without(i);

            if (rangesToCut.length == 1 && rangesToCut[0].getLower() == 0 && rangesToCut[0].length() == sequences[i].size())
                return this;

            int newLength = ranges.length - 1 + rangesToCut.length;
            int destPos = i + rangesToCut.length;
            int rightCopyLen = ranges.length - i - 1;

            Range[] newRanges = new Range[newLength];
            System.arraycopy(ranges, 0, newRanges, 0, i);
            System.arraycopy(ranges, i + 1, newRanges, destPos, rightCopyLen);

            TIntArrayList[] newPositionMaps = new TIntArrayList[newLength];
            System.arraycopy(positionMaps, 0, newPositionMaps, 0, i);
            System.arraycopy(positionMaps, i + 1, newPositionMaps, destPos, rightCopyLen);

            NSequenceWithQuality[] newSequences = new NSequenceWithQuality[newLength];
            System.arraycopy(sequences, 0, newSequences, 0, i);
            System.arraycopy(sequences, i + 1, newSequences, destPos, rightCopyLen);

            Range assemblingFeatureRange = i == assemblingFeatureTargetId
                    ? new Range(assemblingFeatureOffset, assemblingFeatureOffset + assemblingFeatureLength)
                    : null;

            int newAssemblingFeatureOffset = i == assemblingFeatureTargetId
                    ? -1
                    : assemblingFeatureOffset;
            int newAssemblingFeatureTargetId = i == assemblingFeatureTargetId
                    ? -1
                    :
                    assemblingFeatureTargetId < i
                            ? assemblingFeatureTargetId
                            : assemblingFeatureTargetId - 1 + rangesToCut.length;

            for (int j = 0; j < rangesToCut.length; j++) {
                final Range rangeToCut = rangesToCut[j];
                if (assemblingFeatureRange != null && assemblingFeatureRange.intersectsWith(rangeToCut)) {
                    if (!rangeToCut.contains(new Range(assemblingFeatureOffset, assemblingFeatureOffset + assemblingFeatureLength)))
                        throw new IllegalArgumentException();
                    newRanges[i + j] = new Range(positionMaps[i].get(rangeToCut.getLower()), positionMaps[i].get(rangeToCut.getUpper() - 1) + 1);
                    newPositionMaps[i + j] = (TIntArrayList) positionMaps[i].subList(rangeToCut.getLower(), rangeToCut.getUpper());
                    newSequences[i + j] = sequences[i].getRange(rangeToCut);
                    newAssemblingFeatureTargetId = i + j;
                    newAssemblingFeatureOffset = assemblingFeatureOffset - rangeToCut.getLower();
                } else {
                    newRanges[i + j] = new Range(positionMaps[i].get(rangeToCut.getLower()), positionMaps[i].get(rangeToCut.getUpper() - 1) + 1);
                    newPositionMaps[i + j] = (TIntArrayList) positionMaps[i].subList(rangeToCut.getLower(), rangeToCut.getUpper());
                    newSequences[i + j] = sequences[i].getRange(rangeToCut);
                }
            }

            assert newAssemblingFeatureOffset != -1;
            assert newAssemblingFeatureTargetId != -1;

            return new BranchSequences(count, newAssemblingFeatureTargetId, newAssemblingFeatureOffset, assemblingFeatureLength,
                    newRanges, newPositionMaps, newSequences);
        }

        /**
         * Returns new BranchSequences with i-th target cut according to the rangeToCut.
         *
         * @param i          target id
         * @param rangeToCut range in local target coordinates (not global)
         */
        BranchSequences cut(int i, Range rangeToCut) {
            if (rangeToCut.getLower() == 0 && rangeToCut.length() == sequences[i].size())
                return this;

            Range[] newRanges = ranges.clone();
            TIntArrayList[] newPositionMaps = positionMaps.clone();
            NSequenceWithQuality[] newSequences = sequences.clone();
            if (i == assemblingFeatureTargetId) {
                if (!rangeToCut.contains(new Range(assemblingFeatureOffset, assemblingFeatureOffset + assemblingFeatureLength)))
                    throw new IllegalArgumentException();
                newRanges[i] = new Range(newPositionMaps[i].get(rangeToCut.getLower()), newPositionMaps[i].get(rangeToCut.getUpper() - 1) + 1);
                newPositionMaps[i] = (TIntArrayList) newPositionMaps[i].subList(rangeToCut.getLower(), rangeToCut.getUpper());
                newSequences[i] = newSequences[i].getRange(rangeToCut);
                return new BranchSequences(count, assemblingFeatureTargetId,
                        assemblingFeatureOffset - rangeToCut.getLower(), assemblingFeatureLength,
                        newRanges, newPositionMaps, newSequences);
            } else {
                newRanges[i] = new Range(newPositionMaps[i].get(rangeToCut.getLower()), newPositionMaps[i].get(rangeToCut.getUpper() - 1) + 1);
                newPositionMaps[i] = (TIntArrayList) newPositionMaps[i].subList(rangeToCut.getLower(), rangeToCut.getUpper());
                newSequences[i] = newSequences[i].getRange(rangeToCut);
                return new BranchSequences(count, assemblingFeatureTargetId, assemblingFeatureOffset, assemblingFeatureLength,
                        newRanges, newPositionMaps, newSequences);
            }
        }
    }

    /* ================================= Re-align and build final clone ====================================== */

    private Clone buildClone(BranchSequences targets) {
        Alignment<NucleotideSequence>[] vHitAlignments = new Alignment[targets.ranges.length],
                jHitAlignments = new Alignment[targets.ranges.length];
        if (vHit == null)
            throw new UnsupportedOperationException("No V hit.");
        NucleotideSequence vTopReferenceSequence = vHit.getGene().getFeature(vHit.getAlignedFeature());

        if (jHit == null)
            throw new UnsupportedOperationException("No J hit.");
        NucleotideSequence jTopReferenceSequence = jHit.getGene().getFeature(jHit.getAlignedFeature());

        // Excessive optimization
        AlignersCache cache = new AlignersCache();

        int assemblingFeatureLength = targets.assemblingFeatureLength;
        for (int i = 0; i < targets.ranges.length; i++) {
            Range range = targets.ranges[i];
            NucleotideSequence sequence = targets.sequences[i].getSequence();

            // Asserts
            if (range.getFrom() < N_LEFT_DUMMIES + lengthV
                    && range.getTo() >= N_LEFT_DUMMIES + lengthV
                    && i != targets.assemblingFeatureTargetId)
                throw new RuntimeException();

            if (range.getTo() >= rightAssemblingFeatureBound
                    && range.getFrom() < rightAssemblingFeatureBound
                    && i != targets.assemblingFeatureTargetId)
                throw new RuntimeException();

            // Params:
            // V floatingLeftBound = true / false
            // J floatingRightBound = true / false

            // ...V  -  V+CDR3+J  -  J...
            // ...V  -  VVVV  -  V+CDR3+J  -  J...

            if (range.getTo() < N_LEFT_DUMMIES + lengthV) {
                if (range.getTo() <= N_LEFT_DUMMIES)
                    // Target outside V region (to the left of V region)
                    continue;

                boolean floatingLeftBound =
                        !parameters.alignedRegionsOnly
                                && i == 0
                                && alignerParameters.getVAlignerParameters().getParameters().isFloatingLeftBound();

                // Can be reduced to a single statement
                if (range.getFrom() < N_LEFT_DUMMIES)
                    // This target contain extra non-V nucleotides on the left
                    vHitAlignments[i] = alignSeq1FromRight(
                            alignerParameters.getVAlignerParameters().getScoring(),
                            vTopReferenceSequence, sequence.getSequence(),
                            0, range.getTo() - N_LEFT_DUMMIES,
                            0, sequence.size(),
                            !floatingLeftBound,
                            cache);
                else if (floatingLeftBound)
                    vHitAlignments[i] = alignSeq1FromRight(
                            alignerParameters.getVAlignerParameters().getScoring(),
                            vTopReferenceSequence, sequence.getSequence(),
                            range.getFrom() - N_LEFT_DUMMIES, range.length(),
                            0, sequence.size(),
                            false,
                            cache);
                else
                    vHitAlignments[i] = Aligner.alignGlobal(
                            alignerParameters.getVAlignerParameters().getScoring(),
                            vTopReferenceSequence,
                            sequence,
                            range.getFrom() - N_LEFT_DUMMIES, range.length(),
                            0, sequence.size());
            } else if (i == targets.assemblingFeatureTargetId) {
                /*
                 *  V gene
                 */

                boolean vFloatingLeftBound =
                        !parameters.alignedRegionsOnly
                                && i == 0
                                && alignerParameters.getVAlignerParameters().getParameters().isFloatingLeftBound();

                // Can be reduced to a single statement
                if (range.getFrom() < N_LEFT_DUMMIES)
                    // This target contain extra non-V nucleotides on the left
                    vHitAlignments[i] = alignSeq1FromRight(
                            alignerParameters.getVAlignerParameters().getScoring(),
                            vTopReferenceSequence, sequence.getSequence(),
                            0, lengthV,
                            0, targets.assemblingFeatureOffset,
                            !vFloatingLeftBound,
                            cache);
                else if (vFloatingLeftBound)
                    vHitAlignments[i] = alignSeq1FromRight(
                            alignerParameters.getVAlignerParameters().getScoring(),
                            vTopReferenceSequence, sequence.getSequence(),
                            range.getFrom() - N_LEFT_DUMMIES, lengthV - (range.getFrom() - N_LEFT_DUMMIES),
                            0, targets.assemblingFeatureOffset,
                            false,
                            cache);
                else
                    vHitAlignments[i] = Aligner.alignGlobal(
                            alignerParameters.getVAlignerParameters().getScoring(),
                            vTopReferenceSequence,
                            sequence,
                            range.getFrom() - N_LEFT_DUMMIES, lengthV - (range.getFrom() - N_LEFT_DUMMIES),
                            0, targets.assemblingFeatureOffset);

                /*
                 *  J gene
                 */

                boolean jFloatingRightBound =
                        !parameters.alignedRegionsOnly
                                && i == targets.ranges.length - 1
                                && alignerParameters.getJAlignerParameters().getParameters().isFloatingRightBound();

                if (range.getTo() >= rightAssemblingFeatureBound + jLength)
                    // This target contain extra non-J nucleotides on the right
                    jHitAlignments[i] = alignSeq1FromLeft(
                            alignerParameters.getJAlignerParameters().getScoring(),
                            jTopReferenceSequence, sequence.getSequence(),
                            jOffset, jLength,
                            targets.assemblingFeatureOffset + assemblingFeatureLength, sequence.size() - (targets.assemblingFeatureOffset + assemblingFeatureLength),
                            !jFloatingRightBound,
                            cache);
                else if (jFloatingRightBound)
                    jHitAlignments[i] = alignSeq1FromLeft(
                            alignerParameters.getJAlignerParameters().getScoring(),
                            jTopReferenceSequence, sequence.getSequence(),
                            jOffset, range.getTo() - rightAssemblingFeatureBound,
                            targets.assemblingFeatureOffset + assemblingFeatureLength, sequence.size() - (targets.assemblingFeatureOffset + assemblingFeatureLength),
                            false,
                            cache);
                else
                    jHitAlignments[i] = Aligner.alignGlobal(
                            alignerParameters.getJAlignerParameters().getScoring(),
                            jTopReferenceSequence,
                            sequence,
                            jOffset, range.getTo() - rightAssemblingFeatureBound,
                            targets.assemblingFeatureOffset + assemblingFeatureLength, sequence.size() - (targets.assemblingFeatureOffset + assemblingFeatureLength));
            } else if (range.getFrom() > rightAssemblingFeatureBound) {
                if (range.getFrom() >= rightAssemblingFeatureBound + jLength)
                    // Target outside J region (to the right of J region)
                    continue;

                boolean floatingRightBound =
                        !parameters.alignedRegionsOnly
                                && i == targets.ranges.length - 1
                                && alignerParameters.getJAlignerParameters().getParameters().isFloatingRightBound();

                if (range.getTo() >= rightAssemblingFeatureBound + jLength)
                    // This target contain extra non-J nucleotides on the right
                    jHitAlignments[i] = alignSeq1FromLeft(
                            alignerParameters.getJAlignerParameters().getScoring(),
                            jTopReferenceSequence, sequence.getSequence(),
                            jOffset + (range.getFrom() - rightAssemblingFeatureBound), jLength - (range.getFrom() - rightAssemblingFeatureBound),
                            0, sequence.size(),
                            !floatingRightBound,
                            cache);
                else if (floatingRightBound)
                    jHitAlignments[i] = alignSeq1FromLeft(
                            alignerParameters.getJAlignerParameters().getScoring(),
                            jTopReferenceSequence, sequence.getSequence(),
                            jOffset + (range.getFrom() - rightAssemblingFeatureBound), range.length(),
                            0, sequence.size(),
                            false,
                            cache);
                else
                    jHitAlignments[i] = Aligner.alignGlobal(
                            alignerParameters.getJAlignerParameters().getScoring(),
                            jTopReferenceSequence,
                            sequence,
                            jOffset + (range.getFrom() - rightAssemblingFeatureBound), range.length(),
                            0, sequence.size());
            } else
                throw new RuntimeException();
        }

        NSequenceWithQuality assemblingFeatureSeq = targets.sequences[targets.assemblingFeatureTargetId]
                .getRange(targets.assemblingFeatureOffset, targets.assemblingFeatureOffset + targets.assemblingFeatureLength);
        Clone clone = cloneFactory.create(0, targets.count, getOriginalGeneScores(), new NSequenceWithQuality[]{assemblingFeatureSeq});

        vHitAlignments[targets.assemblingFeatureTargetId] =
                mergeTwoAlignments(
                        vHitAlignments[targets.assemblingFeatureTargetId],
                        vHit.getAlignment(0).move(targets.assemblingFeatureOffset));

        jHitAlignments[targets.assemblingFeatureTargetId] =
                mergeTwoAlignments(
                        jHit.getAlignment(0).move(targets.assemblingFeatureOffset),
                        jHitAlignments[targets.assemblingFeatureTargetId]);

        EnumMap<GeneType, VDJCHit[]> hits = new EnumMap<>(GeneType.class);
        for (GeneType gt : GeneType.VDJC_REFERENCE)
            hits.put(gt, Arrays.stream(clone.getHits(gt))
                    .map(h -> moveHitTarget(h, targets.assemblingFeatureTargetId,
                            targets.assemblingFeatureOffset, targets.ranges.length))
                    .toArray(VDJCHit[]::new));

        VDJCHit[] tmp = hits.get(Variable);
        int hitIndex = indexOfGene(tmp, vHit.getGene().getId());
        if (hitIndex != 0 && report != null) {
            report.onVHitReorder();
            tmp[hitIndex] = substituteAlignmentsAndScore(tmp[hitIndex], vHitAlignments, tmp[0].getScore() + 1);
        } else
            tmp[0] = substituteAlignments(tmp[0], vHitAlignments);

        tmp = hits.get(Joining);
        hitIndex = indexOfGene(tmp, jHit.getGene().getId());
        if (hitIndex != 0 && report != null) {
            report.onJHitReorder();
            tmp[hitIndex] = substituteAlignmentsAndScore(tmp[hitIndex], jHitAlignments, tmp[0].getScore() + 1);
        } else
            tmp[0] = substituteAlignments(tmp[0], jHitAlignments);

        return new Clone(targets.sequences, hits, targets.count, 0);
    }

    static int indexOfGene(VDJCHit[] hits, VDJCGeneId gene) {
        for (int i = 0; i < hits.length; i++)
            if (hits[i].getGene().getId().equals(gene))
                return i;
        throw new IllegalStateException();
    }

    static final class AlignersCache {
        final CachedIntArray linearCache = new CachedIntArray();
        final MatrixCache affineCache = new MatrixCache();
    }

    //fixme write docs
    static Alignment<NucleotideSequence>
    alignSeq1FromLeft(AlignmentScoring<NucleotideSequence> scoring,
                      NucleotideSequence seq1, NucleotideSequence seq2,
                      int offset1, int length1,
                      int offset2, int length2,
                      boolean global,
                      AlignersCache cache) {
        if (scoring instanceof LinearGapAlignmentScoring)
            return alignLinearSeq1FromLeft(
                    (LinearGapAlignmentScoring<NucleotideSequence>) scoring,
                    seq1, seq2, offset1, length1, offset2, length2, global, cache.linearCache);
        else
            return alignAffineSeq1FromLeft(
                    (AffineGapAlignmentScoring<NucleotideSequence>) scoring,
                    seq1, seq2, offset1, length1, offset2, length2, global, cache.affineCache);
    }

    //fixme write docs
    static Alignment<NucleotideSequence>
    alignSeq1FromRight(AlignmentScoring<NucleotideSequence> scoring,
                       NucleotideSequence seq1, NucleotideSequence seq2,
                       int offset1, int length1,
                       int offset2, int length2,
                       boolean global,
                       AlignersCache cache) {
        if (scoring instanceof LinearGapAlignmentScoring)
            return alignLinearSeq1FromRight(
                    (LinearGapAlignmentScoring<NucleotideSequence>) scoring,
                    seq1, seq2, offset1, length1, offset2, length2, global, cache.linearCache);
        else
            return alignAffineSeq1FromRight(
                    (AffineGapAlignmentScoring<NucleotideSequence>) scoring,
                    seq1, seq2, offset1, length1, offset2, length2, global, cache.affineCache);
    }

    //fixme write docs
    static Alignment<NucleotideSequence>
    alignLinearSeq1FromLeft(LinearGapAlignmentScoring<NucleotideSequence> scoring,
                            NucleotideSequence seq1, NucleotideSequence seq2,
                            int offset1, int length1,
                            int offset2, int length2,
                            boolean global,
                            CachedIntArray cache) {
        MutationsBuilder<NucleotideSequence> mutations = new MutationsBuilder<>(NucleotideSequence.ALPHABET);
        BandedSemiLocalResult result;
        int width = 2 * length1;
        if (global) {
            int seq2added = width;
            if (length2 > length1) {
                length2 = Math.min(length2, length1 + width);
                seq2added = Math.min(length2, width + (length2 - length1));
            }
            result = BandedLinearAligner.alignRightAdded0(scoring, seq1, seq2, offset1, length1, 0, offset2, length2, seq2added, width, mutations, cache);
        } else
            result = BandedLinearAligner.alignSemiLocalLeft0(scoring, seq1, seq2, offset1, length1, offset2, length2, width, Integer.MIN_VALUE, mutations, cache);

        return new Alignment<>(seq1, mutations.createAndDestroy(),
                new Range(offset1, result.sequence1Stop + 1), new Range(offset2, result.sequence2Stop + 1),
                result.score);
    }

    static Alignment<NucleotideSequence>
    alignAffineSeq1FromLeft(AffineGapAlignmentScoring<NucleotideSequence> scoring,
                            NucleotideSequence seq1, NucleotideSequence seq2,
                            int offset1, int length1,
                            int offset2, int length2,
                            boolean global,
                            MatrixCache cache) {
        MutationsBuilder<NucleotideSequence> mutations = new MutationsBuilder<>(NucleotideSequence.ALPHABET);
        BandedSemiLocalResult result;
        int width = 2 * length1;
        if (global) {
            int seq2added = width;
            if (length2 > length1) {
                length2 = Math.min(length2, length1 + width);
                seq2added = Math.min(length2, width + (length2 - length1));
            }
            result = BandedAffineAligner.semiGlobalRight0(scoring, seq1, seq2, offset1, length1, 0, offset2, length2, seq2added, width, mutations, cache);
        } else
            result = BandedAffineAligner.semiLocalRight0(scoring, seq1, seq2, offset1, length1, offset2, length2, width, mutations, cache);

        return new Alignment<>(seq1, mutations.createAndDestroy(),
                new Range(offset1, result.sequence1Stop + 1), new Range(offset2, result.sequence2Stop + 1),
                result.score);
    }

    //fixme write docs
    static Alignment<NucleotideSequence>
    alignLinearSeq1FromRight(LinearGapAlignmentScoring<NucleotideSequence> scoring,
                             NucleotideSequence seq1, NucleotideSequence seq2,
                             int offset1, int length1,
                             int offset2, int length2,
                             boolean global,
                             CachedIntArray cache) {
        MutationsBuilder<NucleotideSequence> mutations = new MutationsBuilder<>(NucleotideSequence.ALPHABET);
        BandedSemiLocalResult result;
        int width = 2 * length1;
        if (global) {
            int seq2added = width;
            if (length2 > length1) {
                int length2upd = Math.min(length2, length1 + width);
                offset2 += length2 - length2upd;
                length2 = length2upd;
                seq2added = Math.min(length2, width + (length2 - length1));
            }
            result = BandedLinearAligner.alignLeftAdded0(scoring, seq1, seq2, offset1, length1, 0, offset2, length2, seq2added, width, mutations, cache);
        } else
            result = BandedLinearAligner.alignSemiLocalRight0(scoring, seq1, seq2, offset1, length1, offset2, length2, width, Integer.MIN_VALUE, mutations, cache);
        return new Alignment<>(seq1, mutations.createAndDestroy(),
                new Range(result.sequence1Stop, offset1 + length1), new Range(result.sequence2Stop, offset2 + length2),
                result.score);
    }

    static Alignment<NucleotideSequence>
    alignAffineSeq1FromRight(AffineGapAlignmentScoring<NucleotideSequence> scoring,
                             NucleotideSequence seq1, NucleotideSequence seq2,
                             int offset1, int length1,
                             int offset2, int length2,
                             boolean global,
                             MatrixCache cache) {
        MutationsBuilder<NucleotideSequence> mutations = new MutationsBuilder<>(NucleotideSequence.ALPHABET);
        BandedSemiLocalResult result;
        int width = 2 * length1;
        if (global) {
            int seq2added = width;
            if (length2 > length1) {
                int length2upd = Math.min(length2, length1 + width);
                offset2 += length2 - length2upd;
                length2 = length2upd;
                seq2added = Math.min(length2, width + (length2 - length1));
            }
            result = BandedAffineAligner.semiGlobalLeft0(scoring, seq1, seq2, offset1, length1, 0, offset2, length2, seq2added, width, mutations, cache);
        } else
            result = BandedAffineAligner.semiLocalLeft0(scoring, seq1, seq2, offset1, length1, offset2, length2, width, mutations, cache);
        return new Alignment<>(seq1, mutations.createAndDestroy(),
                new Range(result.sequence1Stop, offset1 + length1), new Range(result.sequence2Stop, offset2 + length2),
                result.score);
    }


    static VDJCHit substituteAlignments(VDJCHit hit, Alignment<NucleotideSequence>[] alignments) {
        return new VDJCHit(hit.getGene(), alignments, hit.getAlignedFeature(), hit.getScore());
    }

    static VDJCHit substituteAlignmentsAndScore(VDJCHit hit, Alignment<NucleotideSequence>[] alignments, float score) {
        return new VDJCHit(hit.getGene(), alignments, hit.getAlignedFeature(), score);
    }

    static VDJCHit moveHitTarget(VDJCHit hit, int targetTargetId, int sequence2OffsetInTarget, int targetsCount) {
        // TODO (!!!) extend alignments for targets.assemblingFeatureTargetId

        if (hit.numberOfTargets() != 1)
            throw new IllegalArgumentException();

        Alignment<NucleotideSequence>[] alignments = new Alignment[targetsCount];
        Alignment<NucleotideSequence> al = hit.getAlignment(0);
        if (al != null)
            alignments[targetTargetId] = al.move(sequence2OffsetInTarget);
        return new VDJCHit(hit.getGene(), alignments, hit.getAlignedFeature(), hit.getScore());
    }

    static <S extends Sequence<S>> Alignment<S> mergeTwoAlignments(Alignment<S> a1, Alignment<S> a2) {
        if (a1.getSequence1Range().getTo() != a2.getSequence1Range().getFrom()
                || a1.getSequence2Range().getTo() != a2.getSequence2Range().getFrom()
                || a1.getSequence1() != a2.getSequence1() /* Compare reference */)
            throw new IllegalArgumentException();

        return new Alignment<>(a1.getSequence1(), a1.getAbsoluteMutations().concat(a2.getAbsoluteMutations()),
                new Range(a1.getSequence1Range().getFrom(), a2.getSequence1Range().getTo()),
                new Range(a1.getSequence2Range().getFrom(), a2.getSequence2Range().getTo()),
                a1.getScore() + a2.getScore());
    }

    /* ================================= Computing variants for single point ====================================== */

    /**
     * Call variants for a single position
     */
    private List<Variant> callVariantsForPoint(int[] pointVariantInfos, BitSet targetReads, boolean isAssemblingFeature) {
        // Pre-calculating number of present variants
        int count = 0;
        for (int readId = targetReads.nextSetBit(0); readId >= 0; readId = targetReads.nextSetBit(readId + 1))
            if (pointVariantInfos[readId] != ABSENT_PACKED_VARIANT_INFO)
                ++count;

        if (count == 0)
            return Collections.singletonList(new Variant(ABSENT_PACKED_VARIANT_INFO, targetReads, 0));

        // List of readIds of reads that either:
        //   - don't cover this point
        //   - has insignificant variant in this position
        BitSet unassignedVariants = new BitSet();

        long totalSumQuality = 0;

        // Sorting to GroupBy variantId
        // target = | variant id (3 bytes) | quality (1 byte) | read id (4 bytes) |
        long[] targets = new long[count];
        int i = 0;
        for (int readId = targetReads.nextSetBit(0); readId >= 0; readId = targetReads.nextSetBit(readId + 1)) {
            if (pointVariantInfos[readId] != ABSENT_PACKED_VARIANT_INFO) {
                targets[i++] = ((long) pointVariantInfos[readId]) << 32 | readId;
                totalSumQuality += 0x7F & pointVariantInfos[readId];
            } else
                unassignedVariants.set(readId);
        }
        Arrays.sort(targets);

        // Collecting measures for each variant
        int blockBegin = 0;
        int currentVariant = (int) (targets[blockBegin] >>> 40);
        int currentIndex = 0;
        long variantSumQuality = 0;

        int nonEdgePoints = 0;

        // Will be used if no significant variant is found
        int bestVariant = -1;
        long bestVariantSumQuality = -1;

        ArrayList<Variant> variants = new ArrayList<>();
        do {
            if (currentIndex == count || currentVariant != (int) (targets[currentIndex] >>> 40)) {
                // Checking significance conditions
                if ((1.0 * nonEdgePoints / (currentIndex - blockBegin) >= parameters.minimalNonEdgePointsFraction)
                        && variantSumQuality >= requiredMinimalSumQuality
                        && ((variantSumQuality >= parameters.branchingMinimalSumQuality
                        && variantSumQuality >= parameters.branchingMinimalQualityShare * totalSumQuality)
                        || variantSumQuality >= parameters.decisiveBranchingSumQualityThreshold)) {
                    // Variant is significant
                    BitSet reads = new BitSet();
                    for (int j = currentIndex - 1; j >= blockBegin; --j)
                        reads.set((int) targets[j]);
                    variants.add(new Variant((currentVariant << 8) | (int) Math.min(SequenceQuality.MAX_QUALITY_VALUE, variantSumQuality),
                            reads, currentIndex - blockBegin));
                } else {
                    // Variant is not significant
                    for (int j = currentIndex - 1; j >= blockBegin; --j)
                        unassignedVariants.set((int) targets[j]);
                    if (variantSumQuality > bestVariantSumQuality) {
                        bestVariant = currentVariant;
                        // totalSumQuality is definitely less than Long because variantSumQuality < decisiveBranchingSumQualityThreshold
                        bestVariantSumQuality = variantSumQuality;
                    }
                }

                if (currentIndex != count) {
                    blockBegin = currentIndex;
                    variantSumQuality = 0x7F & (targets[blockBegin] >>> 32);
                    currentVariant = (int) (targets[blockBegin] >>> 40);

                    nonEdgePoints = 0;
                    if (((targets[blockBegin] >>> 32) & 0x80) == 0)
                        ++nonEdgePoints;
                }
            } else {
                if (((targets[currentIndex] >>> 32) & 0x80) == 0)
                    ++nonEdgePoints;
                variantSumQuality += 0x7F & (targets[currentIndex] >>> 32);
            }
        } while (++currentIndex <= count);

        if (variants.isEmpty()) {
            // Checking best variant to meet output criteria
            // Always assemble assembling feature
            if (isAssemblingFeature ||
                    (bestVariantSumQuality >= parameters.outputMinimalQualityShare * totalSumQuality &&
                            bestVariantSumQuality >= parameters.outputMinimalSumQuality)) {
                // no significant variants
                assert bestVariant != -1;
                BitSet reads = new BitSet();
                for (long target : targets)
                    reads.set((int) target);
                reads.or(unassignedVariants);
                double p = 1 - 1.0 * bestVariantSumQuality / totalSumQuality;
                long phredQuality = p == 0 ? bestVariantSumQuality : Math.min((long) (-10 * Math.log10(p)), bestVariantSumQuality);
                // nSignificant = 1 (will not be practically used, only one variant, don't care)
                return Collections.singletonList(
                        new Variant(bestVariant << 8 | (int) Math.min((long) SequenceQuality.MAX_QUALITY_VALUE, phredQuality),
                                reads, 1));
            } else
                // No variants to output (poorly covered or controversial position)
                return Collections.singletonList(new Variant(ABSENT_PACKED_VARIANT_INFO, targetReads, 0));
        } else {
            for (Variant variant : variants)
                variant.reads.or(unassignedVariants);
            return variants;
        }
    }

    private static class Variant {
        final int variantInfo;
        final BitSet reads;
        final int nSignificant;

        Variant(int variantInfo, BitSet reads, int nSignificant) {
            this.variantInfo = variantInfo;
            this.reads = reads;
            this.nSignificant = nSignificant;
        }
    }

    /* ======================================== Collect raw initial data ============================================= */

    private int getVariantIndex(NucleotideSequence sequence) {
        if (sequence.size() == 0)
            return NucleotideSequence.ALPHABET.basicSize();
        if (sequence.size() == 1)
            return sequence.getSequence().codeAt(0);
        int seqIndex = sequenceToVariantId.putIfAbsent(sequence, sequenceToVariantId.size());
        if (seqIndex == -1) {
            seqIndex = sequenceToVariantId.size() - 1;
            variantIdToSequence.put(seqIndex, sequence);
        }
        return seqIndex;
    }

    /**
     * Sets common sequences states with well-defined ids. Returns assembling feature variant id.
     */
    private int initVariantMappings(NucleotideSequence clonalAssemblingFeatureSequence) {
        assert sequenceToVariantId.isEmpty();
        assert variantIdToSequence.isEmpty();

        // Single letters
        for (byte letter = 0; letter < NucleotideSequence.ALPHABET.basicSize(); letter++) {
            NucleotideSequence seq = new NucleotideSequence(new byte[]{letter});
            sequenceToVariantId.put(seq, letter);
            variantIdToSequence.put(letter, seq);
        }

        // Empty sequence
        sequenceToVariantId.put(NucleotideSequence.EMPTY, NucleotideSequence.ALPHABET.basicSize());
        variantIdToSequence.put(NucleotideSequence.ALPHABET.basicSize(), NucleotideSequence.EMPTY);

        // Assembling feature
        int assemblingFeatureId = NucleotideSequence.ALPHABET.basicSize() + 1;
        sequenceToVariantId.put(clonalAssemblingFeatureSequence, assemblingFeatureId);
        variantIdToSequence.put(assemblingFeatureId, clonalAssemblingFeatureSequence);

        return assemblingFeatureId;
    }

    /**
     * Aggregates information about position states in all the provided alignments, and returns the object that allows
     * to iterate from one position to another (sorted by coverage, from most covered to less covered) and see states
     * across all the alignments for each of the positions.
     *
     * @param alignments supplier of alignments iterators. Will be invoked twice.
     */
    public RawVariantsData calculateRawData(Supplier<OutputPort<VDJCAlignments>> alignments) {
        TIntIntHashMap coverage = new TIntIntHashMap();
        TIntObjectHashMap<TIntObjectHashMap<VariantAggregator>> variants = new TIntObjectHashMap<>();

        // Collecting coverage and VariantAggregators
        int nAlignments = 0;
        for (VDJCAlignments al : CUtils.it(alignments.get())) {
            // al = al.mapAlignments(AlignmentUtils::shiftIndelsAtHomopolymers); // FIXME
            ++nAlignments;
            for (PointSequence point : toPointSequences(al)) {
                int seqIndex = getVariantIndex(point.sequence.getSequence());

                coverage.adjustOrPutValue(point.point, 1, 1);

                TIntObjectHashMap<VariantAggregator> map = variants.get(point.point);
                if (map == null)
                    variants.put(point.point, map = new TIntObjectHashMap<>());

                VariantAggregator var = map.get(seqIndex);
                if (var == null)
                    map.put(point.point, var = new VariantAggregator());

                var.count += 1;
                var.sumQuality += 0x7F & point.quality;
            }
        }

        assert nAlignments > 0;

        // Pre-allocating arrays

        // Co-sorting positions according to coverage
        long[] forSort = new long[coverage.size()];
        TIntIntIterator iterator = coverage.iterator();
        int i = 0;
        while (iterator.hasNext()) {
            iterator.advance();
            forSort[i++] = -((((long) iterator.value()) << 32) | iterator.key());
        }

        Arrays.sort(forSort);
        int[] pointsArray = Arrays.stream(forSort).mapToInt(l -> (int) (-l)).toArray();
        TIntIntHashMap revIndex = new TIntIntHashMap();
        for (int j = 0; j < pointsArray.length; j++)
            revIndex.put(pointsArray[j], j);

        int[] coverageArray = Arrays.stream(forSort).mapToInt(l -> (int) ((-l) >> 32)).toArray();

        // Allocating packed data
        int[][] packedData = new int[pointsArray.length][nAlignments];
        for (int[] aPackedData : packedData)
            Arrays.fill(aPackedData, -1);

        // Main data collection loop
        i = 0;
        for (VDJCAlignments al : CUtils.it(alignments.get())) {
            // al = al.mapAlignments(AlignmentUtils::shiftIndelsAtHomopolymers); // FIXME
            for (PointSequence point : toPointSequences(al)) {
                int pointIndex = revIndex.get(point.point);
                packedData[pointIndex][i] =
                        (sequenceToVariantId.get(point.sequence.getSequence()) << 8)
                                | (0xFF & point.quality);
            }
            i++;
        }

        // Returning prepared data
        return new RawVariantsData(nAlignments, pointsArray, coverageArray) {
            @Override
            OutputPort<int[]> createPort() {
                return CUtils.asOutputPort(Arrays.asList(packedData));
            }
        };
    }

    /**
     * Represents aggregated information about nucleotide states for all positions in all reads aggregated with {@link
     * #calculateRawData(Supplier)}.
     */
    public abstract class RawVariantsData {
        /**
         * Total number of reads
         */
        final int nReads;
        final int[] points;
        final int[] coverage;

        RawVariantsData(int nReads, int[] points, int[] coverage) {
            this.nReads = nReads;
            this.points = points;
            this.coverage = coverage;
        }

        /**
         * Returns output port, that iterates over positions in global coordinates, and returns variant-array for each
         * of the positions ( array[readId] = (variantId << 8) | minQuality ). Arrays are returned in the sequences
         * determined by {@link #points}, e.g. firs array will correspond to point[0] position, the second to point[1]
         * position etc. Positions are sorted according to their coverage.
         */
        abstract OutputPort<int[]> createPort();

        /**
         * To be used with file-based storage media
         */
        void destroy() {
        }

        /**
         * String representation of this state matrix
         *
         * @param qualityThreshold quality threshold (positions with quality lower then this value, wil be printed in
         *                         lower case)
         * @param readsFrom        range of read ids to print (beginning; inclusive)
         * @param readsTo          range of read ids to print (end; exclusive)
         */
        public String toString(byte qualityThreshold, int readsFrom, int readsTo) {
            int minPosition = Arrays.stream(points).min().getAsInt();
            int maxPosition = Arrays.stream(points).max().getAsInt() + 1;

            // Calculating maximal observed sequence length for each of the positions
            int[] len = new int[maxPosition - minPosition];
            OutputPort<int[]> port = createPort();
            for (int position : points) {
                int[] states = port.take();
                for (int j = readsFrom; j < readsTo; j++) {
                    int state = states[j];
                    if (state != ABSENT_PACKED_VARIANT_INFO)
                        len[position - minPosition] = Math.max(len[position - minPosition], variantIdToSequence.get(state >>> 8).size());
                }
            }
            assert port.take() == null;

            // Calculating position projection
            int[] positionMap = new int[len.length];
            for (int i = 1; i < len.length; i++)
                positionMap[i] = positionMap[i - 1] + len[i - 1];
            int maxLength = positionMap[len.length - 1] + len[len.length - 1];

            // Allocating main array
            char[][] result = new char[readsTo - readsFrom][maxLength];
            for (char[] line : result)
                Arrays.fill(line, ' ');

            char[] positionStrokes = new char[maxLength];
            char[] positionValues = new char[maxLength + 10];
            Arrays.fill(positionStrokes, ' ');
            Arrays.fill(positionValues, ' ');
            for (int i = 0; i < len.length; i++) {
                positionStrokes[positionMap[i]] = '|';
                if (i % 10 == 0) {
                    String val = "" + (i + minPosition);
                    for (int j = 0; j < val.length(); j++)
                        positionValues[positionMap[i] + j] = val.charAt(j);
                }
            }

            port = createPort();
            for (int position : points) {
                int[] states = port.take();
                for (int j = readsFrom; j < readsTo; j++) {
                    int state = states[j];
                    if (state == ABSENT_PACKED_VARIANT_INFO)
                        continue;
                    String seq = variantIdToSequence.get(state >>> 8).toString();
                    if ((state & 0x7F) < qualityThreshold)
                        seq = seq.toLowerCase();
                    for (int k = 0; k < len[position - minPosition]; k++)
                        if (k < seq.length())
                            result[j + readsFrom][positionMap[position - minPosition] + k] = seq.charAt(k);
                        else
                            result[j + readsFrom][positionMap[position - minPosition] + k] = '.';
                }
            }
            assert port.take() == null;


            return new String(positionValues) + "\n" +
                    new String(positionStrokes) + "\n" +
                    Arrays.stream(result)
                            .map(String::new)
                            .collect(Collectors.joining("\n"));
        }

        /**
         * String representation of this state matrix
         *
         * @param qualityThreshold quality threshold (positions with quality lower then this value, wil be printed in
         *                         lower case)
         */
        public String toCsv(byte qualityThreshold) {
            int minPosition = Arrays.stream(points).min().getAsInt();
            int maxPosition = Arrays.stream(points).max().getAsInt() + 1;

            String[][] cells = new String[nReads][maxPosition - minPosition + 1];

            OutputPort<int[]> port = createPort();
            for (int position : points) {
                int[] states = port.take();
                for (int j = 0; j < nReads; j++) {
                    int state = states[j];
                    if (state != ABSENT_PACKED_VARIANT_INFO) {
                        String seq = variantIdToSequence.get(state >>> 8).toString();
                        if ((state & 0x7F) < qualityThreshold)
                            seq = seq.toLowerCase();
                        if (seq.equals(""))
                            seq = "-";
                        cells[j][position - minPosition] = seq;
                    }
                }
            }
            assert port.take() == null;

            final String allLines = IntStream.range(0, nReads)
                    .mapToObj(readIndex ->
                            "a" + readIndex + "\t" +
                                    IntStream
                                            .range(0, maxPosition - minPosition + 1)
                                            .mapToObj(positionOffset -> {
                                                String cell = cells[readIndex][positionOffset];
                                                return cell == null ? "" : cell;
                                            })
                                            .collect(Collectors.joining("\t"))
                    ).collect(Collectors.joining("\n"));

            StringBuilder header = new StringBuilder();
            header.append("aIndex\t").append(IntStream
                    .range(0, maxPosition - minPosition + 1)
                    .mapToObj(positionOffset -> "" + (positionOffset + minPosition))
                    .collect(Collectors.joining("\t")));

            return header.toString() + "\n" + allLines;
        }

        /**
         * String representation of this state matrix
         *
         * @param qualityThreshold quality threshold (positions with quality lower then this value, wil be printed in
         *                         lower case)
         */
        public String toString(byte qualityThreshold) {
            return toString(qualityThreshold, 0, nReads);
        }

        /**
         * String representation of this state matrix
         */
        public String toString() {
            return toString((byte) 10);
        }
    }

    private static final class VariantAggregator {
        long sumQuality = 0;
        int count = 0;
    }

    PointSequence[] toPointSequences(VDJCAlignments alignments) {
        NSequenceWithQuality assemblingFeature = alignments.getFeature(this.assemblingFeature);
        byte quality = assemblingFeature.getQuality().minValue();
        //if (!inSplitRegion(positionOfAssemblingFeature))
        quality |= 0x80;
        return Stream.concat(
                Stream.of(new PointSequence(positionOfAssemblingFeature, assemblingFeature, quality)),
                IntStream.range(0, alignments.numberOfTargets())
                        .mapToObj(i -> toPointSequences(alignments, i))
                        .flatMap(Collection::stream)
                        .collect(Collectors.groupingBy(s -> s.point))
                        .values().stream()
                        .map(l -> l.stream().max(Comparator.comparingInt(a -> a.quality)).get()))
                .toArray(PointSequence[]::new);
    }

    List<PointSequence> toPointSequences(VDJCAlignments alignments, int iTarget) {

        //
        //  N_LEFT_DUMMIES     assemblingFeatureLength
        //  ------|--------------|--------------|------------------------>
        //        ↓              ↓              ↓
        //  0000000vvvvvvvvvvvvvvCDR3CDR3CDR3CDR3jjjjjjjjjjjjjjjjCCCCCCCCC

        // VDJCHit vHit = alignments.getBestHit(Variable);
        // Alignment<NucleotideSequence> vAlignment =
        //         (vHit == null
        //                 || vHit.getAlignment(iTarget) == null
        //                 || !Objects.equals(genes.v, vHit.getGene())
        //                 || vHit.getAlignment(iTarget).getSequence1Range().getFrom() > lengthV)
        //                 ? null
        //                 : vHit.getAlignment(iTarget);

        final Optional<VDJCHit> vHit = Arrays.stream(alignments.getHits(Variable) == null ? new VDJCHit[0] : alignments.getHits(Variable))
                .filter(hit -> hit != null && Objects.equals(genes.v, hit.getGene()))
                .findFirst();
        Alignment<NucleotideSequence> vAlignment = vHit
                .map(hit -> hit.getAlignment(iTarget))
                .filter(al -> al != null && al.getSequence1Range().getFrom() <= lengthV)
                .orElse(null);

        // VDJCHit jHit = alignments.getBestHit(Joining);
        // Alignment<NucleotideSequence> jAlignment =
        //         (jHit == null
        //                 || jHit.getAlignment(iTarget) == null
        //                 || !Objects.equals(genes.j, jHit.getGene())
        //                 || jHit.getAlignment(iTarget).getSequence1Range().getTo() < jOffset)
        //                 ? null
        //                 : jHit.getAlignment(iTarget);

        final Optional<VDJCHit> jHit = Arrays.stream(alignments.getHits(Joining) == null ? new VDJCHit[0] : alignments.getHits(Joining))
                .filter(hit -> hit != null && Objects.equals(genes.j, hit.getGene()))
                .findFirst();
        Alignment<NucleotideSequence> jAlignment = jHit
                .map(hit -> hit.getAlignment(iTarget))
                .filter(al -> al != null && al.getSequence1Range().getTo() >= jOffset)
                .orElse(null);

        VDJCPartitionedSequence target = alignments.getPartitionedTarget(iTarget,
                vHit.map(VDJCHit::getGene).orElse(null),
                null,
                jHit.map(VDJCHit::getGene).orElse(null),
                null);
        NSequenceWithQuality targetSeq = alignments.getTarget(iTarget);

        List<PointSequence> points = new ArrayList<>();
        if (target.getPartitioning().isAvailable(assemblingFeature.getFirstPoint())) {
            // This target contains left edge of the assembling feature
            int leftStop = target.getPartitioning().getPosition(assemblingFeature.getFirstPoint());
            if (hasV) {
                if (vAlignment != null)
                    toPointSequencesByAlignments(points,
                            vAlignment,
                            targetSeq,
                            new Range(
                                    parameters.alignedRegionsOnly ? vAlignment.getSequence2Range().getFrom() : 0,
                                    leftStop),
                            N_LEFT_DUMMIES);
            } else if (!parameters.alignedRegionsOnly)
                toPointSequencesNoAlignments(points, targetSeq, new Range(0, leftStop), N_LEFT_DUMMIES - leftStop);
        } else if (hasV && vAlignment != null)
            // This target ends before beginning (left edge) of the assembling feature
            toPointSequencesByAlignments(points,
                    vAlignment,
                    targetSeq,
                    new Range(
                            parameters.alignedRegionsOnly ? vAlignment.getSequence2Range().getFrom() : 0,
                            vAlignment.getSequence2Range().getTo()),
                    N_LEFT_DUMMIES);

        if (target.getPartitioning().isAvailable(assemblingFeature.getLastPoint())) {
            // This target contains right edge of the assembling feature
            int rightStart = target.getPartitioning().getPosition(assemblingFeature.getLastPoint());
            if (hasJ) {
                if (jAlignment != null)
                    toPointSequencesByAlignments(points,
                            jAlignment,
                            targetSeq,
                            new Range(rightStart,
                                    parameters.alignedRegionsOnly
                                            ? jAlignment.getSequence2Range().getTo()
                                            : targetSeq.size()),
                            N_LEFT_DUMMIES + lengthV + assemblingFeatureLength - jOffset);
            } else
                toPointSequencesNoAlignments(points, targetSeq, new Range(rightStart, targetSeq.size()), N_LEFT_DUMMIES + lengthV + assemblingFeatureLength - rightStart);
        } else if (hasJ && jAlignment != null)
            // This target starts after the end (right edge) of the assembling feature
            toPointSequencesByAlignments(points,
                    jAlignment,
                    targetSeq,
                    new Range(jAlignment.getSequence2Range().getFrom(),
                            parameters.alignedRegionsOnly
                                    ? jAlignment.getSequence2Range().getTo()
                                    : targetSeq.size()),
                    N_LEFT_DUMMIES + lengthV + assemblingFeatureLength - jOffset);

        return points;
    }

    void toPointSequencesByAlignments(List<PointSequence> points,
                                      Alignment<NucleotideSequence> alignment,
                                      NSequenceWithQuality seq2,
                                      Range seq2Range,
                                      int offset) {

        // if (seq2Range.length() == 0)
        //     return;

        // alignment = AlignmentUtils.shiftIndelsAtHomopolymers(alignment);

        Range
                alSeq2Range = alignment.getSequence2Range(),
                alSeq2RangeIntersection = alSeq2Range.intersectionWithTouch(seq2Range),
                alSeq1RangeIntersection = convertToSeq1Range(alignment, alSeq2RangeIntersection);

        assert alSeq1RangeIntersection != null;

        int shift;

        // left
        shift = offset + alignment.getSequence1Range().getFrom() - alignment.getSequence2Range().getFrom();
        for (int i = seq2Range.getFrom(); i < alSeq2RangeIntersection.getFrom(); ++i)
            points.add(createPointSequence(i + shift, seq2, i, i + 1, alignment.getSequence2Range()));

        // central
        for (int i = alSeq1RangeIntersection.getFrom(); i < alSeq1RangeIntersection.getTo(); ++i)
            points.add(createPointSequence(i + offset, seq2, alignment.convertToSeq2Range(new Range(i, i + 1)), alignment.getSequence2Range()));

        // right
        shift = offset + alignment.getSequence1Range().getTo() - alignment.getSequence2Range().getTo();
        for (int i = alSeq2RangeIntersection.getTo(); i < seq2Range.getTo(); ++i)
            points.add(createPointSequence(i + shift, seq2, i, i + 1, alignment.getSequence2Range()));
    }

    static Range convertToSeq1Range(Alignment alignment, Range rangeInSeq2) {
        int from = alignment.convertToSeq1Position(rangeInSeq2.getFrom());

        if (rangeInSeq2.isEmpty())
            if (from == -1)
                return null;
            else
                return new Range(from, from);

        int to = alignment.convertToSeq1Position(rangeInSeq2.getTo() - 1);

        if (from == -1 || to == -1)
            return null;

        if (from < 0)
            from = -2 - from;
        if (to < 0)
            to = -3 - to;

        return new Range(from, to + 1);
    }


    void toPointSequencesNoAlignments(List<PointSequence> points,
                                      NSequenceWithQuality seq2,
                                      Range seq2Range,
                                      int offset) {
        for (int i = seq2Range.getFrom(); i < seq2Range.getTo(); ++i)
            points.add(createPointSequence(i + offset, seq2, i, i + 1, seq2Range));
    }

    PointSequence createPointSequence(int point, NSequenceWithQuality seq, Range range, Range seq2alignmentRange) {
        return createPointSequence(point, seq, range.getFrom(), range.getTo(), seq2alignmentRange);
    }

    PointSequence createPointSequence(int point, NSequenceWithQuality seq, int from, int to, Range seq2alignmentRange) {
        if (point >= N_LEFT_DUMMIES + lengthV && point < N_LEFT_DUMMIES + lengthV + assemblingFeatureLength)
            throw new IllegalArgumentException();
        if (from == to) {
            byte left = from > 0 ? seq.getQuality().value(from - 1) : -1;
            byte right = from + 1 < seq.size() ? seq.getQuality().value(from + 1) : -1;
            byte quality;

            if (left == -1 && right == -1)
                quality = 0;
            else if (left == -1)
                quality = right;
            else if (right == -1)
                quality = left;
            else
                quality = (byte) (((int) left + right) / 2);

            if (!inSplitRegion(point)
                    || (seq.size() - seq2alignmentRange.getTo() < parameters.alignedSequenceEdgeDelta && seq.size() - to <= parameters.alignmentEdgeRegionSize)
                    || (seq2alignmentRange.getFrom() < parameters.alignedSequenceEdgeDelta && from <= parameters.alignmentEdgeRegionSize))
                quality |= 0x80;

            return new PointSequence(point, NSequenceWithQuality.EMPTY, quality);
        }
        NSequenceWithQuality r = seq.getRange(from, to);
        byte quality = r.getQuality().minValue();
        if (!inSplitRegion(point)
                || (seq.size() - seq2alignmentRange.getTo() < parameters.alignedSequenceEdgeDelta && seq.size() - to <= parameters.alignmentEdgeRegionSize)
                || (seq2alignmentRange.getFrom() < parameters.alignedSequenceEdgeDelta && from <= parameters.alignmentEdgeRegionSize))
            quality |= 0x80;
        return new PointSequence(point, r, quality);
    }

    private boolean inSplitRegion(int p) {
        return splitRegion != null && splitRegion.contains(p);
    }

    /**
     * Check that the V/J gene can be used for full sequence assembly algorithm. Basically it checks that is has required reference points defined.
     *
     * @param hit               hit to check
     * @param assemblingFeature clonal assembling feature
     * @return true if gene is compatible
     */
    public static boolean checkGeneCompatibility(VDJCHit hit, GeneFeature assemblingFeature) {
        GeneFeature vFeature = hit.getAlignedFeature();
        VDJCGene gene = hit.getGene();
        GeneFeature targetFeature = GeneFeature.intersection(assemblingFeature, vFeature);
        return gene.getPartitioning().isAvailable(targetFeature);
    }
}
