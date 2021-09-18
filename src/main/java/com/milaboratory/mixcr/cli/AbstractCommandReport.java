/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 * 
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 * 
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */
package com.milaboratory.mixcr.cli;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.mixcr.util.MiXCRVersionInfo;
import com.milaboratory.util.TimeUtils;

import java.util.Collection;
import java.util.Date;

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
        this.inputFiles = inputFiles.toArray(new String[inputFiles.size()]);
    }

    public void setOutputFiles(String... outputFiles) {
        this.outputFiles = outputFiles;
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
            helper.writeField("Analysis time", TimeUtils.nanoTimeToString(getExecutionTimeMillis() * 1000_000));
    }
}
