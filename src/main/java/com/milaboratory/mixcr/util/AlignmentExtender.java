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
public final class AlignmentExtender implements Processor<VDJCAlignments, VDJCAlignments>, ReportWriter {
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

    public AlignmentExtender(Chains chains, byte extensionQuality, AlignmentScoring<NucleotideSequence> vScoring, AlignmentScoring<NucleotideSequence> jScoring,
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

        boolean vExtended = false, vMerged = false;

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

            Extender vExtension = null;
            if (!topV.getPartitioningForTarget(cdr3target).isAvailable(vLeftExtensionRefPoint)) {
                final GeneFeature vFeature = topV.getAlignedFeature();
                for (VDJCHit vHit : input.getHits(GeneType.Variable)) {
                    if (vHit.getAlignment(cdr3target) == null)
                        break OUTER;

                    if (vHit.getAlignment(cdr3target).getSequence2Range().getFrom() != 0)
                        break OUTER;

                    final VDJCGene vGene = vHit.getGene();

                    //check if input contains some V CDR3 part
                    final int vAnchorPositionInRef = vGene.getPartitioning().getRelativePosition(vFeature, vLeftExtensionRefPoint);
                    if (vAnchorPositionInRef == -1
                            || vHit.getAlignment(cdr3target).getSequence1Range().getTo()
                            < vAnchorPositionInRef)
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

                    if (vAnchorPositionInRef > vLeftEndCoord)
                        vLeftTargetid = -1;

                    if (vLeftTargetid != -1 && vLeftTargetid != cdr3target - 1)
                        break OUTER;

                    Extender r = new Extender(cdr3target,
                            vLeftTargetid == -1 ? -1 : vLeftEndCoord - vAnchorPositionInRef,
                            vHit.getAlignment(cdr3target).getSequence1().getRange(
                                    vLeftTargetid == -1 ? vAnchorPositionInRef : vLeftEndCoord,
                                    vHit.getAlignment(cdr3target).getSequence1Range().getFrom()),
                            true);

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

            vExtended = true;
            if (vExtension.relativeInsertionStart != -1)
                vMerged = true;
            vExtensionLength.addAndGet(vExtension.extension.size());

            // Update top hits
            topV = input.getBestHit(GeneType.Variable);
            topJ = input.getBestHit(GeneType.Joining);
        }

        if (vExtended) {
            this.vExtended.incrementAndGet();
            if (vMerged)
                vExtendedMerged.incrementAndGet();
        }


        boolean jExtended = false, jMerged = false;

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

            Extender jExtension = null;
            if (!topJ.getPartitioningForTarget(cdr3target).isAvailable(jRightExtensionRefPoint)) {
                final GeneFeature jFeature = topJ.getAlignedFeature();
                for (VDJCHit jHit : input.getHits(GeneType.Joining)) {
                    if (jHit.getAlignment(cdr3target) == null)
                        break OUTER;

                    if (jHit.getAlignment(cdr3target).getSequence2Range().getTo() != input.getTarget(cdr3target).size())
                        break OUTER;

                    final VDJCGene jGene = jHit.getGene();

                    //check if input contains some V CDR3 part
                    final int jAnchorPositionInRef = jGene.getPartitioning().getRelativePosition(jFeature, jRightExtensionRefPoint);
                    if (jAnchorPositionInRef == -1
                            || jHit.getAlignment(cdr3target).getSequence1Range().getFrom()
                            >= jAnchorPositionInRef)
                        break OUTER;

                    //extend J
                    int jRightTargetId = -1;
                    int jRightEndCoord = Integer.MAX_VALUE;

                    //searching for adjacent alignment (i.e. right J alignment)
                    for (int i = 0; i < input.numberOfTargets(); i++) {
                        if (i == cdr3target)
                            continue;

                        if (jHit.getAlignment(i) != null) {
                            if (jHit.getAlignment(i).getSequence1Range().getFrom() < jRightEndCoord) {
                                jRightTargetId = i;
                                jRightEndCoord = jHit.getAlignment(i).getSequence1Range().getFrom();
                            }
                        }
                    }

                    if (jRightTargetId != -1)
                        //check that jRight aligned to right
                        if (jHit.getAlignment(jRightTargetId).getSequence2Range().getFrom() != 0)
                            break OUTER;

                    if (jAnchorPositionInRef < jRightEndCoord)
                        jRightTargetId = -1;

                    if (jRightTargetId != -1 && jRightTargetId != cdr3target + 1)
                        break OUTER;

                    Extender r = new Extender(cdr3target,
                            jRightTargetId == -1 ? -1 : jAnchorPositionInRef - jRightEndCoord,
                            jHit.getAlignment(cdr3target).getSequence1().getRange(
                                    jHit.getAlignment(cdr3target).getSequence1Range().getTo(),
                                    jRightTargetId == -1 ? jAnchorPositionInRef : jRightEndCoord),
                            false);

                    if (jExtension == null)
                        jExtension = r;
                    else if (!jExtension.equals(r))
                        break OUTER;
                }
            }

            if (jExtension == null)
                break OUTER;

            // extend
            VDJCAlignments transformed = transform(input, jExtension);

            if (transformed == null)
                // Something went wrong
                return originalInput;

            input = transformed;

            jExtended = true;
            if (jExtension.relativeInsertionStart != -1)
                jMerged = true;
            jExtensionLength.addAndGet(jExtension.extension.size());

            // Update top hits
            topV = input.getBestHit(GeneType.Variable);
            topJ = input.getBestHit(GeneType.Joining);
        }

        if (jExtended) {
            this.jExtended.incrementAndGet();
            if (jMerged)
                jExtendedMerged.incrementAndGet();
        }

        if (vExtended && jExtended)
            vjExtended.incrementAndGet();

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
        if (result.numberOfTargets() == input.numberOfTargets())
            result.setTargetDescriptions(input.getTargetDescriptions());

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

    final class Extender implements VDJCAlignmentTransformer {
        final int cdr3targetId;
        final int leftTargetId;
        final int relativeInsertionStart;
        final NucleotideSequence extension;
        final boolean isV;

        public Extender(int cdr3targetId, int relativeInsertionStart, NucleotideSequence extension, boolean isV) {
            this.cdr3targetId = cdr3targetId;
            this.relativeInsertionStart = relativeInsertionStart;
            this.extension = extension;
            this.isV = isV;
            this.leftTargetId = relativeInsertionStart == -1 ? Integer.MIN_VALUE :
                    (isV ? cdr3targetId - 1 : cdr3targetId);
        }

        public AlignmentScoring<NucleotideSequence> getScoring() {
            return isV ? vScoring : jScoring;
        }

        @Override
        public Alignment<NucleotideSequence>[] transform(VDJCGene gene, Alignment<NucleotideSequence>[] alignments,
                                                         NSequenceWithQuality[] originalTargets) {
            boolean isTargetGeneType;
            if (isV)
                isTargetGeneType = gene.getGeneType() == GeneType.Variable;
            else
                isTargetGeneType = gene.getGeneType() == GeneType.Joining;

            if (relativeInsertionStart == -1) {
                //no adjacent target

                Alignment<NucleotideSequence> al = alignments[cdr3targetId];
                if (isTargetGeneType) {
                    alignments[cdr3targetId] = new Alignment<>(
                            al.getSequence1(),
                            al.getAbsoluteMutations(),
                            isV ?
                                    al.getSequence1Range().expand(extension.size(), 0) :
                                    al.getSequence1Range().expand(0, extension.size()),
                            al.getSequence2Range().expand(0, extension.size()),
                            getScoring());
                } else if (al != null) {
                    alignments[cdr3targetId] = new Alignment<>(
                            al.getSequence1(),
                            al.getAbsoluteMutations(),
                            al.getSequence1Range(),
                            isV ?
                                    al.getSequence2Range().move(extension.size()) :
                                    al.getSequence2Range(),
                            al.getScore());
                }

                return alignments;
            } else {
                // adjacent target id = cdr3targetId - 1

                Alignment<NucleotideSequence> al1 = alignments[leftTargetId];
                Alignment<NucleotideSequence> al2 = alignments[leftTargetId + 1];
                int rightOffset = originalTargets[leftTargetId].size() + extension.size();

                alignments = shrinkArray(alignments);

                if (isTargetGeneType) {
                    Mutations<NucleotideSequence> m1 = al1.getAbsoluteMutations(),
                            m2 = al2.getAbsoluteMutations();

                    Mutations<NucleotideSequence> mutations = new MutationsBuilder<>(NucleotideSequence.ALPHABET)
                            .ensureCapacity(m1.size() + m2.size())
                            .append(m1)
                            .append(m2)
                            .createAndDestroy();

                    alignments[leftTargetId] = new Alignment<>(
                            al1.getSequence1(), mutations,
                            new Range(al1.getSequence1Range().getFrom(),
                                    al2.getSequence1Range().getTo()),
                            new Range(al1.getSequence2Range().getFrom(),
                                    al2.getSequence2Range().getTo() + rightOffset),
                            getScoring());
                } else if (al1 != null && al2 != null)
                    return null;
                else
                    alignments[leftTargetId] = al1 == null ? new Alignment<>(
                            al2.getSequence1(),
                            al2.getAbsoluteMutations(),
                            al2.getSequence1Range(),
                            isV ?
                                    al2.getSequence2Range().move(rightOffset) :
                                    al2.getSequence2Range(),
                            al2.getScore()) : al1;

                return alignments;
            }
        }

        <T> T[] shrinkArray(T[] array) {
            if (array.length - leftTargetId - 2 != 0)
                System.arraycopy(array, leftTargetId + 2, array, leftTargetId + 1,
                        array.length - leftTargetId - 2);
            return Arrays.copyOf(array, array.length - 1);
        }

        @Override
        public NSequenceWithQuality[] transform(NSequenceWithQuality[] targets) {
            NSequenceWithQuality ext = new NSequenceWithQuality(extension,
                    SequenceQuality.getUniformQuality(extensionQuality, extension.size()));

            if (relativeInsertionStart == -1) {
                if (isV)
                    targets[cdr3targetId] = ext
                            .concatenate(targets[cdr3targetId]);
                else
                    targets[cdr3targetId] = targets[cdr3targetId]
                            .concatenate(ext);
                return targets;
            } else {
                NSequenceWithQuality t1 = targets[leftTargetId];
                NSequenceWithQuality t2 = targets[leftTargetId + 1];
                targets = shrinkArray(targets);

                targets[leftTargetId] = new NSequenceWithQualityBuilder()
                        .ensureCapacity(t1.size() + extension.size() + t2.size())
                        .append(t1)
                        .append(ext)
                        .append(t2)
                        .createAndDestroy();

                return targets;
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Extender that = (Extender) o;

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