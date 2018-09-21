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

import cc.redberry.pipe.OutputPortCloseable;
import com.milaboratory.primitivio.PrimitivIState;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;
import net.jpountz.xxhash.XXHash32;
import net.jpountz.xxhash.XXHashFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;

/**
 * This class implements basic single-thread decoding of block-compressed VDJCAlignments stream
 */
public final class BasicVDJCAlignmentReader implements OutputPortCloseable<VDJCAlignments> {
    // Make static?
    final LZ4FastDecompressor decompressor = LZ4Factory.fastestJavaInstance().fastDecompressor();
    // Make static?
    final XXHash32 xxHash32 = XXHashFactory.fastestInstance().hash32();

    /**
     * IO buffers
     */
    final AlignmentsIO.BlockBuffers buffers = new AlignmentsIO.BlockBuffers();
    /**
     * Underlying stream
     */
    final InputStream inputStream;
    /**
     * PrimitivI stream to create object streams from
     */
    final PrimitivIState inputState;
    /**
     * If true stream will be closed on this object close
     */
    final boolean closeUnderlyingStream;
    /**
     * Block content
     */
    Iterator<VDJCAlignments> alignmentsIterator = null;

    public BasicVDJCAlignmentReader(InputStream inputStream, PrimitivIState inputState) {
        this(inputStream, inputState, true);
    }

    public BasicVDJCAlignmentReader(InputStream inputStream, PrimitivIState inputState, boolean closeUnderlyingStream) {
        this.inputStream = inputStream;
        this.inputState = inputState;
        this.closeUnderlyingStream = closeUnderlyingStream;
    }

    @Override
    public synchronized VDJCAlignments take() {
        try {
            if (alignmentsIterator == null || !alignmentsIterator.hasNext()) {
                // Reloading buffers
                List<VDJCAlignments> als = AlignmentsIO.readBlock(inputStream, inputState, decompressor, xxHash32, buffers);

                if (als == null) {
                    close();
                    return null;
                } else
                    alignmentsIterator = als.iterator();
            }

            assert alignmentsIterator.hasNext();

            return alignmentsIterator.next();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public synchronized void close() {
        if (closeUnderlyingStream) {
            try {
                inputStream.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
