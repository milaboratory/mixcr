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
package com.milaboratory.mixcr.util;

import com.milaboratory.util.VersionInfo;
import io.repseq.core.VDJCLibrary;
import io.repseq.core.VDJCLibraryRegistry;

public class VersionInfoProvider {
    private VersionInfoProvider() {
    }

    public static String getVersionString(OutputType outputType) {
        VDJCLibraryRegistry reg = VDJCLibraryRegistry.createDefaultRegistry();
        reg.loadAllLibraries("default");

        VersionInfo milib = VersionInfo.getVersionInfoForArtifact("milib");
        VersionInfo mixcr = VersionInfo.getVersionInfoForArtifact("mixcr");

        StringBuilder builder = new StringBuilder();

        builder.append("MiXCR v")
                .append(mixcr.getVersion())
                .append(" (built ")
                .append(mixcr.getTimestamp())
                .append("; rev=")
                .append(mixcr.getRevision())
                .append("; branch=")
                .append(mixcr.getBranch())
                .append(")")
                .append(outputType.delimiter);

        if (outputType.componentsWord)
            builder.append("Components: ").append(outputType.delimiter);

        builder.append("MiLib v")
                .append(milib.getVersion())
                .append(" (rev=").append(milib.getRevision())
                .append("; branch=").append(milib.getBranch())
                .append(")");

        builder.append("Built-in libraries:\n");

        for (VDJCLibrary lib : reg.getLoadedLibraries())
            builder.append(lib.getLibraryId()).append("\n");

        return builder.toString();
    }

    public enum OutputType {
        ToConsole("\n", true), ToFile("; ", false);
        final String delimiter;
        final boolean componentsWord;

        OutputType(String delimiter, boolean componentsWord) {
            this.delimiter = delimiter;
            this.componentsWord = componentsWord;
        }
    }
}
