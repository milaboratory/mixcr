package com.milaboratory.mixcr.assembler.fullseq;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPort;
import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.*;
import com.milaboratory.core.mutations.MutationsBuilder;
import com.milaboratory.core.sequence.*;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.mixcr.basictypes.VDJCPartitionedSequence;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import gnu.trove.impl.Constants;
import gnu.trove.iterator.TIntIntIterator;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import gnu.trove.set.hash.TIntHashSet;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import io.repseq.core.ReferencePoint;
import io.repseq.core.VDJCGene;
import io.repseq.gen.VDJCGenes;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.repseq.core.GeneType.Joining;
import static io.repseq.core.GeneType.Variable;

/**
 */
public final class FullSeqAssembler {
    private static int ABSENT_PACKED_VARIANT_INFO = -1;
    /** initial clone */
    final Clone clone;
    /** clone assembled feature (must cover CDR3) */
    final GeneFeature assemblingFeature;
    /** top hit genes */
    final VDJCGenes genes;
    /** whether V/J genes are aligned */
    final boolean hasV, hasJ; // always trues for now
    /** number of letters to the left of reference V gene in the global coordinate grid */
    final int nLeftDummies;
    /** length of aligned part of reference V gene */
    final int lengthV;
    /** length of aligned part of reference J gene */
    final int jLength;
    /** length of assembling feature in the clone */
    final int assemblingFeatureLength;
    /** begin of the aligned J part in the reference J gene */
    final int jOffset;
    /** end of alignment of V gene in the global coordinate grid */
    final int rightAssemblingFeatureBound;
    /** splitting rehion in global coordinates */
    final Range splitRegion;
    /** parameters */
    FullSeqAssemblerParameters parameters;
    /** aligner parameters */
    final VDJCAlignerParameters alignerParameters;

    final TObjectIntHashMap<NucleotideSequence> sequenceToVariantId
            = new TObjectIntHashMap<>(Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, -1);

    final TIntObjectHashMap<NucleotideSequence> variantIdToSequence = new TIntObjectHashMap<>();

