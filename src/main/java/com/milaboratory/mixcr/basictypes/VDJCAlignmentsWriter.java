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
import com.milaboratory.primitivio.PrimitivOState;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCGene;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class VDJCAlignmentsWriter implements VDJCAlignmentsWriterI {
    static final int DEFAULT_ALIGNMENTS_IN_BLOCK = 1024; // 1024 alignments * 805-1024 bytes per alignment ~  824 kB - 1MB per block
    static final String MAGIC_V12 = "MiXCR.VDJC.V12";
    static final String MAGIC = MAGIC_V12;
    static final int MAGIC_LENGTH = 14;
    static final byte[] MAGIC_BYTES = MAGIC.getBytes(StandardCharsets.US_ASCII);

    /**
     * Raw underlying output stream
     */
    final OutputStream rawOutput;

    /**
     * LZ4 compressor to compress data blocks
     */
    final LZ4Compressor compressor = LZ4Factory.fastestInstance().highCompressor();

    /**
     * Buffer for accumulation of alignments written with write(VDJCAlignments) method. Buffer is flushed if
     * number of accumulated alignments is DEFAULT_ALIGNMENTS_IN_BLOCK or close() method was invoked.
     */
    final ArrayList<VDJCAlignments> buffer = new ArrayList<>(DEFAULT_ALIGNMENTS_IN_BLOCK);

    /**
     * This number will be added to the end of the file to report number of processed read to the following processing
     * steps. Mainly for informative statistics reporting.
     */
    long numberOfProcessedReads = -1;

    /**
     * State to create PrimitivO streams form.
     */
    PrimitivOState mainOutputState = null;

    boolean closed = false;

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

        if (mainOutputState != null)
            throw new IllegalStateException("Header already written.");

        PrimitivO output = new PrimitivO(rawOutput);

        // Writing meta data using raw stream for easy reconstruction with simple tools like hex viewers

        // Writing magic bytes
        assert MAGIC_BYTES.length == MAGIC_LENGTH;
        output.write(MAGIC_BYTES);

        // Writing version information
        output.writeUTF(
                MiXCRVersionInfo.get().getVersionString(
                        MiXCRVersionInfo.OutputType.ToFile));

        // Writing parameters
        output.writeObject(parameters);

        // Writing history
        if (ppConfiguration != null)
            this.pipelineConfiguration = ppConfiguration;
        output.writeObject(pipelineConfiguration);

        IOUtil.writeAndRegisterGeneReferences(output, genes, parameters);

        // Registering links to features to align
        for (GeneType gt : GeneType.VDJC_REFERENCE) {
            GeneFeature feature = parameters.getFeatureToAlign(gt);
            output.writeObject(feature);
            if (feature != null)
                output.putKnownObject(feature);
        }

        // Saving output state
        mainOutputState = output.getState();
    }

    @Override
    public synchronized void write(VDJCAlignments alignment) {
        try {
            if (mainOutputState == null)
                throw new IllegalStateException("Header not initialized.");

            if (alignment == null)
                throw new NullPointerException();

            buffer.add(alignment);

            if (buffer.size() == DEFAULT_ALIGNMENTS_IN_BLOCK) {

                // TODO more efficient buffer manipulation required
                AlignmentsIO.BlockBuffers bufs = new AlignmentsIO.BlockBuffers();
                AlignmentsIO.writeBlock(buffer, mainOutputState, compressor, bufs);

                bufs.writeTo(rawOutput);

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized void write(Collection<VDJCAlignments> alignments) {

    }

    @Override
    public void close() {
        try {
            if (!closed) {
                // [ 0 : byte ] + [ numberOfProcessedReads : long ]
                byte[] footer = new byte[9];
                // Sign of stream termination
                footer[0] = 0;
                // Number of processed reads is known only in the end of analysis
                // Writing it as last piece of information in the stream
                AlignmentsIO.writeLongBE(numberOfProcessedReads, footer, 1);
                rawOutput.write(footer);
                rawOutput.close();
                closed = true;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
