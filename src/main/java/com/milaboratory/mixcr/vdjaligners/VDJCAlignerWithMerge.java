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

import com.milaboratory.core.io.sequence.PairedRead;
import com.milaboratory.core.io.sequence.SequenceRead;
import com.milaboratory.core.io.sequence.SingleReadImpl;
import com.milaboratory.core.merger.MismatchOnlyPairedReadMerger;
import com.milaboratory.core.merger.PairedReadMergingResult;
import com.milaboratory.mixcr.basictypes.SequenceHistory;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import io.repseq.core.VDJCGene;

/**
 * @author Dmitry Bolotin
 * @author Stanislav Poslavsky
 */
public final class VDJCAlignerWithMerge extends VDJCAligner<PairedRead> {
    final VDJCAlignerS singleAligner;
    final VDJCAlignerPVFirst pairedAligner;
    final MismatchOnlyPairedReadMerger merger;

    public VDJCAlignerWithMerge(VDJCAlignerParameters parameters) {
        super(parameters);
        singleAligner = new VDJCAlignerS(parameters);
        pairedAligner = new VDJCAlignerPVFirst(parameters);
        merger = new MismatchOnlyPairedReadMerger(
                parameters.getMergerParameters().overrideReadsLayout(
                        parameters.getReadsLayout()));
    }

    @Override
    public int addGene(VDJCGene gene) {
        singleAligner.addGene(gene);
        pairedAligner.addGene(gene);
        return super.addGene(gene);
    }

    @Override
    public void setEventsListener(VDJCAlignerEventListener listener) {
        super.setEventsListener(listener);
        singleAligner.setEventsListener(listener);
        pairedAligner.setEventsListener(listener);
    }

    @Override
    protected void init() {
        singleAligner.ensureInitialized();
        pairedAligner.setSAligner(singleAligner.copyWithoutListener());
        pairedAligner.ensureInitialized();
    }

    @Override
    protected VDJCAlignmentResult<PairedRead> process0(final PairedRead read) {
        PairedReadMergingResult merged = merger.process(read);
        if (merged.isSuccessful()) {
            VDJCAlignments alignment = singleAligner.process(
                    new SingleReadImpl(read.getId(), merged.getOverlappedSequence(), "")).alignment;
            if (listener != null)
                listener.onSuccessfulSequenceOverlap(read, alignment);
            if (alignment != null) {
                boolean isRC = ((SequenceHistory.RawSequence) alignment.getHistory(0)).index.isReverseComplement;
                alignment = alignment.setHistory(
                        new SequenceHistory[]{
                                new SequenceHistory.Merge(
                                        SequenceHistory.OverlapType.SequenceOverlap,
                                        new SequenceHistory.RawSequence(read.getId(),
                                                (byte) (isRC ? 1 : 0), false,
                                                (isRC ? read.getR2().getData().size() : read.getR1().getData().size())),
                                        new SequenceHistory.RawSequence(read.getId(),
                                                (byte) (isRC ? 0 : 1), merged.isReversed(),
                                                (isRC ? read.getR1().getData().size() : read.getR2().getData().size())),
                                        isRC
                                                ? merged.getOffset() + read.getR2().getData().size() - read.getR1().getData().size()
                                                : merged.getOffset(),
                                        merged.getErrors())
                        },
                        parameters.isSaveOriginalReads() ? new SequenceRead[]{read} : null);
            }
            return new VDJCAlignmentResult<>(read, alignment);
        } else
            return pairedAligner.process(read);
    }

    public static String getMMDescr(PairedReadMergingResult merge) {
        return getMMDescr(merge.getOverlap(), merge.getErrors());
    }

    public static String getMMDescr(int matches, int mismatches) {
        String r = Integer.toString(matches);
        if (mismatches != 0)
            r += "-" + mismatches;
        return r;
    }
}
