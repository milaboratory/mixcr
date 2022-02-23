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

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPort;
import cc.redberry.pipe.OutputPortCloseable;
import com.milaboratory.cli.PipelineConfiguration;
import com.milaboratory.mixcr.assembler.CloneAssemblerParameters;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import com.milaboratory.primitivio.PrimitivI;
import com.milaboratory.primitivio.blocks.*;
import com.milaboratory.util.CanReportProgress;
import com.milaboratory.util.LambdaSemaphore;
import gnu.trove.map.hash.TIntIntHashMap;
import io.repseq.core.GeneFeature;
import io.repseq.core.VDJCGene;
import io.repseq.core.VDJCLibraryRegistry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Reader of CLNA file format.
 */
public final class ClnAReader extends PipelineConfigurationReaderMiXCR implements CloneReader, AutoCloseable {
    final PrimitivIHybrid input;

    // Index data

    final long firstClonePosition;
    /** last element = position of the last alignments block end */
    final long[] index;
    /** First record always zero */
    final long[] counts;
    /**
     * cloneId -> index in index e.g. alignments for clone with id0 starts from position index[cloneMapping.get(id0)]
     */
    final TIntIntHashMap cloneIdIndex;
    final long totalAlignmentsCount;

    // From constructor

    final VDJCLibraryRegistry libraryRegistry;

    // Read form file header

    final PipelineConfiguration configuration;
    final VDJCAlignerParameters alignerParameters;
    final CloneAssemblerParameters assemblerParameters;
    final VDJCSProperties.CloneOrdering ordering;

    final List<VDJCGene> genes;

    final int numberOfClones;

    // Meta data (also from header)

    final String versionInfo;

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

            // Reading number of clones
            this.numberOfClones = ii.readInt();
        }

        // File ending
        long indexBegin;
        try (PrimitivI pi = this.input.beginRandomAccessPrimitivI(-IOUtil.END_MAGIC_LENGTH - 16)) {
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
                case ClnAWriter.MAGIC:
                    break;
                default:
                    throw new IllegalStateException();
            }

            this.versionInfo = pi.readUTF();
            this.configuration = pi.readObject(PipelineConfiguration.class);
            this.alignerParameters = pi.readObject(VDJCAlignerParameters.class);
            this.assemblerParameters = pi.readObject(CloneAssemblerParameters.class);
            this.ordering = pi.readObject(VDJCSProperties.CloneOrdering.class);
            this.genes = IOUtil.stdVDJCPrimitivIStateInit(pi, this.alignerParameters, libraryRegistry);
        }
    }

    public ClnAReader(String path, VDJCLibraryRegistry libraryRegistry, int concurrency) throws IOException {
        this(Paths.get(path), libraryRegistry, concurrency);
    }

    @Override
    public PipelineConfiguration getPipelineConfiguration() {
        return configuration;
    }

    /**
     * Aligner parameters
     */
    public VDJCAlignerParameters getAlignerParameters() {
        return alignerParameters;
    }

    /**
     * Clone assembler parameters
     */
    public CloneAssemblerParameters getAssemblerParameters() {
        return assemblerParameters;
    }

    /**
     * Clone ordering
     */
    @Override
    public VDJCSProperties.CloneOrdering ordering() {
        return ordering;
    }

    public GeneFeature[] getAssemblingFeatures() {
        return assemblerParameters.getAssemblingFeatures();
    }

    /**
     * Returns number of clones in the file
     */
    @Override
    public int numberOfClones() {
        return numberOfClones;
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
        return counts[cloneIdIndex.get(cloneIndex) + 1];
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
        // Reading clones
        int count = numberOfClones();
        List<Clone> clones = new ArrayList<>(count);

        try (PrimitivIBlocks<Clone>.Reader reader = input.beginPrimitivIBlocks(Clone.class)) {
            for (int i = 0; i < count; i++)
                clones.add(reader.take());
        }

        return new CloneSet(clones, genes, alignerParameters, assemblerParameters, ordering);
    }

    /**
     * Constructs output port to read clones one by one as a stream
     */
    @Override
    public OutputPortCloseable<Clone> readClones() {
        return input.beginPrimitivIBlocks(Clone.class);
    }

    /**
     * Constructs output port to read alignments for a specific clone, or read unassembled alignments block
     *
     * @param cloneIndex index of clone; -1 to read unassembled alignments
     */
    public OutputPortCloseable<VDJCAlignments> readAlignmentsOfClone(int cloneIndex) {
        if (cloneIndex == -1 && !cloneIdIndex.containsKey(-1))
            return CUtils.EMPTY_OUTPUT_PORT_CLOSEABLE;
        return input.beginRandomAccessPrimitivIBlocks(VDJCAlignments.class,
                index[cloneIdIndex.get(cloneIndex)],
                HEADER_ACTION_STOP_AT_ALIGNMENT_BLOCK_END);
    }

    /**
     * Constructs output port to read all alignments form the file. Alignments are sorted by cloneIndex.
     */
    public OutputPortCloseable<VDJCAlignments> readAllAlignments() {
        return input.beginRandomAccessPrimitivIBlocks(VDJCAlignments.class, index[0]);
    }

    /**
     * Constructs output port to read alignments that are not attached to any clone. Alignments are sorted by
     * cloneIndex.
     *
     * Returns: readAlignmentsOfClone(-1)
     */
    public OutputPortCloseable<VDJCAlignments> readNotAssembledAlignments() {
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
            implements OutputPort<CloneAlignments>, CanReportProgress {
        private final AtomicLong processedAlignments = new AtomicLong();
        private final CloneSet fakeCloneSet;
        private final PrimitivIBlocks<Clone>.Reader clones;
        volatile boolean isFinished = false;

        CloneAlignmentsPort() {
            this.clones = input.beginRandomAccessPrimitivIBlocks(Clone.class, firstClonePosition);
            this.fakeCloneSet = new CloneSet(Collections.EMPTY_LIST, genes, alignerParameters, assemblerParameters, ordering);
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
