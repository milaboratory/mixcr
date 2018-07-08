package com.milaboratory.mixcr.cli;

import com.beust.jcommander.Parameter;
import com.milaboratory.cli.ActionParametersWithOutput;
import com.milaboratory.mixcr.basictypes.ActionConfiguration;

import java.util.List;

/**
 * Action parameters which define a unique run configuration which allows to resume or even skip the execution of
 * already processed file
 */
public abstract class ActionParametersWithResume extends ActionParametersWithOutput {
    @Parameter(names = "--resume", description = "try to resume aborted execution")
    public Boolean resume;

    public boolean resume() {
        return resume != null && resume;
    }

    /** returns the input files */
    public abstract List<String> getInputFiles();

    /** returns the unique run configuration */
    public abstract ActionConfiguration getConfiguration();
}
