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

import cc.redberry.pipe.OutputPort;
import cc.redberry.pipe.OutputPortCloseable;
import cc.redberry.pipe.util.CountingOutputPort;
import com.milaboratory.cli.AppVersionInfo;
import com.milaboratory.mixcr.util.MiXCRDebug;
import com.milaboratory.mixcr.util.MiXCRVersionInfo;
import com.milaboratory.primitivio.PrimitivIOStateBuilder;
import com.milaboratory.primitivio.PrimitivO;
import com.milaboratory.primitivio.blocks.PrimitivIOBlockHeader;
import com.milaboratory.primitivio.blocks.PrimitivIOBlocksUtil;
import com.milaboratory.primitivio.blocks.PrimitivOBlocks;
import com.milaboratory.primitivio.blocks.PrimitivOHybrid;
import com.milaboratory.util.CanReportProgressAndStage;
import com.milaboratory.util.TempFileDest;
import com.milaboratory.util.io.HasPosition;
import com.milaboratory.util.sorting.HashSorter;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.list.array.TLongArrayList;
import gnu.trove.set.hash.TIntHashSet;
import io.repseq.core.VDJCGene;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ForkJoinPool;

import static com.milaboratory.mixcr.basictypes.FieldCollection.VDJCACloneIdComparator;
import static com.milaboratory.mixcr.basictypes.FieldCollection.VDJCACloneIdHash;
import static com.milaboratory.mixcr.basictypes.IOUtil.MAGIC_CLNA;

/**
 * Writer for CLNA file format.
 * <p>
 * Usage: 1. Constructor (opens the output file, buffered) 2. writeClones() 3. sortAlignments() 4.
 * writeAlignmentsAndIndex() 5. close()
 */
