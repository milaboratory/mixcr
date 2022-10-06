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
package com.milaboratory.mixcr.partialassembler;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.mixcr.cli.CommandAssemblePartial;
import com.milaboratory.mixcr.cli.AbstractMiXCRCommandReport;
import com.milaboratory.util.ReportHelper;

import java.util.Date;

public class PartialAlignmentsAssemblerReport extends AbstractMiXCRCommandReport {
    @JsonProperty("independentRuns")
    public final long independentRuns;

    @JsonProperty("totalProcessed")
    public final long totalProcessed;

    @JsonProperty("outputAlignments")
    public final long outputAlignments;

    @JsonProperty("withCDR3")
    public final long withCDR3;

    @JsonProperty("overlapped")
    public final long overlapped;

    @JsonProperty("leftTooShortNRegion")
    public final long leftTooShortNRegion;

    @JsonProperty("kMerDiversity")
    public final long kMerDiversity;

    @JsonProperty("droppedWildcardsInKMer")
    public final long droppedWildcardsInKMer;

    @JsonProperty("droppedSmallOverlapNRegion")
    public final long droppedSmallOverlapNRegion;

    @JsonProperty("droppedNoNRegion")
    public final long droppedNoNRegion;

    @JsonProperty("leftParts")
    public final long leftParts;

    @JsonProperty("rightParts")
    public final long rightParts;

    @JsonProperty("complexOverlaps")
    public final long complexOverlaps;

    @JsonProperty("overOverlaps")
    public final long overOverlaps;

    @JsonProperty("partialAlignmentsAsIs")
    public final long partialAlignmentsAsIs;

    @JsonCreator
    public PartialAlignmentsAssemblerReport(
            @JsonProperty("date") Date date,
            @JsonProperty("commandLine") String commandLine,
            @JsonProperty("inputFiles") String[] inputFiles,
            @JsonProperty("outputFiles") String[] outputFiles,
            @JsonProperty("executionTimeMillis") long executionTimeMillis,
            @JsonProperty("version") String version,
            @JsonProperty("independentRuns") long independentRuns,
            @JsonProperty("totalProcessed") long totalProcessed,
            @JsonProperty("outputAlignments") long outputAlignments,
            @JsonProperty("withCDR3") long withCDR3,
            @JsonProperty("overlapped") long overlapped,
            @JsonProperty("leftTooShortNRegion") long leftTooShortNRegion,
            @JsonProperty("kMerDiversity") long kMerDiversity,
            @JsonProperty("droppedWildcardsInKMer") long droppedWildcardsInKMer,
            @JsonProperty("droppedSmallOverlapNRegion") long droppedSmallOverlapNRegion,
            @JsonProperty("droppedNoNRegion") long droppedNoNRegion,
            @JsonProperty("leftParts") long leftParts,
            @JsonProperty("rightParts") long rightParts,
            @JsonProperty("complexOverlaps") long complexOverlaps,
            @JsonProperty("overOverlaps") long overOverlaps,
            @JsonProperty("partialAlignmentsAsIs") long partialAlignmentsAsIs
    ) {
        super(date, commandLine, inputFiles, outputFiles, executionTimeMillis, version);
        this.independentRuns = independentRuns;
        this.totalProcessed = totalProcessed;
        this.outputAlignments = outputAlignments;
        this.withCDR3 = withCDR3;
        this.overlapped = overlapped;
        this.leftTooShortNRegion = leftTooShortNRegion;
        this.kMerDiversity = kMerDiversity;
        this.droppedWildcardsInKMer = droppedWildcardsInKMer;
        this.droppedSmallOverlapNRegion = droppedSmallOverlapNRegion;
        this.droppedNoNRegion = droppedNoNRegion;
        this.leftParts = leftParts;
        this.rightParts = rightParts;
        this.complexOverlaps = complexOverlaps;
        this.overOverlaps = overOverlaps;
        this.partialAlignmentsAsIs = partialAlignmentsAsIs;
    }

    @Override
    public String command() {
        return CommandAssemblePartial.COMMAND_NAME;
    }

    @Override
    public void writeReport(ReportHelper helper) {
        // Writing common analysis information
        writeSuperReport(helper);

        long total = this.totalProcessed;
        if (independentRuns != 1)
            helper.writeField("Independent runs", independentRuns);
        helper.writeField("Total alignments analysed", total);
        helper.writePercentAndAbsoluteField("Number of output alignments", outputAlignments, total);
        helper.writePercentAndAbsoluteField("Alignments already with CDR3 (no overlapping is performed)", withCDR3, total);
        helper.writePercentAndAbsoluteField("Successfully overlapped alignments", overlapped, total);
        helper.writePercentAndAbsoluteField("Left parts with too small N-region (failed to extract k-mer)", leftTooShortNRegion, total);
        helper.writeField("Extracted k-mer diversity", kMerDiversity);
        helper.writePercentAndAbsoluteField("Dropped due to wildcard in k-mer", droppedWildcardsInKMer, total);
        helper.writePercentAndAbsoluteField("Dropped due to too short NRegion parts in overlap", droppedSmallOverlapNRegion, total);
        helper.writePercentAndAbsoluteField("Dropped overlaps with empty N region due to no complete NDN coverage", droppedNoNRegion, total);
        helper.writePercentAndAbsoluteField("Number of left-side alignments", leftParts, total);
        helper.writePercentAndAbsoluteField("Number of right-side alignments", rightParts, total);
        helper.writePercentAndAbsoluteField("Complex overlaps", complexOverlaps, total);
        helper.writePercentAndAbsoluteField("Over-overlaps", overOverlaps, total);
        helper.writePercentAndAbsoluteField("Partial alignments written to output", partialAlignmentsAsIs, total);
    }
}
