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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.mixcr.cli.CommandExtend;
import com.milaboratory.mixcr.cli.MiXCRCommandReport;
import com.milaboratory.util.ReportHelper;

import java.util.Date;

public class VDJCObjectExtenderReport extends MiXCRCommandReport {
    @JsonProperty("totalProcessed")
    public final long totalProcessed;

    @JsonProperty("totalExtended")
    public final long totalExtended;

    @JsonProperty("vExtended")
    public final long vExtended;

    @JsonProperty("vExtendedMerged")
    public final long vExtendedMerged;

    @JsonProperty("jExtended")
    public final long jExtended;

    @JsonProperty("jExtendedMerged")
    public final long jExtendedMerged;

    @JsonProperty("vjExtended")
    public final long vjExtended;

    @JsonProperty("meanVExtensionLength")
    public final double meanVExtensionLength;

    @JsonProperty("meanJExtensionLength")
    public final double meanJExtensionLength;

    @JsonCreator
    public VDJCObjectExtenderReport(
            @JsonProperty("date") Date date,
            @JsonProperty("commandLine") String commandLine,
            @JsonProperty("inputFiles") String[] inputFiles,
            @JsonProperty("outputFiles") String[] outputFiles,
            @JsonProperty("executionTimeMillis") long executionTimeMillis,
            @JsonProperty("version") String version,
            @JsonProperty("totalProcessed") long totalProcessed,
            @JsonProperty("totalExtended") long totalExtended,
            @JsonProperty("vExtended") long vExtended,
            @JsonProperty("vExtendedMerged") long vExtendedMerged,
            @JsonProperty("jExtended") long jExtended,
            @JsonProperty("jExtendedMerged") long jExtendedMerged,
            @JsonProperty("vjExtended") long vjExtended,
            @JsonProperty("meanVExtensionLength") double meanVExtensionLength,
            @JsonProperty("meanJExtensionLength") double meanJExtensionLength
    ) {
        super(date, commandLine, inputFiles, outputFiles, executionTimeMillis, version);
        this.totalProcessed = totalProcessed;
        this.totalExtended = totalExtended;
        this.vExtended = vExtended;
        this.vExtendedMerged = vExtendedMerged;
        this.jExtended = jExtended;
        this.jExtendedMerged = jExtendedMerged;
        this.vjExtended = vjExtended;
        this.meanVExtensionLength = meanVExtensionLength;
        this.meanJExtensionLength = meanJExtensionLength;
    }

    @Override
    public String command() {
        return CommandExtend.COMMAND_NAME;
    }

    @Override
    public void writeReport(ReportHelper helper) {
        // Writing common analysis information
        writeSuperReport(helper);

        helper.writePercentAndAbsoluteField("Extended alignments count", totalExtended, totalProcessed);
        helper.writePercentAndAbsoluteField("V extensions total", vExtended, totalProcessed);
        helper.writePercentAndAbsoluteField("V extensions with merged targets", vExtendedMerged, totalProcessed);
        helper.writePercentAndAbsoluteField("J extensions total", jExtended, totalProcessed);
        helper.writePercentAndAbsoluteField("J extensions with merged targets", jExtendedMerged, totalProcessed);
        helper.writePercentAndAbsoluteField("V+J extensions", vjExtended, totalProcessed);
        helper.writeField("Mean V extension length", meanVExtensionLength);
        helper.writeField("Mean J extension length", meanJExtensionLength);
    }
}
