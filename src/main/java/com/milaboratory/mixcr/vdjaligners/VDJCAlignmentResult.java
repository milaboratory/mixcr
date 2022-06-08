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
import com.milaboratory.mixcr.basictypes.tag.TagTuple;
import io.repseq.core.GeneType;

import java.util.Set;

public final class VDJCAlignmentResult<R extends SequenceRead> {
    public final R read;
    public final VDJCAlignments alignment;
    public final TagTuple tagTuple;

    public VDJCAlignmentResult(R read, VDJCAlignments alignment) {
        this.read = read;
        this.alignment = alignment;
        this.tagTuple = null;
    }

    public VDJCAlignmentResult(R read) {
        this.read = read;
        this.alignment = null;
        this.tagTuple = null;
    }

    public VDJCAlignmentResult(R read, TagTuple tagTuple) {
        this.read = read;
        this.alignment = null;
        this.tagTuple = tagTuple;
    }

    public VDJCAlignmentResult(R read, VDJCAlignments alignment, TagTuple tagTuple) {
        this.read = read;
        this.alignment = alignment;
        this.tagTuple = tagTuple;
    }

    public VDJCAlignmentResult<R> withTagTuple(TagTuple tagTuple) {
        return new VDJCAlignmentResult<>(read, alignment, tagTuple);
    }

    public VDJCAlignmentResult<R> shiftIndelsAtHomopolymers(Set<GeneType> gts) {
        if (alignment == null)
            return this;
        return new VDJCAlignmentResult<>(read, alignment.shiftIndelsAtHomopolymers(gts), tagTuple);
    }
}
