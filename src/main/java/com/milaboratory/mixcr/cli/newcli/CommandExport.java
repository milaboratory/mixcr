package com.milaboratory.mixcr.cli.newcli;

import picocli.CommandLine.Parameters;

import java.util.Collections;
import java.util.List;

/**
 *
 */
public class CommandExport extends ACommandWithOutput {
    @Parameters(index = "0", description = "input file")
    private String in;

    @Parameters(index = "1", description = "output file")
    private String out;

    @Override
    public List<String> getInputFiles() {
        return Collections.singletonList(in);
    }

    @Override
    protected List<String> getOutputFiles() {
        return Collections.singletonList(out);
    }

    @Override
    public void run0() throws Exception {

    }
}
