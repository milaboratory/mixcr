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
import cc.redberry.pipe.OutputPortCloseable;
import com.milaboratory.primitivio.PrimitivI;
import com.milaboratory.primitivio.blocks.PrimitivIHybrid;
import com.milaboratory.util.LambdaSemaphore;
import io.repseq.core.VDJCGene;
import io.repseq.core.VDJCLibraryId;
import io.repseq.core.VDJCLibraryRegistry;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static com.milaboratory.mixcr.basictypes.ClnsWriter.MAGIC;
import static com.milaboratory.mixcr.basictypes.ClnsWriter.MAGIC_LENGTH;

/**
 *
 */
public class ClnsReader implements CloneReader, AutoCloseable {
    private final PrimitivIHybrid input;
    private final VDJCLibraryRegistry libraryRegistry;
    private final MiXCRHeader header;
    private final MiXCRFooter footer;
    private final VDJCSProperties.CloneOrdering ordering;
    private final String versionInfo;
    private final List<VDJCGene> usedGenes;
    private final int numberOfClones;
    private final long clonesPosition;


    public ClnsReader(String file, VDJCLibraryRegistry libraryRegistry) throws IOException {
        this(Paths.get(file), libraryRegistry, 3);
    }

    public ClnsReader(Path file, VDJCLibraryRegistry libraryRegistry) throws IOException {
        this(file, libraryRegistry, 3);
    }

    public ClnsReader(Path file, VDJCLibraryRegistry libraryRegistry, int concurrency) throws IOException {
        this(file, libraryRegistry, new LambdaSemaphore(concurrency));
    }

    public ClnsReader(Path file, VDJCLibraryRegistry libraryRegistry, LambdaSemaphore concurrencyLimiter) throws IOException {
        this(new PrimitivIHybrid(file, concurrencyLimiter), libraryRegistry);
    }

    private ClnsReader(PrimitivIHybrid input, VDJCLibraryRegistry libraryRegistry) {
        this.input = input;
        this.libraryRegistry = libraryRegistry;

        try (PrimitivI i = input.beginPrimitivI(true)) {
            byte[] magicBytes = new byte[MAGIC_LENGTH];
            i.readFully(magicBytes);

            String magicString = new String(magicBytes);

            // SerializersManager serializersManager = input.getSerializersManager();

            switch (magicString) {
                case MAGIC:
                    break;
                default:
                    throw new RuntimeException("Unsupported file format; .clns file of version " + magicString +
                            " while you are running MiXCR " + MAGIC);
            }
        }

        long reportsStartPosition;
        try (PrimitivI pi = this.input.beginRandomAccessPrimitivI(-ClnsWriter.FOOTER_LENGTH)) {
            reportsStartPosition = pi.readLong();
            // Checking file consistency
            byte[] endMagic = new byte[IOUtil.END_MAGIC_LENGTH];
            pi.readFully(endMagic);
            if (!Arrays.equals(IOUtil.getEndMagicBytes(), endMagic))
                throw new RuntimeException("Corrupted file.");
        }

        // read header
        try (PrimitivI i = input.beginPrimitivI(true)) {
            versionInfo = i.readUTF();
            header = Objects.requireNonNull(i.readObject(MiXCRHeader.class));
            ordering = i.readObject(VDJCSProperties.CloneOrdering.class);
            numberOfClones = i.readInt();
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

            usedGenes = IOUtil.stdVDJCPrimitivIStateInit(i, header.getAlignerParameters(), libraryRegistry);
        }

        try (PrimitivI pi = this.input.beginRandomAccessPrimitivI(reportsStartPosition)) {
            footer = pi.readObject(MiXCRFooter.class);
        }

        this.clonesPosition = input.getPosition();
    }

    @Override
    public OutputPortCloseable<Clone> readClones() {
        return input.beginRandomAccessPrimitivIBlocks(Clone.class, clonesPosition);
    }

    public CloneSet getCloneSet() {
        List<Clone> clones = new ArrayList<>();
        for (Clone clone : CUtils.it(readClones()))
            clones.add(clone);
        CloneSet cloneSet = new CloneSet(clones, usedGenes, header, footer, ordering);
        cloneSet.versionInfo = versionInfo;
        return cloneSet;
    }

    @Override
    public MiXCRHeader getHeader() {
        return header;
    }

    @Override
    public MiXCRFooter getFooter() {
        return footer;
    }

    @Override
    public VDJCSProperties.CloneOrdering ordering() {
        return ordering;
    }

    @Override
    public int numberOfClones() {
        return numberOfClones;
    }

    @Override
    public List<VDJCGene> getUsedGenes() {
        return usedGenes;
    }

    @Override
    public void close() throws IOException {
        input.close();
    }
}
