package com.milaboratory.mixcr.cli;

import com.milaboratory.cli.ACommandSimpleExport;

/**
 *
 */
public abstract class ACommandSimpleExportMiXCR extends ACommandSimpleExport implements MiXCRCommand {
    public ACommandSimpleExportMiXCR() {
        super("mixcr");
    }

    @Override
    public void validateInfo(String inputFile) {
        MiXCRCommand.super.validateInfo(inputFile);
    }
}