public final class ClnAWriter implements
        AutoCloseable,
        CanReportProgressAndStage {
    static final String MAGIC_V9 = MAGIC_CLNA + ".V09";
    static final String MAGIC = MAGIC_V9;
    static final int MAGIC_LENGTH = MAGIC.length(); //14
    /** Number of bytes in footer with meta information */
    static final int FOOTER_LENGTH = 8 + 8 + 8 + IOUtil.END_MAGIC_LENGTH;

    /**
     * Separates blocks of alignments assigned to the same clonotype
     */
    static final PrimitivIOBlockHeader ALIGNMENT_BLOCK_SEPARATOR = PrimitivIOBlockHeader.specialHeader().setSpecialByte(0, (byte) 1);

    private final Object sync = new Object();

    /**
     * Will be used for alignments pre-sorting
     */
    private final TempFileDest tempDest;

    private final boolean highCompression;

    private final PrimitivOHybrid output;

    /**
     * Counter OP used to report progress during stage 2
     */
    private volatile CountingOutputPort<VDJCAlignments> toCollator;
    private volatile int numberOfClones = -1, numberOfClonesWritten = 0;
    /**
     * Clone ids written to the clones section
     */
    private volatile TIntHashSet cloneIds = null;
    private volatile HashSorter<VDJCAlignments> collator;
    private volatile OutputPortCloseable<VDJCAlignments> collatedAlignments = null;
    private volatile long numberOfAlignments = -1, numberOfAlignmentsWritten = 0;
    private volatile boolean finished = false;

    public ClnAWriter(Path path, TempFileDest tempDest, boolean highCompression) throws IOException {
        this(path.toFile(), tempDest, highCompression);
    }

    public ClnAWriter(File file, TempFileDest tempDest) throws IOException {
        this(file, tempDest, false);
    }

    public ClnAWriter(Path file, TempFileDest tempDest) throws IOException {
        this(file.toFile(), tempDest, false);
    }

    public ClnAWriter(File file, TempFileDest tempDest, boolean highCompression) throws IOException {
        this.highCompression = highCompression;
        this.tempDest = tempDest;
        this.output = new PrimitivOHybrid(ForkJoinPool.commonPool(), file.toPath());
        try (PrimitivO o = this.output.beginPrimitivO()) {
            o.write(MAGIC.getBytes(StandardCharsets.US_ASCII));
        }
    }

    private long positionOfFirstClone = -1;

    private List<VDJCGene> usedGenes = null;
    private HasFeatureToAlign featureToAlignProvider = null;

    /**
     * Step 1
     */
    public void writeClones(CloneSet cloneSet) {
        synchronized (sync) {
            // Checking state
            if (cloneIds != null)
                throw new IllegalArgumentException("Clone block was already written.");

            // Saving VDJC gene list
            this.usedGenes = cloneSet.getUsedGenes();

            // Writing header in raw primitivio mode
            try (PrimitivO o = this.output.beginPrimitivO(true)) {

                // Writing number of clones ahead of any other content to make it available
                // in a known file position (MAGIC_LENGTH)
                o.writeInt(cloneSet.size());

                // Writing version information
                o.writeUTF(MiXCRVersionInfo.get()
                        .getVersionString(AppVersionInfo.OutputType.ToFile));

                // Writing header meta-info
                o.writeObject(Objects.requireNonNull(cloneSet.header));
                featureToAlignProvider = cloneSet.getAlignmentParameters();

                // Writing clone-set ordering
                o.writeObject(cloneSet.ordering);

                // During deserialization, the same procedure (in the same order) will be applied to
                // the PrimitivI object, so that correct singleton objects (GeneFeature objects and sequences) will be
                // deserialized from reference records.
                // The GeneFeature objects and corresponding nucleotide sequences from all
                // genes in analysis will be added to the set of known references of PrimitivO object
                // so that they will be serialized as 1-2 byte reference records (see PrimitivIO implementation).
                IOUtil.stdVDJCPrimitivOStateInit(o, usedGenes, cloneSet);

                // Saving stream position of the first clone object
                // this value will be written at the very end of the file
                positionOfFirstClone = output.getPosition();

                // Saving number of clones
                numberOfClones = cloneSet.size();
            }

            try (PrimitivOBlocks<Object>.Writer writer = this.output
                    .beginPrimitivOBlocks(4, 1024,
                            PrimitivIOBlocksUtil.getCompressor(highCompression))) {
                // Writing clones
                for (Clone clone : cloneSet) {
                    writer.write(clone);
                    // For progress reporting
                    ++numberOfClonesWritten;
                }
                writer.flush();
            } // will synchronize here (on close method invocation)

            // Saving ids; also tells other methods that clones block was written successful
            TIntHashSet ids = new TIntHashSet(cloneSet.getClones().stream().mapToInt(c -> c.id).toArray());
            ids.add(-1);
            this.cloneIds = ids;
        }
    }

    /**
     * Step 2
     */
    public void collateAlignments(OutputPort<VDJCAlignments> alignments,
                                  long numberOfAlignments) throws IOException {
        synchronized (sync) {
            // Checking state
            if (cloneIds == null)
                throw new IllegalStateException("Write clones before writing alignments.");
            if (collatedAlignments != null)
                throw new IllegalStateException("Alignments are already sorted.");

            // Saving number of alignments
            this.numberOfAlignments = numberOfAlignments;

            // Dirty heuristic to optimize trade-off between memory usage and number of random access places in a file
            // to read from
            // int chunkSize = (int) Math.min(Math.max(16384, numberOfAlignments / 8), 1048576);

            // Sorting alignments by cloneId and then by mapping type (core alignments will be written before all others)
            // and saving sorting output port
            this.toCollator = new CountingOutputPort<>(alignments);

            // Optimize serialization of genes and corresponding subject sequences from alignments
            PrimitivIOStateBuilder stateBuilder = new PrimitivIOStateBuilder();
            IOUtil.registerGeneReferences(stateBuilder, usedGenes, featureToAlignProvider);

            // HDD-offloading collator of alignments
            // Collates solely by cloneId (no sorting by mapping type, etc.);
            // less fields to sort by -> faster the procedure
            long memoryBudget =
                    Runtime.getRuntime().maxMemory() > 10_000_000_000L /* -Xmx10g */
                            ? Runtime.getRuntime().maxMemory() / 4L /* 1 Gb */
                            : 1 << 28 /* 256 Mb */;
            collator = new HashSorter<>(
                    VDJCAlignments.class,
                    VDJCACloneIdHash, VDJCACloneIdComparator,
                    5, tempDest, 4, 6,
                    stateBuilder.getOState(), stateBuilder.getIState(),
                    memoryBudget, 1 << 18 /* 256 Kb */);

            // Here we wait for the first layer of hash collation to finish "write" stage
            // (on average 30% time of full collation process)
            // following steps are executed as needed during port reading
            this.collatedAlignments = collator.port(toCollator);
        }
    }

    /** To be written right before close */
    private volatile MiXCRFooter footer;

    /**
     * Set footer to be written right before close
     */
    public void setFooter(MiXCRFooter footer) {
        this.footer = footer;
    }

    /**
     * Step 3
     */
    public void writeAlignmentsAndIndex() {
        synchronized (sync) {
            // Checking state
            if (collatedAlignments == null)
                throw new IllegalStateException("Call sortAlignments before this method.");
            if (finished)
                throw new IllegalStateException("Writer already closed.");
            if (footer == null)
                throw new IllegalStateException("Footer not written.");

            // Indices that will be written below all alignments
            final TLongArrayList aBlockOffset = new TLongArrayList();
            final TLongArrayList aBlockCount = new TLongArrayList();
            final TIntArrayList cloneIdsIndex = new TIntArrayList();
            long previousAlsCount = 0;
            int currentCloneIndex = Integer.MIN_VALUE;
            long indexBeginOffset;

            try (PrimitivOBlocks<VDJCAlignments>.Writer o = output.beginPrimitivOBlocks(
                    Math.min(4, Runtime.getRuntime().availableProcessors()), // TODO parametrize
                    VDJCAlignmentsWriter.DEFAULT_ALIGNMENTS_IN_BLOCK,
                    PrimitivIOBlocksUtil.getCompressor(highCompression))) {

                // Writing alignments and writing indices
                VDJCAlignments alignments;
                while (true) {
                    alignments = collatedAlignments.take();

                    // End of clone group or the first alignment
                    if (alignments == null || currentCloneIndex != alignments.cloneIndex) {
                        if (currentCloneIndex != Integer.MIN_VALUE) { // Not first alignment
                            // Async flush
                            o.flush();
                            o.writeHeader(ALIGNMENT_BLOCK_SEPARATOR);
                        }

                        // No synchronization here

                        if (alignments != null && !cloneIds.remove((int) alignments.cloneIndex))
                            throw new IllegalArgumentException("Alignment for a wrong clonotype " +
                                    alignments.cloneIndex);

                        // Write stream position as soon as all the blocks are flushed
                        // This will be the start position for the next block
                        o.run(c -> {
                            // In theory synchronization for aBlockOffset access here is not required as all IO
                            // operations as well as this code are executed strictly sequentially

                            // synchronized (aBlockOffset){
                            aBlockOffset.add(((HasPosition) c).getPosition());
                            // }
                        });

                        aBlockCount.add(numberOfAlignmentsWritten - previousAlsCount);
                        previousAlsCount = numberOfAlignmentsWritten;

                        if (alignments == null)
                            break;

                        // Saving clone groups sequence
                        cloneIdsIndex.add((int) alignments.cloneIndex);
                        currentCloneIndex = (int) alignments.cloneIndex;
                    }

                    o.write(alignments);
                    ++numberOfAlignmentsWritten;
                }

                if (!(cloneIds.isEmpty() ||
                        (cloneIds.size() == 1 && cloneIds.iterator().next() == -1)))
                    throw new IllegalArgumentException("Some clones have no alignments.");

                // Waiting for all alignments to be flushed to the file to read file position
                o.sync();

                assert aBlockOffset.size() == aBlockCount.size()
                        && aBlockCount.size() == cloneIdsIndex.size() + 1
                        // there may be no alignments with cloneId == -1
                        && (numberOfClones + 1 == cloneIdsIndex.size() || numberOfClones == cloneIdsIndex.size());

                // o.close() will additionally write EOF header
                indexBeginOffset = o.getPosition() + PrimitivIOBlockHeader.HEADER_SIZE;

                // Print IO stat
                if (MiXCRDebug.DEBUG) {
                    System.out.println("==== IO ClnAWriter: Collator =====");
                    collator.printStat();
                    System.out.println("==== IO ClnAWriter =====");
                    System.out.println(o.getParent().getStats());
                }
            }

            // Closing sorted the output port, this will delete intermediate collation files
            collatedAlignments.close();

            // Saving index offset in file to write in the end of the stream
            long previousValue = 0;
            try (PrimitivO o = output.beginPrimitivO()) { // TODO also use blocks ?
                // Writing index size
                o.writeVarInt(aBlockOffset.size());

                // Writing both indices
                for (int i = 0; i < aBlockOffset.size(); i++) {
                    long iValue = aBlockOffset.get(i);
                    // Writing offset index using deltas to save space
                    // (smaller values are represented by less number of bytes in VarLong representation)
                    o.writeVarLong(iValue - previousValue);
                    previousValue = iValue;

                    o.writeVarLong(aBlockCount.get(i));

                    if (i != aBlockOffset.size() - 1)
                        o.writeVarInt(cloneIdsIndex.get(i));
                }
            }

            // Position of reports
            long footerStartPosition = output.getPosition();

            try (PrimitivO o = output.beginPrimitivO()) {
                o.writeObject(footer);

                // Position of reports
                o.writeLong(footerStartPosition);
                // Writing two key positions in a file
                // This values will be using during deserialization to find certain blocks
                o.writeLong(positionOfFirstClone);
                o.writeLong(indexBeginOffset);

                // Writing end-magic as a file integrity sign
                o.write(IOUtil.getEndMagicBytes());
            }

            // Setting finished flag (will stop progress reporting)
            finished = true;
        }
    }

    @Override
    public double getProgress() {
        if (cloneIds == null)
            if (numberOfClones == -1)
                return Double.NaN;
            else
                return 1.0 * numberOfClonesWritten / numberOfClones;
        else if (collatedAlignments == null) {
            if (toCollator == null)
                return Double.NaN;
            else
                return 1.0 * toCollator.getCount() / numberOfAlignments;
        } else
            return 1.0 * numberOfAlignmentsWritten / numberOfAlignments;
    }

    @Override
    public String getStage() {
        if (cloneIds == null)
            if (numberOfClones == -1)
                return "Initialization";
            else
                return "Writing clones";
        else if (collatedAlignments == null) {
            if (toCollator == null)
                return "Preparing for sorting";
            else
                return "Sorting alignments";
        } else
            return "Writing alignments";
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    @Override
    public void close() throws IOException {
        finished = true;
        output.close();
    }
}
