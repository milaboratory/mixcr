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

import com.milaboratory.primitivio.PrimitivOState;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.xxhash.XXHash32;
import net.jpountz.xxhash.XXHashFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implements basic optionally-parallel block-based VDJCAlignments serialization and compression.
 */
public final class BasicVDJCAlignmentWriterFactory implements AutoCloseable {
    /**
     * Signal to the main thread form encoder about exceptional case
     */
    private volatile boolean error = false;

    /**
     * LZ4 compressor to compress data blocks
     */
    private final LZ4Compressor compressor;

    /**
     * LZ4 hash function
     */
    private final XXHash32 xxHash32 = XXHashFactory.fastestJavaInstance().hash32();

    /**
     * Encoder threads
     */
    private final List<Encoder> encoders;

    /**
     * Encoder threads
     */
    private final List<Writer> writers = new ArrayList<>();

    /**
     * "Exchanger" with encoder threads
     */
    private final SynchronousQueue<BlockToEncode> toEncoders = new SynchronousQueue<>();

    private volatile boolean closed = false;

    public BasicVDJCAlignmentWriterFactory(int encoderThreads) {
        this(encoderThreads, true);
    }

    public BasicVDJCAlignmentWriterFactory(int encoderThreads, boolean highCompression) {
        this.encoders = new ArrayList<>(encoderThreads);
        for (int i = 0; i < encoderThreads; i++) {
            Encoder e = new Encoder();
            e.start();
            encoders.add(e);
        }

        LZ4Factory lz4Factory = LZ4Factory.fastestJavaInstance();
        this.compressor = highCompression
                ? lz4Factory.highCompressor()
                : lz4Factory.fastCompressor();
    }

    public synchronized Writer createWriter(PrimitivOState outputState, OutputStream outputStream,
                                            boolean closeUnderlyingStream) {
        if (closed)
            throw new IllegalStateException();
        Writer writer = new Writer(outputState, outputStream, closeUnderlyingStream);
        writers.add(writer);
        return writer;
    }

