package com.milaboratory.mixcr.util;

import cc.redberry.pipe.Processor;
import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.alignment.AlignmentScoring;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.mutations.MutationsBuilder;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NSequenceWithQualityBuilder;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.SequenceQuality;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.mixcr.cli.ReportHelper;
import com.milaboratory.mixcr.cli.ReportWriter;
import io.repseq.core.*;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Stanislav Poslavsky
 */
public final class CDR3Extender implements Processor<VDJCAlignments, VDJCAlignments>, ReportWriter {
    final Chains chains;
    final byte extensionQuality;
    final AlignmentScoring<NucleotideSequence> vScoring, jScoring;
    final ReferencePoint vLeftExtensionRefPoint, jRightExtensionRefPoint;

    //metrics
    final AtomicLong total = new AtomicLong(0),
            vExtended = new AtomicLong(0),
            jExtended = new AtomicLong(0),
            vExtendedMerged = new AtomicLong(0),
            jExtendedMerged = new AtomicLong(0),
            vjExtended = new AtomicLong(0),
            vExtensionLength = new AtomicLong(0),
            jExtensionLength = new AtomicLong(0);

    public CDR3Extender(Chains chains, byte extensionQuality, AlignmentScoring<NucleotideSequence> vScoring, AlignmentScoring<NucleotideSequence> jScoring,
                        ReferencePoint vLeftExtensionRefPoint, ReferencePoint jRightExtensionRefPoint) {
        this.chains = chains;
        this.extensionQuality = extensionQuality;
        this.vScoring = vScoring;
        this.jScoring = jScoring;
        this.vLeftExtensionRefPoint = vLeftExtensionRefPoint;
        this.jRightExtensionRefPoint = jRightExtensionRefPoint;
    }

    @Override
    public VDJCAlignments process(VDJCAlignments input) {
        VDJCAlignments originalInput = input;

        total.incrementAndGet();

        //check input chains
        if (!chains.intersects(input.getTopChain(GeneType.Variable))
                && !chains.intersects(input.getTopChain(GeneType.Joining)))
            return input;

        GeneFeature extensionFeature = new GeneFeature(vLeftExtensionRefPoint, jRightExtensionRefPoint);

        VDJCHit topV = input.getBestHit(GeneType.Variable);
        VDJCHit topJ = input.getBestHit(GeneType.Joining);

        //reject if any of hits absent
        if (topV == null || topJ == null)
            return input;

        boolean extended = false, merged = false;

        OUTER:
        while (true) {
            //check whether extensionFeature is already covered
            if (input.getFeature(extensionFeature) != null)
                break OUTER;

            int cdr3target = -1;
            for (int i = 0; i < input.numberOfTargets(); i++) {
                if (topV.getAlignment(i) == null || topJ.getAlignment(i) == null)
                    continue;

                if (cdr3target != -1)
                    break OUTER;

                cdr3target = i;
            }

            if (cdr3target == -1)
                break OUTER;

            ExtendResult vExtension = null;
            if (!topV.getPartitioningForTarget(cdr3target).isAvailable(vLeftExtensionRefPoint)) {
                final GeneFeature vFeature = topV.getAlignedFeature();
                for (VDJCHit vHit : input.getHits(GeneType.Variable)) {
                    if (vHit.getAlignment(cdr3target) == null)
                        break OUTER;

                    if (vHit.getAlignment(cdr3target).getSequence2Range().getFrom() != 0)
                        break OUTER;

                    final VDJCGene vGene = vHit.getGene();

                    //check if input contains some V CDR3 part
                    final int vCdr3BeginPositionInRef = vGene.getPartitioning().getRelativePosition(vFeature, vLeftExtensionRefPoint);
                    if (vCdr3BeginPositionInRef == -1
                            || vHit.getAlignment(cdr3target).getSequence1Range().getTo()
                            < vCdr3BeginPositionInRef)
                        break OUTER;

                    //extend V
                    int vLeftTargetid = -1;
                    int vLeftEndCoord = -1;

                    //searching for adjacent alignment (i.e. left V alignment)
                    for (int i = 0; i < input.numberOfTargets(); i++) {
                        if (i == cdr3target)
                            continue;

                        if (vHit.getAlignment(i) != null) {
                            if (vHit.getAlignment(i).getSequence1Range().getTo() > vLeftEndCoord) {
                                vLeftTargetid = i;
                                vLeftEndCoord = vHit.getAlignment(i).getSequence1Range().getTo();
                            }
                        }
                    }

                    if (vLeftTargetid != -1)
                        //check that vLeft aligned to right
                        if (vHit.getAlignment(vLeftTargetid).getSequence2Range().getTo() != input.getTarget(vLeftTargetid).size())
                            break OUTER;

                    if (vCdr3BeginPositionInRef > vLeftEndCoord)
                        vLeftTargetid = -1;

                    if (vLeftTargetid != -1 && vLeftTargetid != cdr3target - 1)
                        break OUTER;

                    ExtendResult r = new VExtender(cdr3target,
                            vLeftTargetid == -1 ? -1 : vLeftEndCoord - vCdr3BeginPositionInRef,
                            vHit.getAlignment(cdr3target).getSequence1().getRange(
                                    vLeftTargetid == -1 ? vCdr3BeginPositionInRef : vLeftEndCoord,
                                    vHit.getAlignment(cdr3target).getSequence1Range().getFrom()));

                    if (vExtension == null)
                        vExtension = r;
                    else if (!vExtension.equals(r))
                        break OUTER;
                }
            }

            if (vExtension == null)
                break OUTER;

            // extend
            VDJCAlignments transformed = transform(input, vExtension);

            if (transformed == null)
                // Something went wrong
                return originalInput;

            input = transformed;

            extended = true;
            if (vExtension.relativeInsertionStart != -1)
                merged = true;
            vExtensionLength.addAndGet(vExtension.extension.size());

            // Update top hits
            topV = input.getBestHit(GeneType.Variable);
            topJ = input.getBestHit(GeneType.Joining);
        }

        if (extended) {
            vExtended.incrementAndGet();
            if (merged)
                vExtendedMerged.incrementAndGet();
        }

        

        return input;
    }

