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

import com.milaboratory.cli.PipelineConfiguration;
import com.milaboratory.mixcr.util.MiXCRVersionInfo;
import com.milaboratory.mixcr.vdjaligners.VDJCAligner;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import com.milaboratory.primitivio.PrimitivO;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCGene;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static com.milaboratory.mixcr.basictypes.AlignmentsIO.DEFAULT_ALIGNMENTS_IN_BLOCK;

public final class VDJCAlignmentsWriter implements VDJCAlignmentsWriterI {
    public static final int DEFAULT_ENCODER_THREADS = 3;
    static final String MAGIC_V13 = "MiXCR.VDJC.V13";
    static final String MAGIC = MAGIC_V13;
    static final int MAGIC_LENGTH = 14;
    static final byte[] MAGIC_BYTES = MAGIC.getBytes(StandardCharsets.US_ASCII);

    /**
     * Buffer for accumulation of alignments written with write(VDJCAlignments) method. Buffer is flushed if
     * number of accumulated alignments is alignmentsInBlock or close() method was invoked.
     */
    volatile ArrayList<VDJCAlignments> currentBuffer;

    /**
     * Number of alignments in block. Larger number allows for better compression while consume more memory.
     */
    final int alignmentsInBlock;

    /**
     * Raw underlying output stream
     */
    final OutputStream rawOutput;

    /**
     * This number will be added to the end of the file to report number of processed read to the following processing
     * steps. Mainly for informative statistics reporting.
     */
    long numberOfProcessedReads = -1;

    /**
     * Pool of encoders
     */
    final BasicVDJCAlignmentWriterFactory writerFactory;

    /**
     * Initialized after header, implements all internal encoding logic.
     */
    volatile BasicVDJCAlignmentWriterFactory.Writer writer = null;

    boolean closed = false;

    public VDJCAlignmentsWriter(String fileName) throws IOException {
        this(fileName, DEFAULT_ENCODER_THREADS, DEFAULT_ALIGNMENTS_IN_BLOCK);
    }

    public VDJCAlignmentsWriter(String fileName, int encoderThreads, int alignmentsInBlock) throws IOException {
        this(new File(fileName), encoderThreads, alignmentsInBlock);
    }

    public VDJCAlignmentsWriter(File file) throws IOException {
        this(file, DEFAULT_ENCODER_THREADS, DEFAULT_ALIGNMENTS_IN_BLOCK);
    }

    public VDJCAlignmentsWriter(File file, int encoderThreads, int alignmentsInBlock) throws IOException {
        this(IOUtil.createOS(file), encoderThreads, alignmentsInBlock);
    }

    public VDJCAlignmentsWriter(OutputStream output) {
        this(output, DEFAULT_ENCODER_THREADS, DEFAULT_ALIGNMENTS_IN_BLOCK);
    }

    public VDJCAlignmentsWriter(OutputStream output, int encoderThreads, int alignmentsInBlock) {
        this.rawOutput = output;
        this.alignmentsInBlock = alignmentsInBlock;
        this.currentBuffer = new ArrayList<>(alignmentsInBlock);
        this.writerFactory = new BasicVDJCAlignmentWriterFactory(encoderThreads);
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

        if (writer != null)
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
        writer = writerFactory.createWriter(output.getState(), rawOutput, false);
    }

    public int getEncodersCount() {
        return writerFactory.getEncodersCount();
    }

    public int getBusyEncoders() {
        return writerFactory.getBusyEncoders();
    }

    @Override
    public synchronized void write(VDJCAlignments alignment) {
        if (writer == null)
            throw new IllegalStateException("Header not initialized.");

        if (alignment == null)
            throw new NullPointerException();

        currentBuffer.add(alignment);

        if (currentBuffer.size() == alignmentsInBlock)
            flushBlock();
    }

    /**
     * Flush alignment buffer
     */
    private void flushBlock() {
        if (currentBuffer.isEmpty())
            return;

        // Enqueue block for async encoding and compression
        writer.writeAsync(currentBuffer);
        currentBuffer = new ArrayList<>(alignmentsInBlock);
    }

    @Override
    public synchronized void close() {
        try {
            if (!closed) {
                flushBlock();

                writer.close(); // This will also write stream termination symbol/block to the stream
                writerFactory.close(); // This blocks the thread until all workers flush their data to the underlying stream

                // [ numberOfProcessedReads : long ]
                byte[] footer = new byte[8];
                // Number of processed reads is known only in the end of analysis
                // Writing it as last piece of information in the stream
                AlignmentsIO.writeLongBE(numberOfProcessedReads, footer, 0);
                rawOutput.write(footer);

                // Writing end-magic as a file integrity sign
                rawOutput.write(IOUtil.getEndMagicBytes());

                rawOutput.close();
                closed = true;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isClosed() {
        return closed;
    }
}
