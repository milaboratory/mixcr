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

import com.milaboratory.cli.AppVersionInfo;
import com.milaboratory.cli.PipelineConfiguration;
import com.milaboratory.cli.PipelineConfigurationWriter;
import com.milaboratory.mixcr.util.MiXCRVersionInfo;
import com.milaboratory.primitivio.PrimitivO;
import com.milaboratory.util.CanReportProgressAndStage;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneFeatureSerializer;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 *
 */
public class ClnsWriter implements PipelineConfigurationWriter,
        CanReportProgressAndStage,
        Closeable {
    static final String MAGIC_V7 = "MiXCR.CLNS.V07";
    static final String MAGIC_V8 = "MiXCR.CLNS.V08";
    static final String MAGIC = MAGIC_V8;
    static final int MAGIC_LENGTH = 14;
    static final byte[] MAGIC_BYTES = MAGIC.getBytes(StandardCharsets.US_ASCII);

    final String stage = "Writing clones";
    final PrimitivO output;
    final CloneSet cloneSet;
    final int size;
    final PipelineConfiguration configuration;

    private volatile int current;

    public ClnsWriter(PipelineConfiguration configuration, CloneSet cloneSet, String fileName) throws IOException {
        this(configuration, cloneSet, new File(fileName));
    }

    public ClnsWriter(PipelineConfiguration configuration, CloneSet cloneSet, File file) throws IOException {
        this(configuration, cloneSet, IOUtil.createOS(file));
    }

    public ClnsWriter(PipelineConfiguration configuration, CloneSet cloneSet, OutputStream outputStream) {
        this.output = new PrimitivO(outputStream);
        this.configuration = configuration;
        this.cloneSet = cloneSet;
        this.size = cloneSet.size();
    }

    @Override
    public String getStage() {
        return stage;
    }

    @Override
    public double getProgress() {
        return (1.0 * current) / size;
    }

    @Override
    public boolean isFinished() {
        return current == size;
    }

    public void write() {
        // Registering custom serializer
        output.getSerializersManager().registerCustomSerializer(GeneFeature.class, new GeneFeatureSerializer(true));

        // Writing magic bytes
        output.write(MAGIC_BYTES);

        // Writing version information
        output.writeUTF(
                MiXCRVersionInfo.get().getVersionString(
                        AppVersionInfo.OutputType.ToFile));

        // Writing analysis meta-information
        output.writeObject(configuration);
        output.writeObject(cloneSet.alignmentParameters);
        output.writeObject(cloneSet.assemblerParameters);

        IO.writeGT2GFMap(output, cloneSet.alignedFeatures);
        IOUtil.writeAndRegisterGeneReferences(output, cloneSet.getUsedGenes(), new ClnsReader.GT2GFAdapter(cloneSet.alignedFeatures));

        output.writeInt(cloneSet.getClones().size());

        for (Clone clone : cloneSet) {
            output.writeObject(clone);
            ++current;
        }

        // Writing end-magic as a file integrity sign
        output.write(IOUtil.getEndMagicBytes());
    }

    @Override
    public void close() {
        output.close();
    }
}
