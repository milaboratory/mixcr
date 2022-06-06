package com.milaboratory.mixcr.assembler.preclone;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.InputPort;
import cc.redberry.pipe.OutputPortCloseable;
import cc.redberry.pipe.blocks.Buffer;
import com.milaboratory.mixcr.basictypes.IOUtil;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader;
import com.milaboratory.primitivio.PrimitivIOStateBuilder;
import com.milaboratory.primitivio.PrimitivO;
import com.milaboratory.primitivio.blocks.PrimitivIOBlockHeader;
import com.milaboratory.primitivio.blocks.PrimitivOBlocks;
import com.milaboratory.primitivio.blocks.PrimitivOHybrid;
import com.milaboratory.util.CanReportProgressAndStage;
import com.milaboratory.util.ProgressAndStage;
import com.milaboratory.util.TempFileDest;
import com.milaboratory.util.sorting.HashSorter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicLong;

import static com.milaboratory.mixcr.basictypes.FieldCollection.*;

public final class FilePreCloneWriter implements AutoCloseable, CanReportProgressAndStage {
    // Headers marking special positions in file
    public static final byte UNASSIGNED_ALIGNMENTS_END_MARK_BYTE_0 = 1;
    public static final byte ALIGNMENTS_END_MARK_BYTE_0 = 2;
    public static final byte CLONES_END_MARK_BYTE_0 = 3;
    public static final PrimitivIOBlockHeader UNASSIGNED_ALIGNMENTS_END_MARK =
            PrimitivIOBlockHeader.specialHeader().setSpecialByte(0, UNASSIGNED_ALIGNMENTS_END_MARK_BYTE_0);
    public static final PrimitivIOBlockHeader ALIGNMENTS_END_MARK =
            PrimitivIOBlockHeader.specialHeader().setSpecialByte(0, ALIGNMENTS_END_MARK_BYTE_0);
    public static final PrimitivIOBlockHeader CLONES_END_MARK =
            PrimitivIOBlockHeader.specialHeader().setSpecialByte(0, CLONES_END_MARK_BYTE_0);

    private final ProgressAndStage ps = new ProgressAndStage("Sorting alignments and clones");

    private final PrimitivOHybrid output;
    private final TempFileDest tempDest;

    private volatile long alignmentsStartPosition;
    private volatile PrimitivOBlocks<VDJCAlignments>.Writer alignmentWriter;

    private final Buffer<VDJCAlignments> alignmentBuffer;
    private final InputPort<VDJCAlignments> alignmentSorterInput;
    private final Buffer<PreCloneImpl> cloneBuffer;
    private final InputPort<PreCloneImpl> cloneSorterInput;

    private final AtomicLong
            numberOfAlignments = new AtomicLong(),
            numberOfAssignedAlignments = new AtomicLong(),
            numberOfClones = new AtomicLong();

    private volatile Thread alignmentSortingThread, cloneSortingThread;
    private volatile HashSorter<VDJCAlignments> alignmentCollator;
    private volatile OutputPortCloseable<VDJCAlignments> sortedAlignments;
    private volatile HashSorter<PreCloneImpl> cloneCollator;
    private volatile OutputPortCloseable<PreCloneImpl> sortedClones;

    public FilePreCloneWriter(Path file, TempFileDest tempDest) throws IOException {
        this.output = new PrimitivOHybrid(ForkJoinPool.commonPool(), file);
        this.tempDest = tempDest;
        this.alignmentBuffer = new Buffer<>(1 << 14);
        this.alignmentSorterInput = alignmentBuffer.createInputPort();
        this.cloneBuffer = new Buffer<>(1 << 10);
        this.cloneSorterInput = cloneBuffer.createInputPort();
    }

    public void init(VDJCAlignmentsReader alignmentReader) {
        // Writing header in raw primitivIO mode and initializing primitivIO state
        try (PrimitivO o = this.output.beginPrimitivO(true)) {
            o.writeObject(alignmentReader.getParameters());
            o.writeLong(alignmentReader.getNumberOfReads());
            IOUtil.stdVDJCPrimitivOStateInit(o, alignmentReader.getUsedGenes(), alignmentReader.getParameters());
        }

        PrimitivIOStateBuilder stateBuilder = new PrimitivIOStateBuilder();
        IOUtil.registerGeneReferences(stateBuilder, alignmentReader.getUsedGenes(), alignmentReader.getParameters());

        long memoryBudget =
                Runtime.getRuntime().maxMemory() > 10_000_000_000L /* -Xmx10g */
                        ? Runtime.getRuntime().maxMemory() / 8L /* 1 Gb */
                        : 1 << 28 /* 256 Mb */;

        alignmentCollator = new HashSorter<>(
                VDJCAlignments.class,
                VDJCACloneIdHash, VDJCACloneIdComparator,
                5, tempDest.addSuffix("al.pre."), 4, 6,
                stateBuilder.getOState(), stateBuilder.getIState(),
                memoryBudget, 1 << 18 /* 256 Kb */);
        alignmentSortingThread = new Thread(() -> sortedAlignments = alignmentCollator.port(alignmentBuffer),
                "alignment-sorting");
        alignmentSortingThread.start();
        cloneCollator = new HashSorter<>(
                PreCloneImpl.class,
                PreCloneIdHash, PreCloneIdComparator,
                5, tempDest.addSuffix("cl.pre."), 4, 6,
                stateBuilder.getOState(), stateBuilder.getIState(),
                memoryBudget, 1 << 18 /* 256 Kb */);
        cloneSortingThread = new Thread(() -> sortedClones = cloneCollator.port(cloneBuffer),
                "clone-sorting");
        cloneSortingThread.start();

        // Saving position in file where alignments block begins
        alignmentsStartPosition = output.getPosition();
        // This writer will be used to write not-assigned alignments during alignment sorting
        alignmentWriter = output.beginPrimitivOBlocks(4, 1024);
    }

