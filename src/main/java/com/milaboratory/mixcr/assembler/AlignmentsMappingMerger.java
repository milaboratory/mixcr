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
package com.milaboratory.mixcr.assembler;

import cc.redberry.pipe.OutputPort;
import cc.redberry.pipe.OutputPortCloseable;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;

public final class AlignmentsMappingMerger implements OutputPortCloseable<VDJCAlignments> {
    final OutputPort<VDJCAlignments> alignments;
    final OutputPort<ReadToCloneMapping> readToCloneMappings;
    ReadToCloneMapping lastMapping;

    public AlignmentsMappingMerger(OutputPort<VDJCAlignments> alignments, OutputPort<ReadToCloneMapping> readToCloneMappings) {
        this.alignments = alignments;
        this.readToCloneMappings = readToCloneMappings;
    }

    @Override
    public VDJCAlignments take() {
        VDJCAlignments al = alignments.take();
        if (al == null) {
            assert readToCloneMappings.take() == null;
            return null;
        }

        // here cloneIndex is set by a pre-clone block
        // -1 == clone not included into any pre-clone

        if (al.getCloneIndex() == -1)
            return al;

        if(lastMapping == null || lastMapping.getPreCloneIdx() != al.getCloneIndex())
            lastMapping = readToCloneMappings.take();
        assert lastMapping.getPreCloneIdx() == al.getCloneIndex();

        return al.setMapping(lastMapping);
    }

    @Override
    public void close() {
        try {
            if (alignments instanceof AutoCloseable)
                ((AutoCloseable) alignments).close();
            if (readToCloneMappings instanceof AutoCloseable)
                ((AutoCloseable) readToCloneMappings).close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
