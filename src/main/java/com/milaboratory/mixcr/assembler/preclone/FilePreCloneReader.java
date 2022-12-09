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
package com.milaboratory.mixcr.assembler.preclone;


import cc.redberry.pipe.OutputPort;
import com.milaboratory.mixcr.basictypes.IOUtil;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.tag.TagsInfo;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import com.milaboratory.primitivio.PrimitivI;
import com.milaboratory.primitivio.blocks.PrimitivIHeaderActions;
import com.milaboratory.primitivio.blocks.PrimitivIHybrid;
import com.milaboratory.util.OutputPortWithExpectedSizeKt;
import com.milaboratory.util.OutputPortWithProgress;
import io.repseq.core.GeneFeature;
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
    private final GeneFeature[] assemblingFeature;
    private final TagsInfo tagsInfo;
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
            this.assemblingFeature = i.readObject(GeneFeature[].class);
            this.tagsInfo = i.readObject(TagsInfo.class);
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

    public long getNumberOfReads() {
        return numberOfReads;
    }

    public long getNumberOfAlignments() {
        return numberOfAlignments;
    }

    public long getNumberOfAssignedAlignments() {
        return numberOfAssignedAlignments;
    }

    public long getNumberOfClones() {
        return numberOfClones;
    }

    public VDJCAlignerParameters getAlignmentParameters() {
        return alignmentParameters;
    }

    public GeneFeature[] getAssemblingFeature() {
        return assemblingFeature;
    }

    public TagsInfo getTagsInfo() {
        return tagsInfo;
    }

    public List<VDJCGene> getUsedGenes() {
        return usedGenes;
    }

    public OutputPortWithProgress<VDJCAlignments> readUnassignedAlignments() {
        OutputPort<VDJCAlignments> port = input.beginRandomAccessPrimitivIBlocks(
                VDJCAlignments.class, alignmentsStartPosition,
                h -> h.getSpecialByte(0) == UNASSIGNED_ALIGNMENTS_END_MARK_BYTE_0
                        ? PrimitivIHeaderActions.stopReading()
                        : PrimitivIHeaderActions.error());
        return OutputPortWithExpectedSizeKt.withExpectedSize(port, numberOfAlignments - numberOfAssignedAlignments);
    }

    public OutputPortWithProgress<VDJCAlignments> readAssignedAlignments() {
        OutputPort<VDJCAlignments> port = input.beginRandomAccessPrimitivIBlocks(VDJCAlignments.class, assignedAlignmentsStartPosition,
                h -> h.getSpecialByte(0) == ALIGNMENTS_END_MARK_BYTE_0
                        ? PrimitivIHeaderActions.stopReading()
                        : PrimitivIHeaderActions.error());
        return OutputPortWithExpectedSizeKt.withExpectedSize(port, numberOfAssignedAlignments);
    }

    @Override
    public OutputPortWithProgress<VDJCAlignments> readAlignments() {
        OutputPort<VDJCAlignments> port = input.beginRandomAccessPrimitivIBlocks(VDJCAlignments.class, alignmentsStartPosition,
                h -> h.getSpecialByte(0) == UNASSIGNED_ALIGNMENTS_END_MARK_BYTE_0
                        ? PrimitivIHeaderActions.skip()
                        : h.getSpecialByte(0) == ALIGNMENTS_END_MARK_BYTE_0
                        ? PrimitivIHeaderActions.stopReading()
                        : PrimitivIHeaderActions.error());
        return OutputPortWithExpectedSizeKt.withExpectedSize(port, numberOfAlignments);
    }

    @Override
    public OutputPortWithProgress<PreClone> readPreClones() {
        OutputPort<PreCloneImpl> port = input.beginRandomAccessPrimitivIBlocks(PreCloneImpl.class, clonesStartPosition,
                h -> h.getSpecialByte(0) == CLONES_END_MARK_BYTE_0
                        ? PrimitivIHeaderActions.stopReading()
                        : PrimitivIHeaderActions.error());
        return OutputPortWithExpectedSizeKt.withExpectedSize(port, numberOfClones);
    }

    @Override
    public void close() throws Exception {
        input.close();
    }
}
