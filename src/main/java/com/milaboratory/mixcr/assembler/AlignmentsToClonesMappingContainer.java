package com.milaboratory.mixcr.assembler;

import cc.redberry.pipe.OutputPort;
import com.milaboratory.mixcr.util.TempFileManager;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.PriorityQueue;

import static com.milaboratory.mixcr.assembler.ReadToCloneMapping.*;

/**
 * @author Stanislav Poslavsky
 */
public class AlignmentsToClonesMappingContainer implements AutoCloseable, Closeable {
    public static final int MAGIC = 0x95bf97e3;

    final RandomAccessFile raf;
    final int cloneCount;
    final long alignmentCount;
    final long[] cloneOffsets;
    final long lastOffset;

    public AlignmentsToClonesMappingContainer(RandomAccessFile raf, int cloneCount, long alignmentCount, long[] cloneOffsets, long lastOffset) {
        this.raf = raf;
        this.cloneCount = cloneCount;
        this.alignmentCount = alignmentCount;
        this.cloneOffsets = cloneOffsets;
        this.lastOffset = lastOffset;
    }

    @Override
    public void close() throws IOException {
        raf.close();
    }

    public int getCloneCount() {
        return cloneCount;
    }

    public long getAlignmentCount() {
        return alignmentCount;
    }

    public OutputPort<ReadToCloneMapping> createPortForClone(int cloneId) {
        long nextOffset = (cloneId == cloneOffsets.length - 1) ?
                lastOffset : cloneOffsets[cloneId + 1];
        return new OP(cloneOffsets[cloneId], (nextOffset - cloneOffsets[cloneId]) / RECORD_SIZE);
    }

    public OutputPort<ReadToCloneMapping> createPortByClones() {
        return new OP(4 + alignmentCount * RECORD_SIZE, alignmentCount);
    }

    public OutputPort<ReadToCloneMapping> createPortByAlignments() {
        return new OP(4, alignmentCount);
    }

    public static AlignmentsToClonesMappingContainer open(String fileName) throws IOException {
        return open(new File(fileName));
    }

    public static AlignmentsToClonesMappingContainer open(File file) throws IOException {
        RandomAccessFile raf = new RandomAccessFile(file, "r");

        // Checking magic bytes
        final int magic = raf.readInt();
        if (magic != MAGIC) {
            raf.close();
            throw new RuntimeException("Wrong file format.");
        }
        final long fileSize = raf.length();

        // Reading cloneCount and alignmentCount in the footer of the file
        raf.seek(fileSize - 4 - 8);
        final int cloneCount = raf.readInt();
        final long alignmentCount = raf.readLong();

        final long lastOffset = fileSize - 4 - 8 - cloneCount * 8;
        raf.seek(lastOffset);
        final long[] cloneOffsets = new long[cloneCount];
        for (int i = 0; i < cloneCount; i++)
            cloneOffsets[i] = raf.readLong();

        return new AlignmentsToClonesMappingContainer(raf, cloneCount, alignmentCount, cloneOffsets, lastOffset);
    }

    public static final int MAX_BUFFER_SIZE_RECORDS = 65536; // <~ 1.4Mb

    public final class OP implements OutputPort<ReadToCloneMapping> {
        private final long offset;
        private final long limit;
        private long pointer = 0;
        //private boolean bufferEmpty;
        //private ByteBuffer buffer = null;
        private final ByteBuffer buffer;

        public OP(long offset, long limit) {
            this.offset = offset;
            this.limit = limit;
            this.buffer = ByteBuffer.allocate((int) Math.min(MAX_BUFFER_SIZE_RECORDS, limit) * RECORD_SIZE);
            readMore();
        }

        private void readMore() {
            try {
                int chunkSize = (int) Math.min(MAX_BUFFER_SIZE_RECORDS, limit - pointer) * RECORD_SIZE;
                buffer.clear();
                buffer.limit(chunkSize);
                int read = raf.getChannel().read(buffer, offset + pointer * RECORD_SIZE);
                assert read == chunkSize;
                buffer.flip();
            } catch (IOException e) {
                throw new RuntimeException();
            }
        }

        @Override
        public synchronized ReadToCloneMapping take() {
            if (pointer == limit)
                return null;

            if (!buffer.hasRemaining())
                readMore();

            ReadToCloneMapping record = ReadToCloneMapping.read(buffer);

            ++pointer;

            return record;
        }
    }

    public static final int DEFAULT_SORTING_CHUNK_SIZE = 2097152;

    public static void writeMapping(final OutputPort<ReadToCloneMapping> mappingPort,
                                    final int cloneCount,
                                    final String fileName) throws IOException {
        writeMapping(mappingPort, cloneCount, new File(fileName));
    }

    public static void writeMapping(final OutputPort<ReadToCloneMapping> mappingPort,
                                    final int cloneCount,
                                    final File file) throws IOException {
        try (DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
            writeMapping(mappingPort, cloneCount, dos, DEFAULT_SORTING_CHUNK_SIZE);
        }
    }