    /**
     * Await all writers to be closed, terminate all encoder threads.
     */
    @Override
    public synchronized void close() {
        if (closed)
            return;

        try {
            for (Writer w : writers)
                w.closed.await();

            // Terminating Encoder threads
            boolean threadsAlive;
            do {
                for (int i = 0; i < encoders.size(); i++)
                    toEncoders.offer(new BlockToEncode(), 100, TimeUnit.MILLISECONDS);
                threadsAlive = false;
                for (Encoder encoder : encoders) {
                    encoder.join(100);
                    if (encoder.isAlive())
                        threadsAlive = true;
                }
            } while (threadsAlive);

            closed = true;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public boolean isClosed() {
        return closed;
    }

    public final class Writer implements AutoCloseable {
        /**
         * Buffers for synchronous write
         */
        private final AlignmentsIO.BlockBuffers buffers = new AlignmentsIO.BlockBuffers();

        /**
         * State to create PrimitivO streams form.
         */
        private final PrimitivOState outputState;

        /**
         * Raw underlying output stream
         */
        private final OutputStream outputStream;

        /**
         * Determines close behaviour
         */
        private final boolean closeUnderlyingStream;

        /**
         * Next encoder will await for this latch before writing content to the output stream
         */
        private volatile CountDownLatch lastBlockWriteLatch = new CountDownLatch(0); // Initialized with opened latch

        /**
         * Writer is closed latch
         */
        private volatile CountDownLatch closed = new CountDownLatch(1);

        private Writer(PrimitivOState outputState, OutputStream outputStream,
                       boolean closeUnderlyingStream) {
            this.outputState = outputState;
            this.outputStream = outputStream;
            this.closeUnderlyingStream = closeUnderlyingStream;
        }

        /**
         * Send write request to the pool of encoders.
         *
         * @param alignments list of alignments to be serialized
         */
        public synchronized void writeAsync(List<VDJCAlignments> alignments) {
            if (error)
                throw new RuntimeException("One of the encoders terminated with error.");

            try {
                BlockToEncode block = new BlockToEncode(alignments, lastBlockWriteLatch, outputState, outputStream);
                lastBlockWriteLatch = block.currentBlockWriteLatch;
                toEncoders.put(block);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        public void waitPreviousBlock() {
            try {
                lastBlockWriteLatch.await();
            } catch (InterruptedException e) {
                error = true;
                throw new RuntimeException(e);
            }
        }

        /**
         * Performs serialization and compression in current thread, synchronously.
         *
         * @param alignments list of alignments to be serialized
         */
        public synchronized void writeSync(List<VDJCAlignments> alignments) {
            if (error)
                throw new RuntimeException("One of the encoders terminated with error.");

            try {
                // Serialize + Compress
                AlignmentsIO.writeBlock(alignments, outputState, compressor, xxHash32, buffers);

                // Wait for previous async block if any
                waitPreviousBlock();

                // Write
                buffers.writeTo(outputStream);
            } catch (IOException e) {
                error = true;
                throw new RuntimeException(e);
            }
        }

        @Override
        public void close() {
            try {

                waitPreviousBlock();
                outputStream.write(AlignmentsIO.LAST_BYTE);

                if (closeUnderlyingStream)
                    outputStream.close();

                closed.countDown();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private final static class BlockToEncode {
        /**
         * Alignments to encode
         */
        final List<VDJCAlignments> content;

        /**
         * Will be opened when this thread completes write to the output stream
         */
        final CountDownLatch currentBlockWriteLatch = new CountDownLatch(1);
        /**
         * Encoder will await for this latch before writing content to the output stream
         */
        final CountDownLatch previousBlockWriteLatch;

        /**
         * State to create PrimitivO streams form.
         */
        final PrimitivOState outputState;

        /**
         * Raw underlying output stream
         */
        final OutputStream outputStream;

        /**
         * Construct end-signal block
         */
        BlockToEncode() {
            this(null, null, null, null);
        }

        public BlockToEncode(List<VDJCAlignments> content, CountDownLatch previousBlockWriteLatch,
                             PrimitivOState outputState, OutputStream outputStream) {
            if (content != null && content.size() == 0)
                throw new IllegalArgumentException("Zero length block.");
            this.content = content;
            this.previousBlockWriteLatch = previousBlockWriteLatch;
            this.outputState = outputState;
            this.outputStream = outputStream;
        }

        boolean isEndSignal() {
            return content == null;
        }
    }

    public int getEncodersCount() {
        return encoders.size();
    }

    public int getBusyEncoders() {
        return busyEncoders.get();
    }

    /**
     * Counter for stats
     */
    final AtomicInteger busyEncoders = new AtomicInteger(0);

    /**
     * To assign names for Encoder threads
     */
    final AtomicInteger encoderCounter = new AtomicInteger();

    private final class Encoder extends Thread {
        public Encoder() {
            super("AlignmentEncoder-" + encoderCounter.incrementAndGet());
        }

        @Override
        public void run() {
            // The same buffers will be used for all blocks processed by this thread
            AlignmentsIO.BlockBuffers buffers = new AlignmentsIO.BlockBuffers();

            try {
                while (true) {
                    BlockToEncode block = toEncoders.take();

                    // Is end signal
                    if (block.isEndSignal())
                        return;

                    // For stats
                    busyEncoders.incrementAndGet();

                    // CPU intensive task (serialize + compress)
                    AlignmentsIO.writeBlock(block.content, block.outputState, compressor, xxHash32, buffers);

                    // Awaiting previous block to be written to the stream
                    block.previousBlockWriteLatch.await();

                    // Writing the data (because of the latch mechanism only one encoder at a time will use the stream)
                    buffers.writeTo(block.outputStream);

                    // Allowing next block to be written
                    block.currentBlockWriteLatch.countDown();

                    // For stats
                    busyEncoders.decrementAndGet();
                }
            } catch (InterruptedException | IOException e) {
                // THe error will rise exception in main thread and initiate auto
                error = true;
                throw new RuntimeException(e);
            }
        }
    }
}
