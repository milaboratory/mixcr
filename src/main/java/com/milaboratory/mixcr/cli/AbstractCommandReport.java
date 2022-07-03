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

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.mixcr.util.MiXCRVersionInfo;
import com.milaboratory.util.ReportHelper;

import java.util.Collection;
import java.util.Date;

import static com.milaboratory.util.FormatUtils.nanoTimeToString;

public abstract class AbstractCommandReport implements CommandReport {
    private Date date = new Date();
    private String commandLine;
    private String[] inputFiles, outputFiles;
    private long startMillis, finishMillis;

    @JsonProperty("timestamp")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss.SSSZ")
    public Date getDate() {
        return date;
    }

    @JsonProperty("commandLine")
    public String getCommandLine() {
        return commandLine;
    }

    @JsonProperty("inputFiles")
    public String[] getInputFiles() {
        return inputFiles;
    }

    @JsonProperty("outputFiles")
    public String[] getOutputFiles() {
        return outputFiles;
    }

    @JsonProperty("executionTimeMillis")
    public long getExecutionTimeMillis() {
        return finishMillis - startMillis;
    }

    @JsonProperty("version")
    public String getVersion() {
        return MiXCRVersionInfo.get().getShortestVersionString();
    }

    public void setCommandLine(String commandLine) {
        this.commandLine = commandLine;
    }

    public void setInputFiles(String... inputFiles) {
        this.inputFiles = inputFiles;
    }

    public void setInputFiles(Collection<String> inputFiles) {
        this.inputFiles = inputFiles.toArray(new String[0]);
    }

    public void setOutputFiles(String... outputFiles) {
        this.outputFiles = outputFiles;
    }

    public void setOutputFiles(Collection<String> outputFiles) {
        this.outputFiles = outputFiles.toArray(new String[0]);
    }

    public void setStartMillis(long startMillis) {
        this.startMillis = startMillis;
    }

    public void setFinishMillis(long finishMillis) {
        this.finishMillis = finishMillis;
    }

    private static String join(String[] strs) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; ; ++i) {
            builder.append(strs[i]);
            if (i == strs.length - 1)
                break;
            builder.append(',');
        }
        return builder.toString();
    }

    public void writeSuperReport(ReportHelper helper) {
        if (!helper.isStdout())
            helper.writeField("Analysis date", getDate())
                    .writeField("Input file(s)", join(getInputFiles()))
                    .writeField("Output file(s)", join(getOutputFiles()))
                    .writeField("Version", getVersion());

        if (!helper.isStdout())
            if (getCommandLine() != null)
                helper.writeField("Command line arguments", getCommandLine());

        if (getExecutionTimeMillis() >= 0)
            helper.writeField("Analysis time", nanoTimeToString(getExecutionTimeMillis() * 1000_000));
    }
}
