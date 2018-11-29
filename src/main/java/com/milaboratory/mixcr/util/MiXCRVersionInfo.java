/*
 * Copyright (c) 2014-2018, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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
package com.milaboratory.mixcr.util;

import com.fasterxml.jackson.annotation.*;
import com.milaboratory.cli.AppVersionInfo;
import com.milaboratory.cli.AppVersionInfo.OutputType;
import com.milaboratory.util.VersionInfo;
import io.repseq.core.VDJCLibraryRegistry;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

@JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.ANY,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE)
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type")
public final class MiXCRVersionInfo {
    private static volatile MiXCRVersionInfo instance = null;
    private final AppVersionInfo appVersionInfo;

    private MiXCRVersionInfo(@JsonProperty("appVersionInfo") AppVersionInfo appVersionInfo) {
        this.appVersionInfo = appVersionInfo;
    }

    public static MiXCRVersionInfo get() {
        if (instance == null)
            synchronized (MiXCRVersionInfo.class) {
                if (instance == null) {
                    HashMap<String, VersionInfo> componentVersions = new HashMap<>();
                    HashMap<String, String> componentStringVersions = new HashMap<>();
                    String libName = "";
                    try (InputStream stream = VDJCLibraryRegistry.class
                            .getResourceAsStream("/libraries/default.alias")) {
                        if (stream != null)
                            libName = IOUtils.toString(stream, StandardCharsets.UTF_8);
                    } catch (IOException ignored) {
                    }
                    componentVersions.put("mixcr", VersionInfo.getVersionInfoForArtifact("mixcr"));
                    componentVersions.put("milib", VersionInfo.getVersionInfoForArtifact("milib"));
                    componentVersions.put("repseqio", VersionInfo.getVersionInfoForArtifact("repseqio"));
                    componentStringVersions.put("builtInLibrary", libName);
                    AppVersionInfo.init(componentVersions, componentStringVersions);
                    instance = new MiXCRVersionInfo(AppVersionInfo.get());
                }
            }
        return instance;
    }

    public static AppVersionInfo getAppVersionInfo() {
        get();  // initialize AppVersionInfo if not initialized
        return AppVersionInfo.get();
    }

    public String getShortestVersionString() {
        VersionInfo mixcr = appVersionInfo.getComponentVersions().get("mixcr");
        return mixcr.getVersion() +
                "; built=" +
                mixcr.getTimestamp() +
                "; rev=" +
                mixcr.getRevision() +
                "; lib=" +
                appVersionInfo.getComponentStringVersions().get("builtInLibrary");
    }

    public String getVersionString(OutputType outputType, boolean full) {
        Map<String, VersionInfo> componentVersions = appVersionInfo.getComponentVersions();
        Map<String, String> componentStringVersions = appVersionInfo.getComponentStringVersions();
        VersionInfo mixcr = componentVersions.get("mixcr");
        VersionInfo milib = componentVersions.get("milib");
        VersionInfo repseqio = componentVersions.get("repseqio");
        String builtInLibrary = componentStringVersions.get("builtInLibrary");

        StringBuilder builder = new StringBuilder();

        builder.append("MiXCR v")
                .append(mixcr.getVersion())
                .append(" (built ")
                .append(mixcr.getTimestamp())
                .append("; rev=")
                .append(mixcr.getRevision())
                .append("; branch=")
                .append(mixcr.getBranch());

        if (full)
            builder.append("; host=")
                    .append(mixcr.getHost());

        builder.append(")")
                .append(outputType.delimiter);

        builder.append("RepSeq.IO v")
                .append(repseqio.getVersion())
                .append(" (rev=")
                .append(repseqio.getRevision())
                .append(")")
                .append(outputType.delimiter);

        builder.append("MiLib v")
                .append(milib.getVersion())
                .append(" (rev=")
                .append(milib.getRevision())
                .append(")")
                .append(outputType.delimiter);

        if (!builtInLibrary.isEmpty())
            builder.append("Built-in V/D/J/C library: ")
                    .append(builtInLibrary)
                    .append(outputType.delimiter);

        return builder.toString();
    }

    public String getVersionString(OutputType outputType) {
        return getVersionString(outputType, false);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MiXCRVersionInfo that = (MiXCRVersionInfo)o;
        return appVersionInfo.equals(that.appVersionInfo);
    }

    @Override
    public int hashCode() {
        return appVersionInfo.hashCode();
    }
}
