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
import cc.redberry.pipe.OutputPortCloseable;
import com.milaboratory.cli.PipelineConfiguration;
import com.milaboratory.mixcr.assembler.CloneAssemblerParameters;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import com.milaboratory.primitivio.PrimitivI;
import com.milaboratory.primitivio.blocks.PrimitivIHybrid;
import com.milaboratory.util.LambdaSemaphore;
import io.repseq.core.VDJCGene;
import io.repseq.core.VDJCLibraryRegistry;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.milaboratory.mixcr.basictypes.ClnsWriter.MAGIC;
import static com.milaboratory.mixcr.basictypes.ClnsWriter.MAGIC_LENGTH;

/**
 *
 */
public class ClnsReader extends PipelineConfigurationReaderMiXCR implements CloneReader, AutoCloseable {
    private final PrimitivIHybrid input;
    private final VDJCLibraryRegistry libraryRegistry;

    private final PipelineConfiguration pipelineConfiguration;
    private final VDJCAlignerParameters alignerParameters;
    private final CloneAssemblerParameters assemblerParameters;
    private final VDJCSProperties.CloneOrdering ordering;
    private final String versionInfo;
    private final List<VDJCGene> genes;

    private final long clonesPosition;

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

        try (PrimitivI pi = this.input.beginRandomAccessPrimitivI(-IOUtil.END_MAGIC_LENGTH)) {
            // Checking file consistency
            byte[] endMagic = new byte[IOUtil.END_MAGIC_LENGTH];
            pi.readFully(endMagic);
            if (!Arrays.equals(IOUtil.getEndMagicBytes(), endMagic))
                throw new RuntimeException("Corrupted file.");
        }

        try (PrimitivI i = input.beginPrimitivI(true)) {
            versionInfo = i.readUTF();
            pipelineConfiguration = i.readObject(PipelineConfiguration.class);
            alignerParameters = i.readObject(VDJCAlignerParameters.class);
            assemblerParameters = i.readObject(CloneAssemblerParameters.class);
            ordering = i.readObject(VDJCSProperties.CloneOrdering.class);

            genes = IOUtil.stdVDJCPrimitivIStateInit(i, alignerParameters, libraryRegistry);
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
        CloneSet cloneSet = new CloneSet(clones, genes, alignerParameters, assemblerParameters, ordering);
        cloneSet.versionInfo = versionInfo;
        return cloneSet;
    }

    @Override
    public PipelineConfiguration getPipelineConfiguration() {
        return pipelineConfiguration;
    }

    public VDJCAlignerParameters getAlignerParameters() {
        return alignerParameters;
    }

    public CloneAssemblerParameters getAssemblerParameters() {
        return assemblerParameters;
    }

    @Override
    public VDJCSProperties.CloneOrdering ordering() {
        return ordering;
    }

    @Override
    public List<VDJCGene> getGenes() {
        return genes;
    }

    @Override
    public void close() throws IOException {
        input.close();
    }
}
