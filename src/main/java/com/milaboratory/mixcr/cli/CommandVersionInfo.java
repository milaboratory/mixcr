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

import com.milaboratory.mixcr.basictypes.ClnAReader;
import com.milaboratory.mixcr.basictypes.CloneSet;
import com.milaboratory.mixcr.basictypes.CloneSetIO;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader;
import io.repseq.core.VDJCLibraryRegistry;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.Collections;
import java.util.List;

@Command(name = "versionInfo",
        separator = " ",
        description = "Output information about MiXCR version which generated the file.")
public class CommandVersionInfo extends MiXCRCommand {
    @Parameters(description = "input_file")
    public String inputFile;

    @Override
    protected List<String> getInputFiles() {
        return Collections.singletonList(inputFile);
    }

    @Override
    protected List<String> getOutputFiles() {
        return Collections.emptyList();
    }

    @Override
    public void run0() throws Exception {
        String i = inputFile.toLowerCase();
        if (i.endsWith(".vdjca.gz") || i.endsWith(".vdjca")) {
            try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(inputFile)) {
                reader.ensureInitialized();
                System.out.println("MagicBytes = " + reader.getMagic());
                System.out.println(reader.getVersionInfo());
            }
        } else if (i.endsWith(".clns.gz") || i.endsWith(".clns")) {
            CloneSet cs = CloneSetIO.read(inputFile);
            System.out.println(cs.getVersionInfo());
        } else if (i.endsWith(".clna")) {
            try (ClnAReader reader = new ClnAReader(inputFile, VDJCLibraryRegistry.getDefault(), 1)) {
                System.out.println(reader.getVersionInfo());
            }
        } else
            throwValidationException("Wrong file type.");
    }
}