    public FullSeqAssembler(FullSeqAssemblerParameters parameters, Clone clone, VDJCAlignerParameters alignerParameters) {
        if (alignerParameters.getVAlignerParameters().getScoring() instanceof AffineGapAlignmentScoring
                || alignerParameters.getJAlignerParameters().getScoring() instanceof AffineGapAlignmentScoring)
            throw new IllegalArgumentException("Do not support Affine Gap Alignment Scoring.");

        this.parameters = parameters;
        this.clone = clone;
        this.alignerParameters = alignerParameters;
        GeneFeature[] assemblingFeatures = clone.getAssemblingFeatures();
        if (assemblingFeatures.length != 1)
            throw new IllegalArgumentException();

        if (assemblingFeatures[0].isComposite())
            throw new IllegalArgumentException();

        this.assemblingFeature = assemblingFeatures[0];
        this.genes = clone.getBestHitGenes();

        ReferencePoint
                start = assemblingFeature.getFirstPoint(),
                end = assemblingFeature.getLastPoint();

        this.hasV = start.getGeneType() == Variable;
        this.hasJ = end.getGeneType() == Joining;


        //  nLeftDummies     assemblingFeatureLength
        //  ------|--------------|--------------|------------------------>
        //        ↓              ↓              ↓
        //  0000000vvvvvvvvvvvvvvCDR3CDR3CDR3CDR3jjjjjjjjjjjjjjjjCCCCCCCCC
        //      ------- Type A
        //          -------- Type B

        this.nLeftDummies = 1024; // fixme

        int splitRegionBegin = -1, splitRegionEnd = -1;
        if (hasV) {
            VDJCHit vHit = clone.getBestHit(Variable);
            GeneFeature vFeature = vHit.getAlignedFeature();
            VDJCGene gene = vHit.getGene();
            this.lengthV =
                    gene.getPartitioning().getLength(vFeature)
                            - gene.getFeature(GeneFeature.intersection(assemblingFeature, vFeature)).size();
            if (parameters.subCloningRegion != null) {
                int p = gene.getPartitioning().getRelativePosition(vFeature, parameters.subCloningRegion.getFirstPoint());
                if (p != -1)
                    splitRegionBegin = nLeftDummies + p;

                p = gene.getPartitioning().getRelativePosition(vFeature, parameters.subCloningRegion.getLastPoint());
                if (p != -1)
                    splitRegionEnd = nLeftDummies + p;
            }
        } else
            this.lengthV = 0;

        this.assemblingFeatureLength = clone.getFeature(assemblingFeature).size();

        if (hasJ) {
            VDJCHit jHit = clone.getBestHit(Joining);
            VDJCGene gene = jHit.getGene();
            GeneFeature jFeature = jHit.getAlignedFeature();
            this.jOffset = gene.getPartitioning().getRelativePosition(jFeature, assemblingFeature.getLastPoint());
            this.jLength = gene.getPartitioning().getLength(jFeature) - jOffset;

            if (parameters.subCloningRegion != null) {
                int p = gene.getPartitioning().getRelativePosition(jFeature, parameters.subCloningRegion.getLastPoint());
                if (p != -1)
                    splitRegionEnd = nLeftDummies + lengthV + assemblingFeatureLength + p;

                p = gene.getPartitioning().getRelativePosition(jFeature, parameters.subCloningRegion.getFirstPoint());
                if (p != -1)
                    splitRegionBegin = nLeftDummies + lengthV + assemblingFeatureLength + p;
            }
        } else {
            this.jOffset = 0;
            this.jLength = 0;
        }

        if (splitRegionBegin != -1 && splitRegionEnd != -1)
            this.splitRegion = new Range(splitRegionBegin, splitRegionEnd);
        else
            this.splitRegion = null;

        this.rightAssemblingFeatureBound = nLeftDummies + lengthV + assemblingFeatureLength;
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
                List<Variant> variants = callVariantsForPoint(variantInfos, branch.reads);
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

        clusterizeBranches(data.points, branches);

        return branches.stream()
                .map(branch -> buildClone(branch.count, assembleBranchSequences(data.points, branch)))
                .toArray(Clone[]::new);
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

            for (int j = i + 1; j < branches.size(); ++j) {
                VariantBranch cluster = branches.get(j);
                cluster.count += branch.count * weights[j - i - 1] / sumWeight;
            }

            branches.remove(i);
        }

