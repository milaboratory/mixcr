package com.milaboratory.mixcr.assembler;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.InputPort;
import cc.redberry.pipe.OutputPort;
import cc.redberry.pipe.blocks.Buffer;
import com.milaboratory.util.ObjectSerializer;
import com.milaboratory.util.Sorter;
import com.milaboratory.util.TempFileManager;
import org.apache.commons.io.output.CountingOutputStream;

import java.io.*;
import java.nio.channels.Channels;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.milaboratory.mixcr.assembler.ReadToCloneMapping.ALIGNMENTS_COMPARATOR;
import static com.milaboratory.mixcr.assembler.ReadToCloneMapping.CLONE_COMPARATOR;

/**
 * @author Stanislav Poslavsky
 */
public class AlignmentsToClonesMappingContainer implements AutoCloseable, Closeable {
    public static final int MAGIC = 0x95bf97e4;

    final RandomAccessFile raf;
    final int cloneCount;
    final long alignmentCount;
    final long[] cloneOffsets;
    final int[] alignmentsInClones;

    public AlignmentsToClonesMappingContainer(RandomAccessFile raf, int cloneCount,
                                              long alignmentCount, long[] cloneOffsets,
                                              int[] alignmentsInClones) {
        this.raf = raf;
        this.cloneCount = cloneCount;
        this.alignmentCount = alignmentCount;
        this.cloneOffsets = cloneOffsets;
        this.alignmentsInClones = alignmentsInClones;
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
        return new OP(cloneOffsets[cloneId], alignmentsInClones[cloneId]);
    }

    public OutputPort<ReadToCloneMapping> createPortByClones() {
        if (alignmentCount == 0)
            return CUtils.EMPTY_OUTPUT_PORT;
        else
            return new OP(cloneOffsets[0], alignmentCount);
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

        raf.seek(fileSize - 4 - 8 - cloneCount * 12);
        final int[] alignmentsInClones = new int[cloneCount];
        for (int i = 0; i < cloneCount; i++)
            alignmentsInClones[i] = raf.readInt();
        final long[] cloneOffsets = new long[cloneCount];
        for (int i = 0; i < cloneCount; i++)
            cloneOffsets[i] = raf.readLong();

        return new AlignmentsToClonesMappingContainer(raf, cloneCount, alignmentCount, cloneOffsets, alignmentsInClones);
    }

    public static final int MAX_BUFFER_SIZE_RECORDS = 65536; // <~ 1.4Mb

    public final class OP implements OutputPort<ReadToCloneMapping> {
        private final long limit;
        private long pointer = 0;
        private final DataInput data;

        public OP(long offset, long limit) {
            try {
                raf.seek(offset);
                this.data = new DataInputStream(new BufferedInputStream(Channels.newInputStream(raf.getChannel())));
                this.limit = limit;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public synchronized ReadToCloneMapping take() {
            if (pointer == limit)
                return null;

            ReadToCloneMapping record = ReadToCloneMapping.read(data);

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
                                    final OutputStream output,
                                    final int sortingChunkSize) throws IOException {
        // Counting OS to keep track of file position
        CountingOutputStream cOutput = new CountingOutputStream(output);
        DataOutput dataOutput = new DataOutputStream(cOutput);

        // Writing 4 magic bytes
        dataOutput.writeInt(MAGIC);

        // Counter of alignments (this info will be written in the file footer)
        long alignmentsCount = 0;

        // Temp file for merge sorting
        final File tempFile = TempFileManager.getTempFile();

        // Saving number of records for each clone
        final int[] alignmentsInClones = new int[cloneCount];

        // Sorting blocks (sortingChunkSize) of records by clone id (for "by clone id" index file section) in separate thread
        // Simultaneously writing records sorted "by alignment id"

        final Buffer<ReadToCloneMapping> mappings = new Buffer<>();

        // Port will be used in this thread to pass objects to sorterThread (will also provide back-pressure in a sense)
        InputPort<ReadToCloneMapping> toSorter = mappings.createInputPort();

        final Sorter<ReadToCloneMapping> sorter = new Sorter<>(mappings, CLONE_COMPARATOR, sortingChunkSize,
                new ObjectSerializer.PrimitivIOObjectSerializer<>(ReadToCloneMapping.class),
                tempFile);

        // Flag to check sorting
        final AtomicBoolean sortingOk = new AtomicBoolean(false);

        // Starting separate thread for sorting
        Thread sorterThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    sorter.build();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                sortingOk.set(true);
            }
        });
        sorterThread.start();

        ReadToCloneMapping current;
        ReadToCloneMapping previous = null;
        while ((current = mappingPort.take()) != null) {
            // Skip dropped alignments
            if (current.isDropped())
                continue;

            // Checking that input stream is correctly sorted
            if (previous != null && ALIGNMENTS_COMPARATOR.compare(previous, current) >= 0)
                throw new IllegalArgumentException();

            // Count alignments
            ++alignmentsCount;
            // and clone records
            ++alignmentsInClones[current.cloneIndex];

            // Writing record to "by alignment id" section
            ReadToCloneMapping.write(dataOutput, current);

            // Passing object to sorter thread
            toSorter.put(current);

            // Saving previous record
            previous = current;
        }
        // close toSorter port
        toSorter.put(null);

        // Waiting for sorter thread to finish
        try {
            sorterThread.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        // Checking sorting status
        if (!sortingOk.get())
            throw new RuntimeException("Sorting thread finished with error.");

        // Saving offsets
        final long[] cloneOffsets = new long[cloneCount];
        // Writing "by clone id" section
        int previousCloneId = 0;
        // Position of first record
        cloneOffsets[0] = cOutput.getByteCount();
        // Writing "by clone id" file section using merge-sort
        for (ReadToCloneMapping mapping : CUtils.it(sorter.getSorted())) {
            if (mapping.cloneIndex != previousCloneId) {
                // Checking that all clones appeared at least once in the stream
                if (previousCloneId != mapping.cloneIndex - 1)
                    throw new IllegalArgumentException();
                // Saving position to index
                cloneOffsets[mapping.cloneIndex] = cOutput.getByteCount();
                previousCloneId = mapping.cloneIndex;
            }
            // Writing record to "by clone index" section
            ReadToCloneMapping.write(dataOutput, mapping);
        }

        // Writing alignmentsInClones
        for (int alignmentsInClone : alignmentsInClones)
            dataOutput.writeInt(alignmentsInClone);

        // Writing "by clone" offset reference
        for (long cloneOffset : cloneOffsets)
            dataOutput.writeLong(cloneOffset);

        // Writing number of clones
        dataOutput.writeInt(cloneCount);
        // Writing number of alignments = number of records in each section
        dataOutput.writeLong(alignmentsCount);
    }
}
