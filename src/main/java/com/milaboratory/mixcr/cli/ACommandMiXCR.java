package com.milaboratory.mixcr.cli;

import com.milaboratory.cli.ACommand;

/**
 *
 */
public abstract class ACommandMiXCR extends ACommand implements MiXCRCommand {
    public ACommandMiXCR() {
        super("mixcr");
    }

    @Override
    public void validateInfo(String inputFile) {
        MiXCRCommand.super.validateInfo(inputFile);
    }
}
