package com.milaboratory.mixcr.assembler.preclone;


import com.milaboratory.mixcr.basictypes.IOUtil;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.util.OutputPortWithProgress;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import com.milaboratory.primitivio.PrimitivI;
import com.milaboratory.primitivio.blocks.PrimitivIHeaderActions;
import com.milaboratory.primitivio.blocks.PrimitivIHybrid;
import io.repseq.core.VDJCGene;
import io.repseq.core.VDJCLibraryRegistry;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static com.milaboratory.mixcr.assembler.preclone.FilePreCloneWriter.*;

public final class FilePreCloneReader implements PreCloneReader {
    private final PrimitivIHybrid input;
    private final VDJCAlignerParameters alignmentParameters;
    private final List<VDJCGene> usedGenes;
    private final long alignmentsStartPosition,
            assignedAlignmentsStartPosition,
            clonesStartPosition,
            numberOfReads,
            numberOfAlignments,
            numberOfAssignedAlignments,
            numberOfClones;

    public FilePreCloneReader(Path file) throws IOException {
        this.input = new PrimitivIHybrid(file, 4);
        try (PrimitivI i = input.beginPrimitivI(true)) {
            this.alignmentParameters = i.readObject(VDJCAlignerParameters.class);
            this.numberOfReads = i.readLong();
            this.usedGenes = IOUtil.stdVDJCPrimitivIStateInit(i, alignmentParameters,
                    VDJCLibraryRegistry.getDefault());
        }

        try (PrimitivI i = input.beginRandomAccessPrimitivI(-8 * 6)) {
            this.alignmentsStartPosition = i.readLong();
            this.assignedAlignmentsStartPosition = i.readLong();
            this.clonesStartPosition = i.readLong();
            this.numberOfAlignments = i.readLong();
            this.numberOfAssignedAlignments = i.readLong();
            this.numberOfClones = i.readLong();
        }
    }

    public VDJCAlignerParameters getAlignmentParameters() {
        return alignmentParameters;
    }

    public List<VDJCGene> getUsedGenes() {
        return usedGenes;
    }

    public OutputPortWithProgress<VDJCAlignments> readUnassignedAlignments() {
        return OutputPortWithProgress.wrap(numberOfAlignments - numberOfAssignedAlignments,
                input.beginRandomAccessPrimitivIBlocks(VDJCAlignments.class, alignmentsStartPosition,
                        h -> h.getSpecialByte(0) == UNASSIGNED_ALIGNMENTS_END_MARK_BYTE_0
                                ? PrimitivIHeaderActions.stopReading()
                                : PrimitivIHeaderActions.error()));
    }

    public OutputPortWithProgress<VDJCAlignments> readAssignedAlignments() {
        return OutputPortWithProgress.wrap(numberOfAssignedAlignments,
                input.beginRandomAccessPrimitivIBlocks(VDJCAlignments.class, assignedAlignmentsStartPosition,
                        h -> h.getSpecialByte(0) == ALIGNMENTS_END_MARK_BYTE_0
                                ? PrimitivIHeaderActions.stopReading()
                                : PrimitivIHeaderActions.error()));
    }

    @Override
    public OutputPortWithProgress<VDJCAlignments> readAlignments() {
        return OutputPortWithProgress.wrap(numberOfAlignments,
                input.beginRandomAccessPrimitivIBlocks(VDJCAlignments.class, alignmentsStartPosition,
                        h -> h.getSpecialByte(0) == UNASSIGNED_ALIGNMENTS_END_MARK_BYTE_0
                                ? PrimitivIHeaderActions.skip()
                                : h.getSpecialByte(0) == ALIGNMENTS_END_MARK_BYTE_0
                                ? PrimitivIHeaderActions.stopReading()
                                : PrimitivIHeaderActions.error()));
    }

    @Override
    public OutputPortWithProgress<PreClone> readPreClones() {
        return OutputPortWithProgress.wrap(numberOfClones,
                input.beginRandomAccessPrimitivIBlocks(PreClone.class, clonesStartPosition,
                        h -> h.getSpecialByte(0) == CLONES_END_MARK_BYTE_0
                                ? PrimitivIHeaderActions.stopReading()
                                : PrimitivIHeaderActions.error()));
    }

    @Override
    public long getTotalNumberOfReads() {
        return numberOfReads;
    }

    @Override
    public void close() throws Exception {
        input.close();
    }
}
