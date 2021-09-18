/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 * 
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 * 
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */
package com.milaboratory.mixcr.assembler.fullseq;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.util.concurrent.AtomicDouble;
import com.milaboratory.mixcr.assembler.fullseq.FullSeqAssembler.VariantBranch;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.cli.Report;
import com.milaboratory.mixcr.cli.ReportHelper;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

/**
 *
 */
public class FullSeqAssemblerReport implements Report {
    private final AtomicInteger assemblePrematureTerminationCount = new AtomicInteger(0);
    private final AtomicInteger initialCloneCount = new AtomicInteger(0);
    private final AtomicInteger finalCloneCount = new AtomicInteger(0);
    private final AtomicDouble totalReads = new AtomicDouble(0);
    private final AtomicInteger totalClustered = new AtomicInteger(0);
    private final AtomicDouble totalClusteredReads = new AtomicDouble(0);
    private final AtomicDouble dividedReads = new AtomicDouble(0);
    private final AtomicInteger longestContigLength = new AtomicInteger(0);
    private final AtomicLong variantsBeforeClustering = new AtomicLong(0);
    private final AtomicInteger canceledAssemblies = new AtomicInteger(0);
    private final AtomicLong vHitsReorder = new AtomicLong(0);
    private final AtomicLong jHitsReorder = new AtomicLong(0);

    public void onVariantClustered(VariantBranch minor) {
        totalClustered.incrementAndGet();
        totalClusteredReads.addAndGet(minor.count);
    }

    public void onEmptyOutput(Clone clone) {
        assemblePrematureTerminationCount.incrementAndGet();
    }

    public void onVariantsCreated(List<VariantBranch> branches) {
        variantsBeforeClustering.addAndGet(branches.size());
    }

    public void onVHitReorder() {
        vHitsReorder.incrementAndGet();
    }

    public void onJHitReorder() {
        jHitsReorder.incrementAndGet();
    }

    public void onAssemblyCanceled(Clone initialClone) {
        canceledAssemblies.incrementAndGet();
        initialCloneCount.incrementAndGet();
        finalCloneCount.incrementAndGet();
        totalReads.addAndGet(initialClone.getCount());
    }

    public void afterVariantsClustered(Clone initialClone, Clone[] branches) {
        initialCloneCount.incrementAndGet();
        finalCloneCount.addAndGet(branches.length);
        totalReads.addAndGet(initialClone.getCount());
        if (branches.length > 1)
            dividedReads.addAndGet(Arrays.stream(branches, 1, branches.length).mapToDouble(Clone::getCount).sum());
        int maxLength = Arrays.stream(branches)
                .mapToInt(clone -> IntStream.range(0, clone.numberOfTargets()).map(i -> clone.getTarget(i).size()).sum())
                .max().orElse(0);
        longestContigLength.accumulateAndGet(maxLength, Math::max);
    }

    @JsonProperty("initialCloneCount")
    public int getInitialCloneCount() {
        return initialCloneCount.get();
    }

    @JsonProperty("canceledAssemblies")
    public int getCanceledAssemblies() {
        return canceledAssemblies.get();
    }

    @JsonProperty("finalCloneCount")
    public int getFinalCloneCount() {
        return finalCloneCount.get();
    }

    @JsonProperty("totalReadsProcessed")
    public double getTotalReads() {
        return totalReads.get();
    }

    @JsonProperty("clonesClustered")
    public int getClonesClustered() {
        return totalClustered.get();
    }

    @JsonProperty("readsClustered")
    public double getReadsClustered() {
        return totalClusteredReads.get();
    }

    @JsonProperty("longestContigLength")
    public int getLongestContigLength() {
        return longestContigLength.get();
    }

    @JsonProperty("totalDividedVariantReads")
    public double getTotalDividedVariantReads() {
        return dividedReads.get();
    }

    @JsonProperty("assemblePrematureTerminationEvents")
    public double getAssemblePrematureTerminationEvents() {
        return assemblePrematureTerminationCount.get();
    }

    @Override
    public void writeReport(ReportHelper helper) {
        helper.writeField("Initial clonotype count", getInitialCloneCount())
                .writePercentAndAbsoluteField("Final clonotype count", getFinalCloneCount(), getInitialCloneCount())
                .writePercentAndAbsoluteField("Canceled assemblies", getCanceledAssemblies(), getInitialCloneCount())
                .writePercentAndAbsoluteField("Number of premature termination assembly events, percent of number of initial clonotypes", getAssemblePrematureTerminationEvents(), getInitialCloneCount())
                .writeField("Longest contig length", getLongestContigLength())
                .writePercentAndAbsoluteField("Clustered variants", getClonesClustered(), getFinalCloneCount() + getClonesClustered())
                .writePercentAndAbsoluteField("Reads in clustered variants", getReadsClustered(), getTotalReads())
                .writePercentAndAbsoluteField("Reads in divided (newly created) clones", getTotalDividedVariantReads(), getTotalReads());
    }
}
