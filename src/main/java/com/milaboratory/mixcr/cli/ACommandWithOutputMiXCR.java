/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 *
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 *
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */
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
