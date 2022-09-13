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
import java.util.Objects;

public abstract class AbstractCommandReportBuilder<T extends AbstractCommandReportBuilder<T>> implements ReportBuilder {
    private final Date date = new Date();
    private String commandLine;
    private String[] inputFiles, outputFiles;
    private long startMillis, finishMillis;

    public Date getDate() {
        return date;
    }

    public String getCommandLine() {
        return Objects.requireNonNull(commandLine);
    }

    public String[] getInputFiles() {
        return Objects.requireNonNull(inputFiles);
    }

    public String[] getOutputFiles() {
        return Objects.requireNonNull(outputFiles);
    }

    public long getExecutionTimeMillis() {
        if (startMillis == 0L || finishMillis == 0L) throw new IllegalStateException();
        return finishMillis - startMillis;
    }

    public String getVersion() {
        return MiXCRVersionInfo.get().getShortestVersionString();
    }

    public T setCommandLine(String commandLine) {
        this.commandLine = commandLine;
        return that();
    }

    public T setInputFiles(String... inputFiles) {
        this.inputFiles = inputFiles;
        return that();
    }

    public T setInputFiles(Collection<String> inputFiles) {
        this.inputFiles = inputFiles.toArray(new String[0]);
        return that();
    }

    public T setOutputFiles(String... outputFiles) {
        this.outputFiles = outputFiles;
        return that();
    }

    public T setOutputFiles(Collection<String> outputFiles) {
        this.outputFiles = outputFiles.toArray(new String[0]);
        return that();
    }

    public T setStartMillis(long startMillis) {
        this.startMillis = startMillis;
        return that();
    }

    public T setFinishMillis(long finishMillis) {
        this.finishMillis = finishMillis;
        return that();
    }

    protected abstract T that();

    @Override
    public abstract MiXCRCommandReport buildReport();
}
