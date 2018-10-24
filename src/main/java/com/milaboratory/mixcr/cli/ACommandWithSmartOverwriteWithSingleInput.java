package com.milaboratory.mixcr.cli;

import com.milaboratory.mixcr.basictypes.IOUtil;
import com.milaboratory.mixcr.basictypes.PipelineConfiguration;
import com.milaboratory.mixcr.basictypes.PipelineConfigurationReader;
import picocli.CommandLine.Parameters;

import java.util.Collections;
import java.util.List;

/**
 *
 */
public abstract class ACommandWithSmartOverwriteWithSingleInput extends ACommandWithSmartOverwrite {
    @Parameters(index = "0", description = "input file")
    public String in;

    @Parameters(index = "1", description = "output file")
    public String out;

    @Override
    public final List<String> getOutputFiles() {
        return Collections.singletonList(out);
    }

    @Override
    public final List<String> getInputFiles() {
        return Collections.singletonList(in);
    }

    private boolean inputFileInfoInitialized = false;
    private IOUtil.MiXCRFileInfo inputFileInfo = null;

    public IOUtil.MiXCRFileInfo getInputFileInfo() {
        if (getInputFiles().size() != 1) throw new RuntimeException();
        if (!inputFileInfoInitialized) {
            inputFileInfo = IOUtil.getFileInfo(in);
            inputFileInfoInitialized = true;
        }
        return inputFileInfo;
    }

    @Override
    public PipelineConfiguration getFullPipelineConfiguration() {
        return PipelineConfiguration.appendStep(PipelineConfigurationReader.fromFile(in, getInputFileInfo()), getInputFiles(), getConfiguration());
    }
}
