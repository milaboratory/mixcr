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
import com.milaboratory.util.ReportHelper;

public class ReadTrimmerReport implements MiXCRReport {
    @JsonProperty("total")
    public final long total;

    @JsonProperty("r1LeftTrimmedEvents")
    public final long r1LeftTrimmedEvents;

    @JsonProperty("r1RightTrimmedEvents")
    public final long r1RightTrimmedEvents;

    @JsonProperty("r2LeftTrimmedEvents")
    public final long r2LeftTrimmedEvents;

    @JsonProperty("r2RightTrimmedEvents")
    public final long r2RightTrimmedEvents;

    @JsonProperty("r1LeftTrimmedNucleotides")
    public final long r1LeftTrimmedNucleotides;

    @JsonProperty("r1RightTrimmedNucleotides")
    public final long r1RightTrimmedNucleotides;

    @JsonProperty("r2LeftTrimmedNucleotides")
    public final long r2LeftTrimmedNucleotides;

    @JsonProperty("r2RightTrimmedNucleotides")
    public final long r2RightTrimmedNucleotides;

    @JsonCreator
    public ReadTrimmerReport(@JsonProperty("total") long total,
                             @JsonProperty("r1LeftTrimmedEvents") long r1LeftTrimmedEvents,
                             @JsonProperty("r1RightTrimmedEvents") long r1RightTrimmedEvents,
                             @JsonProperty("r2LeftTrimmedEvents") long r2LeftTrimmedEvents,
                             @JsonProperty("r2RightTrimmedEvents") long r2RightTrimmedEvents,
                             @JsonProperty("r1LeftTrimmedNucleotides") long r1LeftTrimmedNucleotides,
                             @JsonProperty("r1RightTrimmedNucleotides") long r1RightTrimmedNucleotides,
                             @JsonProperty("r2LeftTrimmedNucleotides") long r2LeftTrimmedNucleotides,
                             @JsonProperty("r2RightTrimmedNucleotides") long r2RightTrimmedNucleotides) {
        this.total = total;
        this.r1LeftTrimmedEvents = r1LeftTrimmedEvents;
        this.r1RightTrimmedEvents = r1RightTrimmedEvents;
        this.r2LeftTrimmedEvents = r2LeftTrimmedEvents;
        this.r2RightTrimmedEvents = r2RightTrimmedEvents;
        this.r1LeftTrimmedNucleotides = r1LeftTrimmedNucleotides;
        this.r1RightTrimmedNucleotides = r1RightTrimmedNucleotides;
        this.r2LeftTrimmedNucleotides = r2LeftTrimmedNucleotides;
        this.r2RightTrimmedNucleotides = r2RightTrimmedNucleotides;
    }

    @Override
    public void writeReport(ReportHelper helper) {
        // assert trimmingReport.getAlignments() == total;
        helper.writePercentAndAbsoluteField("R1 Reads Trimmed Left", r1LeftTrimmedEvents, total);
        helper.writePercentAndAbsoluteField("R1 Reads Trimmed Right", r1RightTrimmedEvents, total);
        helper.writeField("Average R1 Nucleotides Trimmed Left", 1.0 * r1LeftTrimmedNucleotides / total);
        helper.writeField("Average R1 Nucleotides Trimmed Right", 1.0 * r1RightTrimmedNucleotides / total);
        if (r2LeftTrimmedEvents > 0 || r2RightTrimmedEvents > 0) {
            helper.writePercentAndAbsoluteField("R2 Reads Trimmed Left", r2LeftTrimmedEvents, total);
            helper.writePercentAndAbsoluteField("R2 Reads Trimmed Right", r2RightTrimmedEvents, total);
            helper.writeField("Average R2 Nucleotides Trimmed Left", 1.0 * r2LeftTrimmedNucleotides / total);
            helper.writeField("Average R2 Nucleotides Trimmed Right", 1.0 * r2RightTrimmedNucleotides / total);
        }
    }
}
