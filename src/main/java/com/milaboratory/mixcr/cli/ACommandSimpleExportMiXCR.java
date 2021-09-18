/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 * 
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 * 
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */
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
