package com.milaboratory.mixcr.cli;

import com.milaboratory.mixcr.util.MiXCRVersionInfo;
import io.repseq.core.VDJCLibraryRegistry;
import picocli.CommandLine;

import java.util.ArrayList;
import java.util.Arrays;

@CommandLine.Command(
        name = "mixcr",
        versionProvider = CommandMain.VersionProvider.class,
        separator = " ")
public class CommandMain {
    @CommandLine.Option(
            names = {"-v", "--version"},
            versionHelp = true,
            description = "print version information and exit")
    boolean versionRequested;

    static final class VersionProvider implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() throws Exception {
            ArrayList<String> lines = new ArrayList<>();

            lines.addAll(Arrays.asList(MiXCRVersionInfo
                    .get()
                    .getVersionString(MiXCRVersionInfo.OutputType.ToConsole, true)
                    .split("\n")));

            lines.add("");
            lines.add("Library search path:");

            for (VDJCLibraryRegistry.LibraryResolver resolvers : VDJCLibraryRegistry.getDefault()
                    .getLibraryResolvers()) {
                if (resolvers instanceof VDJCLibraryRegistry.ClasspathLibraryResolver)
                    lines.add("- built-in libraries");
                if (resolvers instanceof VDJCLibraryRegistry.FolderLibraryResolver)
                    lines.add("- " + ((VDJCLibraryRegistry.FolderLibraryResolver) resolvers).getPath());
            }

            return lines.toArray(new String[lines.size()]);
        }
    }
}
