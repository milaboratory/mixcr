package com.milaboratory.mixcr.cli.newcli;

import picocli.CommandLine.Option;

import java.io.File;

/** A command which produce output files */
public abstract class ACommandWithOutput extends ACommand {
    @Option(names = {"-f", "--force"},
            description = "Force overwrite of output file(s).")
    public boolean force = false;

    @Override
    public void validate() {
        super.validate();
        for (String f : getOutputFiles())
            if (new File(f).exists())
                handleExistenceOfOutputFile(f);
    }

    /** Specifies behaviour in the case with output exists (default is to throw exception) */
    public void handleExistenceOfOutputFile(String outFileName) {
        if (!force)
            throwValidationException("File \"" + outFileName + "\" already exists. Use -f option to overwrite it.", false);
    }
}
