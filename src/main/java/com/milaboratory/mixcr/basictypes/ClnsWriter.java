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

import cc.redberry.pipe.InputPort;
import com.milaboratory.cli.AppVersionInfo;
import com.milaboratory.cli.PipelineConfiguration;
import com.milaboratory.cli.PipelineConfigurationWriter;
import com.milaboratory.mixcr.assembler.CloneAssemblerParameters;
import com.milaboratory.mixcr.basictypes.tag.TagsInfo;
import com.milaboratory.mixcr.util.MiXCRVersionInfo;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import com.milaboratory.primitivio.PrimitivO;
import com.milaboratory.primitivio.blocks.PrimitivOHybrid;
import io.repseq.core.VDJCGene;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.List;

/**
 *
 */
public final class ClnsWriter implements PipelineConfigurationWriter, AutoCloseable {
    static final String MAGIC_V11 = "MiXCR.CLNS.V11";
    static final String MAGIC = MAGIC_V11;
    static final int MAGIC_LENGTH = 14;
    static final byte[] MAGIC_BYTES = MAGIC.getBytes(StandardCharsets.US_ASCII);

    final PrimitivOHybrid output;

    public ClnsWriter(String fileName) throws IOException {
        this(new PrimitivOHybrid(Paths.get(fileName)));
    }

    public ClnsWriter(File file) throws IOException {
        this(new PrimitivOHybrid(file.toPath()));
    }

    public ClnsWriter(PrimitivOHybrid output) {
        this.output = output;
    }

    public void writeHeaderFromCloneSet(
            PipelineConfiguration configuration,
            CloneSet cloneSet) {
        writeHeader(configuration,
                cloneSet.getAlignmentParameters(),
                cloneSet.getAssemblerParameters(),
                cloneSet.getTagsInfo(),
                cloneSet.getOrdering(),
                cloneSet.getUsedGenes(),
                cloneSet,
                cloneSet.size());
    }

    public void writeHeader(
            PipelineConfiguration configuration,
            VDJCAlignerParameters alignmentParameters,
            CloneAssemblerParameters assemblerParameters,
            TagsInfo tagsInfo,
            VDJCSProperties.CloneOrdering ordering,
            List<VDJCGene> genes,
            HasFeatureToAlign featureToAlign,
            int numberOfClones
    ) {
        try (PrimitivO o = output.beginPrimitivO(true)) {
            // Writing magic bytes
            o.write(MAGIC_BYTES);

            // Writing version information
            o.writeUTF(
                    MiXCRVersionInfo.get().getVersionString(
                            AppVersionInfo.OutputType.ToFile));

            // Writing analysis meta-information
            o.writeObject(configuration);
            o.writeObject(alignmentParameters);
            o.writeObject(assemblerParameters);
            o.writeObject(tagsInfo);
            o.writeObject(ordering);
            o.writeInt(numberOfClones);

            IOUtil.stdVDJCPrimitivOStateInit(o, genes, featureToAlign);
        }
    }

    /**
     * Must be closed by putting null
     */
    public InputPort<Clone> cloneWriter() {
        return output.beginPrimitivOBlocks(3, 512);
    }

    public void writeCloneSet(PipelineConfiguration configuration, CloneSet cloneSet) {
        writeHeaderFromCloneSet(configuration, cloneSet);
        InputPort<Clone> cloneIP = cloneWriter();
        for (Clone clone : cloneSet)
            cloneIP.put(clone);
        cloneIP.put(null);
    }

    @Override
    public void close() throws IOException {
        try (PrimitivO o = output.beginPrimitivO()) {
            // Writing end-magic as a file integrity sign
            o.write(IOUtil.getEndMagicBytes());
        }
        output.close();
    }
}
