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
import com.milaboratory.primitivio.blocks.PrimitivOBlocks;
import com.milaboratory.primitivio.blocks.PrimitivOHybrid;
import com.milaboratory.util.TempFileDest;
import com.milaboratory.util.sorting.HashSorter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ForkJoinPool;

import static com.milaboratory.mixcr.basictypes.FieldCollection.*;

public final class PreCloneWriter implements AutoCloseable {
    private final PrimitivOHybrid output;
    private final TempFileDest tempDest;
    private final Buffer<VDJCAlignments> alignmentBuffer;
    private final InputPort<VDJCAlignments> alignmentInput;
    private final Buffer<PreClone> cloneBuffer;
    private final InputPort<PreClone> cloneInput;
    private volatile Thread alignmentSortingThread, cloneSortingThread;
    private volatile HashSorter<VDJCAlignments> alignmentCollator;
    private volatile OutputPortCloseable<VDJCAlignments> sortedAlignments;
    private volatile HashSorter<PreClone> cloneCollator;
    private volatile OutputPortCloseable<PreClone> sortedClones;

    public PreCloneWriter(Path file, TempFileDest tempDest) throws IOException {
        this.output = new PrimitivOHybrid(ForkJoinPool.commonPool(), file);
        this.tempDest = tempDest;
        this.alignmentBuffer = new Buffer<>(1 << 14);
        this.alignmentInput = alignmentBuffer.createInputPort();
        this.cloneBuffer = new Buffer<>(1 << 10);
        this.cloneInput = cloneBuffer.createInputPort();
    }

    public void init(VDJCAlignmentsReader alignmentReader) {
        // Writing header in raw primitivIO mode and initializing primitivIO state
        try (PrimitivO o = this.output.beginPrimitivO(true)) {
            o.writeObject(alignmentReader.getParameters());
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
                PreClone.class,
                PreCloneIdHash, PreCloneIdComparator,
                5, tempDest.addSuffix("cl.pre."), 4, 6,
                stateBuilder.getOState(), stateBuilder.getIState(),
                memoryBudget, 1 << 18 /* 256 Kb */);
        cloneSortingThread = new Thread(() -> sortedClones = cloneCollator.port(cloneBuffer),
                "clone-sorting");
    }

    public void putClone(PreClone clone) {
        cloneInput.put(clone);
    }

    public void putAlignment(VDJCAlignments alignment) {
        alignmentInput.put(alignment);
    }

    public void finishWrite() {
        alignmentInput.put(null);
        cloneInput.put(null);
        try {
            alignmentSortingThread.join();
            cloneSortingThread.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        long clonesStartPosition = output.getPosition();
        long cloneChecksum = 17;
        try (PrimitivOBlocks<PreClone>.Writer writer = output.beginPrimitivOBlocks(4, 1024)) {
            // final clone id generator
            long newCloneIdx = 0;
            for (PreClone preClone : CUtils.it(sortedClones)) {
                cloneChecksum = cloneChecksum * 71 + preClone.id;
                writer.write(preClone.withId(newCloneIdx++));
            }
        }

        long alignmentsStartPosition = output.getPosition();
        long alignmentsChecksum = 17;
        try (PrimitivOBlocks<VDJCAlignments>.Writer writer = output.beginPrimitivOBlocks(4, 1024)) {
            // final clone id generator
            long newCloneIdx = -1;
            long previousCloneIdx = -1;
            for (VDJCAlignments al : CUtils.it(sortedAlignments)) {
                if (al.getCloneIndex() != previousCloneIdx) {
                    newCloneIdx++;
                    alignmentsChecksum = alignmentsChecksum * 71 + al.getCloneIndex();
                }
                writer.write(al.withCloneIndex(newCloneIdx));
            }
        }

        if (alignmentsChecksum != cloneChecksum)
            throw new IllegalStateException("Inconsistent sequences of clones and alignments.");

        try (PrimitivO o = output.beginPrimitivO()) {
            o.writeLong(clonesStartPosition);
            o.writeLong(alignmentsStartPosition);
        }
    }

    @Override
    public void close() throws Exception {
        output.close();
    }
}
