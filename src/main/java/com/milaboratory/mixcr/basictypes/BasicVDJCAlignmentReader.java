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
