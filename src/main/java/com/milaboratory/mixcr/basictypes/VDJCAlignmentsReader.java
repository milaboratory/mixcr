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

import cc.redberry.pipe.OutputPortCloseable;
import com.milaboratory.mixcr.assembler.AlignmentsProvider;
import com.milaboratory.mixcr.basictypes.tag.TagsInfo;
import com.milaboratory.mixcr.cli.MiXCRCommandReport;
import com.milaboratory.mixcr.cli.MiXCRReport;
import com.milaboratory.mixcr.util.OutputPortWithProgress;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import com.milaboratory.primitivio.PrimitivI;
import com.milaboratory.primitivio.PrimitivIState;
import com.milaboratory.primitivio.blocks.PrimitivIBlocks;
import com.milaboratory.primitivio.blocks.PrimitivIBlocksStats;
import com.milaboratory.primitivio.blocks.PrimitivIHybrid;
import com.milaboratory.util.CanReportProgress;
import io.repseq.core.VDJCGene;
import io.repseq.core.VDJCLibraryRegistry;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicLong;

import static com.milaboratory.mixcr.basictypes.VDJCAlignmentsWriter.*;

public final class VDJCAlignmentsReader implements
        OutputPortCloseable<VDJCAlignments>,
        AlignmentsProvider,
        VDJCFileHeaderData,
        ReportsFooterData,
        CanReportProgress {
    public static final int DEFAULT_CONCURRENCY = 4;
    public static final int DEFAULT_READ_AHEAD_BLOCKS = 5;

    final PrimitivIHybrid input;
    final int readAheadBlocks;
    final VDJCLibraryRegistry vdjcRegistry;

    /** Start position of the payload in the input file, used to create secondary readers */
    long alignmentsBegin;
    PrimitivIBlocks<VDJCAlignments>.Reader reader;

    MiXCRMetaInfo info = null;
    List<VDJCGene> usedGenes;

    private List<MiXCRCommandReport> reports;

    PrimitivIState iState;

    String versionInfo;
    String magic;
    long counter = 0;
    final long reportsStartPosition, numberOfAlignments, numberOfReads;
    boolean closed = false;

    public VDJCAlignmentsReader(String fileName) throws IOException {
        this(fileName, VDJCLibraryRegistry.getDefault());
    }

    public VDJCAlignmentsReader(String fileName, VDJCLibraryRegistry vdjcRegistry) throws IOException {
        this(fileName, vdjcRegistry, DEFAULT_CONCURRENCY);
    }

    public VDJCAlignmentsReader(String fileName, VDJCLibraryRegistry vdjcRegistry, int concurrency) throws IOException {
        this(Paths.get(fileName), vdjcRegistry, concurrency);
    }

    public VDJCAlignmentsReader(File file) throws IOException {
        this(file, VDJCLibraryRegistry.getDefault());
    }

    public VDJCAlignmentsReader(File file, VDJCLibraryRegistry vdjcRegistry) throws IOException {
        this(file, vdjcRegistry, DEFAULT_CONCURRENCY);
    }

    public VDJCAlignmentsReader(File file, VDJCLibraryRegistry vdjcRegistry, int concurrency) throws IOException {
        this(file.toPath(), vdjcRegistry, concurrency);
    }

    public VDJCAlignmentsReader(Path path) throws IOException {
        this(path, VDJCLibraryRegistry.getDefault());
    }

    public VDJCAlignmentsReader(Path path, VDJCLibraryRegistry vdjcRegistry) throws IOException {
        this(path, vdjcRegistry, DEFAULT_CONCURRENCY);
    }

    public VDJCAlignmentsReader(Path path, VDJCLibraryRegistry vdjcRegistry, int concurrency) throws IOException {
        this(path, vdjcRegistry, concurrency, ForkJoinPool.commonPool());
    }

    public VDJCAlignmentsReader(Path path, VDJCLibraryRegistry vdjcRegistry, int concurrency, ExecutorService executor) throws IOException {
        this(path, vdjcRegistry, concurrency, executor, DEFAULT_READ_AHEAD_BLOCKS);
    }

    public VDJCAlignmentsReader(Path path, VDJCLibraryRegistry vdjcRegistry, int concurrency, ExecutorService executor, int readAheadBlocks) throws IOException {
        this.input = new PrimitivIHybrid(executor, path, concurrency);
        this.readAheadBlocks = readAheadBlocks;
        this.vdjcRegistry = vdjcRegistry;

        try (PrimitivI i = input.beginRandomAccessPrimitivI(-FOOTER_LENGTH)) {
            reportsStartPosition = i.readLong();
            numberOfAlignments = i.readLong();
            numberOfReads = i.readLong();
            if (!Arrays.equals(i.readBytes(IOUtil.END_MAGIC_LENGTH), IOUtil.getEndMagicBytes()))
                throw new IOException("Malformed file, END_MAGIC mismatch.");
        }
    }

    public void ensureInitialized() {
        if (reader != null)
            return;

        try (final PrimitivI pi = input.beginPrimitivI(true)) {
            assert MAGIC_BYTES.length == MAGIC_LENGTH;
            byte[] magic = new byte[MAGIC_LENGTH];
            pi.readFully(magic);
            String magicString = new String(magic);
            this.magic = magicString;

            // SerializersManager serializersManager = input.getSerializersManager();
            switch (magicString) {
                case MAGIC:
                    break;
                default:
                    throw new RuntimeException("Unsupported file format; .vdjca file of version " + new String(magic)
                            + " while you are running MiXCR " + MAGIC);
            }

            versionInfo = pi.readUTF();

            info = Objects.requireNonNull(pi.readObject(MiXCRMetaInfo.class));

            this.usedGenes = IOUtil.stdVDJCPrimitivIStateInit(pi, info.getAlignerParameters(), vdjcRegistry);
            this.iState = pi.getState();
        }

        // Saving alignments begin position
        alignmentsBegin = input.getPosition();

        try (PrimitivI pi = input.beginRandomAccessPrimitivI(reportsStartPosition)) {
            reports = new ArrayList<>();
            int nReports = pi.readInt();
            for (int i = 0; i < nReports; i++) {
                reports.add((MiXCRCommandReport) pi.readObject(MiXCRReport.class));
            }
        }

        this.reader = input.beginRandomAccessPrimitivIBlocks(VDJCAlignments.class, alignmentsBegin, readAheadBlocks);
    }

    public PrimitivIBlocksStats getStats() {
        if (reader == null)
            return null;
        return reader.getParent().getStats();
    }

    public MiXCRMetaInfo getInfo() {
        ensureInitialized();
        return info;
    }

    public synchronized VDJCAlignerParameters getParameters() {
        ensureInitialized();
        return info.getAlignerParameters();
    }

    public synchronized List<VDJCGene> getUsedGenes() {
        ensureInitialized();
        return usedGenes;
    }

    @Override
    public TagsInfo getTagsInfo() {
        ensureInitialized();
        return info.getTagsInfo();
    }

    /**
     * Returns information about version of MiXCR which produced this file.
     *
     * @return information about version of MiXCR which produced this file
     */
    public String getVersionInfo() {
        ensureInitialized();
        return versionInfo;
    }

    @Override
    public List<MiXCRCommandReport> reports() {
        ensureInitialized();
        return reports;
    }

    /**
     * Returns magic bytes of this file.
     *
     * @return magic bytes of this file
     */
    public String getMagic() {
        ensureInitialized();
        return magic;
    }

    /** Return primitivI state that can be used to read or write alignments returned by the reader */
    public PrimitivIState getIState() {
        ensureInitialized();
        return iState;
    }

    public long getNumberOfAlignments() {
        return numberOfAlignments;
    }

    @Override
    public long getNumberOfReads() {
        return numberOfReads;
    }

    @Override
    public double getProgress() {
        if (numberOfAlignments == 0)
            return Double.NaN;
        return (1.0 * counter) / numberOfAlignments;
    }

    @Override
    public boolean isFinished() {
        return closed || counter == numberOfAlignments;
    }

    @Override
    public synchronized void close() {
        close(false);
    }

    private void close(boolean onEnd) {
        if (closed)
            return;

        try {
            // Closing blocked reader
            reader.close();
            input.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            closed = true;
        }
    }

    @Override
    public synchronized VDJCAlignments take() {
        if (closed)
            return null;

        ensureInitialized();

        VDJCAlignments al = reader.take();

        if (al == null) {
            // close(true);
            return null;
        }

        return al.setAlignmentsIndex(counter++);
    }

    @Override
    public SecondaryReader readAlignments() {
        ensureInitialized();
        return new SecondaryReader(false);
    }

    public SecondaryReader createRawSecondaryReader() {
        ensureInitialized();
        return new SecondaryReader(true);
    }

    public class SecondaryReader implements OutputPortWithProgress<VDJCAlignments> {
        private final boolean raw;
        private final Object sync = new Object();
        private volatile boolean finished = false;
        private final AtomicLong counter = new AtomicLong();
        private final PrimitivIBlocks<VDJCAlignments>.Reader reader;

        private SecondaryReader(boolean raw) {
            this.raw = raw;
            this.reader = input.beginRandomAccessPrimitivIBlocks(VDJCAlignments.class, alignmentsBegin);
        }

        @Override
        public VDJCAlignments take() {
            if (raw) {
                VDJCAlignments al = reader.take();
                if (al == null) {
                    finished = true;
                    return null;
                }
                counter.incrementAndGet();
                return al;
            } else {
                synchronized (sync) {
                    VDJCAlignments al = reader.take();
                    if (al == null) {
                        finished = true;
                        return null;
                    }
                    return al.setAlignmentsIndex(counter.getAndIncrement());
                }
            }
        }

        @Override
        public long currentIndex() {
            return counter.get();
        }

        @Override
        public double getProgress() {
            return 1.0 * counter.get() / numberOfAlignments;
        }

        @Override
        public boolean isFinished() {
            return finished;
        }

        @Override
        public synchronized void close() {
            reader.close();
            finished = true;
        }
    }
}
