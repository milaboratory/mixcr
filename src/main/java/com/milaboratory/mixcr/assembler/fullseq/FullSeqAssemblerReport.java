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
package com.milaboratory.mixcr.assembler.fullseq;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.mixcr.cli.AbstractMiXCRCommandReport;
import com.milaboratory.mixcr.cli.CommandAssembleContigs;
import com.milaboratory.util.ReportHelper;

import java.util.Date;

/**
 *
 */
public class FullSeqAssemblerReport extends AbstractMiXCRCommandReport {
    @JsonProperty("clonesWithAmbiguousLetters")
    public final long clonesWithAmbiguousLetters;

    @JsonProperty("clonesWithAmbiguousLettersInSplittingRegion")
    public final long clonesWithAmbiguousLettersInSplittingRegion;

    @JsonProperty("readsWithAmbiguousLetters")
    public final double readsWithAmbiguousLetters;

    @JsonProperty("readsWithAmbiguousLettersInSplittingRegion")
    public final double readsWithAmbiguousLettersInSplittingRegion;

    @JsonProperty("totalAmbiguousLetters")
    public final long totalAmbiguousLetters;

    @JsonProperty("totalAmbiguousLettersInSplittingRegion")
    public final long totalAmbiguousLettersInSplittingRegion;

    @JsonProperty("initialCloneCount")
    public final int initialCloneCount;

    @JsonProperty("canceledAssemblies")
    public final int canceledAssemblies;

    @JsonProperty("finalCloneCount")
    public final int finalCloneCount;

    @JsonProperty("totalReadsProcessed")
    public final double totalReadsProcessed;

    @JsonProperty("clonesClustered")
    public final int clonesClustered;

    @JsonProperty("readsClustered")
    public final double readsClustered;

    @JsonProperty("longestContigLength")
    public final int longestContigLength;

    @JsonProperty("totalDividedVariantReads")
    public final double totalDividedVariantReads;

    @JsonProperty("assemblePrematureTerminationEvents")
    public final double assemblePrematureTerminationEvents;

    @JsonCreator
    public FullSeqAssemblerReport(
            @JsonProperty("date") Date date,
            @JsonProperty("commandLine") String commandLine,
            @JsonProperty("inputFiles") String[] inputFiles,
            @JsonProperty("outputFiles") String[] outputFiles,
            @JsonProperty("executionTimeMillis") long executionTimeMillis,
            @JsonProperty("version") String version,
            @JsonProperty("clonesWithAmbiguousLetters") long clonesWithAmbiguousLetters,
            @JsonProperty("clonesWithAmbiguousLettersInSplittingRegion") long clonesWithAmbiguousLettersInSplittingRegion,
            @JsonProperty("readsWithAmbiguousLetters") double readsWithAmbiguousLetters,
            @JsonProperty("readsWithAmbiguousLettersInSplittingRegion") double readsWithAmbiguousLettersInSplittingRegion,
            @JsonProperty("totalAmbiguousLetters") long totalAmbiguousLetters,
            @JsonProperty("totalAmbiguousLettersInSplittingRegion") long totalAmbiguousLettersInSplittingRegion,
            @JsonProperty("initialCloneCount") int initialCloneCount,
            @JsonProperty("canceledAssemblies") int canceledAssemblies,
            @JsonProperty("finalCloneCount") int finalCloneCount,
            @JsonProperty("totalReadsProcessed") double totalReadsProcessed,
            @JsonProperty("clonesClustered") int clonesClustered,
            @JsonProperty("readsClustered") double readsClustered,
            @JsonProperty("longestContigLength") int longestContigLength,
            @JsonProperty("totalDividedVariantReads") double totalDividedVariantReads,
            @JsonProperty("assemblePrematureTerminationEvents") double assemblePrematureTerminationEvents
    ) {
        super(date, commandLine, inputFiles, outputFiles, executionTimeMillis, version);
        this.clonesWithAmbiguousLetters = clonesWithAmbiguousLetters;
        this.clonesWithAmbiguousLettersInSplittingRegion = clonesWithAmbiguousLettersInSplittingRegion;
        this.readsWithAmbiguousLetters = readsWithAmbiguousLetters;
        this.readsWithAmbiguousLettersInSplittingRegion = readsWithAmbiguousLettersInSplittingRegion;
        this.totalAmbiguousLetters = totalAmbiguousLetters;
        this.totalAmbiguousLettersInSplittingRegion = totalAmbiguousLettersInSplittingRegion;
        this.initialCloneCount = initialCloneCount;
        this.canceledAssemblies = canceledAssemblies;
        this.finalCloneCount = finalCloneCount;
        this.totalReadsProcessed = totalReadsProcessed;
        this.clonesClustered = clonesClustered;
        this.readsClustered = readsClustered;
        this.longestContigLength = longestContigLength;
        this.totalDividedVariantReads = totalDividedVariantReads;
        this.assemblePrematureTerminationEvents = assemblePrematureTerminationEvents;
    }

    @Override
    public String command() {
        return CommandAssembleContigs.ASSEMBLE_CONTIGS_COMMAND_NAME;
    }

    @Override
    public void writeReport(ReportHelper helper) {
        // Writing common analysis information
        writeSuperReport(helper);

        helper.writeField("Initial clonotype count", initialCloneCount)
                .writePercentAndAbsoluteField("Final clonotype count", finalCloneCount, initialCloneCount)
                .writePercentAndAbsoluteField("Canceled assemblies", canceledAssemblies, initialCloneCount)
                .writePercentAndAbsoluteField("Number of premature termination assembly events, percent of number of initial clonotypes", assemblePrematureTerminationEvents, initialCloneCount)
                .writeField("Longest contig length", longestContigLength)
                .writePercentAndAbsoluteField("Clustered variants", clonesClustered, finalCloneCount + clonesClustered)
                .writePercentAndAbsoluteField("Reads in clustered variants", readsClustered, totalReadsProcessed)
                .writePercentAndAbsoluteField("Reads in divided (newly created) clones", totalDividedVariantReads, totalReadsProcessed)
                // .writePercentAndAbsoluteField("Clones with ambiguous letters", ClonesWithAmbiguousLetters(), FinalCloneCount())
                .writePercentAndAbsoluteField("Clones with ambiguous letters in splitting region", clonesWithAmbiguousLettersInSplittingRegion, finalCloneCount)
                // .writePercentAndAbsoluteField("Reads in clones with ambiguous letters", ReadsWithAmbiguousLetters(), TotalReads())
                .writePercentAndAbsoluteField("Reads in clones with ambiguous letters in splitting region", readsWithAmbiguousLettersInSplittingRegion, totalReadsProcessed)
                // .writeField("Average number of ambiguous letters per clone with ambiguous letters", 1.0 * TotalAmbiguousLetters() / ClonesWithAmbiguousLetters())
                .writeField("Average number of ambiguous letters per clone with ambiguous letters in splitting region", 1.0 * totalAmbiguousLettersInSplittingRegion / clonesWithAmbiguousLettersInSplittingRegion);
    }
}
