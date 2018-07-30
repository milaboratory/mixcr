/*
 * Copyright (c) 2014-2018, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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

import com.milaboratory.mixcr.util.MiXCRVersionInfo;
import com.milaboratory.mixcr.vdjaligners.VDJCAligner;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import com.milaboratory.primitivio.PrimitivO;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCGene;
import net.jpountz.lz4.LZ4BlockOutputStream;
import net.jpountz.lz4.LZ4Factory;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class VDJCAlignmentsWriter implements VDJCAlignmentsWriterI {
    static final int COMPRESSION_BLOCK_SIZE = 1048576; // 1MB
    static final String MAGIC_V12 = "MiXCR.VDJC.V12";
    static final String MAGIC = MAGIC_V12;
    static final int MAGIC_LENGTH = 14;
    static final byte[] MAGIC_BYTES = MAGIC.getBytes(StandardCharsets.US_ASCII);
    final OutputStream rawOutput;
    PrimitivO mainOutput = null;
    long numberOfProcessedReads = -1;
    boolean header = false, closed = false;

    public VDJCAlignmentsWriter(String fileName) throws IOException {
        this(new File(fileName));
    }

    public VDJCAlignmentsWriter(File file) throws IOException {
        this(IOUtil.createOS(file));
    }

    public VDJCAlignmentsWriter(OutputStream output) {
        this.rawOutput = output;
    }

    @Override
    public void setNumberOfProcessedReads(long numberOfProcessedReads) {
        this.numberOfProcessedReads = numberOfProcessedReads;
    }

    public void header(VDJCAlignmentsReader reader, PipelineConfiguration pipelineConfiguration) {
        header(reader.getParameters(), reader.getUsedGenes(), pipelineConfiguration);
    }

    public void header(VDJCAligner aligner, PipelineConfiguration pipelineConfiguration) {
        header(aligner.getParameters(), aligner.getUsedGenes(), pipelineConfiguration);
    }

    /** History to write in the header */
    private PipelineConfiguration pipelineConfiguration = null;

    public synchronized void setPipelineConfiguration(PipelineConfiguration configuration) {
        if (pipelineConfiguration == null)
            pipelineConfiguration = configuration;
        else if (!configuration.equals(this.pipelineConfiguration))
            throw new IllegalStateException();
    }

    @Override
    public void header(VDJCAlignerParameters parameters, List<VDJCGene> genes,
                       PipelineConfiguration ppConfiguration) {
        if (parameters == null || genes == null)
            throw new IllegalArgumentException();

        if (header)
            throw new IllegalStateException("Header already written.");

        PrimitivO outputNotCompressed = new PrimitivO(rawOutput);

        // Writing meta data using raw stream for easy reconstruction with simple tools like hex viewers

        // Writing magic bytes
        assert MAGIC_BYTES.length == MAGIC_LENGTH;
        outputNotCompressed.write(MAGIC_BYTES);

        // Writing version information
        outputNotCompressed.writeUTF(
                MiXCRVersionInfo.get().getVersionString(
                        MiXCRVersionInfo.OutputType.ToFile));

        // Writing parameters
        outputNotCompressed.writeObject(parameters);

        // Writing history
        if (ppConfiguration != null)
            this.pipelineConfiguration = ppConfiguration;
        outputNotCompressed.writeObject(pipelineConfiguration);

        // Initialization of main stream, compressed with LZ4 algorithm

        mainOutput = new PrimitivO(new LZ4BlockOutputStream(rawOutput, COMPRESSION_BLOCK_SIZE,
                LZ4Factory.fastestInstance().fastCompressor()));

        IOUtil.writeAndRegisterGeneReferences(mainOutput, genes, parameters);

        // Registering links to features to align
        for (GeneType gt : GeneType.VDJC_REFERENCE) {
            GeneFeature feature = parameters.getFeatureToAlign(gt);
            mainOutput.writeObject(feature);
            if (feature != null)
                mainOutput.putKnownObject(feature);
        }

        header = true;
    }

    @Override
    public void write(VDJCAlignments alignment) {
        if (!header)
            throw new IllegalStateException();

        if (alignment == null)
            throw new NullPointerException();

        mainOutput.writeObject(alignment);
    }

    @Override
    public void close() {
        if (!closed) {
            // Sign of stream termination
            mainOutput.writeObject(null);
            // Number of processed reads is known only in the end of analysis
            // Writing it as last piece of information in the stream
            mainOutput.writeLong(numberOfProcessedReads);
            // This will also finish LZ4 stream
            mainOutput.close();
            closed = true;
        }
    }
}
