package com.milaboratory.mixcr.util;

import cc.redberry.pipe.Processor;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.alignment.AlignmentScoring;
import com.milaboratory.core.io.sequence.SequenceRead;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.mutations.MutationsBuilder;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.SequenceQuality;
import com.milaboratory.mixcr.basictypes.*;
import com.milaboratory.mixcr.cli.Report;
import com.milaboratory.mixcr.cli.ReportHelper;
import io.repseq.core.*;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Stanislav Poslavsky
 */
public final class VDJCObjectExtender<T extends VDJCObject> implements Processor<T, T>, Report {
    final Chains chains;
    final byte extensionQuality;
    final AlignmentScoring<NucleotideSequence> vScoring, jScoring;
    final ReferencePoint vLeftExtensionRefPoint, jRightExtensionRefPoint;
    final int minimalVScore;
    final int minimalJScore;

    //metrics
    final AtomicLong total = new AtomicLong(0),
            vExtended = new AtomicLong(0),
            jExtended = new AtomicLong(0),
            vExtendedMerged = new AtomicLong(0),
            jExtendedMerged = new AtomicLong(0),
            vjExtended = new AtomicLong(0),
            vExtensionLength = new AtomicLong(0),
            jExtensionLength = new AtomicLong(0);

    public VDJCObjectExtender(Chains chains, byte extensionQuality,
                              AlignmentScoring<NucleotideSequence> vScoring, AlignmentScoring<NucleotideSequence> jScoring,
                              int minimalVScore, int minimalJScore,
                              ReferencePoint vLeftExtensionRefPoint, ReferencePoint jRightExtensionRefPoint) {
        this.chains = chains;
        this.extensionQuality = extensionQuality;
        this.vScoring = vScoring;
        this.jScoring = jScoring;
        this.minimalVScore = minimalVScore;
        this.minimalJScore = minimalJScore;
        this.vLeftExtensionRefPoint = vLeftExtensionRefPoint;
        this.jRightExtensionRefPoint = jRightExtensionRefPoint;
    }

    @Override
    public T process(T input) {
        T originalInput = input;

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

        if (topV.getScore() >= minimalVScore) {
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

                        //checking one more time, whether extension is required
                        //this termination point will be triggered if aligned V hits do not agree on
                        //the position of vLeftExtensionRefPoint
                        if (vAnchorPositionInRef >= vHit.getAlignment(cdr3target).getSequence1Range().getFrom()) {
                            //dropping any previous extension intents
                            vExtension = null;
                            //breaking only current loop
                            break;
                        }

                        //extend V
                        int vLeftTargetId = -1;
                        int vLeftEndCoord = -1;

                        //searching for adjacent alignment (i.e. left V alignment)
                        for (int i = 0; i < input.numberOfTargets(); i++) {
                            if (i == cdr3target)
                                continue;

                            if (vHit.getAlignment(i) != null) {
                                if (vHit.getAlignment(i).getSequence1Range().getTo() > vLeftEndCoord) {
                                    vLeftTargetId = i;
                                    vLeftEndCoord = vHit.getAlignment(i).getSequence1Range().getTo();
                                }
                            }
                        }

                        if (vLeftTargetId != -1) {
                            //check that vLeft aligned to right
                            if (vHit.getAlignment(vLeftTargetId).getSequence2Range().getTo() != input.getTarget(vLeftTargetId).size())
                                break OUTER;
                            //check that there is no overlap between left and right parts
                            if (vLeftEndCoord > vHit.getAlignment(cdr3target).getSequence1Range().getFrom())
                                break OUTER;
                        }

                        if (vAnchorPositionInRef > vLeftEndCoord)
                            vLeftTargetId = -1;

                        if (vLeftTargetId != -1 && vLeftTargetId != cdr3target - 1)
                            break OUTER;

                        NucleotideSequence ext = vHit.getAlignment(cdr3target).getSequence1().getRange(
                                vLeftTargetId == -1 ? vAnchorPositionInRef : vLeftEndCoord,
                                vHit.getAlignment(cdr3target).getSequence1Range().getFrom());
                        Extender r = new Extender(vLeftTargetId, cdr3target, ext, vScoring, GeneType.Variable);

                        //Extender r = new Extender(cdr3target,
                        //        vLeftTargetId == -1 ? -1 : vLeftEndCoord - vAnchorPositionInRef,
                        //        vHit.getAlignment(cdr3target).getSequence1().getRange(
                        //                vLeftTargetId == -1 ? vAnchorPositionInRef : vLeftEndCoord,
                        //                vHit.getAlignment(cdr3target).getSequence1Range().getFrom()),
                        //        true);

                        if (vExtension == null)
                            vExtension = r;
                        else if (!vExtension.equals(r))
                            break OUTER;
                    }
                }

