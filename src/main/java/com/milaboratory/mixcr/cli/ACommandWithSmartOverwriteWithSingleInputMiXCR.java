/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 *
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 *
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */
package com.milaboratory.mixcr.cli;

import com.milaboratory.cli.ACommandWithSmartOverwriteWithSingleInput;
import com.milaboratory.cli.PipelineConfiguration;
import com.milaboratory.mixcr.util.MiXCRVersionInfo;

import static com.milaboratory.mixcr.basictypes.IOUtil.fileInfoExtractorInstance;
import static com.milaboratory.mixcr.basictypes.PipelineConfigurationReaderMiXCR.pipelineConfigurationReaderInstance;

/**
 *
 */
public abstract class ACommandWithSmartOverwriteWithSingleInputMiXCR extends ACommandWithSmartOverwriteWithSingleInput
        implements MiXCRCommand {
    public ACommandWithSmartOverwriteWithSingleInputMiXCR() {
        super("mixcr", fileInfoExtractorInstance, pipelineConfigurationReaderInstance);
    }

    @Override
    public void validateInfo(String inputFile) {
        MiXCRCommand.super.validateInfo(inputFile);
    }

    @Override
    public PipelineConfiguration getFullPipelineConfiguration() {
        return PipelineConfiguration.appendStep(pipelineConfigurationReader.fromFile(in, getInputFileInfo()),
                getInputFiles(), getConfiguration(), MiXCRVersionInfo.getAppVersionInfo());
    }
}
