/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 * 
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 * 
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */
package com.milaboratory.mixcr.vdjaligners;

import com.milaboratory.core.io.sequence.SequenceRead;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import io.repseq.core.GeneType;

public interface VDJCAlignerEventListener {
    void onFailedAlignment(SequenceRead read, VDJCAlignmentFailCause cause);

    void onSuccessfulAlignment(SequenceRead read, VDJCAlignments alignment);

    /**
     * Fired on successful sequence-aided overlap (e.g. using PEAR-like algorithm, see {@link
     * com.milaboratory.core.merger.MismatchOnlyPairedReadMerger})
     *
     * @param read       original read
     * @param alignments resulting alignment
     */
    void onSuccessfulSequenceOverlap(SequenceRead read, VDJCAlignments alignments);

    /**
     * Fired on successful alignment-aided overlap (see {@link VDJCAlignerWithMerge})
     *
     * @param read       original read
     * @param alignments resulting alignment
     */
    void onSuccessfulAlignmentOverlap(SequenceRead read, VDJCAlignments alignments);

    /**
     * Rather technical event, used for algorithm performance monitoring
     */
    void onTopHitSequenceConflict(SequenceRead read, VDJCAlignments alignments, GeneType geneType);

    void onSegmentChimeraDetected(GeneType geneType, SequenceRead read, VDJCAlignments alignments);

    /** only for paired-end PV-first aligner */
    void onRealignmentWithForcedNonFloatingBound(boolean forceLeftEdgeInRight, boolean forceRightEdgeInLeft);
}
