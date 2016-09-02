package com.milaboratory.mixcr.assembler;

import cc.redberry.pipe.OutputPortCloseable;
import com.milaboratory.mixcr.util.TempFileManager;

import java.io.*;
import java.util.Arrays;
import java.util.PriorityQueue;

import static com.milaboratory.mixcr.assembler.ReadToCloneMapping.CLONE_COMPARATOR;
import static com.milaboratory.mixcr.assembler.ReadToCloneMapping.RECORD_SIZE;

/**
 * @author Stanislav Poslavsky
 */
public class AlignmentsToClonesMapping implements AutoCloseable, Closeable {
    public static final int MAGIC = 0x95bf97e3;

    final RandomAccessFile rnd;
    final int cloneCount;
    final long alignmentCount;
    final long[] cloneOffsets;

    public AlignmentsToClonesMapping(RandomAccessFile rnd, int cloneCount, long alignmentCount, long[] cloneOffsets) {
        this.rnd = rnd;
        this.cloneCount = cloneCount;
        this.alignmentCount = alignmentCount;
        this.cloneOffsets = cloneOffsets;
    }


    @Override
    public void close() throws IOException {
        rnd.close();
    }

    public static AlignmentsToClonesMapping readMapping(String fileName) throws IOException {
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

        return new AlignmentsToClonesMapping(file, cloneCount, alignmentCount, cloneOffsets);
    }


    public static void writeMapping(final OutputPortCloseable<ReadToCloneMapping> mappingPort,
                                    final int cloneCount,
                                    final DataOutput output,
                                    final int chunkSize) throws IOException {
        output.writeInt(MAGIC);
        long alignmentsCount = 0;

        final File tempFile = TempFileManager.getTempFile();
        final long[] cloneOffsets = new long[cloneCount];
        try (final DataOutputStream tempOutput = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tempFile), 262144))) {
            final ReadToCloneMapping[] buffer = new ReadToCloneMapping[chunkSize];
            ReadToCloneMapping mapping;
            int pointer = 0;
            while ((mapping = mappingPort.take()) != null) {
                if (mapping.isDropped())
                    continue;

                ++alignmentsCount;
                ++cloneOffsets[mapping.cloneIndex];
                ReadToCloneMapping.write(output, mapping);

                if (pointer == chunkSize) {
                    Arrays.sort(buffer, CLONE_COMPARATOR);
                    for (int i = 0; i < chunkSize; ++i)
                        ReadToCloneMapping.write(tempOutput, buffer[i]);
                    pointer = 0;
                }

                buffer[pointer++] = mapping;
            }


            Arrays.sort(buffer, 0, pointer, CLONE_COMPARATOR);
            for (int i = 0; i < pointer; ++i)
                ReadToCloneMapping.write(tempOutput, buffer[i]);
        }

        final int nPivots = (int) ((alignmentsCount + chunkSize - 1) / chunkSize);
        final PriorityQueue<SortedView> sorter = new PriorityQueue<>();
        SortedView head = null;
        try {
            for (int i = 0; i < nPivots; i++) {
                final SortedView pivot = new SortedView(tempFile, i, chunkSize);
                pivot.advance();
                sorter.add(pivot);
            }

            while (!sorter.isEmpty()) {
                head = sorter.poll();
                ReadToCloneMapping.write(output, head.current());
                head.advance();
                if (head.current() != null) {
                    head = null;
                    sorter.add(head);
                } else head.close();
            }
        } finally {
            for (SortedView view : sorter)
                view.close();
            if (head != null)
                head.close();
        }

        long cOffset = 4 + alignmentsCount * RECORD_SIZE;
        for (int i = 0; i < cloneCount; i++) {
            long p = cloneOffsets[i];
            cloneOffsets[i] = cOffset;
            cOffset += p * RECORD_SIZE;
        }

        for (long cloneOffset : cloneOffsets)
            output.writeLong(cloneOffset);
        output.writeInt(cloneCount);
        output.writeLong(alignmentsCount);
    }

    private static final class SortedView implements Comparable<SortedView>, AutoCloseable, Closeable {
        final DataInputStream file;
        final int chunkSize;
        private int position = 0;
        private ReadToCloneMapping current = null;

        public SortedView(File tmpFile, long chunkId, int chunkSize) throws IOException {
            this.chunkSize = chunkSize;
            final FileInputStream fo = new FileInputStream(tmpFile);
            fo.getChannel().position(chunkId * chunkSize * RECORD_SIZE);
            this.file = new DataInputStream(new BufferedInputStream(fo, 1024));
        }

        public void advance() throws IOException {
            if (position == chunkSize || file.available() == 0) {
                current = null;
            } else {
                ++position;
                current = ReadToCloneMapping.read(file);
            }
        }

        public ReadToCloneMapping current() {
            return current;
        }

        @Override
        public void close() throws IOException {
            this.file.close();
        }

        @Override
        public int compareTo(SortedView o) {
            return CLONE_COMPARATOR.compare(current, o.current);
        }
    }
}