        branches.sort(Comparator.comparingDouble(c -> -c.count));
    }

    private static class VariantBranch {
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
    }

    BranchSequences assembleBranchSequences(int[] points, VariantBranch branch) {
        long[] positionedStates = new long[points.length];
        for (int i = 0; i < points.length; i++)
            positionedStates[i] = ((long) points[i]) << 32 | branch.pointStates[i];
        Arrays.sort(positionedStates);

        List<NSequenceWithQuality> sequences = new ArrayList<>();
        List<Range> ranges = new ArrayList<>();
        NSequenceWithQualityBuilder sequenceBuilder = new NSequenceWithQualityBuilder();
        int blockStartPosition = -1;
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

            sequenceBuilder.append(
                    new NSequenceWithQuality(
                            variantIdToSequence.get(((int) (positionedStates[i] >>> 8)) & 0xFFFFFF),
                            (byte) positionedStates[i]));

            if (currentPosition != nextPosition - 1) {
                sequences.add(sequenceBuilder.createAndDestroy());
                ranges.add(new Range(blockStartPosition, currentPosition + 1));
                sequenceBuilder = new NSequenceWithQualityBuilder();
                blockStartPosition = nextPosition;
            }
        }

        assert blockStartPosition != -1;

        int assemblingFeatureTargetId = -1;
        int assemblingFeatureOffset = -1;
        for (int i = 0; i < ranges.size(); i++) {
            if (ranges.get(i).getTo() == nLeftDummies + lengthV) {
                assemblingFeatureOffset = sequences.get(i).size();
                if (i < ranges.size() - 1
                        && ranges.get(i + 1).getFrom() == nLeftDummies + lengthV + assemblingFeatureLength) {
                    // seq[i]-AssemblingFeature-seq[i+1]
                    ranges.set(i, new Range(ranges.get(i).getFrom(), ranges.get(i + 1).getTo()));
                    ranges.remove(i + 1);
                    sequences.set(i, sequences.get(i).concatenate(clone.getTarget(0)).concatenate(sequences.get(i + 1)));
                    sequences.remove(i + 1);
                } else {
                    // seq[i]-AssemblingFeature
                    ranges.set(i, new Range(ranges.get(i).getFrom(), nLeftDummies + lengthV + assemblingFeatureLength));
                    sequences.set(i, sequences.get(i).concatenate(clone.getTarget(0)));
                }
                assemblingFeatureTargetId = i;
                break;
            }

            if (ranges.get(i).getFrom() == nLeftDummies + lengthV + assemblingFeatureLength) {
                // AssemblingFeature-seq[i]
                ranges.set(i, new Range(nLeftDummies + lengthV, ranges.get(i).getTo()));
                sequences.set(i, clone.getTarget(0).concatenate(sequences.get(i)));
                assemblingFeatureOffset = 0;
                assemblingFeatureTargetId = i;
                break;
            }

            if (ranges.get(i).getFrom() > nLeftDummies + lengthV + assemblingFeatureLength) {
                // seq[i-1]    AssemblingFeature    seq[i]
                ranges.add(i, new Range(nLeftDummies + lengthV, nLeftDummies + lengthV + assemblingFeatureLength));
                sequences.add(i, clone.getTarget(0));
                assemblingFeatureOffset = 0;
                assemblingFeatureTargetId = i;
                break;
            }
        }

        if (assemblingFeatureTargetId == -1) {
            // seq[last]   AssemblingFeature
            ranges.add(new Range(nLeftDummies + lengthV, nLeftDummies + lengthV + assemblingFeatureLength));
            sequences.add(clone.getTarget(0));
            assemblingFeatureOffset = 0;
            assemblingFeatureTargetId = ranges.size() - 1;
        }

        return new BranchSequences(
                assemblingFeatureTargetId,
                assemblingFeatureOffset,
                ranges.toArray(new Range[ranges.size()]),
                sequences.toArray(new NSequenceWithQuality[sequences.size()]));
    }

    private static int extractPosition(long positionedState) {
        return (int) (positionedState >>> 32);
    }

    private static boolean isAbsent(long positionedState) {
        return (int) (positionedState & 0xFFFFFFFF) == ABSENT_PACKED_VARIANT_INFO;
    }

    private final class BranchSequences {
        final int assemblingFeatureTargetId;
        final int assemblingFeatureOffset;
        final Range[] ranges;
        final NSequenceWithQuality[] sequences;

        BranchSequences(int assemblingFeatureTargetId, int assemblingFeatureOffset, Range[] ranges, NSequenceWithQuality[] sequences) {
            this.assemblingFeatureTargetId = assemblingFeatureTargetId;
            this.assemblingFeatureOffset = assemblingFeatureOffset;
            this.ranges = ranges;
            this.sequences = sequences;
        }
    }

    /* ================================= Re-align and build final clone ====================================== */

    private Clone buildClone(double count, BranchSequences targets) {
        Alignment<NucleotideSequence>[] vTopHitAlignments = new Alignment[targets.ranges.length],
                jTopHitAlignments = new Alignment[targets.ranges.length];
        VDJCHit hit = clone.getBestHit(Variable);
        if (hit == null)
            throw new UnsupportedOperationException("No V hit.");
        NucleotideSequence vTopReferenceSequence = hit.getGene().getFeature(hit.getAlignedFeature());
        hit = clone.getBestHit(Joining);
        if (hit == null)
            throw new UnsupportedOperationException("No J hit.");
        NucleotideSequence jTopReferenceSequence = hit.getGene().getFeature(hit.getAlignedFeature());

        // Excessive optimization
        CachedIntArray cachedIntArray = new CachedIntArray();

        for (int i = 0; i < targets.ranges.length; i++) {
            Range range = targets.ranges[i];
            NucleotideSequence sequence = targets.sequences[i].getSequence();

            // Asserts
            if (range.getFrom() < nLeftDummies + lengthV
                    && range.getTo() >= nLeftDummies + lengthV
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

            if (range.getTo() < nLeftDummies + lengthV) {
                boolean floatingLeftBound =
                        i == 0 && alignerParameters.getVAlignerParameters().getParameters().isFloatingLeftBound();

                // Can be reduced to a single statement
                if (range.getFrom() < nLeftDummies)
                    // This target contain extra non-V nucleotides on the left
                    vTopHitAlignments[i] = alignLinearSeq1FromRight(
                            ((LinearGapAlignmentScoring<NucleotideSequence>) alignerParameters.getVAlignerParameters().getScoring()),
                            vTopReferenceSequence, sequence.getSequence(),
                            0, range.getTo() - nLeftDummies,
                            0, sequence.size(),
                            !floatingLeftBound,
                            cachedIntArray);
                else if (floatingLeftBound)
                    vTopHitAlignments[i] = alignLinearSeq1FromRight(
                            ((LinearGapAlignmentScoring<NucleotideSequence>) alignerParameters.getVAlignerParameters().getScoring()),
                            vTopReferenceSequence, sequence.getSequence(),
                            range.getFrom() - nLeftDummies, range.length(),
                            0, sequence.size(),
                            false,
                            cachedIntArray);
                else
                    vTopHitAlignments[i] = Aligner.alignGlobal(
                            alignerParameters.getVAlignerParameters().getScoring(),
                            vTopReferenceSequence,
                            sequence,
                            range.getFrom() - nLeftDummies, range.length(),
                            0, sequence.size());
            } else if (i == targets.assemblingFeatureTargetId) {
                /*
                 *  V gene
                 */

                boolean vFloatingLeftBound =
                        i == 0 && alignerParameters.getVAlignerParameters().getParameters().isFloatingLeftBound();

                // Can be reduced to a single statement
                if (range.getFrom() < nLeftDummies)
                    // This target contain extra non-V nucleotides on the left
                    vTopHitAlignments[i] = alignLinearSeq1FromRight(
                            ((LinearGapAlignmentScoring<NucleotideSequence>) alignerParameters.getVAlignerParameters().getScoring()),
                            vTopReferenceSequence, sequence.getSequence(),
                            0, lengthV,
                            0, targets.assemblingFeatureOffset,
                            !vFloatingLeftBound,
                            cachedIntArray);
                else if (vFloatingLeftBound)
                    vTopHitAlignments[i] = alignLinearSeq1FromRight(
                            ((LinearGapAlignmentScoring<NucleotideSequence>) alignerParameters.getVAlignerParameters().getScoring()),
                            vTopReferenceSequence, sequence.getSequence(),
                            range.getFrom() - nLeftDummies, lengthV - (range.getFrom() - nLeftDummies),
                            0, targets.assemblingFeatureOffset,
                            false,
                            cachedIntArray);
                else
                    vTopHitAlignments[i] = Aligner.alignGlobal(
                            alignerParameters.getVAlignerParameters().getScoring(),
                            vTopReferenceSequence,
                            sequence,
                            range.getFrom() - nLeftDummies, lengthV - (range.getFrom() - nLeftDummies),
                            0, targets.assemblingFeatureOffset);

                /*
                 *  J gene
                 */

                boolean jFloatingRightBound =
                        i == targets.ranges.length - 1 && alignerParameters.getJAlignerParameters().getParameters().isFloatingRightBound();

                if (range.getTo() >= rightAssemblingFeatureBound + jLength)
                    // This target contain extra non-J nucleotides on the right
                    jTopHitAlignments[i] = alignLinearSeq1FromLeft(
                            ((LinearGapAlignmentScoring<NucleotideSequence>) alignerParameters.getJAlignerParameters().getScoring()),
                            jTopReferenceSequence, sequence.getSequence(),
                            jOffset, jLength,
                            targets.assemblingFeatureOffset + assemblingFeatureLength, sequence.size() - (targets.assemblingFeatureOffset + assemblingFeatureLength),
                            !jFloatingRightBound,
                            cachedIntArray);
                else if (jFloatingRightBound)
                    jTopHitAlignments[i] = alignLinearSeq1FromLeft(
                            ((LinearGapAlignmentScoring<NucleotideSequence>) alignerParameters.getJAlignerParameters().getScoring()),
                            jTopReferenceSequence, sequence.getSequence(),
                            jOffset, range.getTo() - rightAssemblingFeatureBound,
                            targets.assemblingFeatureOffset + assemblingFeatureLength, sequence.size() - (targets.assemblingFeatureOffset + assemblingFeatureLength),
                            false,
                            cachedIntArray);
                else
                    jTopHitAlignments[i] = Aligner.alignGlobal(
                            alignerParameters.getJAlignerParameters().getScoring(),
                            jTopReferenceSequence,
                            sequence,
                            jOffset, range.getTo() - rightAssemblingFeatureBound,
                            targets.assemblingFeatureOffset + assemblingFeatureLength, sequence.size() - (targets.assemblingFeatureOffset + assemblingFeatureLength));
            } else if (range.getFrom() > rightAssemblingFeatureBound) {
                boolean floatingRightBound =
                        i == targets.ranges.length - 1 && alignerParameters.getJAlignerParameters().getParameters().isFloatingRightBound();

                if (range.getTo() >= rightAssemblingFeatureBound + jLength)
                    // This target contain extra non-J nucleotides on the right
                    jTopHitAlignments[i] = alignLinearSeq1FromLeft(
                            ((LinearGapAlignmentScoring<NucleotideSequence>) alignerParameters.getJAlignerParameters().getScoring()),
                            jTopReferenceSequence, sequence.getSequence(),
                            jOffset + (range.getFrom() - rightAssemblingFeatureBound), jLength - (range.getFrom() - rightAssemblingFeatureBound),
                            0, sequence.size(),
                            !floatingRightBound,
                            cachedIntArray);
                else if (floatingRightBound)
                    jTopHitAlignments[i] = alignLinearSeq1FromLeft(
                            ((LinearGapAlignmentScoring<NucleotideSequence>) alignerParameters.getJAlignerParameters().getScoring()),
                            jTopReferenceSequence, sequence.getSequence(),
                            jOffset + (range.getFrom() - rightAssemblingFeatureBound), range.length(),
                            0, sequence.size(),
                            false,
                            cachedIntArray);
                else
                    jTopHitAlignments[i] = Aligner.alignGlobal(
                            alignerParameters.getJAlignerParameters().getScoring(),
                            jTopReferenceSequence,
                            sequence,
                            jOffset + (range.getFrom() - rightAssemblingFeatureBound), range.length(),
                            0, sequence.size());
            } else
                throw new RuntimeException();
        }

        vTopHitAlignments[targets.assemblingFeatureTargetId] =
                mergeTwoAlignments(
                        vTopHitAlignments[targets.assemblingFeatureTargetId],
                        clone.getBestHit(Variable).getAlignment(0).move(targets.assemblingFeatureOffset));

        jTopHitAlignments[targets.assemblingFeatureTargetId] =
                mergeTwoAlignments(
                        clone.getBestHit(Joining).getAlignment(0).move(targets.assemblingFeatureOffset),
                        jTopHitAlignments[targets.assemblingFeatureTargetId]);

        EnumMap<GeneType, VDJCHit[]> hits = new EnumMap<>(GeneType.class);
        for (GeneType gt : GeneType.VDJC_REFERENCE)
            hits.put(gt, Arrays.stream(clone.getHits(gt))
                    .map(h -> moveHitTarget(h, targets.assemblingFeatureTargetId,
                            targets.assemblingFeatureOffset, targets.ranges.length))
                    .toArray(VDJCHit[]::new));

        VDJCHit[] tmp = hits.get(Variable);
        tmp[0] = substituteAlignments(tmp[0], vTopHitAlignments);
        tmp = hits.get(Joining);
        tmp[0] = substituteAlignments(tmp[0], jTopHitAlignments);

        return new Clone(targets.sequences, hits, clone.getAssemblingFeatures(), count, 0);
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
                length2 = Math.min(length2, length1 + width);
                seq2added = Math.min(length2, width + (length2 - length1));
            }
            result = BandedLinearAligner.alignLeftAdded0(scoring, seq1, seq2, offset1, length1, 0, offset2, length2, seq2added, width, mutations, cache);
        } else
            result = BandedLinearAligner.alignSemiLocalRight0(scoring, seq1, seq2, offset1, length1, offset2, length2, width, Integer.MIN_VALUE, mutations, cache);
        return new Alignment<>(seq1, mutations.createAndDestroy(),
                new Range(result.sequence1Stop, offset1 + length1), new Range(result.sequence2Stop, offset2 + length2),
                result.score);
    }


    static VDJCHit substituteAlignments(VDJCHit hit, Alignment<NucleotideSequence>[] alignments) {
        return new VDJCHit(hit.getGene(), alignments, hit.getAlignedFeature(), hit.getScore());
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

    private List<Variant> callVariantsForPoint(int[] pointVariantInfos, BitSet targetReads) {
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
                        && ((variantSumQuality >= parameters.minimalSumQuality
                        && variantSumQuality >= parameters.minimalQualityShare * totalSumQuality)
                        || variantSumQuality >= parameters.decisiveSumQualityThreshold)) {
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
                        // totalSumQuality is definitely less than Long because variantSumQuality < decisiveSumQualityThreshold
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
            // no significant variants
            assert bestVariant != -1;
            BitSet reads = new BitSet();
            for (long target : targets)
                reads.set((int) target);
            reads.or(unassignedVariants);
            // nSignificant = 1 (will not be practically used, only one variant, don't care)
            return Collections.singletonList(
                    new Variant(bestVariant << 8 | (int) Math.min((long) SequenceQuality.MAX_QUALITY_VALUE, bestVariantSumQuality),
                            reads, 1));
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

    public RawVariantsData calculateRawData(Supplier<OutputPort<VDJCAlignments>> alignments) {
        if (!sequenceToVariantId.isEmpty())
            throw new IllegalStateException();

        for (byte letter = 0; letter < NucleotideSequence.ALPHABET.basicSize(); letter++) {
            NucleotideSequence seq = new NucleotideSequence(new byte[]{letter});
            sequenceToVariantId.put(seq, letter);
            variantIdToSequence.put(letter, seq);
        }
        sequenceToVariantId.put(NucleotideSequence.EMPTY, NucleotideSequence.ALPHABET.basicSize());
        variantIdToSequence.put(NucleotideSequence.ALPHABET.basicSize(), NucleotideSequence.EMPTY);

        TIntIntHashMap coverage = new TIntIntHashMap();
        TIntObjectHashMap<TIntObjectHashMap<VariantAggregator>> variants = new TIntObjectHashMap<>();

        int nAlignments = 0;
        for (VDJCAlignments al : CUtils.it(alignments.get())) {
            ++nAlignments;
            for (PointSequence point : toPointSequences(al)) {
                int seqIndex;
                if (point.sequence.size() == 0)
                    seqIndex = NucleotideSequence.ALPHABET.basicSize();
                else if (point.sequence.size() == 1)
                    seqIndex = point.sequence.getSequence().codeAt(0);
                else {
                    seqIndex = sequenceToVariantId.putIfAbsent(point.sequence.getSequence(), sequenceToVariantId.size());
                    if (seqIndex == -1) {
                        seqIndex = sequenceToVariantId.size() - 1;
                        variantIdToSequence.put(seqIndex, point.sequence.getSequence());
                    }
                }

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

        int[][] packedData = new int[pointsArray.length][nAlignments];
        for (int[] aPackedData : packedData)
            Arrays.fill(aPackedData, -1);

        i = 0;
        for (VDJCAlignments al : CUtils.it(alignments.get())) {
            for (PointSequence point : toPointSequences(al)) {
                int pointIndex = revIndex.get(point.point);
                packedData[pointIndex][i] =
                        (sequenceToVariantId.get(point.sequence.getSequence()) << 8)
                                | (0xFF & point.quality);
            }
            i++;
        }

        return new RawVariantsData(nAlignments, pointsArray, coverageArray) {
            @Override
            OutputPort<int[]> createPort() {
                return CUtils.asOutputPort(Arrays.asList(packedData));
            }
        };
    }

    public static abstract class RawVariantsData {
        final int nReads;
        final int[] points;
        final int[] coverage;

        RawVariantsData(int nReads, int[] points, int[] coverage) {
            this.nReads = nReads;
            this.points = points;
            this.coverage = coverage;
        }

        // array[readId] = (variantId << 8) | minQuality
        abstract OutputPort<int[]> createPort();

        void destroy() {
        }
    }

    static final class VariantAggregator {
        long sumQuality = 0;
        int count = 0;
    }

    PointSequence[] toPointSequences(VDJCAlignments alignments) {
        return IntStream.range(0, alignments.numberOfTargets())
                .mapToObj(i -> toPointSequences(alignments, i))
                .flatMap(Collection::stream)
                .collect(Collectors.groupingBy(s -> s.point))
                .values().stream()
                .map(l -> l.stream().max(Comparator.comparingInt(a -> a.quality)).get())
                .toArray(PointSequence[]::new);
    }

    List<PointSequence> toPointSequences(VDJCAlignments alignments, int iTarget) {

        //
        //  nLeftDummies     assemblingFeatureLength
        //  ------|--------------|--------------|------------------------>
        //        ↓              ↓              ↓
        //  0000000vvvvvvvvvvvvvvCDR3CDR3CDR3CDR3jjjjjjjjjjjjjjjjCCCCCCCCC

        VDJCPartitionedSequence target = alignments.getPartitionedTarget(iTarget);
        NSequenceWithQuality targetSeq = alignments.getTarget(iTarget);

        VDJCHit vHit = alignments.getBestHit(Variable);
        Alignment<NucleotideSequence> vAlignment =
                (vHit == null
                        || vHit.getAlignment(iTarget) == null
                        || !Objects.equals(genes.v, vHit.getGene())
                        || vHit.getAlignment(iTarget).getSequence1Range().getFrom() > lengthV)
                        ? null
                        : vHit.getAlignment(iTarget);

        VDJCHit jHit = alignments.getBestHit(Joining);
        Alignment<NucleotideSequence> jAlignment =
                (jHit == null
                        || jHit.getAlignment(iTarget) == null
                        || !Objects.equals(genes.j, jHit.getGene())
                        || jHit.getAlignment(iTarget).getSequence1Range().getTo() < jOffset)
                        ? null
                        : jHit.getAlignment(iTarget);

        List<PointSequence> points = new ArrayList<>();
        if (target.getPartitioning().isAvailable(assemblingFeature.getFirstPoint())) {
            int leftStop = target.getPartitioning().getPosition(assemblingFeature.getFirstPoint());
            if (hasV) {
                if (vAlignment != null)
                    toPointSequencesByAlignments(points,
                            vAlignment,
                            targetSeq,
                            new Range(0, leftStop),
                            nLeftDummies);
            } else
                toPointSequencesNoAlignments(points, targetSeq, new Range(0, leftStop), nLeftDummies - leftStop);
        } else if (hasV && vAlignment != null)
            toPointSequencesByAlignments(points,
                    vAlignment,
                    targetSeq,
                    new Range(0, vAlignment.getSequence2Range().getTo()),
                    nLeftDummies);

        if (target.getPartitioning().isAvailable(assemblingFeature.getLastPoint())) {
            int rightStart = target.getPartitioning().getPosition(assemblingFeature.getLastPoint());
            if (hasJ) {
                if (jAlignment != null)
                    toPointSequencesByAlignments(points,
                            jAlignment,
                            targetSeq,
                            new Range(rightStart, targetSeq.size()),
                            nLeftDummies + lengthV + assemblingFeatureLength - jOffset);
            } else
                toPointSequencesNoAlignments(points, targetSeq, new Range(rightStart, targetSeq.size()), nLeftDummies + lengthV + assemblingFeatureLength - rightStart);
        } else if (hasJ && jAlignment != null)
            toPointSequencesByAlignments(points,
                    jAlignment,
                    targetSeq,
                    new Range(jAlignment.getSequence2Range().getFrom(), targetSeq.size()),
                    nLeftDummies + lengthV + assemblingFeatureLength - jOffset);

        return points;
    }

    void toPointSequencesByAlignments(List<PointSequence> points,
                                      Alignment<NucleotideSequence> alignment,
                                      NSequenceWithQuality seq2,
                                      Range seq2Range,
                                      int offset) {

//        if (seq2Range.length() == 0)
//            return;

        alignment = AlignmentUtils.shiftIndelsAtHomopolymers(alignment);

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
        if (point >= nLeftDummies + lengthV && point < nLeftDummies + lengthV + assemblingFeatureLength)
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
        return splitRegion == null || splitRegion.contains(p);
    }
}
