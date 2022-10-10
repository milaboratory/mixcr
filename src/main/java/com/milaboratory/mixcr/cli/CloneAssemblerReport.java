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
import com.milaboratory.mixcr.assembler.preclone.PreCloneAssemblerReport;
import com.milaboratory.util.ReportHelper;

import java.util.Date;

public class CloneAssemblerReport extends AbstractMiXCRCommandReport {
    @JsonProperty("preCloneAssemblerReport")
    public final PreCloneAssemblerReport preCloneAssemblerReport;

    @JsonProperty("totalReadsProcessed")
    public final long totalReadsProcessed;

    @JsonProperty("initialClonesCreated")
    public final int initialClonesCreated;

    @JsonProperty("readsDroppedNoTargetSequence")
    public final long readsDroppedNoTargetSequence;

    @JsonProperty("readsDroppedTooShortClonalSequence")
    public final long readsDroppedTooShortClonalSequence;

    @JsonProperty("readsDroppedLowQuality")
    public final long readsDroppedLowQuality;

    @JsonProperty("coreReads")
    public final long coreReads;

    @JsonProperty("readsDroppedFailedMapping")
    public final long readsDroppedFailedMapping;

    @JsonProperty("lowQualityRescued")
    public final long lowQualityRescued;

    @JsonProperty("clonesClustered")
    public final int clonesClustered;

    @JsonProperty("readsClustered")
    public final long readsClustered;

    @JsonProperty("clones")
    public final int clones;

    @JsonProperty("clonesDroppedAsLowQuality")
    public final int clonesDroppedAsLowQuality;

    @JsonProperty("clonesPreClustered")
    public final int clonesPreClustered;

    @JsonProperty("readsPreClustered")
    public final long readsPreClustered;

    @JsonProperty("readsInClones")
    public final long readsInClones;

    @JsonProperty("readsInClonesBeforeClustering")
    public final long readsInClonesBeforeClustering;

    @JsonProperty("readsDroppedWithLowQualityClones")
    public final long readsDroppedWithLowQualityClones;

    @JsonProperty("clonalChainUsage")
    public final ChainUsageStats clonalChainUsage;

    @JsonCreator
    public CloneAssemblerReport(@JsonProperty("date") Date date,
                                @JsonProperty("commandLine") String commandLine,
                                @JsonProperty("inputFiles") String[] inputFiles,
                                @JsonProperty("outputFiles") String[] outputFiles,
                                @JsonProperty("executionTimeMillis") Long executionTimeMillis,
                                @JsonProperty("version") String version,
                                @JsonProperty("preCloneAssemblerReport") PreCloneAssemblerReport preCloneAssemblerReport,
                                @JsonProperty("totalReadsProcessed") long totalReadsProcessed,
                                @JsonProperty("initialClonesCreated") int initialClonesCreated,
                                @JsonProperty("readsDroppedNoTargetSequence") long readsDroppedNoTargetSequence,
                                @JsonProperty("readsDroppedTooShortClonalSequence") long readsDroppedTooShortClonalSequence,
                                @JsonProperty("readsDroppedLowQuality") long readsDroppedLowQuality,
                                @JsonProperty("coreReads") long coreReads,
                                @JsonProperty("readsDroppedFailedMapping") long readsDroppedFailedMapping,
                                @JsonProperty("lowQualityRescued") long lowQualityRescued,
                                @JsonProperty("clonesClustered") int clonesClustered,
                                @JsonProperty("readsClustered") long readsClustered,
                                @JsonProperty("clones") int clones,
                                @JsonProperty("clonesDroppedAsLowQuality") int clonesDroppedAsLowQuality,
                                @JsonProperty("clonesPreClustered") int clonesPreClustered,
                                @JsonProperty("readsPreClustered") long readsPreClustered,
                                @JsonProperty("readsInClones") long readsInClones,
                                @JsonProperty("readsInClonesBeforeClustering") long readsInClonesBeforeClustering,
                                @JsonProperty("readsDroppedWithLowQualityClones") long readsDroppedWithLowQualityClones,
                                @JsonProperty("clonalChainUsage") ChainUsageStats clonalChainUsage) {
        super(date, commandLine, inputFiles, outputFiles, executionTimeMillis, version);
        this.preCloneAssemblerReport = preCloneAssemblerReport;
        this.totalReadsProcessed = totalReadsProcessed;
        this.initialClonesCreated = initialClonesCreated;
        this.readsDroppedNoTargetSequence = readsDroppedNoTargetSequence;
        this.readsDroppedTooShortClonalSequence = readsDroppedTooShortClonalSequence;
        this.readsDroppedLowQuality = readsDroppedLowQuality;
        this.coreReads = coreReads;
        this.readsDroppedFailedMapping = readsDroppedFailedMapping;
        this.lowQualityRescued = lowQualityRescued;
        this.clonesClustered = clonesClustered;
        this.readsClustered = readsClustered;
        this.clones = clones;
        this.clonesDroppedAsLowQuality = clonesDroppedAsLowQuality;
        this.clonesPreClustered = clonesPreClustered;
        this.readsPreClustered = readsPreClustered;
        this.readsInClones = readsInClones;
        this.readsInClonesBeforeClustering = readsInClonesBeforeClustering;
        this.readsDroppedWithLowQualityClones = readsDroppedWithLowQualityClones;
        this.clonalChainUsage = clonalChainUsage;
    }

