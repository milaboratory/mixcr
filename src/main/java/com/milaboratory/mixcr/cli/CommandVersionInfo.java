package com.milaboratory.mixcr.cli;

import com.milaboratory.mixcr.basictypes.ClnAReader;
import com.milaboratory.mixcr.basictypes.CloneSet;
import com.milaboratory.mixcr.basictypes.CloneSetIO;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader;
import io.repseq.core.VDJCLibraryRegistry;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "versionInfo",
        separator = " ",
        description = "Output information about MiXCR version which generated the file.")
public class CommandVersionInfo extends ACommand {
    @Parameters(description = "input_file")
    public String inputFile;

    @Override
    public void run0() throws Exception {
        String i = inputFile.toLowerCase();
        if (i.endsWith(".vdjca.gz") || i.endsWith(".vdjca")) {
            try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(inputFile)) {
                reader.init();
                System.out.println("MagicBytes = " + reader.getMagic());
                System.out.println(reader.getVersionInfo());
            }
        } else if (i.endsWith(".clns.gz") || i.endsWith(".clns")) {
            CloneSet cs = CloneSetIO.read(inputFile);
            System.out.println(cs.getVersionInfo());
        } else if (i.endsWith(".clna")) {
            try (ClnAReader reader = new ClnAReader(inputFile, VDJCLibraryRegistry.getDefault())) {
                System.out.println(reader.getVersionInfo());
            }
        } else
            throwValidationException("Wrong file type.");
    }
}
