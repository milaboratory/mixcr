package com.milaboratory.mixcr.basictypes;

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
                        MiXCRVersionInfo.OutputType.ToFile));

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
