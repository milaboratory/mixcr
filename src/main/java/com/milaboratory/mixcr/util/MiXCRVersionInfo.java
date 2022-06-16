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
