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

    void onNoCDR3PartsAlignment();

    void onPartialAlignment();
}
