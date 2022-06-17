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

import com.milaboratory.cli.BinaryFileInfo;

import static com.milaboratory.mixcr.basictypes.IOUtil.fileInfoExtractorInstance;

public interface MiXCRCommand {
    void throwValidationException(String message, boolean printHelp);

    /** Validate injected parameters and options */
    default void validateInfo(String inputFile) {
        BinaryFileInfo info = fileInfoExtractorInstance.getFileInfo(inputFile);
        if (info != null && !info.valid)
            throwValidationException("ERROR: input file \"" + inputFile + "\" is corrupted.", false);
    }
}
