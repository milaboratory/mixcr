package com.milaboratory.mixcr.cli;

import picocli.CommandLine.Option;

import java.io.File;

/** A command which produce output files */
public abstract class ACommandWithOutput extends ACommand {
    @Option(names = {"-f", "--force-overwrite"},
            description = "Force overwrite of output file(s).")
    public boolean forceOverwrite = false;

    @Option(names = {"--force"}, hidden = true)
    public void setForce(boolean value) {
        if (value) {
            warn("--force option is deprecated; use --force-overwrite instead.");
            forceOverwrite = true;
        }
    }

    @Override
    public void validate() {
        super.validate();
        for (String f : getOutputFiles())
            if (new File(f).exists())
                handleExistenceOfOutputFile(f);
    }

    /** Specifies behaviour in the case with output exists (default is to throw exception) */
    public void handleExistenceOfOutputFile(String outFileName) {
        if (!forceOverwrite)
            throwValidationException("File \"" + outFileName + "\" already exists. Use -f / --force-overwrite option to overwrite it.", false);
    }
}
