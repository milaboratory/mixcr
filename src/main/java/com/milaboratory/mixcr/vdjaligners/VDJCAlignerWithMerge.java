/*
 * Copyright (c) 2014-2015, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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
 * commercial purposes should contact the Inventors using one of the following
 * email addresses: chudakovdm@mail.ru, chudakovdm@gmail.com
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
                        new SequenceRead[]{read});
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
