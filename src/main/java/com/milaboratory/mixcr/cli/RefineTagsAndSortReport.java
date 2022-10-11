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
import com.milaboratory.mitool.refinement.TagCorrectionReport;
import com.milaboratory.util.Report;

import java.util.Date;
import java.util.Objects;

public class RefineTagsAndSortReport extends MiXCRCommandReportWrapper {
    @JsonProperty("correctionReport")
    public final TagCorrectionReport correctionReport;

    @JsonCreator
    public RefineTagsAndSortReport(@JsonProperty("date") Date date,
                                   @JsonProperty("commandLine") String commandLine,
                                   @JsonProperty("inputFiles") String[] inputFiles,
                                   @JsonProperty("outputFiles") String[] outputFiles,
                                   @JsonProperty("executionTimeMillis") Long executionTimeMillis,
                                   @JsonProperty("version") String version,
                                   @JsonProperty("correctionReport") TagCorrectionReport correctionReport) {
        super(date, commandLine, inputFiles, outputFiles, executionTimeMillis, version);
        this.correctionReport = correctionReport;
    }

    @Override
    protected Report innerReport() {
        return correctionReport;
    }

    @Override
    public String command() {
        return CommandRefineTagsAndSort.COMMAND_NAME;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RefineTagsAndSortReport that = (RefineTagsAndSortReport) o;
        return Objects.equals(correctionReport, that.correctionReport);
    }

    @Override
    public int hashCode() {
        return Objects.hash(correctionReport);
    }
}
