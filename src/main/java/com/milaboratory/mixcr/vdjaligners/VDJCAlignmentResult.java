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

public final class VDJCAlignmentResult<R extends SequenceRead> {
    public final R read;
    public final VDJCAlignments alignment;

    public VDJCAlignmentResult(R read, VDJCAlignments alignment) {
        this.read = read;
        this.alignment = alignment;
    }

    public VDJCAlignmentResult(R read) {
        this.read = read;
        this.alignment = null;
    }

    public VDJCAlignmentResult<R> shiftIndelsAtHomopolymers() {
        if (alignment == null)
            return this;
        return new VDJCAlignmentResult<>(read, alignment.shiftIndelsAtHomopolymers());
    }
}
