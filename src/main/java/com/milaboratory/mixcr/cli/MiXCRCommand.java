/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 * 
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 * 
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
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
