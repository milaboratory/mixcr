package com.milaboratory.mixcr.cli;

import com.milaboratory.cli.ACommandWithSmartOverwrite;

import static com.milaboratory.mixcr.basictypes.IOUtil.fileInfoExtractorInstance;
import static com.milaboratory.mixcr.basictypes.PipelineConfigurationReaderMiXCR.pipelineConfigurationReaderInstance;

/** A command which allows resuming execution */
public abstract class ACommandWithSmartOverwriteMiXCR extends ACommandWithSmartOverwrite implements MiXCRCommand {
    public ACommandWithSmartOverwriteMiXCR() {
        super("mixcr", fileInfoExtractorInstance, pipelineConfigurationReaderInstance);
    }

    @Override
    public void validateInfo(String inputFile) {
        MiXCRCommand.super.validateInfo(inputFile);
    }
}
