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
import com.milaboratory.mixcr.basictypes.tag.TagsInfo;
import com.milaboratory.mixcr.util.MiXCRDebug;
import com.milaboratory.mixcr.util.MiXCRVersionInfo;
import com.milaboratory.mixcr.vdjaligners.VDJCAligner;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
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
import java.nio.file.Paths;
import java.util.List;

public final class VDJCAlignmentsWriter implements VDJCAlignmentsWriterI, HasPosition {
    public static final int DEFAULT_ENCODER_THREADS = 3;
    public static final int DEFAULT_ALIGNMENTS_IN_BLOCK = 1 << 10; // 805-1024 bytes per alignment
    static final String MAGIC_V17 = "MiXCR.VDJC.V17";
    static final String MAGIC = MAGIC_V17;
    static final int MAGIC_LENGTH = 14;
    static final byte[] MAGIC_BYTES = MAGIC.getBytes(StandardCharsets.US_ASCII);

    /** Number of bytes in footer with meta information */
    static final int FOOTER_LENGTH = 8 + 8 + IOUtil.END_MAGIC_LENGTH;

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
        this(file, DEFAULT_ENCODER_THREADS, DEFAULT_ALIGNMENTS_IN_BLOCK);
    }

    public VDJCAlignmentsWriter(File file, int encoderThreads, int alignmentsInBlock) throws IOException {
        this(new PrimitivOHybrid(file.toPath()), encoderThreads, alignmentsInBlock, false);
    }

    public VDJCAlignmentsWriter(File file, int encoderThreads, int alignmentsInBlock, boolean highCompression) throws IOException {
        this(new PrimitivOHybrid(file.toPath()), encoderThreads, alignmentsInBlock, highCompression);
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

    public void header(VDJCAlignmentsReader reader, TagsInfo tagsInfo) {
        header(reader.getParameters(), reader.getUsedGenes(), tagsInfo);
    }

    public void header(VDJCAligner aligner, TagsInfo tagsInfo) {
        header(aligner.getParameters(), aligner.getUsedGenes(), tagsInfo);
    }

    @Override
    public void header(VDJCAlignerParameters parameters, List<VDJCGene> genes, TagsInfo tags) {
        if (parameters == null || genes == null)
            throw new IllegalArgumentException();

        if (writer != null)
            throw new IllegalStateException("Header already written.");

        // Writing meta data using raw stream for easy reconstruction with simple tools like hex viewers
        try (PrimitivO o = output.beginPrimitivO(true)) {
            // Writing magic bytes
            assert MAGIC_BYTES.length == MAGIC_LENGTH;
            o.write(MAGIC_BYTES);

            // Writing version information
            o.writeUTF(
                    MiXCRVersionInfo.get().getVersionString(
                            AppVersionInfo.OutputType.ToFile));

            // Writing parameters
            o.writeObject(parameters);

            // Information about tags
            o.writeObject(tags);

            IOUtil.stdVDJCPrimitivOStateInit(o, genes, parameters);
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

        numberOfAlignments++;
        writer.write(alignment);
    }

    @Override
    public synchronized void close() {
        try {
            if (!closed) {
                writer.close(); // This will also write stream termination symbol/block to the stream

                // Printing IO stat
                if (MiXCRDebug.DEBUG)
                    System.out.println(writer.getParent().getStats());

                try (PrimitivO o = output.beginPrimitivO()) {
                    // // [ numberOfProcessedReads : long ]
                    // byte[] footer = new byte[8];
                    //
                    // // Number of processed reads is known only in the end of analysis
                    // // Writing it as last piece of information in the stream
                    // AlignmentsIO.writeLongBE(numberOfProcessedReads, footer, 0);
                    // o.write(footer);

                    // Total size = 8 + 8 + END_MAGIC_LENGTH

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
