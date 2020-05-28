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

import com.milaboratory.cli.PipelineConfiguration;
import com.milaboratory.mixcr.assembler.CloneAssemblerParameters;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import com.milaboratory.primitivio.PrimitivI;
import io.repseq.core.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import static com.milaboratory.mixcr.basictypes.ClnsWriter.*;

/**
 *
 */
public class ClnsReader extends PipelineConfigurationReaderMiXCR implements AutoCloseable {
    private final PrimitivI input;
    private final VDJCLibraryRegistry libraryRegistry;

    private ClnsReader(PrimitivI input, VDJCLibraryRegistry libraryRegistry) {
        this.input = input;
        this.libraryRegistry = libraryRegistry;
    }

    public ClnsReader(InputStream inputStream, VDJCLibraryRegistry libraryRegistry) {
        this(new PrimitivI(inputStream), libraryRegistry);
    }

    public ClnsReader(File file, VDJCLibraryRegistry libraryRegistry) throws IOException {
        this(IOUtil.createIS(file), libraryRegistry);
    }

    public ClnsReader(String file, VDJCLibraryRegistry libraryRegistry) throws IOException {
        this(new File(file), libraryRegistry);
    }

    private boolean initialized = false;
    private CloneSet cloneSet = null;
    private PipelineConfiguration pipelineConfiguration = null;
    private VDJCAlignerParameters alignerParameters = null;
    private CloneAssemblerParameters assemblerParameters = null;

    private synchronized void init() {
        if (initialized)
            return;

        // Registering custom serializer
        //input.getSerializersManager().registerCustomSerializer(GeneFeature.class, new GeneFeatureSerializer(true));

        byte[] magicBytes = new byte[MAGIC_LENGTH];
        input.readFully(magicBytes);

        String magicString = new String(magicBytes);

        // SerializersManager serializersManager = input.getSerializersManager();

        switch (magicString) {
            case MAGIC:
                break;
            default:
                throw new RuntimeException("Unsupported file format; .clns file of version " + magicString +
                        " while you are running MiXCR " + MAGIC);
        }

        String versionInfo = input.readUTF();

        pipelineConfiguration = input.readObject(PipelineConfiguration.class);
        alignerParameters = input.readObject(VDJCAlignerParameters.class);
        assemblerParameters = input.readObject(CloneAssemblerParameters.class);

        // EnumMap<GeneType, GeneFeature> alignedFeatures = IO.readGF2GTMap(input);
        // List<VDJCGene> genes = IOUtil.readAndRegisterGeneReferences(input, libraryRegistry, new GT2GFAdapter(alignedFeatures));

        List<VDJCGene> genes = IOUtil.stdVDJCPrimitivIStateInit(input, alignerParameters, libraryRegistry);

        int count = input.readInt();
        List<Clone> clones = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            clones.add(input.readObject(Clone.class));

        this.cloneSet = new CloneSet(clones, genes, alignerParameters, assemblerParameters);
        cloneSet.versionInfo = versionInfo;

        initialized = true;
    }

    public CloneSet getCloneSet() {
        init();
        return cloneSet;
    }

    @Override
    public PipelineConfiguration getPipelineConfiguration() {
        init();
        return pipelineConfiguration;
    }

    public VDJCAlignerParameters getAlignerParameters() {
        init();
        return alignerParameters;
    }

    public CloneAssemblerParameters getAssemblerParameters() {
        init();
        return assemblerParameters;
    }

    @Override
    public void close() {
        input.close();
    }

    public static class GT2GFAdapter implements HasFeatureToAlign {
        public final EnumMap<GeneType, GeneFeature> map;

        public GT2GFAdapter(EnumMap<GeneType, GeneFeature> map) {
            this.map = map;
        }

        @Override
        public GeneFeature getFeatureToAlign(GeneType geneType) {
            return map.get(geneType);
        }
    }
}
