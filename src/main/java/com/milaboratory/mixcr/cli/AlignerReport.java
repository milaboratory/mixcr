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
import com.milaboratory.mitool.report.ParseReport;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignmentFailCause;
import com.milaboratory.util.ReportHelper;

import java.util.Date;
import java.util.SortedMap;

public final class AlignerReport extends AbstractMiXCRCommandReport {
    @JsonProperty("trimmingReport")
    public final ReadTrimmerReport trimmingReport;

    @JsonProperty("totalReadsProcessed")
    public final long totalReadsProcessed;

    @JsonProperty("aligned")
    public final long aligned;

    @JsonProperty("notAligned")
    public final long notAligned;

    @JsonProperty("notAlignedReasons")
    public final SortedMap<VDJCAlignmentFailCause, Long> notAlignedReasons;

    @JsonProperty("chimeras")
    public final long chimeras;

    @JsonProperty("overlapped")
    public final long overlapped;

    @JsonProperty("alignmentAidedOverlaps")
    public final long alignmentAidedOverlaps;

    @JsonProperty("overlappedAligned")
    public final long overlappedAligned;

    @JsonProperty("overlappedNotAligned")
    public final long overlappedNotAligned;

    @JsonProperty("pairedEndAlignmentConflicts")
    public final long pairedEndAlignmentConflicts;

    @JsonProperty("vChimeras")
    public final long vChimeras;

    @JsonProperty("jChimeras")
    public final long jChimeras;

    @JsonProperty("chainUsage")
    public final ChainUsageStats chainUsage;

    @JsonProperty("realignedWithForcedNonFloatingBound")
    public final long realignedWithForcedNonFloatingBound;

    @JsonProperty("realignedWithForcedNonFloatingRightBoundInLeftRead")
    public final long realignedWithForcedNonFloatingRightBoundInLeftRead;

    @JsonProperty("realignedWithForcedNonFloatingLeftBoundInRightRead")
    public final long realignedWithForcedNonFloatingLeftBoundInRightRead;

    @JsonProperty("noCDR3PartsAlignments")
    public final long noCDR3PartsAlignments;

    @JsonProperty("partialAlignments")
    public final long partialAlignments;

    @JsonProperty("tagParsingReport")
    public final ParseReport tagParsingReport;

    @JsonCreator
    public AlignerReport(@JsonProperty("date") Date date,
                         @JsonProperty("commandLine") String commandLine,
                         @JsonProperty("inputFiles") String[] inputFiles,
                         @JsonProperty("outputFiles") String[] outputFiles,
                         @JsonProperty("executionTimeMillis") Long executionTimeMillis,
                         @JsonProperty("version") String version,
                         @JsonProperty("trimmingReport") ReadTrimmerReport trimmingReport,
                         @JsonProperty("totalReadsProcessed") long totalReadsProcessed,
                         @JsonProperty("aligned") long aligned,
                         @JsonProperty("notAligned") long notAligned,
                         @JsonProperty("notAlignedReasons") SortedMap<VDJCAlignmentFailCause, Long> notAlignedReasons,
                         @JsonProperty("chimeras") long chimeras,
                         @JsonProperty("overlapped") long overlapped,
                         @JsonProperty("alignmentAidedOverlaps") long alignmentAidedOverlaps,
                         @JsonProperty("overlappedAligned") long overlappedAligned,
                         @JsonProperty("overlappedNotAligned") long overlappedNotAligned,
                         @JsonProperty("pairedEndAlignmentConflicts") long pairedEndAlignmentConflicts,
                         @JsonProperty("vChimeras") long vChimeras,
                         @JsonProperty("jChimeras") long jChimeras,
                         @JsonProperty("chainUsage") ChainUsageStats chainUsage,
                         @JsonProperty("realignedWithForcedNonFloatingBound") long realignedWithForcedNonFloatingBound,
                         @JsonProperty("realignedWithForcedNonFloatingRightBoundInLeftRead") long realignedWithForcedNonFloatingRightBoundInLeftRead,
                         @JsonProperty("realignedWithForcedNonFloatingLeftBoundInRightRead") long realignedWithForcedNonFloatingLeftBoundInRightRead,
                         @JsonProperty("noCDR3PartsAlignments") long noCDR3PartsAlignments,
                         @JsonProperty("partialAlignments") long partialAlignments,
                         @JsonProperty("tagParsingReport") ParseReport tagParsingReport) {
        super(date, commandLine, inputFiles, outputFiles, executionTimeMillis, version);
        this.trimmingReport = trimmingReport;
        this.totalReadsProcessed = totalReadsProcessed;
        this.aligned = aligned;
        this.notAligned = notAligned;
        this.notAlignedReasons = notAlignedReasons;
        this.chimeras = chimeras;
        this.overlapped = overlapped;
        this.alignmentAidedOverlaps = alignmentAidedOverlaps;
        this.overlappedAligned = overlappedAligned;
        this.overlappedNotAligned = overlappedNotAligned;
        this.pairedEndAlignmentConflicts = pairedEndAlignmentConflicts;
        this.vChimeras = vChimeras;
        this.jChimeras = jChimeras;
        this.chainUsage = chainUsage;
        this.realignedWithForcedNonFloatingBound = realignedWithForcedNonFloatingBound;
        this.realignedWithForcedNonFloatingRightBoundInLeftRead = realignedWithForcedNonFloatingRightBoundInLeftRead;
        this.realignedWithForcedNonFloatingLeftBoundInRightRead = realignedWithForcedNonFloatingLeftBoundInRightRead;
        this.noCDR3PartsAlignments = noCDR3PartsAlignments;
        this.partialAlignments = partialAlignments;
        this.tagParsingReport = tagParsingReport;
    }

