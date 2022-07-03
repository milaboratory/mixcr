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

import com.milaboratory.mixcr.util.MiXCRVersionInfo;
import com.milaboratory.util.ReportBuilder;

import java.util.Collection;
import java.util.Date;

public abstract class AbstractCommandReportBuilder implements ReportBuilder {
    private final Date date = new Date();
    private String commandLine;
    private String[] inputFiles, outputFiles;
    private long startMillis, finishMillis;

    public Date getDate() {
        return date;
    }

    public String getCommandLine() {
        return commandLine;
    }

    public String[] getInputFiles() {
        return inputFiles;
    }

    public String[] getOutputFiles() {
        return outputFiles;
    }

    public long getExecutionTimeMillis() {
        return finishMillis - startMillis;
    }

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

    @Override
    public abstract MiXCRCommandReport buildReport();
}
