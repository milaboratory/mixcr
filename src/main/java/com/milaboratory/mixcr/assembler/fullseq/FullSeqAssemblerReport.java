/*
 * Copyright (c) 2014-2019, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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
 * commercial purposes should contact MiLaboratory LLC, which owns exclusive
 * rights for distribution of this program for commercial purposes, using the
 * following email address: licensing@milaboratory.com.
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
    private final AtomicInteger initialCloneCount = new AtomicInteger(0);
    private final AtomicInteger finalCloneCount = new AtomicInteger(0);
    private final AtomicDouble totalReads = new AtomicDouble(0);
    private final AtomicInteger totalClustered = new AtomicInteger(0);
    private final AtomicDouble totalClusteredReads = new AtomicDouble(0);
    private final AtomicDouble dividedReads = new AtomicDouble(0);
    private final AtomicInteger longestContigLength = new AtomicInteger(0);
    private final AtomicLong variantsBeforeClustering = new AtomicLong(0);
    private final AtomicLong vHitsReorder = new AtomicLong(0);
    private final AtomicLong jHitsReorder = new AtomicLong(0);

    public void onVariantClustered(VariantBranch minor) {
        totalClustered.incrementAndGet();
        totalClusteredReads.addAndGet(minor.count);
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

    @Override
    public void writeReport(ReportHelper helper) {
        helper.writeField("Initial clonotype count", getInitialCloneCount())
                .writePercentAndAbsoluteField("Final clonotype count", getFinalCloneCount(), getInitialCloneCount())
                .writeField("Longest contig length", getLongestContigLength())
                .writePercentAndAbsoluteField("Clustered variants", getClonesClustered(), getFinalCloneCount() + getClonesClustered())
                .writePercentAndAbsoluteField("Reads in clustered variants", getReadsClustered(), getTotalReads())
                .writePercentAndAbsoluteField("Reads in divided (newly created) clones", getTotalDividedVariantReads(), getTotalReads());
    }
}