    @Override
    public void writeReport(ReportHelper helper) {
        long total = this.total.get();
        helper.writePercentAndAbsoluteField("Extended alignments count", vExtended.get() + jExtended.get() - vjExtended.get(), total);
        helper.writePercentAndAbsoluteField("V extensions total", vExtended, total);
        helper.writePercentAndAbsoluteField("V extensions with merged targets", vExtendedMerged, total);
        helper.writePercentAndAbsoluteField("J extensions total", jExtended, total);
        helper.writePercentAndAbsoluteField("J extensions with merged targets", jExtendedMerged, total);
        helper.writePercentAndAbsoluteField("V+J extensions", vjExtended, total);
        helper.writeField("Mean V extension length", 1.0 * vExtensionLength.get() / vExtended.get());
        helper.writeField("Mean J extension length", 1.0 * jExtensionLength.get() / jExtended.get());
    }

    interface VDJCAlignmentTransformer {
        /**
         * @return result or null is something went wrong
         */
        Alignment<NucleotideSequence>[] transform(VDJCGene gene, Alignment<NucleotideSequence>[] alignments,
                                                  NSequenceWithQuality[] originalTargets);

        NSequenceWithQuality[] transform(NSequenceWithQuality[] targets);
    }

    /**
     * @return result or null is something went wrong
     */

    static VDJCAlignments transform(VDJCAlignments input,
                                    VDJCAlignmentTransformer transformer) {

        NSequenceWithQuality[] originalTargets = input.getTargets();
        EnumMap<GeneType, VDJCHit[]> newHitsMap = new EnumMap<>(GeneType.class);
        for (GeneType gt : GeneType.VDJC_REFERENCE) {
            final VDJCHit[] inputHits = input.getHits(gt);
            final VDJCHit[] newHits = new VDJCHit[inputHits.length];
            for (int i = 0; i < inputHits.length; i++) {
                final VDJCGene gene = inputHits[i].getGene();
                Alignment<NucleotideSequence>[] transformed = transformer.transform(gene, inputHits[i].getAlignments(),
                        originalTargets);

                if (transformed == null)
                    return null;

                newHits[i] = new VDJCHit(gene, transformed, inputHits[i].getAlignedFeature());
            }
            newHitsMap.put(gt, newHits);
        }


        final VDJCAlignments result = new VDJCAlignments(input.getReadId(), newHitsMap, transformer.transform(originalTargets));
        result.setAlignmentsIndex(input.getAlignmentsIndex());
        result.setOriginalDescriptions(input.getOriginalDescriptions());
        result.setOriginalSequences(input.getOriginalSequences());

//        String[] tdescrs = result.getTargetDescriptions();
//        if (mergedTargetid != -1) {
//            tdescrs[mergedTargetid] = addon + ";  " + tdescrs[mergedTargetid] + ";  " + tdescrs[mergedTargetid + 1];
//            if (mergedTargetid + 2 < tdescrs.length)
//                System.arraycopy(tdescrs, mergedTargetid + 2, tdescrs, mergedTargetid + 1, tdescrs.length - mergedTargetid - 2);
//            tdescrs = Arrays.copyOf(tdescrs, tdescrs.length - 1);
//        }
//        result.setTargetDescriptions(tdescrs);
        return result;
    }

