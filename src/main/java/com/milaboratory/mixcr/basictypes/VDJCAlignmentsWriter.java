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
import net.jpountz.xxhash.XXHash32;
import net.jpountz.xxhash.XXHashFactory;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.SynchronousQueue;

public final class VDJCAlignmentsWriter implements VDJCAlignmentsWriterI {
    static final int DEFAULT_ALIGNMENTS_IN_BLOCK = 1024; // 1024 alignments * 805-1024 bytes per alignment ~  824 kB - 1MB per block
    static final int DEFAULT_ENCODER_THREADS = 3;
    static final String MAGIC_V12 = "MiXCR.VDJC.V12";
    static final String MAGIC = MAGIC_V12;
    static final int MAGIC_LENGTH = 14;
    static final byte[] MAGIC_BYTES = MAGIC.getBytes(StandardCharsets.US_ASCII);

    /**
     * Signal to the main thread form encoder about exceptional case
     */
    volatile boolean error = false;

    /**
     * Buffer for accumulation of alignments written with write(VDJCAlignments) method. Buffer is flushed if
     * number of accumulated alignments is alignmentsInBlock or close() method was invoked.
     */
    volatile ArrayList<VDJCAlignments> currentBuffer;

    /**
     * Next encoder will await for this latch before writing content to the output stream
     */
    volatile CountDownLatch lastBlockWriteLatch = new CountDownLatch(0); // Initialized with opened latch

    /**
     * Encoder threads
     */
    final List<Encoder> encoders;

    /**
     * "Exchanger" with encoder threads
     */
    final SynchronousQueue<BlockToEncode> toEncoders = new SynchronousQueue<>();

    /**
     * Number of alignments in block. Larger number allows for better compression while consume more memory.
     */
    final int alignmentsInBlock;

    /**
     * Raw underlying output stream
     */
    final OutputStream rawOutput;

    /**
     * LZ4 compressor to compress data blocks
     */
    final LZ4Compressor compressor = LZ4Factory.fastestInstance().highCompressor();

    /**
     * LZ4 hash function
     */
    final XXHash32 xxHash32 = XXHashFactory.fastestInstance().hash32();

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
        this(output, DEFAULT_ENCODER_THREADS, DEFAULT_ALIGNMENTS_IN_BLOCK);
    }

    public VDJCAlignmentsWriter(OutputStream output, int encoderThreads, int alignmentsInBlock) {
        this.rawOutput = output;
        this.alignmentsInBlock = alignmentsInBlock;
        this.encoders = new ArrayList<>(encoderThreads);
        for (int i = 0; i < encoderThreads; i++) {
            Encoder e = new Encoder();
            e.start();
            encoders.add(e);
        }
        this.currentBuffer = new ArrayList<>(alignmentsInBlock);
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
        if (mainOutputState == null)
            throw new IllegalStateException("Header not initialized.");

        if (error)
            throw new RuntimeException("One of the encoders terminated with error.");

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
        if (error)
            throw new RuntimeException("One of the encoders terminated with error.");

        if (currentBuffer.isEmpty())
            return;

        try {
            BlockToEncode block = new BlockToEncode(currentBuffer, lastBlockWriteLatch);
            lastBlockWriteLatch = block.currentBlockWriteLatch;
            toEncoders.put(block);
            currentBuffer = new ArrayList<>(alignmentsInBlock);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public synchronized void close() {
        try {
            if (!closed) {
                flushBlock();
                // Terminating Encoder threads
                for (int i = 0; i < encoders.size(); i++)
                    toEncoders.put(new BlockToEncode());
                for (Encoder encoder : encoders)
                    encoder.join();

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
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private final static class BlockToEncode {
        /**
         * Will be opened when this thread completes write to the output stream
         */
        final CountDownLatch currentBlockWriteLatch = new CountDownLatch(1);
        /**
         * Encoder will await for this latch before writing content to the output stream
         */
        final CountDownLatch previousBlockWriteLatch;
        /**
         * Alignments to encode
         */
        final List<VDJCAlignments> content;

        /**
         * Construct end-signal block
         */
        BlockToEncode() {
            this(null, null);
        }

        BlockToEncode(List<VDJCAlignments> content,
                      CountDownLatch previousBlockWriteLatch) {
            this.content = content;
            this.previousBlockWriteLatch = previousBlockWriteLatch;
        }

        boolean isEndSignal() {
            return content == null;
        }
    }

    private final class Encoder extends Thread {
        @Override
        public void run() {
            // The same buffers will be used for all blocks processed by this thread
            AlignmentsIO.BlockBuffers bufs = new AlignmentsIO.BlockBuffers();

            try {
                while (true) {
                    BlockToEncode block = toEncoders.take();

                    // Is end signal
                    if (block.isEndSignal())
                        return;

                    // CPU intensive task (serialize + compress)
                    AlignmentsIO.writeBlock(block.content, mainOutputState, compressor, xxHash32, bufs);

                    // Awaiting previous block to be written to the stream
                    block.previousBlockWriteLatch.await();

                    // Writing the data (because of the latch mechanism only one encoder at a time will use the stream
                    bufs.writeTo(rawOutput);

                    // Allowing next block to be written
                    block.currentBlockWriteLatch.countDown();
                }
            } catch (InterruptedException | IOException e) {
                error = true;
                throw new RuntimeException(e);
            }
        }
    }
}
