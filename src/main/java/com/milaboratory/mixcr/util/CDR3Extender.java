package com.milaboratory.mixcr.util;

import cc.redberry.pipe.Processor;
import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import io.repseq.core.*;

import java.util.EnumMap;

/**
 * @author Stanislav Poslavsky
 */
public final class CDR3Extender implements Processor<VDJCAlignments, VDJCAlignments> {
    final Chains chains;

    public CDR3Extender(Chains chains) {
        this.chains = chains;
    }

    @Override
    public VDJCAlignments process(VDJCAlignments input) {
        //check input chains
        if (!chains.intersects(input.getTopChain(GeneType.Variable))
                && !chains.intersects(input.getTopChain(GeneType.Joining)))
            return input;

        //check whether cdr3 is already covered
        if (input.getFeature(GeneFeature.CDR3) != null)
            return input;

        final VDJCHit topV = input.getBestHit(GeneType.Variable);
        final VDJCHit topJ = input.getBestHit(GeneType.Joining);

        //reject if any of hits absent
        if (topV == null || topJ == null)
            return input;

        int cdr3target = -1;
        for (int i = 0; i < input.numberOfTargets(); i++) {
            if (topV.getAlignment(i) == null || topJ.getAlignment(i) == null)
                continue;

            if (cdr3target != -1)
                return input;
            cdr3target = i;
        }

        ExtendResult vExtension = null;
        if (!topV.getPartitioningForTarget(cdr3target).isAvailable(ReferencePoint.CDR3Begin)) {
            final GeneFeature vFeature = topV.getAlignedFeature();
            for (VDJCHit vHit : input.getHits(GeneType.Variable)) {
                if (vHit.getAlignment(cdr3target) == null)
                    return input;

                if (vHit.getAlignment(cdr3target).getSequence2Range().getFrom() != 0)
                    return input;

                final VDJCGene vGene = vHit.getGene();

                //check if input contains some V CDR3 part
                final int vCdr3BeginPositionInRef = vGene.getPartitioning().getRelativePosition(vFeature, ReferencePoint.CDR3Begin);
                if (vCdr3BeginPositionInRef == -1
                        || vHit.getAlignment(cdr3target).getSequence1Range().getTo()
                        < vCdr3BeginPositionInRef)
                    return input;

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
                        return input;

                if (vCdr3BeginPositionInRef > vLeftEndCoord)
                    vLeftTargetid = -1;

                if (vLeftTargetid != -1 && vLeftTargetid != cdr3target - 1)
                    return input;

                ExtendResult r = new ExtendResult(cdr3target,
                        vLeftTargetid == -1 ? -1 : vLeftEndCoord - vCdr3BeginPositionInRef,
                        vHit.getAlignment(cdr3target).getSequence1().getRange(vLeftEndCoord, vHit.getAlignment(cdr3target).getSequence1Range().getFrom()),
                        true);

                if (vExtension == null)
                    vExtension = r;
                else if (!vExtension.equals(r))
                    return input;
            }
        }

        ExtendResult jExtension = null;


        if (vExtension == null && jExtension == null)
            return input;


        if (vExtension != null) {

            if (vExtension.relativeInsertionStart == -1) {
                //extend
                input = transform(input, new VDJCAlignmentTransformer() {
                    @Override
                    @SuppressWarnings("unchecked")
                    public Alignment<NucleotideSequence>[] transform(VDJCGene gene, Alignment<NucleotideSequence>[] alignment) {
                        return new Alignment[0];
                    }
                }, null);
            }


        }


        return input;
    }

    interface VDJCAlignmentTransformer {
        Alignment<NucleotideSequence>[] transform(VDJCGene gene, Alignment<NucleotideSequence>[] alignment);
    }

    static VDJCAlignments transform(VDJCAlignments input,
                                    VDJCAlignmentTransformer transformer,
                                    NSequenceWithQuality[] newTargets) {
        EnumMap<GeneType, VDJCHit[]> newHitsMap = new EnumMap<>(GeneType.class);
        for (GeneType gt : GeneType.VDJC_REFERENCE) {
            final VDJCHit[] inputHits = input.getHits(gt);
            final VDJCHit[] newHits = new VDJCHit[inputHits.length];
            for (int i = 0; i < inputHits.length; i++) {
                final VDJCGene gene = inputHits[i].getGene();
                newHits[i] = new VDJCHit(gene, transformer.transform(gene, inputHits[i].getAlignments()), inputHits[i].getAlignedFeature());
            }
            newHitsMap.put(gt, newHits);
        }

        final VDJCAlignments result = new VDJCAlignments(input.getReadId(), newHitsMap, newTargets);
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

    final static class ExtendResult implements VDJCAlignmentTransformer {
        final int cdr3targetId;
        final int relativeInsertionStart;
        final NucleotideSequence extension;
        final boolean extendV;

        public ExtendResult(int cdr3targetId, int relativeInsertionStart, NucleotideSequence extension, boolean extendV) {
            this.cdr3targetId = cdr3targetId;
            this.relativeInsertionStart = relativeInsertionStart;
            this.extension = extension;
            this.extendV = extendV;
        }

        @Override
        public Alignment<NucleotideSequence>[] transform(VDJCGene gene, Alignment<NucleotideSequence>[] alignment) {
            if (relativeInsertionStart == -1) {
                //no adjacent target

            } else {

            }
            return new Alignment[0];
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ExtendResult that = (ExtendResult) o;

            if (cdr3targetId != that.cdr3targetId) return false;
            if (relativeInsertionStart != that.relativeInsertionStart) return false;
            if (extendV != that.extendV) return false;
            return extension.equals(that.extension);

        }

        @Override
        public int hashCode() {
            int result = cdr3targetId;
            result = 31 * result + relativeInsertionStart;
            result = 31 * result + extension.hashCode();
            result = 31 * result + (extendV ? 1 : 0);
            return result;
        }
    }
}