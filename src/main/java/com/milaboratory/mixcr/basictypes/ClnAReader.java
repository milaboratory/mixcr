/*
 * Copyright (c) 2014-2017, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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
 * commercial purposes should contact the Inventors using one of the following
 * email addresses: chudakovdm@mail.ru, chudakovdm@gmail.com
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
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import com.milaboratory.primitivio.PipeDataInputReader;
import com.milaboratory.primitivio.PrimitivI;
import com.milaboratory.util.CanReportProgress;
import io.repseq.core.GeneFeature;
import io.repseq.core.VDJCGene;
import io.repseq.core.VDJCLibraryRegistry;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Reader of CLNA file format.
 */
public final class ClnAReader implements AutoCloseable {
    public static final int DEFAULT_CHUNK_SIZE = 262144;
    final int chunkSize;
    /**
     * Input file channel. All interaction with file are made through this object.
     */
    final FileChannel channel;

    // Index data

    final long firstClonePosition;
    final long[] index;
    final long[] counts;
    final long totalAlignmentsCount;

    // From constructor

    final VDJCLibraryRegistry libraryRegistry;

    // Read form file header

    final VDJCAlignerParameters alignerParameters;
    final GeneFeature[] assemblingFeatures;
    final CloneSetIO.GT2GFAdapter alignedFeatures;
    final List<VDJCGene> genes;
    final int numberOfClones;

    // Meta data

    final String versionInfo;

    public ClnAReader(Path path, VDJCLibraryRegistry libraryRegistry, int chunkSize) throws IOException {
        this.chunkSize = chunkSize;
        this.channel = FileChannel.open(path, StandardOpenOption.READ);
        this.libraryRegistry = libraryRegistry;

        PrimitivI input;

        // Reading magic string

        ByteBuffer buf = ByteBuffer.allocate(ClnAWriter.MAGIC_LENGTH + 4); // ClnAWriter.MAGIC_LENGTH + 4 = 18
        channel.read(buf, 0L);
        buf.flip();

        byte[] magicBytes = new byte[ClnAWriter.MAGIC_LENGTH];
        buf.get(magicBytes);
        String magicString = new String(magicBytes, StandardCharsets.US_ASCII);

        if (!magicString.equals(ClnAWriter.MAGIC))
            throw new IllegalArgumentException("Wrong file type. Magic = " + magicString +
                    ", expected = " + ClnAWriter.MAGIC);

        // Reading number of clones

        this.numberOfClones = buf.getInt();

        // Reading key file offsets from last 16 bytes of the file

        buf.flip();
        buf.limit(16);
        long fSize = channel.size();
        channel.read(buf, fSize - 16);
        buf.flip();
        this.firstClonePosition = buf.getLong();
        long indexBegin = buf.getLong();

        // Reading index data

        input = new PrimitivI(new InputDataStream(indexBegin, fSize - 8));
        this.index = new long[numberOfClones + 2];
        this.counts = new long[numberOfClones + 2];
        long previousValue = 0;
        long totalAlignmentsCount = 0L;
        for (int i = 0; i < numberOfClones + 2; i++) {
            previousValue = index[i] = previousValue + input.readVarInt();
            totalAlignmentsCount += counts[i] = input.readVarLong();
        }
        this.totalAlignmentsCount = totalAlignmentsCount;

        // Reading gene features

        input = new PrimitivI(new InputDataStream(ClnAWriter.MAGIC_LENGTH + 4, firstClonePosition));
        this.versionInfo = input.readUTF();
        this.alignerParameters = input.readObject(VDJCAlignerParameters.class);
        this.assemblingFeatures = input.readObject(GeneFeature[].class);
        this.alignedFeatures = new CloneSetIO.GT2GFAdapter(IO.readGF2GTMap(input));
        this.genes = IOUtil.readGeneReferences(input, libraryRegistry);
    }

    public ClnAReader(Path path, VDJCLibraryRegistry libraryRegistry) throws IOException {
        this(path, libraryRegistry, DEFAULT_CHUNK_SIZE);
    }

    public ClnAReader(String path, VDJCLibraryRegistry libraryRegistry) throws IOException {
        this(Paths.get(path), libraryRegistry, DEFAULT_CHUNK_SIZE);
    }

    /**
     * Aligner parameters
     */
    public VDJCAlignerParameters getAlignerParameters() {
        return alignerParameters;
    }

    public GeneFeature[] getAssemblingFeatures() {
        return assemblingFeatures;
    }