    public static void writeMapping(final OutputPort<ReadToCloneMapping> mappingPort,
                                    final int cloneCount,
                                    final DataOutput output,
                                    final int sortingChunkSize) throws IOException {
        // Writing 4 magic bytes
        output.writeInt(MAGIC);

        // Counter of alignments (this info will be written in the file footer)
        long alignmentsCount = 0;

        // Temp file for merge sorting
        final File tempFile = TempFileManager.getTempFile();

        // Saving number of records for each clone
        final long[] cloneOffsets = new long[cloneCount];

        // Sorting blocks (sortingChunkSize) of records by clone id (for "by clone id" index file section)
        // Simultaneously writing records sorted "by alignment id"
        try (final DataOutputStream tempOutput = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tempFile), 262144))) {
            final ReadToCloneMapping[] buffer = new ReadToCloneMapping[sortingChunkSize];
            ReadToCloneMapping mapping;
            ReadToCloneMapping previous = null;
            int pointer = 0;
            while ((mapping = mappingPort.take()) != null) {
                // Skip dropped alignments
                if (mapping.isDropped())
                    continue;

                // Checking that input stream is correctly sorted
                if (previous != null && ALIGNMENTS_COMPARATOR.compare(previous, mapping) >= 0)
                    throw new IllegalArgumentException();

                // Count alignments
                ++alignmentsCount;
                // and clone records
                ++cloneOffsets[mapping.cloneIndex];

                // Writing record to "by alignment id" section
                ReadToCloneMapping.write(output, mapping);

                // If we collected sortingChunkSize records,
                // sort by clone id, and write sorted block to temp file
                if (pointer == sortingChunkSize) {
                    Arrays.sort(buffer, CLONE_COMPARATOR);
                    for (int i = 0; i < sortingChunkSize; ++i)
                        ReadToCloneMapping.write(tempOutput, buffer[i]);

                    // Resetting pointer
                    pointer = 0;
                }

                // Saving record for further block-sorting
                buffer[pointer++] = mapping;

                // Saving previous record
                previous = mapping;
            }

            // Sorting and flushing final chunk for "by clone id" index file section
            Arrays.sort(buffer, 0, pointer, CLONE_COMPARATOR);
            for (int i = 0; i < pointer; ++i)
                ReadToCloneMapping.write(tempOutput, buffer[i]);
        }

        // Writing "by clone id" file section using merge-sort

        // Calculating number of chunks
        final int nPivots = (int) ((alignmentsCount + sortingChunkSize - 1) / sortingChunkSize);

        // Queue's head will always contain SortedBlock pointing to the least record (in terms of CLONE_COMPARATOR)
        final PriorityQueue<SortedBlockReader> blocks = new PriorityQueue<>();

        // Used to correctly close file in case of exception
        SortedBlockReader head = null;
        try {
            // Opening SortedBlocks and initiating reading
            for (int i = 0; i < nPivots; i++) {
                final SortedBlockReader pivot = new SortedBlockReader(tempFile, i, sortingChunkSize);
                pivot.advance();
                blocks.add(pivot);
            }

            // Perform sorting and write of "by clone id" index file section
            while (!blocks.isEmpty()) {
                // Getting reader pointing to the least reader
                head = blocks.poll();

                // Writing this value to output file
                ReadToCloneMapping.write(output, head.current());

                // Advance the reader
                head.advance();
                if (head.current() != null) { // If reader has more records put it back to queue
                    blocks.add(head);
                    head = null;
                } else { // If reader was completely drained close it and don't put it back to queue
                    head.close();
                    head = null;
                }
            }
        } finally {
            // Closing readers if something went wrong
            for (SortedBlockReader view : blocks)
                view.close();
            if (head != null)
                head.close();
        }

        assert blocks.isEmpty();

        // Calculating offsets for first record in each block with the same clone id
        // in "by clone id" index file section
        long cOffset = 4 + alignmentsCount * RECORD_SIZE; // Initial offset
        for (int i = 0; i < cloneCount; i++) {
            // Checking that all clones appeared at least once in the stream
            if (cloneOffsets[i] == 0)
                throw new IllegalArgumentException();
            long p = cloneOffsets[i];
            cloneOffsets[i] = cOffset;
            cOffset += p * RECORD_SIZE;
        }

        // Writing "by clone" offset reference
        for (long cloneOffset : cloneOffsets)
            output.writeLong(cloneOffset);
        // Writing number of clones
        output.writeInt(cloneCount);
        // Writing number of alignments = number of records in each section
        output.writeLong(alignmentsCount);

        // Total file size must be = 4 + alignmentsCount * RECORD_SIZE * 2 + cloneCount * 8 + 4 + 8
    }

    /**
     * Used to read sorted block from tem file during merge-sort procedure
     */
    private static final class SortedBlockReader implements Comparable<SortedBlockReader>, AutoCloseable, Closeable {
        final DataInputStream input;
        final int chunkSize;
        private int position = 0;
        private ReadToCloneMapping current = null;

        public SortedBlockReader(File file, long chunkId, int chunkSize) throws IOException {
            this.chunkSize = chunkSize;

            final FileInputStream fo = new FileInputStream(file);
            // Setting file position to the beginning of the chunkId-th chunk
            fo.getChannel().position(chunkId * chunkSize * RECORD_SIZE);

            // Read using small buffer (~50 records)
            this.input = new DataInputStream(new BufferedInputStream(fo, 1024));
        }

        public void advance() throws IOException {
            if (position == chunkSize || input.available() == 0) {
                current = null;
            } else {
                ++position;
                current = ReadToCloneMapping.read(input);
            }
        }

        public ReadToCloneMapping current() {
            return current;
        }

        @Override
        public void close() throws IOException {
            this.input.close();
        }

        @Override
        public int compareTo(SortedBlockReader o) {
            return CLONE_COMPARATOR.compare(current, o.current);
        }
    }
}
