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

import com.milaboratory.primitivio.PrimitivI;
import com.milaboratory.primitivio.PrimitivIState;
import com.milaboratory.primitivio.PrimitivO;
import com.milaboratory.primitivio.PrimitivOState;
import com.milaboratory.util.io.ByteArrayDataOutput;
import com.milaboratory.util.io.ByteBufferDataInputAdapter;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4FastDecompressor;
import net.jpountz.xxhash.XXHash32;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Utility methods for block-compressed alignment objects IO.
 *
 * Block:
 *
 * Header (17 bytes total):
 * [ 1 byte : bit0 = (0 = last block; 1 = data block); bit1 = (1 = compressed ; 0 = raw) ]
 * [ 4 bytes : int : number of alignments ]
 * [ 4 bytes : int : rawDataSize ]
 * [ 4 bytes : int : compressedDataSize ]
 * [ 4 bytes : int : checksum for the raw data ]
 *
 * Data:
 * [ dataSize bytes ] (compressed, if bit1 of header is 1; uncompressed, if bit1 is 0 )
 */
public final class AlignmentsIO {
    public static final int HASH_SEED = 0xD5D20F71;
    public static final byte LAST_BYTE = 0; // kB
    public static final int AVERAGE_ALIGNMENT_SIZE = 1024; // kB

    public static final int DEFAULT_ALIGNMENTS_IN_BLOCK = 1024; // 1024 alignments * 805-1024 bytes per alignment ~  824 kB - 1MB per block

    private AlignmentsIO() {
    }

    static final int BLOCK_HEADER_SIZE = 17;

    public static void writeIntBE(int val, byte[] buffer, int offset) {
        buffer[offset] = (byte) (val >>> 24);
        buffer[offset + 1] = (byte) (val >>> 16);
        buffer[offset + 2] = (byte) (val >>> 8);
        buffer[offset + 3] = (byte) val;
    }

    public static void writeLongBE(long val, byte[] buffer, int offset) {
        buffer[offset] = (byte) (val >>> 56);
        buffer[offset + 1] = (byte) (val >>> 48);
        buffer[offset + 2] = (byte) (val >>> 40);
        buffer[offset + 3] = (byte) (val >>> 32);
        buffer[offset + 4] = (byte) (val >>> 24);
        buffer[offset + 5] = (byte) (val >>> 16);
        buffer[offset + 6] = (byte) (val >>> 8);
        buffer[offset + 7] = (byte) val;
    }

    public static int readIntBE(byte[] buffer, int offset) {
        int value = 0;
        for (int i = 0; i < 4; ++i) {
            value <<= 8;
            value |= 0xFF & buffer[offset + i];
        }
        return value;
    }

    public static int readIntBE(ByteBuffer buffer) {
        int value = 0;
        for (int i = 0; i < 4; ++i) {
            value <<= 8;
            value |= 0xFF & buffer.get();
        }
        return value;
    }

    public static long readLongBE(byte[] buffer, int offset) {
        long value = 0;
        for (int i = 0; i < 8; ++i) {
            value <<= 8;
            value |= 0xFF & buffer[offset++];
        }
        return value;
    }

    public static long readLongBE(ByteBuffer buffer) {
        long value = 0;
        for (int i = 0; i < 8; ++i) {
            value <<= 8;
            value |= 0xFF & buffer.get();
        }
        return value;
    }

    /**
     * Output date can be written to the output stream with buffers.writeTo(...) .
     */
    public static void writeBlock(Collection<VDJCAlignments> alignments, PrimitivOState outputState,
                                  LZ4Compressor compressor, XXHash32 xxHash32, BlockBuffers buffers) {
        // Assert
        assert alignments.size() != 0;
        boolean assertOn = false;
        assert assertOn = true;
        //noinspection ConstantConditions
        if (!assertOn && alignments.size() == 0)
            System.err.println("Writing empty block in AlignmentsIO.");

        buffers.ensureRawBufferSize(alignments.size() * AVERAGE_ALIGNMENT_SIZE);
        ByteArrayDataOutput dataOutput = new ByteArrayDataOutput(buffers.rawBuffer);

        PrimitivO output = outputState.createPrimitivO(dataOutput);

        // Writing alignments to memory buffer
        for (VDJCAlignments al : alignments)
            output.writeObject(al);

        int checksum = xxHash32.hash(dataOutput.getBuffer(), 0, dataOutput.size(), HASH_SEED);

        buffers.ensureCompressedBufferSize(compressor.maxCompressedLength(dataOutput.size()) + BLOCK_HEADER_SIZE);

        byte[] block = buffers.compressedBuffer;
        int compressedLength = compressor.compress(dataOutput.getBuffer(), 0, dataOutput.size(),
                block, BLOCK_HEADER_SIZE);

        writeIntBE(alignments.size(), block, 1);

        if (compressedLength > dataOutput.size()) {
            // Compression increased data size, writing raw block
            System.arraycopy(dataOutput.getBuffer(), 0, block, BLOCK_HEADER_SIZE, dataOutput.size());
            block[0] = 0x1; // bit0 = 1, bit0 = 0
            writeIntBE(dataOutput.size(), block, 5);
            writeIntBE(dataOutput.size(), block, 9);
            buffers.compressedSize = BLOCK_HEADER_SIZE + dataOutput.size();
        } else {
            block[0] = 0x3; // bit0 = 1, bit0 = 1
            writeIntBE(dataOutput.size(), block, 5);
            writeIntBE(compressedLength, block, 9);
            buffers.compressedSize = BLOCK_HEADER_SIZE + compressedLength;
        }

        // Writing checksum
        writeIntBE(checksum, block, 13);

        // Saving raw buffer if it was further enlarged by internal ByteArrayDataOutput logic
        buffers.rawBuffer = dataOutput.getBuffer();
    }

