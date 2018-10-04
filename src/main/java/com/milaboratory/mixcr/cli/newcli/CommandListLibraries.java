package com.milaboratory.mixcr.cli.newcli;

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
public class CommandListLibraries extends ACommand {
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