    final class VExtender extends ExtendResult {
        public VExtender(int cdr3targetId, int relativeInsertionStart, NucleotideSequence extension) {
            super(cdr3targetId, relativeInsertionStart, extension);
        }

        @Override
        public Alignment<NucleotideSequence>[] transform(VDJCGene gene, Alignment<NucleotideSequence>[] alignments,
                                                         NSequenceWithQuality[] originalTargets) {
            if (relativeInsertionStart == -1) {
                //no adjacent target

                Alignment<NucleotideSequence> al = alignments[cdr3targetId];
                if (gene.getGeneType() == GeneType.Variable) {
                    alignments[cdr3targetId] = new Alignment<>(
                            al.getSequence1(),
                            al.getAbsoluteMutations(),
                            al.getSequence1Range().expand(extension.size(), 0),
                            al.getSequence2Range().expand(0, extension.size()),
                            vScoring);
                } else if (al != null) {
                    alignments[cdr3targetId] = new Alignment<>(
                            al.getSequence1(),
                            al.getAbsoluteMutations(),
                            al.getSequence1Range(),
                            al.getSequence2Range().move(extension.size()),
                            al.getScore());
                }

                return alignments;
            } else {
                // adjacent target id = cdr3targetId - 1

                Alignment<NucleotideSequence> al1 = alignments[cdr3targetId - 1];
                Alignment<NucleotideSequence> al2 = alignments[cdr3targetId];
                int rightOffset = originalTargets[cdr3targetId - 1].size() + extension.size();

                alignments = shrinkArray(alignments);

                if (gene.getGeneType() == GeneType.Variable) {
                    Mutations<NucleotideSequence> m1 = al1.getAbsoluteMutations(),
                            m2 = al2.getAbsoluteMutations();

                    Mutations<NucleotideSequence> mutations = new MutationsBuilder<>(NucleotideSequence.ALPHABET)
                            .ensureCapacity(m1.size() + m2.size())
                            .append(m1)
                            .append(m2)
                            .createAndDestroy();

                    alignments[cdr3targetId - 1] = new Alignment<>(
                            al1.getSequence1(), mutations,
                            new Range(al1.getSequence1Range().getFrom(),
                                    al2.getSequence1Range().getTo()),
                            new Range(al1.getSequence2Range().getFrom(),
                                    al2.getSequence2Range().getTo() + rightOffset),
                            vScoring);
                } else if (al1 != null && al2 != null)
                    return null;
                else
                    alignments[cdr3targetId - 1] = al1 == null ? new Alignment<>(
                            al2.getSequence1(),
                            al2.getAbsoluteMutations(),
                            al2.getSequence1Range(),
                            al2.getSequence2Range().move(rightOffset),
                            al2.getScore()) : al1;

                return alignments;
            }
        }

        <T> T[] shrinkArray(T[] array) {
            if (array.length - 1 - cdr3targetId != 0)
                System.arraycopy(array, cdr3targetId + 1, array, cdr3targetId,
                        array.length - 1 - cdr3targetId);
            return Arrays.copyOf(array, array.length - 1);
        }

        @Override
        public NSequenceWithQuality[] transform(NSequenceWithQuality[] targets) {
            NSequenceWithQuality ext = new NSequenceWithQuality(extension,
                    SequenceQuality.getUniformQuality(extensionQuality, extension.size()));

            if (relativeInsertionStart == -1) {
                targets[cdr3targetId] = ext
                        .concatenate(targets[cdr3targetId]);
                return targets;
            } else {
                NSequenceWithQuality t1 = targets[cdr3targetId - 1];
                NSequenceWithQuality t2 = targets[cdr3targetId];
                targets = shrinkArray(targets);

                targets[cdr3targetId - 1] = new NSequenceWithQualityBuilder()
                        .ensureCapacity(t1.size() + extension.size() + t2.size())
                        .append(t1)
                        .append(ext)
                        .append(t2)
                        .createAndDestroy();

                return targets;
            }
        }
    }

    abstract class ExtendResult implements VDJCAlignmentTransformer {
        final int cdr3targetId;
        final int relativeInsertionStart;
        final NucleotideSequence extension;

        public ExtendResult(int cdr3targetId, int relativeInsertionStart, NucleotideSequence extension) {
            this.cdr3targetId = cdr3targetId;
            this.relativeInsertionStart = relativeInsertionStart;
            this.extension = extension;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ExtendResult that = (ExtendResult) o;

            if (cdr3targetId != that.cdr3targetId) return false;
            if (relativeInsertionStart != that.relativeInsertionStart) return false;
            return extension.equals(that.extension);

        }

        @Override
        public int hashCode() {
            int result = cdr3targetId;
            result = 31 * result + relativeInsertionStart;
            result = 31 * result + extension.hashCode();
            return result;
        }
    }
}