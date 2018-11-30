package com.milaboratory.mixcr.cli;

import com.milaboratory.cli.ACommandWithOutput;

/** A command which produce output files */
public abstract class ACommandWithOutputMiXCR extends ACommandWithOutput implements MiXCRCommand {
    public ACommandWithOutputMiXCR() {
        super("mixcr");
    }

    @Override
    public void validateInfo(String inputFile) {
        MiXCRCommand.super.validateInfo(inputFile);
    }
}
