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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.util.ReportHelper;

import java.io.ByteArrayOutputStream;
import java.util.Date;

import static com.milaboratory.util.FormatUtils.nanoTimeToString;

public abstract class MiXCRCommandReport implements MiXCRReport {
    @JsonIgnore
    public final Date date;

    @JsonProperty("commandLine")
    public final String commandLine;

    @JsonProperty("inputFiles")
    public final String[] inputFiles;

    @JsonProperty("outputFiles")
    public final String[] outputFiles;

    @JsonIgnore
    public final long executionTimeMillis;

    @JsonProperty("version")
    public final String version;

    public MiXCRCommandReport(Date date,
                              String commandLine,
                              String[] inputFiles,
                              String[] outputFiles,
                              long executionTimeMillis,
                              String version) {
        this.date = date;
        this.commandLine = commandLine;
        this.inputFiles = inputFiles;
        this.outputFiles = outputFiles;
        this.executionTimeMillis = executionTimeMillis;
        this.version = version;
    }

    public abstract String command();

    public void writeSuperReport(ReportHelper helper) {
        if (helper.isStdout()) {
            String command = command().substring(0, 1).toUpperCase() + command().substring(1);
            helper.writeLine("============== " + command + " Report ==============");
        }

        if (!helper.isStdout())
            helper.writeNotNullField("Analysis date", date)
                    .writeNotNullField("Input file(s)", join(inputFiles))
                    .writeNotNullField("Output file(s)", join(outputFiles))
                    .writeNotNullField("Version", version);

        if (!helper.isStdout())
            if (commandLine != null)
                helper.writeNotNullField("Command line arguments", commandLine);

        if (executionTimeMillis >= 0)
            helper.writeNotNullField("Analysis time", nanoTimeToString(executionTimeMillis * 1000_000));
    }

    @Override
    public String toString() {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        writeReport(new ReportHelper(os, true));
        return os.toString();
    }

    static String join(String[] strs) {
        if (strs == null) return null;
        return String.join(",", strs);
    }
}
