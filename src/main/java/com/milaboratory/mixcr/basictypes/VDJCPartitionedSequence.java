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
    public TargetPartitioning getPartitioning() {
        return partitioning;
    }
}
