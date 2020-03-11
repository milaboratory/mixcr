/*
 * Copyright (c) 2014-2019, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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
 * commercial purposes should contact MiLaboratory LLC, which owns exclusive
 * rights for distribution of this program for commercial purposes, using the
 * following email address: licensing@milaboratory.com.
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

import com.milaboratory.cli.ABaseCommand;
import com.milaboratory.cli.AppVersionInfo;
import com.milaboratory.mixcr.util.MiXCRVersionInfo;
import io.repseq.core.VDJCLibraryRegistry;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.ArrayList;
import java.util.Arrays;

@Command(name = "mixcr",
        versionProvider = CommandMain.VersionProvider.class,
        separator = " ")
public class CommandMain extends ABaseCommand {
    CommandMain() {
        super("mixcr");
    }

    @Option(names = {"-v", "--version"},
            versionHelp = true,
            description = "print version information and exit")
    boolean versionRequested;

    // @Option(names = {"-h", "--help"},
    //         hidden = true)
    // @Override
    // public void requestHelp(boolean b) {
    //     throwValidationException("ERROR: -h / --help is not supported: use `mixcr help` for usage.");
    // }

    static final class VersionProvider implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() throws Exception {
            ArrayList<String> lines = new ArrayList<>();

            lines.addAll(Arrays.asList(MiXCRVersionInfo
                    .get()
                    .getVersionString(AppVersionInfo.OutputType.ToConsole, true)
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
