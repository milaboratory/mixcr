/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 * 
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 * 
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */
package com.milaboratory.mixcr.basictypes;

import com.milaboratory.core.Range;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import io.repseq.core.PartitionedSequence;
import io.repseq.core.SequencePartitioning;

/**
 * Created by poslavsky on 09/07/14.
 */
public class VDJCPartitionedSequence extends PartitionedSequence<NSequenceWithQuality> {
    private final NSequenceWithQuality target;
    private final TargetPartitioning partitioning;

    public VDJCPartitionedSequence(NSequenceWithQuality target, TargetPartitioning partitioning) {
        this.target = target;
        this.partitioning = partitioning;
    }

    public NSequenceWithQuality getSequence() {
        return target;
    }

    @Override
    protected NSequenceWithQuality getSequence(Range range) {
        return target.getRange(range);
    }

    @Override
    public SequencePartitioning getPartitioning() {
        return partitioning;
    }
}