    @Override
    public String command() {
        return CommandAssemble.COMMAND_NAME;
    }

    @Override
    public void writeReport(ReportHelper helper) {
        // Writing common analysis information
        writeSuperReport(helper);

        // Writing pre-clone assembler report (should be present for barcoded analysis)
        if (preCloneAssemblerReport != null)
            preCloneAssemblerReport.writeReport(helper);

        if (totalReadsProcessed == -1)
            throw new IllegalStateException("TotalReads count not set.");

        int clonesCount = clones;

        long alignmentsInClones = readsInClones;

        // Alignments in clones before clusterization
        long clusterizationBase = readsInClonesBeforeClustering;

        helper.writeField("Final clonotype count", clonesCount)
                .writeField("Average number of reads per clonotype", ReportHelper.PERCENT_FORMAT.format(1.0 * alignmentsInClones / clonesCount))
                .writePercentAndAbsoluteField("Reads used in clonotypes, percent of total", alignmentsInClones, totalReadsProcessed)
                .writePercentAndAbsoluteField("Reads used in clonotypes before clustering, percent of total", clusterizationBase, totalReadsProcessed)
                .writePercentAndAbsoluteField("Number of reads used as a core, percent of used", coreReads, clusterizationBase)
                .writePercentAndAbsoluteField("Mapped low quality reads, percent of used", lowQualityRescued, clusterizationBase)
                .writePercentAndAbsoluteField("Reads clustered in PCR error correction, percent of used", readsClustered, clusterizationBase)
                .writePercentAndAbsoluteField("Reads pre-clustered due to the similar VJC-lists, percent of used", readsPreClustered, alignmentsInClones)
                .writePercentAndAbsoluteField("Reads dropped due to the lack of a clone sequence, percent of total",
                        readsDroppedNoTargetSequence, totalReadsProcessed)
                .writePercentAndAbsoluteField("Reads dropped due to a too short clonal sequence, percent of total",
                        readsDroppedTooShortClonalSequence, totalReadsProcessed)
                .writePercentAndAbsoluteField("Reads dropped due to low quality, percent of total",
                        clonesDroppedAsLowQuality, totalReadsProcessed)
                .writePercentAndAbsoluteField("Reads dropped due to failed mapping, percent of total",
                        readsDroppedFailedMapping, totalReadsProcessed)
                .writePercentAndAbsoluteField("Reads dropped with low quality clones, percent of total", readsDroppedWithLowQualityClones, totalReadsProcessed)
                .writeField("Clonotypes eliminated by PCR error correction", clonesClustered)
                .writeField("Clonotypes dropped as low quality", clonesDroppedAsLowQuality)
                .writeField("Clonotypes pre-clustered due to the similar VJC-lists", clonesPreClustered);


        // Writing distribution by chains
        clonalChainUsage.writeReport(helper);
    }
}
