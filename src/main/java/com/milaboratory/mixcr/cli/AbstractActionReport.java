/*
 * Copyright (c) 2014-2017, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.mixcr.util.MiXCRVersionInfo;
import com.milaboratory.util.TimeUtils;

import java.util.Date;

public abstract class AbstractActionReport implements ActionReport {
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
