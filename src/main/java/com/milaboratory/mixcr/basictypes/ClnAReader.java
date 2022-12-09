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

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPort;
import com.milaboratory.mixcr.cli.ApplicationException;
import com.milaboratory.mixcr.util.BackwardCompatibilityUtils;
import com.milaboratory.primitivio.PrimitivI;
import com.milaboratory.primitivio.blocks.*;
import com.milaboratory.util.LambdaSemaphore;
import com.milaboratory.util.OutputPortWithProgress;
import gnu.trove.map.hash.TIntIntHashMap;
import io.repseq.core.GeneFeature;
import io.repseq.core.VDJCGene;
import io.repseq.core.VDJCLibraryId;
import io.repseq.core.VDJCLibraryRegistry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Reader of CLNA file format.
 */
public final class ClnAReader implements CloneReader, AutoCloseable {
    final PrimitivIHybrid input;

    // Index data

    final long firstClonePosition;
    /**
     * last element = position of the last alignments block end
     */
    final long[] index;
    /**
     * First record always zero
     */
    final long[] counts;
    /**
     * cloneId -> index in index e.g. alignments for clone with id0 starts from position index[cloneMapping.get(id0)]
     */
    final TIntIntHashMap cloneIdIndex;
    final long totalAlignmentsCount;

    // From constructor

    final VDJCLibraryRegistry libraryRegistry;

    // Read form file header

    final MiXCRHeader header;
    final VDJCSProperties.CloneOrdering ordering;
    final MiXCRFooter footer;

    final List<VDJCGene> usedGenes;

    final int numberOfClones;

    // Meta data (also from header)

    final String versionInfo;

    private final long footerStartPosition;

    public ClnAReader(Path path, VDJCLibraryRegistry libraryRegistry, int concurrency) throws IOException {
        this(path, libraryRegistry, new LambdaSemaphore(concurrency));
    }

    public ClnAReader(Path path, VDJCLibraryRegistry libraryRegistry, LambdaSemaphore concurrencyLimiter) throws IOException {
        this.input = new PrimitivIHybrid(path, concurrencyLimiter);

        this.libraryRegistry = libraryRegistry;

        // File beginning
        String magicString;
        try (PrimitivI ii = this.input.beginPrimitivI()) {
            // Reading magic string
            byte[] magicBytes = new byte[ClnAWriter.MAGIC_LENGTH];
            ii.readFully(magicBytes);
            magicString = new String(magicBytes, StandardCharsets.US_ASCII);
            switch (magicString) {
                case ClnAWriter.MAGIC_V9: // see below for custom serializers registration
                case ClnAWriter.MAGIC_V10: // see below for custom serializers registration
                case ClnAWriter.MAGIC_V11: // see below for custom serializers registration
                case ClnAWriter.MAGIC:
                    break;
                default:
                    throw new ApplicationException("Unexpected file version of format: found " +
                            magicString + " expected " + ClnAWriter.MAGIC, false);
            }

            // Reading number of clones
            this.numberOfClones = ii.readInt();
        }

        // File ending
        long indexBegin;
        try (PrimitivI pi = this.input.beginRandomAccessPrimitivI(-ClnAWriter.FOOTER_LENGTH)) {
            this.footerStartPosition = pi.readLong();
            // Reading key file offsets from last 16 bytes of the file
            this.firstClonePosition = pi.readLong();
            indexBegin = pi.readLong();

            // Checking file consistency
            byte[] endMagic = new byte[IOUtil.END_MAGIC_LENGTH];
            pi.readFully(endMagic);
            if (!Arrays.equals(IOUtil.getEndMagicBytes(), endMagic))
                throw new RuntimeException("Corrupted file.");
        }

        // TODO move index deserialization to lazy initialization, there are use-cases which need only meta-information from the reader

        // Step back
        try (PrimitivI pi = this.input.beginRandomAccessPrimitivI(indexBegin)) {
            int indexSize = pi.readVarInt();

            assert indexSize == numberOfClones + 1 || indexSize == numberOfClones + 2;

            // Reading index data
            this.index = new long[indexSize];
            this.counts = new long[indexSize];
            this.cloneIdIndex = new TIntIntHashMap(indexSize - 1);
            long previousValue = 0;
            long totalAlignmentsCount = 0;
            for (int i = 0; i < indexSize; i++) {
                previousValue = index[i] = previousValue + pi.readVarLong();
                totalAlignmentsCount += counts[i] = pi.readVarLong();
                if (i != indexSize - 1)
                    cloneIdIndex.put(pi.readVarInt(), i);
            }
            this.totalAlignmentsCount = totalAlignmentsCount;
        }

        // Returning to the file begin
        try (PrimitivI pi = this.input.beginPrimitivI(true)) {
            switch (magicString) {
                case ClnAWriter.MAGIC_V9:
                    BackwardCompatibilityUtils.register41rc2Serializers(pi.getSerializersManager());
                    break;
                case ClnAWriter.MAGIC_V10:
                    BackwardCompatibilityUtils.register41_0Serializers(pi.getSerializersManager());
                    break;
                case ClnAWriter.MAGIC_V11:
                    BackwardCompatibilityUtils.register41_1Serializers(pi.getSerializersManager());
                    break;
                case ClnAWriter.MAGIC:
                    break;
                default:
                    throw new IllegalStateException();
            }

            this.versionInfo = pi.readUTF();
            this.header = Objects.requireNonNull(pi.readObject(MiXCRHeader.class));
            this.ordering = pi.readObject(VDJCSProperties.CloneOrdering.class);

            MiXCRHeader.FoundAlleles foundAlleles = header.getFoundAlleles();
            if (foundAlleles != null) {
                VDJCLibraryId foundAllelesLibraryId = foundAlleles.getLibraryIdWithoutChecksum();
                boolean alreadyRegistered = libraryRegistry.getLoadedLibraries()
                        .stream()
                        .anyMatch(it -> it.getLibraryId().withoutChecksum().equals(foundAllelesLibraryId));
                if (!alreadyRegistered) {
                    libraryRegistry.registerLibrary(null, foundAlleles.getLibraryName(), foundAlleles.getLibraryData());
                }
            }

            this.usedGenes = IOUtil.stdVDJCPrimitivIStateInit(pi, this.header.getFeaturesToAlign(), libraryRegistry);
        }

        // read reports from footer
        try (PrimitivI pi = this.input.beginRandomAccessPrimitivI(footerStartPosition)) {
            footer = pi.readObject(MiXCRFooter.class);
        }
    }