                if (vExtension == null)
                    break OUTER;

                // extend
                T transformed = (T) transform(input, vExtension);

                if (transformed == null)
                    // Something went wrong
                    return originalInput;

                input = transformed;

                vExtended = true;
                if (vExtension.isMerging())
                    vMerged = true;
                vExtensionLength.addAndGet(vExtension.extension.size());

                // Update top hits
                topV = input.getBestHit(GeneType.Variable);
                topJ = input.getBestHit(GeneType.Joining);
            }
        }

        if (vExtended) {
            this.vExtended.incrementAndGet();
            if (vMerged)
                vExtendedMerged.incrementAndGet();
        }


        boolean jExtended = false, jMerged = false;

        if (topJ.getScore() >= minimalJScore) {
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

                        //checking one more time, whether extension is required
                        //this termination point will be triggered if aligned J hits do not agree on
                        //the position of jRightExtensionRefPoint
                        if (jAnchorPositionInRef <= jHit.getAlignment(cdr3target).getSequence1Range().getTo()) {
                            //dropping any previous extension intents
                            jExtension = null;
                            //breaking only current loop
                            break;
                        }

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

                        if (jRightTargetId != -1) {
                            //check that jRight aligned to right
                            if (jHit.getAlignment(jRightTargetId).getSequence2Range().getFrom() != 0)
                                break OUTER;
                            //check that there is no overlap between left and right parts
                            if (jRightEndCoord < jHit.getAlignment(cdr3target).getSequence1Range().getTo())
                                break OUTER;
                        }

                        if (jAnchorPositionInRef < jRightEndCoord)
                            jRightTargetId = -1;

                        if (jRightTargetId != -1 && jRightTargetId != cdr3target + 1)
                            break OUTER;

                        NucleotideSequence ext = jHit.getAlignment(cdr3target).getSequence1().getRange(
                                jHit.getAlignment(cdr3target).getSequence1Range().getTo(),
                                jRightTargetId == -1 ? jAnchorPositionInRef : jRightEndCoord);

                        Extender r = new Extender(cdr3target, jRightTargetId, ext, jScoring, GeneType.Joining);

                        //Extender r = new Extender(cdr3target,
                        //        jRightTargetId == -1 ? -1 : jAnchorPositionInRef - jRightEndCoord,
                        //        jHit.getAlignment(cdr3target).getSequence1().getRange(
                        //                jHit.getAlignment(cdr3target).getSequence1Range().getTo(),
                        //                jRightTargetId == -1 ? jAnchorPositionInRef : jRightEndCoord),
                        //        false);

                        if (jExtension == null)
                            jExtension = r;
                        else if (!jExtension.equals(r))
                            break OUTER;
                    }
                }

                if (jExtension == null)
                    break OUTER;

                // extend
                T transformed = (T) transform(input, jExtension);

                if (transformed == null)
                    // Something went wrong
                    return originalInput;

                input = transformed;

                jExtended = true;
                if (jExtension.isMerging())
                    jMerged = true;
                jExtensionLength.addAndGet(jExtension.extension.size());

                // Update top hits
                topV = input.getBestHit(GeneType.Variable);
                topJ = input.getBestHit(GeneType.Joining);
            }
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

    @JsonProperty("totalProcessed")
    public long getTotalProcessed() {
        return total.get();
    }

    @JsonProperty("totalExtended")
    public long getTotalExtended() {
        return vExtended.get() + jExtended.get() - vjExtended.get();
    }

    @JsonProperty("vExtended")
    public long getVExtended() {
        return vExtended.get();
    }

    @JsonProperty("vExtendedMerged")
    public long getVExtendedMerged() {
        return vExtendedMerged.get();
    }

    @JsonProperty("jExtended")
    public long getJExtended() {
        return jExtended.get();
    }

    @JsonProperty("jExtendedMerged")
    public long getJExtendedMerged() {
        return jExtendedMerged.get();
    }

    @JsonProperty("vjExtended")
    public long getVJExtended() {
        return jExtendedMerged.get();
    }

    @JsonProperty("meanVExtensionLength")
    public double getMeanVExtensionLength() {
        return 1.0 * vExtensionLength.get() / vExtended.get();
    }

    @JsonProperty("meanJExtensionLength")
    public double getMeanJExtensionLength() {
        return 1.0 * jExtensionLength.get() / jExtended.get();
    }

    @Override
    public void writeReport(ReportHelper helper) {
        long total = this.total.get();
        helper.writePercentAndAbsoluteField("Extended alignments count", getTotalExtended(), total);
        helper.writePercentAndAbsoluteField("V extensions total", getVExtended(), total);
        helper.writePercentAndAbsoluteField("V extensions with merged targets", getVExtendedMerged(), total);
        helper.writePercentAndAbsoluteField("J extensions total", getJExtended(), total);
        helper.writePercentAndAbsoluteField("J extensions with merged targets", getJExtendedMerged(), total);
        helper.writePercentAndAbsoluteField("V+J extensions", getVJExtended(), total);
        helper.writeField("Mean V extension length", getMeanVExtensionLength());
        helper.writeField("Mean J extension length", getMeanJExtensionLength());
    }

    /**
     * @return result or null is something went wrong
     */
    static VDJCObject transform(VDJCObject input,
                                VDJCObjectExtender<?>.Extender transformer) {
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

                float sumScore = (float) Arrays.stream(transformed)
                        .filter(Objects::nonNull)
                        .mapToDouble(Alignment::getScore)
                        .sum();

                newHits[i] = new VDJCHit(gene, transformed, inputHits[i].getAlignedFeature(),
                        Math.max(sumScore, inputHits[i].getScore()));
            }
            newHitsMap.put(gt, newHits);
        }

        if (input instanceof VDJCAlignments)
            return doTransformAlignment((VDJCAlignments) input, transformer, newHitsMap);
        else if (input instanceof Clone)
            return doTransformClone((Clone) input, transformer, newHitsMap);
        else
            throw new IllegalArgumentException("Object type not supported: " + input);
    }

    static Clone doTransformClone(Clone clone,
                                  VDJCObjectExtender<?>.Extender transformer,
                                  EnumMap<GeneType, VDJCHit[]> newHitsMap) {
        return new Clone(transformer.transform(clone.getTargets()),
                newHitsMap, clone.getCount(), clone.getId());
    }

    static VDJCAlignments doTransformAlignment(VDJCAlignments alignment,
                                               VDJCObjectExtender<?>.Extender transformer,
                                               EnumMap<GeneType, VDJCHit[]> newHitsMap) {
        return new VDJCAlignments(
                newHitsMap,
                transformer.transform(alignment.getTargets()),
                transformer.transform(alignment.getHistory()),
                alignment.getOriginalReads() == null
                        ? null
                        : alignment.getOriginalReads().toArray(new SequenceRead[alignment.getOriginalReads().size()]))
                .setAlignmentsIndex(alignment.getAlignmentsIndex());
    }

    static <T> void shrinkArray0(T[] src, T[] dest, int leftTargetId, int rightTargetId) {
        assert leftTargetId == rightTargetId - 1;
        System.arraycopy(src, 0, dest, 0, leftTargetId);
        if (rightTargetId < src.length - 1)
            System.arraycopy(src, rightTargetId + 1, dest, rightTargetId, src.length - rightTargetId - 1);
    }

    final class Extender {
        final int leftTargetId;
        final int rightTargetId;
        final NucleotideSequence extension;
        final AlignmentScoring<NucleotideSequence> scoring;
        final GeneType extensionGeneType;

        Extender(int leftTargetId, int rightTargetId, NucleotideSequence extension, AlignmentScoring<NucleotideSequence> scoring, GeneType extensionGeneType) {
            this.leftTargetId = leftTargetId;
            this.rightTargetId = rightTargetId;
            this.extension = extension;
            this.scoring = scoring;
            this.extensionGeneType = extensionGeneType;
        }

        boolean isMerging() {
            return rightTargetId != -1 && leftTargetId != -1;
        }

        <T> void shrinkArray(T[] src, T[] dest) {
            shrinkArray0(src, dest, leftTargetId, rightTargetId);
        }

        @SuppressWarnings("unchecked")
        public Alignment<NucleotideSequence>[] transform(VDJCGene gene, Alignment<NucleotideSequence>[] alignments, NSequenceWithQuality[] originalTargets) {
            Alignment<NucleotideSequence>[] newAlignments = isMerging() ? new Alignment[alignments.length - 1] : alignments.clone();

            GeneType currentGeneType = gene.getGeneType();
            if (currentGeneType == extensionGeneType) {
                if (leftTargetId != -1 && rightTargetId != -1) {
                    Alignment<NucleotideSequence> alL = alignments[leftTargetId];
                    Alignment<NucleotideSequence> alR = alignments[rightTargetId];
                    assert alL != null && alR != null : "L" + alL + "R" + alR;
                    shrinkArray(alignments, newAlignments);


                    Mutations<NucleotideSequence> mL = alL.getAbsoluteMutations(),
                            mR = alR.getAbsoluteMutations();

                    Mutations<NucleotideSequence> mutations = new MutationsBuilder<>(NucleotideSequence.ALPHABET)
                            .ensureCapacity(mL.size() + mR.size())
                            .append(mL)
                            .append(mR)
                            .createAndDestroy();

                    newAlignments[leftTargetId] = new Alignment<>(
                            alL.getSequence1(), mutations,
                            new Range(alL.getSequence1Range().getFrom(),
                                    alR.getSequence1Range().getTo()),
                            new Range(alL.getSequence2Range().getFrom(),
                                    alR.getSequence2Range().getTo() + originalTargets[leftTargetId].size() +
                                            extension.size()), scoring);
                } else if (rightTargetId != -1) {
                    assert alignments[rightTargetId] != null;
                    newAlignments[rightTargetId] = expandSeq2Range(newAlignments[rightTargetId], extension.size(), 0);
                    assert newAlignments[rightTargetId].getSequence2Range().getFrom() == 0;
                } else {
                    assert alignments[leftTargetId] != null;
                    newAlignments[leftTargetId] = expandSeq2Range(newAlignments[leftTargetId], 0, extension.size());
                    assert newAlignments[leftTargetId].getSequence2Range().getTo() == originalTargets[leftTargetId].size() + extension.size();
                }
            } else {
                if (leftTargetId != -1 && rightTargetId != -1) {
                    if (alignments[leftTargetId] != null && alignments[rightTargetId] != null)
                        return null; // Can't merge not-extensionGeneType alignment
                    Alignment<NucleotideSequence> centralAlignment = alignments[leftTargetId] == null ?
                            alignments[rightTargetId] : alignments[leftTargetId];

                    shrinkArray(alignments, newAlignments);
                    if (centralAlignment == null)
                        return newAlignments;

                    if (extensionGeneType.getOrder() < currentGeneType.getOrder())
                        newAlignments[leftTargetId] = shiftSeq2Range(centralAlignment,
                                originalTargets[leftTargetId].size() + extension.size());
                    else
                        newAlignments[leftTargetId] = centralAlignment;
                } else if (rightTargetId != -1 && alignments[rightTargetId] != null)
                    newAlignments[rightTargetId] = shiftSeq2Range(newAlignments[rightTargetId], extension.size());
            }
            return newAlignments;
        }

        public Alignment<NucleotideSequence> shiftSeq2Range(Alignment<NucleotideSequence> alignment, int shift) {
            return new Alignment<>(
                    alignment.getSequence1(),
                    alignment.getAbsoluteMutations(),
                    alignment.getSequence1Range(),
                    alignment.getSequence2Range().move(shift),
                    alignment.getScore());
        }

        public Alignment<NucleotideSequence> expandSeq2Range(Alignment<NucleotideSequence> alignment, int left, int right) {
            return new Alignment<>(
                    alignment.getSequence1(),
                    alignment.getAbsoluteMutations(),
                    alignment.getSequence1Range().expand(left, right),
                    alignment.getSequence2Range().expand(0, right + left),
                    alignment.getScore());
        }

        public NSequenceWithQuality[] transform(NSequenceWithQuality[] targets) {
            NSequenceWithQuality ext = new NSequenceWithQuality(extension,
                    SequenceQuality.getUniformQuality(extensionQuality, extension.size()));

            NSequenceWithQuality[] newTargets = isMerging() ? new NSequenceWithQuality[targets.length - 1] : targets.clone();

            if (leftTargetId != -1 && rightTargetId != -1) {
                shrinkArray(targets, newTargets);
                newTargets[leftTargetId] = targets[leftTargetId].concatenate(ext).concatenate(targets[rightTargetId]);
            } else if (leftTargetId != -1)
                newTargets[leftTargetId] = newTargets[leftTargetId].concatenate(ext);
            else
                newTargets[rightTargetId] = ext.concatenate(newTargets[rightTargetId]);

            return newTargets;
        }

        public SequenceHistory[] transform(SequenceHistory[] histories) {
            SequenceHistory[] newHistories = isMerging() ? new SequenceHistory[histories.length - 1] : histories.clone();

            if (leftTargetId != -1 && rightTargetId != -1) {
                shrinkArray(histories, newHistories);
                SequenceHistory l, r;
                if (extensionGeneType == GeneType.Variable) {
                    l = histories[leftTargetId];
                    r = new SequenceHistory.Extend(histories[rightTargetId], extension.size(), 0);
                } else {
                    l = new SequenceHistory.Extend(histories[leftTargetId], 0, extension.size());
                    r = histories[rightTargetId];
                }
                newHistories[leftTargetId] = new SequenceHistory.Merge(SequenceHistory.OverlapType.ExtensionMerge,
                        l, r, l.length(), 0);
            } else if (leftTargetId != -1)
                newHistories[leftTargetId] = new SequenceHistory.Extend(histories[leftTargetId], 0, extension.size());
            else
                newHistories[rightTargetId] = new SequenceHistory.Extend(histories[rightTargetId], extension.size(), 0);

            return newHistories;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof VDJCObjectExtender.Extender)) return false;
            Extender extender = (Extender) o;
            return leftTargetId == extender.leftTargetId &&
                    rightTargetId == extender.rightTargetId &&
                    Objects.equals(extension, extender.extension) &&
                    Objects.equals(scoring, extender.scoring) &&
                    extensionGeneType == extender.extensionGeneType;
        }

        @Override
        public int hashCode() {
            return Objects.hash(leftTargetId, rightTargetId, extension, scoring, extensionGeneType);
        }


        // @Override
        // public boolean equals(Object o) {
        //     if (this == o) return true;
        //     if (!(o instanceof Extender)) return false;
        //
        //     Extender extender1 = (Extender) o;
        //
        //     if (leftTargetId != extender1.leftTargetId) return false;
        //     if (rightTargetId != extender1.rightTargetId) return false;
        //     if (!extension.equals(extender1.extension)) return false;
        //     if (!scoring.equals(extender1.scoring)) return false;
        //     return extensionGeneType == extender1.extensionGeneType;
        // }
        //
        // @Override
        // public int hashCode() {
        //     int result = leftTargetId;
        //     result = 31 * result + rightTargetId;
        //     result = 31 * result + extension.hashCode();
        //     result = 31 * result + scoring.hashCode();
        //     result = 31 * result + extensionGeneType.hashCode();
        //     return result;
        // }
        //
        //@Override
        //public String[] transform(String[] targets) {
        //    return new NSequenceWithQuality[0];
        //}
    }
}