    @Override
    public String command() {
        return CommandAlign.COMMAND_NAME;
    }

    @Override
    public void writeReport(ReportHelper helper) {
        // Writing common analysis information
        writeSuperReport(helper);

        long total = totalReadsProcessed;
        long success = aligned;
        helper.writeField("Total sequencing reads", total);
        helper.writePercentAndAbsoluteField("Successfully aligned reads", success, total);

        if (chimeras != 0)
            helper.writePercentAndAbsoluteField("Chimeras", chimeras, total);

        if (pairedEndAlignmentConflicts != 0)
            helper.writePercentAndAbsoluteField("Paired-end alignment conflicts eliminated", pairedEndAlignmentConflicts, total);

        for (VDJCAlignmentFailCause cause : VDJCAlignmentFailCause.values())
            if (notAlignedReasons.get(cause) != 0)
                helper.writePercentAndAbsoluteField(cause.reportLine, notAlignedReasons.get(cause), total);

        helper.writePercentAndAbsoluteField("Overlapped", overlapped, total);
        helper.writePercentAndAbsoluteField("Overlapped and aligned", overlappedAligned, total);
        helper.writePercentAndAbsoluteField("Alignment-aided overlaps", alignmentAidedOverlaps, overlappedAligned);
        helper.writePercentAndAbsoluteField("Overlapped and not aligned", overlappedNotAligned, total);
        helper.writePercentAndAbsoluteFieldNonZero("No CDR3 parts alignments, percent of successfully aligned", noCDR3PartsAlignments, success);
        helper.writePercentAndAbsoluteFieldNonZero("Partial aligned reads, percent of successfully aligned", partialAlignments, success);

        if (vChimeras != 0)
            helper.writePercentAndAbsoluteField("V gene chimeras", vChimeras, total);

        if (jChimeras != 0)
            helper.writePercentAndAbsoluteField("J gene chimeras", jChimeras, total);

        // Writing distribution by chains
        chainUsage.writeReport(helper);

        helper.writePercentAndAbsoluteField("Realigned with forced non-floating bound", realignedWithForcedNonFloatingBound, total);
        helper.writePercentAndAbsoluteField("Realigned with forced non-floating right bound in left read", realignedWithForcedNonFloatingRightBoundInLeftRead, total);
        helper.writePercentAndAbsoluteField("Realigned with forced non-floating left bound in right read", realignedWithForcedNonFloatingLeftBoundInRightRead, total);

        if (trimmingReport != null) {
            helper.println("Trimming report:");
            trimmingReport.writeReport(helper.indentedHelper());
        }

        if (tagParsingReport != null) {
            helper.println("Tag parsing report:");
            tagParsingReport.writeReport(helper.indentedHelper());
        }
    }
}
