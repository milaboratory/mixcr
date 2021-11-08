/*
 * Copyright (c) 2014-2019, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
 * (here and after addressed as Inventors)
 * All Rights Reserved
 *
 * Permission to use, copy, modify and distribute any part of this program for
 * educational, research and non-profit purposes, by non-profit institutions
 * only, without fee, and without a written agreement is hereby granted,
 * provided that the above copyright notice, this paragraph and the following
 * three paragraphs appear in all copies.
 *
 * Those desiring to incorporate this work into commercial products or use for
 * commercial purposes should contact MiLaboratory LLC, which owns exclusive
 * rights for distribution of this program for commercial purposes, using the
 * following email address: licensing@milaboratory.com.
 *
 * IN NO EVENT SHALL THE INVENTORS BE LIABLE TO ANY PARTY FOR DIRECT, INDIRECT,
 * SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING LOST PROFITS,
 * ARISING OUT OF THE USE OF THIS SOFTWARE, EVEN IF THE INVENTORS HAS BEEN
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * THE SOFTWARE PROVIDED HEREIN IS ON AN "AS IS" BASIS, AND THE INVENTORS HAS
 * NO OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR
 * MODIFICATIONS. THE INVENTORS MAKES NO REPRESENTATIONS AND EXTENDS NO
 * WARRANTIES OF ANY KIND, EITHER IMPLIED OR EXPRESS, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY OR FITNESS FOR A
 * PARTICULAR PURPOSE, OR THAT THE USE OF THE SOFTWARE WILL NOT INFRINGE ANY
 * PATENT, TRADEMARK OR OTHER RIGHTS.
 */
package com.milaboratory.mixcr.basictypes;

import cc.redberry.pipe.InputPort;
import com.milaboratory.cli.AppVersionInfo;
import com.milaboratory.cli.PipelineConfiguration;
import com.milaboratory.cli.PipelineConfigurationWriter;
import com.milaboratory.mixcr.assembler.CloneAssemblerParameters;
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
    static final String MAGIC_V10 = "MiXCR.CLNS.V10";
    static final String MAGIC = MAGIC_V10;
    static final int MAGIC_LENGTH = 14;
    static final byte[] MAGIC_BYTES = MAGIC.getBytes(StandardCharsets.US_ASCII);

    final String stage = "Writing clones";
    final PrimitivOHybrid output;

    private volatile int current;

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
                cloneSet.getOrdering(),
                cloneSet.getUsedGenes(),
                cloneSet);
    }

    public void writeHeader(
            PipelineConfiguration configuration,
            VDJCAlignerParameters alignmentParameters,
            CloneAssemblerParameters assemblerParameters,
            VDJCSProperties.CloneOrdering ordering,
            List<VDJCGene> genes,
            HasFeatureToAlign featureToAlign
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
            o.writeObject(ordering);

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
