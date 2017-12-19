package com.milaboratory.mixcr.assembler;

import cc.redberry.pipe.OutputPort;
import cc.redberry.pipe.OutputPortCloseable;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;

public final class AlignmentsMappingMerger implements OutputPortCloseable<VDJCAlignments> {
    final OutputPort<VDJCAlignments> alignments;
    final OutputPort<ReadToCloneMapping> readToCloneMappings;

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
        ReadToCloneMapping m = readToCloneMappings.take();
        assert m.alignmentsId == al.getAlignmentsIndex();
        return al.setMapping(m);
    }

    @Override
    public void close() {
        try {
            if (alignments instanceof AutoCloseable)
                ((AutoCloseable) alignments).close();
            if (readToCloneMappings instanceof AutoCloseable)
                ((AutoCloseable) readToCloneMappings).close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
