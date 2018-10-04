package com.milaboratory.mixcr.cli.newcli;

import com.milaboratory.mixcr.basictypes.PipelineConfiguration;
import com.milaboratory.mixcr.basictypes.PipelineConfigurationReader;
import picocli.CommandLine.Parameters;

import java.util.Collections;
import java.util.List;

/**
 *
 */
public abstract class ACommandWithResumeWithSingleInput extends ACommandWithResume {
    @Parameters(description = "input file")
    public String in;

    @Parameters(description = "output file")
    public String out;

    @Override
    public final List<String> getOutputFiles() {
        return Collections.singletonList(out);
    }

    @Override
    public final List<String> getInputFiles() {
        return Collections.singletonList(in);
    }

    @Override
    public PipelineConfiguration getFullPipelineConfiguration() {
        return PipelineConfiguration.appendStep(PipelineConfigurationReader.fromFile(getInputFiles().get(0)), getInputFiles(), getConfiguration());
    }
}
