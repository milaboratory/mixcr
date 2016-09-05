package com.milaboratory.mixcr.assembler;

import cc.redberry.pipe.OutputPort;
import com.milaboratory.mixcr.util.TempFileManager;

import java.io.*;
import java.util.Arrays;
import java.util.PriorityQueue;

import static com.milaboratory.mixcr.assembler.ReadToCloneMapping.*;

/**
 * @author Stanislav Poslavsky
 */
public class AlignmentsToClonesMappingContainer implements AutoCloseable, Closeable {
    public static final int MAGIC = 0x95bf97e3;

    final RandomAccessFile rnd;
    final int cloneCount;
    final long alignmentCount;
    final long[] cloneOffsets;

    public AlignmentsToClonesMappingContainer(RandomAccessFile rnd, int cloneCount, long alignmentCount, long[] cloneOffsets) {
        this.rnd = rnd;
        this.cloneCount = cloneCount;
        this.alignmentCount = alignmentCount;
        this.cloneOffsets = cloneOffsets;
    }

    @Override
    public void close() throws IOException {
        rnd.close();
    }

    public static AlignmentsToClonesMappingContainer readMapping(String fileName) throws IOException {
        RandomAccessFile file = new RandomAccessFile(fileName, "r");
        final int magic = file.readInt();
        if (magic != MAGIC) {
            file.close();
            throw new RuntimeException("Wrong magic.");
        }
        final long fileSize = file.length();

        file.seek(fileSize - 4 - 8);
        final int cloneCount = file.readInt();
        final long alignmentCount = file.readLong();

        file.seek(fileSize - 4 - 8 - cloneCount * 4);
        final long[] cloneOffsets = new long[cloneCount];
        for (int i = 0; i < cloneCount; i++)
            cloneOffsets[i] = file.readLong();

        return new AlignmentsToClonesMappingContainer(file, cloneCount, alignmentCount, cloneOffsets);
    }


    public static void writeMapping(final OutputPort<ReadToCloneMapping> mappingPort,
                                    final int cloneCount,
                                    final DataOutput output,
                                    final int chunkSize) throws IOException {
        // Writing 4 magic bytes
        output.writeInt(MAGIC);

        // Counter of alignments (this info will be written in the file footer)
        long alignmentsCount = 0;

        // Temp file for merge sorting
        final File tempFile = TempFileManager.getTempFile();

        // Saving number of records for each clone
        final long[] cloneOffsets = new long[cloneCount];

        // Sorting blocks (chunkSize) of records by clone id (for "by clone id" index file section)
        // Simultaneously writing records sorted "by alignment id"
        try (final DataOutputStream tempOutput = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tempFile), 262144))) {
            final ReadToCloneMapping[] buffer = new ReadToCloneMapping[chunkSize];
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

                // If we collected chunkSize records,
                // sort by clone id, and write sorted block to temp file
                if (pointer == chunkSize) {
                    Arrays.sort(buffer, CLONE_COMPARATOR);
                    for (int i = 0; i < chunkSize; ++i)
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
        final int nPivots = (int) ((alignmentsCount + chunkSize - 1) / chunkSize);

        // Queue's head will always contain SortedBlock pointing to the least record (in terms of CLONE_COMPARATOR)
        final PriorityQueue<SortedBlockReader> blocks = new PriorityQueue<>();

        // Used to correctly close file in case of exception
        SortedBlockReader head = null;
        try {
            // Opening SortedBlocks and initiating reading
            for (int i = 0; i < nPivots; i++) {
                final SortedBlockReader pivot = new SortedBlockReader(tempFile, i, chunkSize);
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
