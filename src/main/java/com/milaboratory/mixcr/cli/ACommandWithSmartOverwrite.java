package com.milaboratory.mixcr.cli;

import com.milaboratory.mixcr.basictypes.ActionConfiguration;
import com.milaboratory.mixcr.basictypes.IOUtil;
import com.milaboratory.mixcr.basictypes.PipelineConfiguration;
import com.milaboratory.mixcr.basictypes.PipelineConfigurationReader;
import picocli.CommandLine;

/** A command which allows resuming execution */
public abstract class ACommandWithSmartOverwrite extends ACommandWithOutput {
    @CommandLine.Option(
            names = "--overwrite-if-required",
            description = "Overwrite output file if it is corrupted or if it was generated from different input file " +
                    "or with different parameters. -f / --force-overwrite overrides this option.")
    public boolean overwriteIfRequired = false;

    /** returns the unique run configuration */
    public abstract ActionConfiguration getConfiguration();

    /** returns the full pipeline configuration that will be written to the output file */
    public abstract PipelineConfiguration getFullPipelineConfiguration();

    public final String getOutput() {
        return getOutputFiles().get(0);
    }

    private boolean outputFileInfoInitialized = false;
    private IOUtil.MiXCRFileInfo outputFileInfo = null;

    public IOUtil.MiXCRFileInfo getOutputFileInfo() {
        if (getOutputFiles().size() != 1) throw new RuntimeException();
        if (!outputFileInfoInitialized) {
            outputFileInfo = IOUtil.getFileInfo(getOutput());
            outputFileInfoInitialized = true;
        }
        return outputFileInfo;
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
        if (forceOverwrite)
            // rewrite anyway
            return;

        // analysis supposed to be performed now
        PipelineConfiguration expectedPipeline = getFullPipelineConfiguration();
        // history written in existing file
        PipelineConfiguration actualPipeline = PipelineConfigurationReader.fromFileOrNull(outFileName, getOutputFileInfo());

        if (actualPipeline != null
                && expectedPipeline != null
                && actualPipeline.compatibleWith(expectedPipeline)) {

            String exists = "File " + outFileName + " already exists and contains correct " +
                    "binary data obtained from the specified input file. ";

            if (!overwriteIfRequired)
                throwValidationException(exists +
                        "Use --overwrite-if-required option to skip execution (output file will remain unchanged) or " +
                        "use -f / --force-overwrite option to force overwrite it.", false);
            else {
                warn("Skipping " + expectedPipeline.lastConfiguration().actionName() + ". " + exists);

                // print warns in case different MiXCR versions
                for (int i = 0; i < expectedPipeline.pipelineSteps.length; i++) {
                    ActionConfiguration
                            prev = actualPipeline.pipelineSteps[i].configuration,
                            curr = expectedPipeline.pipelineSteps[i].configuration;
                    if (!prev.versionId().equals(curr.versionId()))
                        warn(String.format("WARNING (--overwrite-if-required): %s was performed with previous MiXCR version (%s). " +
                                        "Consider re-running analysis using --force-overwrite option.",
                                prev.actionName(),
                                actualPipeline.pipelineSteps[i].versionOfMiXCR));
                }

                skipExecution = true; // nothing to do in run0, just exit
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
}
