/*
 * Copyright (c) 2014-2022, MiLaboratories Inc. All Rights Reserved
 *
 * Before downloading or accessing the software, please read carefully the
 * License Agreement available at:
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 *
 * By downloading or accessing the software, you accept and agree to be bound
 * by the terms of the License Agreement. If you do not want to agree to the terms
 * of the Licensing Agreement, you must not download or access the software.
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
