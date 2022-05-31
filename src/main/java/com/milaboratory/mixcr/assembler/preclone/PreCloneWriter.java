package com.milaboratory.mixcr.assembler.preclone;

import com.milaboratory.mixcr.basictypes.IOUtil;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader;
import com.milaboratory.primitivio.PrimitivIOStateBuilder;
import com.milaboratory.primitivio.PrimitivO;
import com.milaboratory.primitivio.blocks.PrimitivOHybrid;
import com.milaboratory.util.TempFileManager;
import com.milaboratory.util.sorting.HashSorter;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ForkJoinPool;

import static com.milaboratory.mixcr.basictypes.FieldCollection.*;

public final class PreCloneWriter {
    private final Path alignmentPresortFolder;
    private final PrimitivOHybrid output;
    private volatile HashSorter<VDJCAlignments> alignmentCollator;
    private volatile HashSorter<PreClone> cloneCollator;

    public PreCloneWriter(Path file) throws IOException {
        this.alignmentPresortFolder = file.toAbsolutePath().resolveSibling(file.getFileName().toString() + ".presorted");
        if (Files.exists(alignmentPresortFolder))
            FileUtils.deleteDirectory(alignmentPresortFolder.toFile());
        TempFileManager.register(alignmentPresortFolder.toFile());
        Files.createDirectory(this.alignmentPresortFolder);
        this.output = new PrimitivOHybrid(ForkJoinPool.commonPool(), file);
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
                5, tempFolder, 4, 6,
                stateBuilder.getOState(), stateBuilder.getIState(),
                memoryBudget, 1 << 18 /* 256 Kb */);
        cloneCollator = new HashSorter<>(
                PreClone.class,
                PreCloneIdHash, PreCloneIdComparator,
                5, tempFolder, 4, 6,
                stateBuilder.getOState(), stateBuilder.getIState(),
                memoryBudget, 1 << 18 /* 256 Kb */);
    }
}