    public static final class InputStreamBufferReader implements BufferReader {
        final DataInputStream dis;

        public InputStreamBufferReader(InputStream stream) {
            this.dis = new DataInputStream(stream);
        }

        @Override
        public void readFully(byte[] b) throws IOException {
            dis.readFully(b);
        }

        @Override
        public void readFully(byte[] b, int off, int len) throws IOException {
            dis.readFully(b, off, len);
        }

        @Override
        public byte readByte() throws IOException {
            return dis.readByte();
        }
    }

    public static final class FileChannelBufferReader implements BufferReader {
        private final FileChannel fileChannel;
        private long position;
        private long to;

        public FileChannelBufferReader(FileChannel fileChannel, long position) {
            this(fileChannel, position, -1);

        }

        public FileChannelBufferReader(FileChannel fileChannel, long position, long to) {
            this.fileChannel = fileChannel;
            this.position = position;
            this.to = to;
        }

        @Override
        public void readFully(byte[] b) throws IOException {
            if (position + b.length > to)
                throw new IOException("No more bytes. Stream limit reached. This is a sign of malformed input file.");
            fileChannel.read(ByteBuffer.wrap(b), position);
            position += b.length;
        }

        @Override
        public void readFully(byte[] b, int off, int len) throws IOException {
            if (position + len > to)
                throw new IOException("No more bytes. Stream limit reached. This is a sign of malformed input file.");
            fileChannel.read(ByteBuffer.wrap(b, off, len), position);
            position += len;
        }

        @Override
        public byte readByte() throws IOException {
            if (position + 1 > to)
                throw new IOException("No more bytes. Stream limit reached. This is a sign of malformed input file.");
            byte[] b = new byte[1];
            fileChannel.read(ByteBuffer.wrap(b), position);
            position += 1;
            return b[0];
        }
    }

    public static List<VDJCAlignments> readBlock(InputStream stream, PrimitivIState inputState,
                                                 LZ4FastDecompressor decompressor, XXHash32 xxHash32,
                                                 BlockBuffers buffers) throws IOException {

        return readBlock(new InputStreamBufferReader(stream), inputState, decompressor, xxHash32, buffers);
    }

    public static List<VDJCAlignments> readBlock(BufferReader reader, PrimitivIState inputState,
                                                 LZ4FastDecompressor decompressor, XXHash32 xxHash32,
                                                 BlockBuffers buffers) throws IOException {
        // Reading header

        // First byte
        byte h0 = reader.readByte();
        if (h0 == 0)
            return null;

        byte[] header1 = new byte[BLOCK_HEADER_SIZE - 1];
        reader.readFully(header1);
        int numberOfAlignments = readIntBE(header1, 0);
        int rawSize = readIntBE(header1, 4);
        int compressedSize = readIntBE(header1, 8);
        int checksum = readIntBE(header1, 12);

        if ((h0 & 2) == 0) { // Not compressed block
            buffers.ensureRawBufferSize(rawSize);
            assert rawSize == compressedSize;
            reader.readFully(buffers.rawBuffer, 0, rawSize);
        } else { // Compressed
            buffers.ensureRawBufferSize(rawSize);
            buffers.ensureCompressedBufferSize(compressedSize);
            reader.readFully(buffers.compressedBuffer, 0, compressedSize);
            decompressor.decompress(buffers.compressedBuffer, 0,
                    buffers.rawBuffer, 0, rawSize);
        }

        if (checksum != xxHash32.hash(buffers.rawBuffer, 0, rawSize, HASH_SEED))
            throw new RuntimeException("Checksum verification failed. Malformed file.");

        if (numberOfAlignments == 0)
            throw new IllegalArgumentException("Zero-alignments block.");

        PrimitivI input = inputState.createPrimitivI(new ByteBufferDataInputAdapter(
                ByteBuffer.wrap(buffers.rawBuffer, 0, rawSize)));

        ArrayList<VDJCAlignments> alignments = new ArrayList<>(numberOfAlignments);
        for (int i = 0; i < numberOfAlignments; i++)
            alignments.add(input.readObject(VDJCAlignments.class));

        return alignments;
    }

    public interface BufferReader {
        default void readFully(byte b[]) throws IOException {
            readFully(b, 0, b.length);
        }

        void readFully(byte b[], int off, int len) throws IOException;

        byte readByte() throws IOException;
    }

    public static final class BlockBuffers {
        public byte[] rawBuffer, compressedBuffer;
        /**
         * HEADER + data
         */
        public int compressedSize;

        public void ensureRawBufferSize(int size) {
            if (rawBuffer == null || rawBuffer.length < size)
                rawBuffer = new byte[size];
        }

        public void ensureCompressedBufferSize(int size) {
            if (compressedBuffer == null || compressedBuffer.length < size)
                compressedBuffer = new byte[size];
        }

        public void writeTo(OutputStream stream) throws IOException {
            stream.write(compressedBuffer, 0, compressedSize);
        }
    }
}