    public void putClone(PreCloneImpl clone) {
        numberOfClones.incrementAndGet();
        cloneSorterInput.put(clone);
    }

    public void putAlignment(VDJCAlignments alignment) {
        numberOfAlignments.incrementAndGet();
        if (alignment.getCloneIndex() == -1)
            alignmentWriter.write(alignment);
        else {
            numberOfAssignedAlignments.incrementAndGet();
            alignmentSorterInput.put(alignment);
        }
    }

    public void finish() {
        alignmentSorterInput.put(null);
        cloneSorterInput.put(null);
        try {
            alignmentSortingThread.join();
            cloneSortingThread.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        alignmentSortingThread = null;
        cloneSortingThread = null;

        long assignedAlignmentsStartPosition;
        long alignmentsChecksum = 17;
        try (PrimitivOBlocks<VDJCAlignments>.Writer writer = alignmentWriter) {
            // Finishing block of not-assigned alignments
            writer.flush();
            writer.writeHeader(UNASSIGNED_ALIGNMENTS_END_MARK);
            writer.sync();
            assignedAlignmentsStartPosition = writer.getPosition();

            ps.setStage("Writing alignments");
            ps.setProgress(0.0);

            // resulting clone id generator
            long newCloneIdx = -1;
            long previousCloneIdx = -1;
            long alignments = 0;
            for (VDJCAlignments al : CUtils.it(sortedAlignments)) {
                assert al.getCloneIndex() >= 0;

                if (al.getCloneIndex() != previousCloneIdx) {
                    newCloneIdx++;

                    // al.getCloneIndex() will be mapped to newCloneIdx
                    alignmentsChecksum = alignmentsChecksum * 71 + al.getCloneIndex();
                    alignmentsChecksum = alignmentsChecksum * 71 + newCloneIdx;

                    previousCloneIdx = al.getCloneIndex();
                }
                writer.write(al.withCloneIndex(newCloneIdx));

                alignments++;
                ps.setProgress(1.0 * alignments / numberOfAssignedAlignments.get());
            }
            writer.flush();
            writer.writeHeader(ALIGNMENTS_END_MARK);
        } finally {
            sortedAlignments.close();
        }

        ps.setStage("Writing clones");
        ps.setProgress(0.0);

        long clonesStartPosition = output.getPosition();
        long cloneChecksum = 17;
        long clones = 0;
        try (PrimitivOBlocks<PreCloneImpl>.Writer writer = output.beginPrimitivOBlocks(4, 1024)) {
            // resulting clone id generator
            long newCloneIdx = -1;
            for (PreCloneImpl preClone : CUtils.it(sortedClones)) {
                newCloneIdx++;

                // preClone.id will be mapped to newCloneIdx
                cloneChecksum = cloneChecksum * 71 + preClone.id;
                cloneChecksum = cloneChecksum * 71 + newCloneIdx;

                writer.write(preClone.withIndex(newCloneIdx));
                clones++;
                ps.setProgress(1.0 * clones / numberOfClones.get());
            }
            writer.flush();
            writer.writeHeader(CLONES_END_MARK);
        } finally {
            sortedClones.close();
        }

        // Important invariant that must always be true (assert)
        if (alignmentsChecksum != cloneChecksum)
            throw new IllegalStateException("Inconsistent sequences of clones and alignments.");

        try (PrimitivO o = output.beginPrimitivO()) {
            o.writeLong(alignmentsStartPosition);
            o.writeLong(assignedAlignmentsStartPosition);
            o.writeLong(clonesStartPosition);
            o.writeLong(numberOfAlignments.get());
            o.writeLong(numberOfAssignedAlignments.get());
            o.writeLong(numberOfClones.get());
        }

        ps.finish();
    }

    @Override
    public double getProgress() {
        return ps.getProgress();
    }

    @Override
    public boolean isFinished() {
        return ps.isFinished();
    }

    @Override
    public String getStage() {
        return ps.getStage();
    }

    @Override
    public void close() throws Exception {
        if (alignmentSortingThread != null) {
            alignmentSorterInput.put(null);
            try {
                alignmentSortingThread.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        if (cloneSortingThread != null) {
            cloneSorterInput.put(null);
            try {
                cloneSortingThread.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        if (sortedAlignments != null)
            sortedAlignments.close();

        if (sortedClones != null)
            sortedClones.close();

        output.close();
    }
}
