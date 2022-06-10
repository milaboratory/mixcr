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
package com.milaboratory.mixcr.partialassembler;

import com.milaboratory.core.io.sequence.MultiRead;
import com.milaboratory.core.io.sequence.SequenceRead;
import com.milaboratory.core.io.sequence.SingleRead;
import com.milaboratory.core.io.sequence.SingleReadImpl;
import com.milaboratory.mixcr.basictypes.SequenceHistory;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;

import java.util.List;

/**
 * @author Dmitry Bolotin
 * @author Stanislav Poslavsky
 */
public class VDJCMultiRead extends MultiRead {
    private final List<AlignedTarget> targets;

    public VDJCMultiRead(SingleRead... data) {
        super(data.clone());
        targets = null;
    }

    @SuppressWarnings("unchecked")
    public VDJCMultiRead(List<AlignedTarget> targets) {
        super(extractReads(targets));
        this.targets = targets;
        //this.expectedGeneTypes = new EnumSet[targets.size()];
        //for (int i = 0; i < expectedGeneTypes.length; i++)
        //    expectedGeneTypes[i] = targets.get(i).getExpectedGenes();
    }

    public static SingleReadImpl[] extractReads(List<AlignedTarget> targets) {
        final SingleReadImpl[] reads = new SingleReadImpl[targets.size()];
        for (int i = 0; i < reads.length; i++)
            reads[i] = new SingleReadImpl(-1, targets.get(i).getTarget(), "");
        return reads;
    }

    SequenceRead[] getOriginalReads() {
        if (targets == null)
            return null;
        VDJCAlignments[] result = new VDJCAlignments[targets.size()];
        for (int i = 0; i < result.length; i++)
            result[i] = targets.get(i).getAlignments();
        return VDJCAlignments.mergeOriginalReads(result);
    }

    SequenceHistory[] getHistory() {
        SequenceHistory[] result = new SequenceHistory[numberOfReads()];
        for (int i = 0; i < result.length; i++)
            result[i] = targets == null
                    ? new SequenceHistory.RawSequence(getId(), (byte) i, false, getRead(i).getData().size())
                    : targets.get(i).getHistory();
        return result;
    }
}
