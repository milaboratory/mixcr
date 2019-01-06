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

import cc.redberry.pipe.OutputPort;
import com.milaboratory.primitivio.PrimitivIState;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;
import net.jpountz.xxhash.XXHash32;
import net.jpountz.xxhash.XXHashFactory;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class implements basic single-thread decoding of block-compressed VDJCAlignments stream
 */
public final class BasicVDJCAlignmentReader implements OutputPort<VDJCAlignments> {
    // Make static?
    private final LZ4FastDecompressor decompressor = LZ4Factory.fastestJavaInstance().fastDecompressor();
    // Make static?
    private final XXHash32 xxHash32 = XXHashFactory.fastestInstance().hash32();

    /**
     * IO buffers
     */
    private final AlignmentsIO.BlockBuffers buffers = new AlignmentsIO.BlockBuffers();
    /**
     * Underlying stream
     */
    private final AlignmentsIO.BufferReader reader;
    /**
     * PrimitivI stream to create object streams from
     */
    private final PrimitivIState inputState;
    /**
     * Block content
     */
    private Iterator<VDJCAlignments> alignmentsIterator = null;
    /**
     * "Exchanger" with decoder threads
     */
    private final BlockingQueue<Block> fromDecoder;
    /**
     * Decoder thread
     */
    private final DecoderThread decoderThread;

    private boolean closed = false;

    public BasicVDJCAlignmentReader(AlignmentsIO.BufferReader reader, PrimitivIState inputState) {
        this(reader, inputState, false);
    }

    public BasicVDJCAlignmentReader(AlignmentsIO.BufferReader reader, PrimitivIState inputState, boolean separateDecoderThread) {
        this.reader = reader;
        this.inputState = inputState;
        if (separateDecoderThread) {
            this.fromDecoder = new ArrayBlockingQueue<>(2);
            this.decoderThread = new DecoderThread();
            this.decoderThread.start();
        } else {
            this.fromDecoder = null;
            this.decoderThread = null;
        }
    }

    @Override
    public synchronized VDJCAlignments take() {
        if (closed)
            return null;
        try {
            if (alignmentsIterator == null || !alignmentsIterator.hasNext()) {
                // Reloading buffer
                List<VDJCAlignments> als;
                if (fromDecoder == null) {
                    als = AlignmentsIO.readBlock(reader, inputState, decompressor, xxHash32, buffers);
                } else {
                    try {
                        Block take = fromDecoder.take();
                        if (take.error)
                            throw new RuntimeException("Decoder thread died with error.");
                        als = take.alignments;
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }

                if (als == null) {
                    closed = true;
                    return null;
                } else {
                    alignmentsIterator = als.iterator();
                    assert alignmentsIterator.hasNext();
                }
            }

            return alignmentsIterator.next();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public int getQueueSize() {
        if (fromDecoder == null)
            return -1;
        return fromDecoder.size();
    }

    private static final AtomicInteger readerThreadCounter = new AtomicInteger(0);

    private static final class Block {
        final List<VDJCAlignments> alignments;
        final boolean error;

        Block(List<VDJCAlignments> alignments, boolean error) {
            this.alignments = alignments;
            this.error = error;
        }
    }

    private final class DecoderThread extends Thread {
        DecoderThread() {
            super("BasicVDJCAlignmentReaderThread-" + readerThreadCounter.getAndIncrement());
        }

        @Override
        public void run() {
            try {
                try {
                    List<VDJCAlignments> vdjcAlignments;
                    do {
                        // Reading blocks
                        vdjcAlignments = AlignmentsIO.readBlock(reader, inputState, decompressor, xxHash32, buffers);
                        // And piping them to the queue
                        fromDecoder.put(new Block(vdjcAlignments, false));
                    } while (vdjcAlignments != null);
                    // End of stream sign
                    fromDecoder.put(new Block(null, false));
                } catch (IOException e) {
                    // Signal main thread about exception
                    fromDecoder.put(new Block(null, true));
                    throw new RuntimeException(e);
                }
            } catch (InterruptedException e1) {
                throw new RuntimeException(e1);
            }
        }
    }
}
