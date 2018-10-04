package com.milaboratory.mixcr.cli.newcli;

import com.milaboratory.mixcr.basictypes.ActionConfiguration;
import com.milaboratory.mixcr.basictypes.PipelineConfiguration;
import com.milaboratory.mixcr.basictypes.PipelineConfigurationReader;
import picocli.CommandLine;

/** A command which allows resuming execution */
public abstract class ACommandWithResume extends ACommandWithOutput {
    @CommandLine.Option(
            names = "--resume",
            description = "try to resume the aborted execution")
    public boolean resume = false;

    /** returns the unique run configuration */
    public abstract ActionConfiguration getConfiguration();

    /** returns the full pipeline configuration that will be written to the output file */
    public abstract PipelineConfiguration getFullPipelineConfiguration();

    public final String getOutput() {
        return getOutputFiles().get(0);
    }

    @Override
    public void validate() {
        super.validate();
        if (getOutputFiles().size() != 1)
            throwValidationException("single output file expected");
    }

    /** whether to skip execution or not */
    private boolean skipExecution = false;

    @Override
    public void handleExistenceOfOutputFile(String outFileName) {
        if (force)
            // rewrite anyway
            return;

        // analysis supposed to be performed now
        PipelineConfiguration expectedPipeline = getFullPipelineConfiguration();
        // history written in existing file
        PipelineConfiguration actualPipeline = PipelineConfigurationReader.fromFileOrNull(outFileName);

        if (actualPipeline != null
                && expectedPipeline != null
                && actualPipeline.compatibleWith(expectedPipeline)) {

            String exists = "File " + outFileName + " already exists and contains correct " +
                    "binary data obtained from the specified input file. ";

            if (!resume)
                throwValidationException(exists +
                        "Use --resume option to skip execution (output file will remain unchanged) or use -f option " +
                        "to force overwrite it.", false);
            else {
                warn("Skipping " + expectedPipeline.lastConfiguration().actionName() + " (--resume option specified). " + exists);

                // print warns in case different MiXCR versions
                for (int i = 0; i < expectedPipeline.pipelineSteps.length; i++) {
                    ActionConfiguration
                            prev = actualPipeline.pipelineSteps[i].configuration,
                            curr = expectedPipeline.pipelineSteps[i].configuration;
                    if (!prev.versionId().equals(curr.versionId()))
                        warn(String.format("WARNING (--resume): %s was performed with outdated MiXCR version (%s). Consider re-running analysis using --force option.",
                                prev.actionName(),
                                actualPipeline.pipelineSteps[i].versionOfMiXCR));
                }

                skipExecution = true; // nothing to do, just exit
                return;
            }
        }
        super.handleExistenceOfOutputFile(outFileName);
    }

    @Override
    public final void run0() throws Exception {
        if (skipExecution)
            return;
        run1();
    }

    public abstract void run1() throws Exception;
//
//    public static abstract class ActionParametersWithResumeWithBinaryInput extends ActionParametersWithResumeOption {
//        @Override
//        public PipelineConfiguration getFullPipelineConfiguration() {
//            return PipelineConfiguration.appendStep(PipelineConfigurationReader.fromFile(getInputFiles().get(0)), getInputFiles(), getConfiguration());
//        }
//    }
}