    public ClnAReader(String path, VDJCLibraryRegistry libraryRegistry, int concurrency) throws IOException {
        this(Paths.get(path), libraryRegistry, concurrency);
    }

    @Override
    public MiXCRHeader getHeader() {
        return header;
    }

    /**
     * Clone ordering
     */
    @Override
    public VDJCSProperties.CloneOrdering ordering() {
        return ordering;
    }

    public GeneFeature[] getAssemblingFeatures() {
        return header.getAssemblerParameters().getAssemblingFeatures();
    }

    /**
     * Returns number of clones in the file
     */
    @Override
    public int numberOfClones() {
        return numberOfClones;
    }

    @Override
    public List<VDJCGene> getUsedGenes() {
        return usedGenes;
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
        return counts[cloneIdIndex.get(cloneIndex) + 1];
    }

    /**
     * MiXCR version this file was produced with.
     */
    public String getVersionInfo() {
        return versionInfo;
    }

    @Override
    public MiXCRFooter getFooter() {
        return footer;
    }

    /**
     * Read clone set completely
     */
    public CloneSet readCloneSet() {
        // Reading clones
        int count = numberOfClones();
        List<Clone> clones = new ArrayList<>(count);

        try (PrimitivIBlocks<Clone>.Reader reader = input.beginPrimitivIBlocks(Clone.class)) {
            for (int i = 0; i < count; i++)
                clones.add(reader.take());
        }

        return new CloneSet(clones, usedGenes, header, footer, ordering);
    }

    /**
     * Constructs output port to read clones one by one as a stream
     */
    @Override
    public OutputPort<Clone> readClones() {
        return input.beginRandomAccessPrimitivIBlocks(Clone.class, firstClonePosition);
    }

    /**
     * Constructs output port to read alignments for a specific clone, or read unassembled alignments block
     *
     * @param cloneIndex index of clone; -1 to read unassembled alignments
     */
    public OutputPort<VDJCAlignments> readAlignmentsOfClone(int cloneIndex) {
        if (cloneIndex == -1 && !cloneIdIndex.containsKey(-1))
            return CUtils.EMPTY_OUTPUT_PORT;
        return input.beginRandomAccessPrimitivIBlocks(VDJCAlignments.class,
                index[cloneIdIndex.get(cloneIndex)],
                HEADER_ACTION_STOP_AT_ALIGNMENT_BLOCK_END);
    }

    /**
     * Constructs output port to read all alignments form the file. Alignments are sorted by cloneIndex.
     */
    public OutputPort<VDJCAlignments> readAllAlignments() {
        return input.beginRandomAccessPrimitivIBlocks(VDJCAlignments.class, index[0]);
    }

    /**
     * Constructs output port to read alignments that are not attached to any clone. Alignments are sorted by
     * cloneIndex.
     * <p>
     * Returns: readAlignmentsOfClone(-1)
     */
    public OutputPort<VDJCAlignments> readNotAssembledAlignments() {
        return readAlignmentsOfClone(-1);
    }

    /**
     * Constructs output port of CloneAlignments objects, that allows to get synchronised view on clone and it's
     * corresponding alignments
     */
    public CloneAlignmentsPort clonesAndAlignments() {
        return new CloneAlignmentsPort();
    }

    public final class CloneAlignmentsPort
            implements OutputPortWithProgress<CloneAlignments> {
        private final AtomicLong processedAlignments = new AtomicLong();
        private final CloneSet fakeCloneSet;
        private final PrimitivIBlocks<Clone>.Reader clones;
        volatile boolean isFinished = false;

        CloneAlignmentsPort() {
            this.clones = input.beginRandomAccessPrimitivIBlocks(Clone.class, firstClonePosition);
            this.fakeCloneSet = new CloneSet(Collections.EMPTY_LIST, usedGenes, header, footer, ordering);
        }

        @Override
        public CloneAlignments take() {
            Clone clone = clones.take();
            if (clone == null) {
                isFinished = true;
                return null;
            }
            clone.setParentCloneSet(fakeCloneSet);
            CloneAlignments result = new CloneAlignments(clone, clone.id);
            processedAlignments.addAndGet(result.alignmentsCount);
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
            this.alignmentsCount = numberOfAlignmentsInClone(cloneId);
        }

        /**
         * Alignments
         */
        public OutputPort<VDJCAlignments> alignments() {
            return readAlignmentsOfClone(cloneId);
        }
    }

    @Override
    public void close() throws IOException {
        input.close();
    }

    private static final Function<PrimitivIOBlockHeader, PrimitivIHeaderAction<VDJCAlignments>> HEADER_ACTION_STOP_AT_ALIGNMENT_BLOCK_END =
            h -> h.equals(ClnAWriter.ALIGNMENT_BLOCK_SEPARATOR)
                    ? PrimitivIHeaderActions.stopReading()
                    : PrimitivIHeaderActions.skip();
}
