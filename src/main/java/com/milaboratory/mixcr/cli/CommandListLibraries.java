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

import io.repseq.core.VDJCLibrary;
import io.repseq.core.VDJCLibraryRegistry;
import picocli.CommandLine.Command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Command(name = "listLibraries",
        sortOptions = true,
        hidden = true,
        separator = " ",
        description = "List all available library by scanning all library search paths.")
public class CommandListLibraries extends ACommandMiXCR {
    @Override
    public void run0() {
        VDJCLibraryRegistry.getDefault().loadAllLibraries();
        System.out.println("Available libraries:");
        List<VDJCLibrary> loadedLibraries = new ArrayList<>(VDJCLibraryRegistry.getDefault().getLoadedLibraries());
        Collections.sort(loadedLibraries);
        for (VDJCLibrary library : loadedLibraries) {
            System.out.println(library.getLibraryId());
            System.out.println(VDJCLibraryRegistry.getDefault().getSpeciesNames(library.getTaxonId()));
        }
    }
}
