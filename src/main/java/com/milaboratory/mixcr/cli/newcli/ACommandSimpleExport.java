package com.milaboratory.mixcr.cli.newcli;

import picocli.CommandLine.Parameters;

import java.util.Collections;
import java.util.List;

/**
 *
 */
public abstract class ACommandSimpleExport extends ACommandWithOutput {
    @Parameters(index = "0", description = "input_file")
    public String in;

    @Parameters(index = "1", description = "[output_file]", arity = "0..1")
    public String out = null;

    @Override
    public List<String> getInputFiles() {
        return Collections.singletonList(in);
    }

    @Override
    protected List<String> getOutputFiles() {
        return out == null ? Collections.emptyList() : Collections.singletonList(out);
    }
}
