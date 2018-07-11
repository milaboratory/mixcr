package com.milaboratory.mixcr.cli;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.milaboratory.cli.ActionParametersWithOutput;
import com.milaboratory.mixcr.basictypes.ActionConfiguration;
import com.milaboratory.mixcr.basictypes.PipelineConfiguration;
import com.milaboratory.mixcr.basictypes.PipelineConfigurationReader;
import com.milaboratory.mixcr.util.MiXCRVersionInfo;

import java.util.List;
import java.util.Objects;

/**
 * Action parameters which define a unique run configuration that allows to resume or even skip the execution of already
 * processed file
 */
public abstract class ActionParametersWithResumeOption extends ActionParametersWithOutput {
    @Parameter(names = "--resume", description = "try to resume aborted execution")
    public boolean resume = false;

    public boolean resume() {
        return resume;
    }

    /** returns the input files */
    public abstract List<String> getInputFiles();

    /** returns the unique run configuration */
    public abstract ActionConfiguration getConfiguration();

    /** returns the full pipeline configuration that will be written to the output file */
    public abstract PipelineConfiguration getFullPipelineConfiguration();

    @Override
    public void validate() {
        super.validate();
        if (getOutputFiles().size() != 1)
            throw new ParameterException("single output file expected");
    }

    private boolean skipExecution = false;

    public boolean skipExecution() {
        return skipExecution;
    }

    @Override
    public void handleExistenceOfOutputFile(String outFileName) {
        // analysis supposed to be performed now
        PipelineConfiguration expectedPipeline = getFullPipelineConfiguration();
        // history written in existing file
        PipelineConfiguration actualPipeline = PipelineConfigurationReader.fromFileOrNull(outFileName);

        if (Objects.equals(actualPipeline, expectedPipeline)) {
            String exists = "File " + outFileName + " already exists and contains correct " +
                    "binary data obtained from the specified input file with the current " +
                    "version of MiXCR (" + MiXCRVersionInfo.get().getShortestVersionString() + "). ";
            if (!resume())
                throw new ParameterException(exists +
                        "Use --resume option to skip execution (output file will remain unchanged) or use -f option " +
                        "to force overwrite it.");
            else {
                System.out.println("Skipping " + expectedPipeline.lastConfiguration().actionName() + " (--resume option specified). " + exists);
                skipExecution = true; // nothing to do, just exit
                return;
            }
        }
        super.handleExistenceOfOutputFile(outFileName);
    }

    public static abstract class ActionParametersWithResumeWithBinaryInput extends ActionParametersWithResumeOption {
        @Override
        public PipelineConfiguration getFullPipelineConfiguration() {
            return PipelineConfiguration.appendStep(PipelineConfigurationReader.fromFile(getInputFiles().get(0)), getInputFiles(), getConfiguration());
        }
    }
}