    /**
     * Returns number of clones in the file
     */
    public int numberOfClones() {
        // Index contain two additional records:
        //  - first = position of alignment block with cloneIndex == -1
        //  - last = position of the last alignments block end
        return index.length - 2;
    }

    public List<VDJCGene> getGenes() {
        return genes;
    }

    /**
     * Returns total number of alignments in the file, including unassembled.
     */
    public long numberOfAlignments() {
        return totalAlignmentsCount;
    }

    /**
     * Returns number of alignments contained in particular clone
     *
     * @param cloneIndex clone index
     * @return number of alignments
     */
    public long numberOfAlignmentsInClone(int cloneIndex) {
        return counts[cloneIndex + 1];
    }

    /**
     * MiXCR version this file was produced with.
     */
    public String getVersionInfo() {
        return versionInfo;
    }

    /**
     * Read clone set completely
     */
    public CloneSet readCloneSet() throws IOException {
        PrimitivI input = new PrimitivI(new InputDataStream(firstClonePosition, index[0]));
        // Initializing PrimitivI object
        // (see big comment block in ClnAWriter.writeClones())
        IOUtil.registerGeneReferences(input, genes, alignedFeatures);

        // Reading clones
        int count = numberOfClones();
        List<Clone> clones = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            clones.add(input.readObject(Clone.class));

        return new CloneSet(clones, genes, alignedFeatures.map, assemblingFeatures);
    }

    /**
     * Constructs output port to read clones one by one as a stream
     */
    public OutputPort<Clone> readClones() throws IOException {
        PrimitivI input = new PrimitivI(new InputDataStream(firstClonePosition, index[0]));
        IOUtil.registerGeneReferences(input, genes, alignedFeatures);

        return new PipeDataInputReader<>(Clone.class, input, numberOfClones());
    }

    /**
     * Constructs output port to read alignments for a specific clone, or read unassembled alignments block
     *
     * @param cloneIndex index of clone; -1 to read unassembled alignments
     */
    public OutputPort<VDJCAlignments> readAlignmentsOfClone(int cloneIndex) throws IOException {
        PrimitivI input = new PrimitivI(new InputDataStream(index[cloneIndex + 1], index[cloneIndex + 2]));
        IOUtil.registerGeneReferences(input, genes, alignedFeatures);
        return new PipeDataInputReader<>(VDJCAlignments.class, input, counts[cloneIndex + 1]);
    }

    /**
     * Constructs output port to read all alignments form the file. Alignments are sorted by cloneIndex.
     */
    public OutputPort<VDJCAlignments> readAllAlignments() throws IOException {
        PrimitivI input = new PrimitivI(new InputDataStream(index[0], index[index.length - 1]));
        IOUtil.registerGeneReferences(input, genes, alignedFeatures);
        return new PipeDataInputReader<>(VDJCAlignments.class, input, totalAlignmentsCount);
    }

    /**
     * Constructs output port to read all alignments that are attached to a clone. Alignments are sorted by cloneIndex.
     */
    public OutputPort<VDJCAlignments> readAssembledAlignments() throws IOException {
        PrimitivI input = new PrimitivI(new InputDataStream(index[1], index[index.length - 1]));
        IOUtil.registerGeneReferences(input, genes, alignedFeatures);
        return new PipeDataInputReader<>(VDJCAlignments.class, input, totalAlignmentsCount - counts[0]);
    }

    /**
     * Constructs output port to read alignments that are not attached to any clone. Alignments are sorted by
     * cloneIndex.
     *
     * Returns: readAlignmentsOfClone(-1)
     */
    public OutputPort<VDJCAlignments> readNotAssembledAlignments() throws IOException {
        return readAlignmentsOfClone(-1);
    }

    /**
     * Constructs output port of CloneAlignments objects, that allows to get synchronised view on clone and it's
     * corresponding alignments
     */
    public CloneAlignmentsPort clonesAndAlignments() throws IOException {
        return new CloneAlignmentsPort();
    }

    public final class CloneAlignmentsPort
            implements OutputPort<CloneAlignments>, CanReportProgress {
        private int cloneIndex = 0;
        final PipeDataInputReader<Clone> clones;
        boolean isFinished = false;
        AtomicLong processedAlignments = new AtomicLong();

        CloneAlignmentsPort() throws IOException {
            PrimitivI input = new PrimitivI(new InputDataStream(firstClonePosition, index[0]));
            IOUtil.registerGeneReferences(input, genes, alignedFeatures);
            this.clones = new PipeDataInputReader<>(Clone.class, input, numberOfClones());
        }

        @Override
        public CloneAlignments take() {
            Clone clone = clones.take();
            if (clone == null) {
                isFinished = true;
                return null;
            }
            CloneAlignments result = new CloneAlignments(clone, cloneIndex);
            processedAlignments.addAndGet(result.alignmentsCount);
            ++cloneIndex;
            return result;
        }

        @Override
        public double getProgress() {
            return 1.0 * processedAlignments.get() / totalAlignmentsCount;
        }

        @Override
        public boolean isFinished() {
            return isFinished;
        }
    }

