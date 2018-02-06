/*
 * Copyright (c) 2014-2015, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
 * (here and after addressed as Inventors)
 * All Rights Reserved
 *
 * Permission to use, copy, modify and distribute any part of this program for
 * educational, research and non-profit purposes, by non-profit institutions
 * only, without fee, and without a written agreement is hereby granted,
 * provided that the above copyright notice, this paragraph and the following
 * three paragraphs appear in all copies.
 *
 * Those desiring to incorporate this work into commercial products or use for
 * commercial purposes should contact the Inventors using one of the following
 * email addresses: chudakovdm@mail.ru, chudakovdm@gmail.com
 *
 * IN NO EVENT SHALL THE INVENTORS BE LIABLE TO ANY PARTY FOR DIRECT, INDIRECT,
 * SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING LOST PROFITS,
 * ARISING OUT OF THE USE OF THIS SOFTWARE, EVEN IF THE INVENTORS HAS BEEN
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * THE SOFTWARE PROVIDED HEREIN IS ON AN "AS IS" BASIS, AND THE INVENTORS HAS
 * NO OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR
 * MODIFICATIONS. THE INVENTORS MAKES NO REPRESENTATIONS AND EXTENDS NO
 * WARRANTIES OF ANY KIND, EITHER IMPLIED OR EXPRESS, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY OR FITNESS FOR A
 * PARTICULAR PURPOSE, OR THAT THE USE OF THE SOFTWARE WILL NOT INFRINGE ANY
 * PATENT, TRADEMARK OR OTHER RIGHTS.
 */
package com.milaboratory.mixcr.cli;

import com.milaboratory.cli.JCommanderBasedMain;
import com.milaboratory.mixcr.util.MiXCRVersionInfo;
import com.milaboratory.util.TempFileManager;
import io.repseq.core.VDJCLibraryRegistry;
import io.repseq.seqbase.SequenceResolvers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    private static volatile boolean initialized = false;

    public static void main(String... args) throws Exception {
        // Getting command string if executed from script
        String command = System.getProperty("mixcr.command", "java -jar mixcr.jar");

        if (!initialized) {
            TempFileManager.setPrefix("mixcr_");

            Path cachePath = Paths.get(System.getProperty("user.home"), ".mixcr", "cache");
            //if (System.getProperty("allow.http") != null || System.getenv("MIXCR_ALLOW_HTTP") != null)
            //TODO add mechanism to deny http requests
            SequenceResolvers.initDefaultResolver(cachePath);

            Path libraries = Paths.get(System.getProperty("user.home"), ".mixcr", "libraries");

            VDJCLibraryRegistry.getDefault().addPathResolverWithPartialSearch(".");

            if (System.getProperty("mixcr.path") != null) {
                Path bin = Paths.get(System.getProperty("mixcr.path"));
                Path searchPath = bin.resolve("libraries");
                if (Files.exists(searchPath))
                    VDJCLibraryRegistry.getDefault().addPathResolverWithPartialSearch(searchPath);
            }

            if (System.getProperty("library.path") != null)
                VDJCLibraryRegistry.getDefault().addPathResolverWithPartialSearch(System.getProperty("library.path"));

            if (System.getenv("MIXCR_LIBRARY_PATH") != null)
                VDJCLibraryRegistry.getDefault().addPathResolverWithPartialSearch(System.getenv("MIXCR_LIBRARY_PATH"));

            if (Files.exists(libraries))
                VDJCLibraryRegistry.getDefault().addPathResolverWithPartialSearch(libraries);
            initialized = true;
        }
        // Setting up main helper
        JCommanderBasedMain main = new JCommanderBasedMain(command,
                new ActionAlign(),
                new ActionExportAlignments(),
                new ActionAssemble(),
                new ActionExportClones(),
                new ActionExportAlignmentsPretty(),
                new ActionExportClonesPretty(),
                new ActionAlignmentsStat(),
                new ActionMergeAlignments(),
                new ActionInfo(),
                // new ActionExportCloneReads(),
                new VersionInfoAction(),
                new ActionAlignmentsDiff(),
                new ActionAssemblePartialAlignments(),
                new ActionAssembleContig(),
                new ActionExportReads(),
                new ActionClonesDiff(),
                new ActionFilterAlignments(),
                new ActionListLibraries(),
                new ActionExtendAlignments(),
                new ActionSortAlignments());

        // Adding version info callback
        main.setVersionInfoCallback(
                new Runnable() {
                    @Override
                    public void run() {
                        printVersion(false);
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        printVersion(true);
                    }
                });

        // Executing main method
        JCommanderBasedMain.ProcessResult processResult = main.main(args);

        // If something was wrong, exit with code 1
        if (processResult == JCommanderBasedMain.ProcessResult.Error)
            System.exit(1);
    }

    static void printVersion(boolean full) {
        System.err.print(
                MiXCRVersionInfo.get().getVersionString(
                        MiXCRVersionInfo.OutputType.ToConsole, full));
        System.err.println();
        System.err.println("Library search path:");
        for (VDJCLibraryRegistry.LibraryResolver resolvers : VDJCLibraryRegistry.getDefault()
                .getLibraryResolvers()) {
            if (resolvers instanceof VDJCLibraryRegistry.ClasspathLibraryResolver)
                System.out.println("- built-in libraries");
            if (resolvers instanceof VDJCLibraryRegistry.FolderLibraryResolver)
                System.out.println("- " + ((VDJCLibraryRegistry.FolderLibraryResolver) resolvers).getPath());
        }
    }
}
