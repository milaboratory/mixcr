package com.milaboratory.mixcr.cli.newcli;

import com.milaboratory.mixcr.basictypes.PipelineConfiguration;
import com.milaboratory.mixcr.basictypes.PipelineConfigurationReader;
import picocli.CommandLine;

import java.util.Collections;
import java.util.List;

/**
 *
 */
public abstract class ACommandWithResumeWithSingleInput extends ACommandWithResume {
    @CommandLine.Parameters(description = "input file")
    public String in;

    @CommandLine.Parameters(description = "output file")
    public String out;

    @Override
    public List<String> getOutputFiles() {
        return Collections.singletonList(out);
    }

    @Override
    public List<String> getInputFiles() {
        return Collections.singletonList(in);
    }

    @Override
    public PipelineConfiguration getFullPipelineConfiguration() {
        return PipelineConfiguration.appendStep(PipelineConfigurationReader.fromFile(getInputFiles().get(0)), getInputFiles(), getConfiguration());
    }
}