    /**
     * Clone and alignments it was formed form
     */
    public final class CloneAlignments {
        /**
         * Clone
         */
        public final Clone clone;
        final int cloneId;
        final long alignmentsCount;

        CloneAlignments(Clone clone, int cloneId) {
            this.clone = clone;
            this.cloneId = cloneId;
            this.alignmentsCount = counts[cloneId + 1];
        }

        /**
         * Alignments
         */
        public OutputPort<VDJCAlignments> alignments() throws IOException {
            return readAlignmentsOfClone(cloneId);
        }
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }

    /**
     * FileChannel -> DataInput adapter that can be constructed for an arbitrary file position.
     *
     * Implemented using ByteBuffer.
     *
     * Thread-unsafe.
     */
    private class InputDataStream implements DataInput {
        private final long to;
        private final ByteBuffer buffer;
        private long lastPosition;

        InputDataStream(long from, long to) throws IOException {
            this.to = to;
            this.buffer = ByteBuffer.allocate(chunkSize);
            this.lastPosition = from;

            // Initially buffer is empty
            this.buffer.limit(0);

            // Filling first chunk of data
            fillBuffer();
        }

        void fillBuffer() throws IOException {
            // Number of bytes to read from file
            int size = (int) Math.min(chunkSize - buffer.remaining(), to - lastPosition);

            // Checking state
            if (size == 0)
                throw new IllegalArgumentException("No more bytes.");

            // Saving remaining bytes
            ByteBuffer remaining = buffer.slice();

            // Reset buffer state
            buffer.flip();

            // Setting new limit
            buffer.limit(size + remaining.limit());

            // Transferring remaining buffer bytes
            buffer.put(remaining);

            // Reading content form file
            int read = channel.read(buffer, lastPosition);

            // Flipping buffer
            buffer.flip();

            if (read != size)
                throw new IOException("Wrong block positions.");

            // Advancing last position
            this.lastPosition += read;
        }

        void ensureBuffer(int requiredSize) throws IOException {
            if (requiredSize > chunkSize)
                throw new IllegalArgumentException("Can't read this many bytes.");
            if (buffer.remaining() < requiredSize)
                fillBuffer();
        }

        @Override
        public void readFully(byte[] b) throws IOException {
            readFully(b, 0, b.length);
        }

        @Override
        public void readFully(byte[] b, int off, int len) throws IOException {
            do {
                int l = Math.min(chunkSize, len);
                ensureBuffer(l);
                buffer.get(b, off, l);
                off += l;
                len -= l;
            } while (len != 0);
        }

        @Override
        public int skipBytes(int n) throws IOException {
            ensureBuffer(n);
            buffer.position(buffer.position() + n);
            return n;
        }

        @Override
        public boolean readBoolean() throws IOException {
            byte b = buffer.get();
            if (b == 1)
                return true;
            else if (b == 0)
                return false;
            else
                throw new IOException("Illegal file format, can't deserialize boolean.");
        }

        @Override
        public byte readByte() throws IOException {
            ensureBuffer(1);
            return buffer.get();
        }

        @Override
        public int readUnsignedByte() throws IOException {
            ensureBuffer(1);
            return 0xFF & buffer.get();
        }

        @Override
        public short readShort() throws IOException {
            ensureBuffer(2);
            return (short) buffer.getChar();
        }

        @Override
        public int readUnsignedShort() throws IOException {
            ensureBuffer(2);
            return 0xFFFF & buffer.getChar();
        }

        @Override
        public char readChar() throws IOException {
            ensureBuffer(2);
            return buffer.getChar();
        }

        @Override
        public int readInt() throws IOException {
            ensureBuffer(4);
            return buffer.getInt();
        }

        @Override
        public long readLong() throws IOException {
            ensureBuffer(8);
            return buffer.getLong();
        }

        @Override
        public float readFloat() throws IOException {
            ensureBuffer(4);
            return buffer.getFloat();
        }

        @Override
        public double readDouble() throws IOException {
            ensureBuffer(8);
            return buffer.getDouble();
        }

        @Override
        public String readLine() throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public String readUTF() throws IOException {
            return DataInputStream.readUTF(this);
        }
    }
}
