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

import com.milaboratory.cli.AppVersionInfo;
import com.milaboratory.mixcr.util.MiXCRDebug;
import com.milaboratory.mixcr.util.MiXCRVersionInfo;
import com.milaboratory.primitivio.PrimitivO;
import com.milaboratory.primitivio.blocks.PrimitivIOBlocksUtil;
import com.milaboratory.primitivio.blocks.PrimitivOBlocks;
import com.milaboratory.primitivio.blocks.PrimitivOBlocksStats;
import com.milaboratory.primitivio.blocks.PrimitivOHybrid;
import com.milaboratory.util.io.HasPosition;
import io.repseq.core.VDJCGene;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static com.milaboratory.mixcr.basictypes.IOUtil.MAGIC_VDJC;

public final class VDJCAlignmentsWriter implements VDJCAlignmentsWriterI, HasPosition {
    public static final int DEFAULT_ENCODER_THREADS = 3;
    public static final int DEFAULT_ALIGNMENTS_IN_BLOCK = 1 << 10; // 805-1024 bytes per alignment
    static final String MAGIC_V19 = MAGIC_VDJC + ".V19";
    static final String MAGIC_V20 = MAGIC_VDJC + ".V20";

    static final String MAGIC_V21 = MAGIC_VDJC + ".V21";
    static final String MAGIC = MAGIC_V21;
    static final int MAGIC_LENGTH = 14;
    static final byte[] MAGIC_BYTES = MAGIC.getBytes(StandardCharsets.US_ASCII);

    /**
     * Number of bytes in footer with meta information
     */
    static final int FIXED_FOOTER_LENGTH = 8 + 8 + 8 + IOUtil.END_MAGIC_LENGTH;

    private final boolean highCompression;

    /**
     * This number will be added to the end of the file to report number of processed read to the following processing
     * steps. Mainly for informative statistics reporting.
     */
    long numberOfProcessedReads = -1;

    /** Writer settings */
    final int encoderThreads, alignmentsInBlock;

    /** Counter of alignments */
    long numberOfAlignments = 0;

    /** Main block output class */
    final PrimitivOHybrid output;

    /** Footer to write to the file right before close */
    private MiXCRFooter footer = null;

    /** Initialized after headers */
    volatile PrimitivOBlocks<VDJCAlignments>.Writer writer;

    boolean closed = false;

    public VDJCAlignmentsWriter(String fileName) throws IOException {
        this(fileName, DEFAULT_ENCODER_THREADS, DEFAULT_ALIGNMENTS_IN_BLOCK);
    }

    public VDJCAlignmentsWriter(String fileName, int encoderThreads, int alignmentsInBlock) throws IOException {
        this(new PrimitivOHybrid(Paths.get(fileName)), encoderThreads, alignmentsInBlock, false);
    }

    public VDJCAlignmentsWriter(String fileName, int encoderThreads, int alignmentsInBlock, boolean highCompression) throws IOException {
        this(new PrimitivOHybrid(Paths.get(fileName)), encoderThreads, alignmentsInBlock, highCompression);
    }

    public VDJCAlignmentsWriter(File file) throws IOException {
        this(file.toPath());
    }

    public VDJCAlignmentsWriter(Path file) throws IOException {
        this(file.toFile(), DEFAULT_ENCODER_THREADS, DEFAULT_ALIGNMENTS_IN_BLOCK);
    }

    public VDJCAlignmentsWriter(File file, int encoderThreads, int alignmentsInBlock) throws IOException {
        this(new PrimitivOHybrid(file.toPath()), encoderThreads, alignmentsInBlock, false);
    }

    public VDJCAlignmentsWriter(Path file, int encoderThreads, int alignmentsInBlock, boolean highCompression) throws IOException {
        this(new PrimitivOHybrid(file), encoderThreads, alignmentsInBlock, highCompression);
    }

    public VDJCAlignmentsWriter(PrimitivOHybrid output, int encoderThreads, int alignmentsInBlock, boolean highCompression) {
        this.highCompression = highCompression;
        this.output = output;
        this.encoderThreads = encoderThreads;
        this.alignmentsInBlock = alignmentsInBlock;
    }

    @Override
    public void setNumberOfProcessedReads(long numberOfProcessedReads) {
        this.numberOfProcessedReads = numberOfProcessedReads;
    }

    @Override
    public void inheritHeaderAndFooterFrom(VDJCAlignmentsReader reader) {
        writeHeader(reader.getHeader(), reader.getUsedGenes());
        setFooter(reader.getFooter());
    }

    /** Header saved for alignment objects validation */
    private MiXCRHeader header = null;

    @Override
    public void writeHeader(MiXCRHeader header, List<VDJCGene> genes) {
        if (header == null || genes == null)
            throw new IllegalArgumentException();

        if (this.header != null)
            throw new IllegalStateException("Header already written.");

        this.header = header;

        // Writing metadata using raw stream for easy reconstruction with simple tools like hex viewers
        try (PrimitivO o = output.beginPrimitivO(true)) {
            // Writing magic bytes
            assert MAGIC_BYTES.length == MAGIC_LENGTH;
            o.write(MAGIC_BYTES);

            // Writing version information
            o.writeUTF(
                    MiXCRVersionInfo.get().getVersionString(
                            AppVersionInfo.OutputType.ToFile));

            // Writing parameters
            o.writeObject(header);

            IOUtil.stdVDJCPrimitivOStateInit(o, genes, header.getFeaturesToAlign());
        }

        writer = output.beginPrimitivOBlocks(encoderThreads, alignmentsInBlock,
                highCompression
                        ? PrimitivIOBlocksUtil.highLZ4Compressor()
                        : PrimitivIOBlocksUtil.fastLZ4Compressor());
    }

    @Override
    public long getPosition() {
        return output.getPosition();
    }

    public int getEncodersCount() {
        if (writer == null)
            return 0;
        return writer.getParent().getStats().getConcurrency();
    }

    public int getBusyEncoders() {
        if (writer == null)
            return 0;
        PrimitivOBlocksStats stats = writer.getParent().getStats();
        return stats.getOngoingSerdes() + stats.getOngoingIOOps();
    }

    @Override
    public synchronized void write(VDJCAlignments alignment) {
        if (writer == null)
            throw new IllegalStateException("Header not initialized.");

        if (alignment == null)
            throw new NullPointerException();

        if (alignment.getTagCount().depth() != header.getTagsInfo().size())
            throw new IllegalStateException("Inconsistent tags in alignment and file header.");

        numberOfAlignments++;
        writer.write(alignment);
    }

    @Override
    public void setFooter(MiXCRFooter footer) {
        this.footer = footer;
    }

    @Override
    public synchronized void close() {
        if (footer == null)
            throw new IllegalStateException("Footer not set");

        try {
            if (!closed) {
                writer.close(); // This will also write stream termination symbol/block to the stream

                // Printing IO stat
                if (MiXCRDebug.DEBUG)
                    System.out.println(writer.getParent().getStats());

                long footerStartPosition = output.getPosition();
                try (PrimitivO o = output.beginPrimitivO()) {
                    // Writing footer
                    o.writeObject(footer);

                    // Total size = 8 + 8 + 8 + END_MAGIC_LENGTH = FIXED_FOOTER_LENGTH
                    o.writeLong(footerStartPosition);
                    o.writeLong(numberOfAlignments);
                    o.writeLong(numberOfProcessedReads);

                    // Writing end-magic as a file integrity sign
                    o.write(IOUtil.getEndMagicBytes());
                } finally {
                    closed = true;
                    output.close();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isClosed() {
        return closed;
    }
}
