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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.util.Report;
import com.milaboratory.util.ReportHelper;

import java.util.Date;

public abstract class MiXCRCommandReportWrapper extends MiXCRCommandReport {
    @JsonCreator
    public MiXCRCommandReportWrapper(@JsonProperty("date") Date date,
                                     @JsonProperty("commandLine") String commandLine,
                                     @JsonProperty("inputFiles") String[] inputFiles,
                                     @JsonProperty("outputFiles") String[] outputFiles,
                                     @JsonProperty("executionTimeMillis") long executionTimeMillis,
                                     @JsonProperty("version") String version) {
        super(date, commandLine, inputFiles, outputFiles, executionTimeMillis, version);
    }

    abstract Report innerReport();

    @Override
    public void writeReport(ReportHelper helper) {
        writeSuperReport(helper);
        Report report = innerReport();
        if (report != null)
            report.writeReport(helper);
    }
}
