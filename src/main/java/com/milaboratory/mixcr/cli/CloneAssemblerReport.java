/*
 * Copyright (c) 2014-2015, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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
 * commercial purposes should contact the Inventors using one of the following
 * email addresses: chudakovdm@mail.ru, chudakovdm@gmail.com
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
package com.milaboratory.mixcr.cli;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.mixcr.assembler.CloneAccumulator;
import com.milaboratory.mixcr.assembler.CloneAssemblerListener;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.CloneSet;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class CloneAssemblerReport extends AbstractActionReport implements CloneAssemblerListener {
    private final ChainUsageStats chainStats = new ChainUsageStats();
    long totalReads = -1;
    final AtomicInteger clonesCreated = new AtomicInteger();
    final AtomicLong failedToExtractTarget = new AtomicLong();
    final AtomicLong droppedAsLowQuality = new AtomicLong();
    final AtomicLong alignmentsInClones = new AtomicLong();
    final AtomicLong coreAlignments = new AtomicLong();
    final AtomicLong deferred = new AtomicLong();
    final AtomicLong deferredAlignmentsDropped = new AtomicLong();
    final AtomicLong deferredAlignmentsMapped = new AtomicLong();
    final AtomicInteger clonesClustered = new AtomicInteger();
    final AtomicInteger clonesDropped = new AtomicInteger();
    final AtomicLong readsDroppedWithClones = new AtomicLong();
    final AtomicInteger clonesPreClustered = new AtomicInteger();
    final AtomicLong readsPreClustered = new AtomicLong();
    final AtomicLong readsClustered = new AtomicLong();

    @Override
    public String getAction() {
        return "assemble";
    }

    @JsonProperty("totalReadsProcessed")
    public long getTotalReads() {
        return totalReads;
    }

    @JsonProperty("initialClonesCreated")
    public int getClonesCreated() {
        return clonesCreated.get();
    }

    @JsonProperty("readsDroppedNoTargetSequence")
    public long getFailedToExtractTarget() {
        return failedToExtractTarget.get();
    }

    @JsonProperty("readsDroppedLowQuality")
    public long getDroppedAsLowQuality() {
        return droppedAsLowQuality.get();
    }

    public long getDeferred() {
        return deferred.get();
    }

    @JsonProperty("coreReads")
    public long getCoreAlignments() {
        return coreAlignments.get();
    }

    @JsonProperty("readsDroppedFailedMapping")
    public long getDeferredAlignmentsDropped() {
        return deferredAlignmentsDropped.get();
    }

    @JsonProperty("lowQualityRescued")
    public long getDeferredReadsMapped() {
        return deferredAlignmentsMapped.get();
    }

    @JsonProperty("clonesClustered")
    public int getClonesClustered() {
        return clonesClustered.get();
    }

    @JsonProperty("readsClustered")
    public long getReadsClustered() {
        return readsClustered.get();
    }

    @JsonProperty("clones")
    public int getCloneCount() {
        return clonesCreated.get() - clonesClustered.get() - clonesDropped.get() - clonesPreClustered.get();
    }

    @JsonProperty("clonesDroppedAsLowQuality")
    public int getClonesDropped() {
        return clonesDropped.get();
    }

    @JsonProperty("clonesPreClustered")
    public int getClonesPreClustered() {
        return clonesPreClustered.get();
    }

    @JsonProperty("readsPreClustered")
    public long getReadsPreClustered() {
        return readsPreClustered.get();
    }

    @JsonProperty("readsInClones")
    public long getReadsInClones() {
        return alignmentsInClones.get(); //coreAlignments.get() + deferredAlignmentsMapped.get() - readsDroppedWithClones.get();
    }

    @JsonProperty("readsInClonesBeforeClustering")
    public long getReadsInClonesBeforeClustering() {
        return deferredAlignmentsMapped.get() + coreAlignments.get();
    }

    @JsonProperty("readsDroppedWithLowQualityClones")
    public long getReadsDroppedWithClones() {
        return readsDroppedWithClones.get();
    }

    @Override
    public void onNewCloneCreated(CloneAccumulator accumulator) {
        clonesCreated.incrementAndGet();
    }

    @Override
    public void onFailedToExtractTarget(VDJCAlignments alignments) {
        failedToExtractTarget.addAndGet(alignments.getNumberOfReads());
    }

    @Override
    public void onTooManyLowQualityPoints(VDJCAlignments alignments) {
        droppedAsLowQuality.addAndGet(alignments.getNumberOfReads());
    }

    @Override
    public void onAlignmentDeferred(VDJCAlignments alignments) {
        deferred.addAndGet(alignments.getNumberOfReads());
    }

    @Override
    public void onAlignmentAddedToClone(VDJCAlignments alignments, CloneAccumulator accumulator) {
        coreAlignments.addAndGet(alignments.getNumberOfReads());
        alignmentsInClones.addAndGet(alignments.getNumberOfReads());
    }

    @Override
    public void onNoCandidateFoundForDeferredAlignment(VDJCAlignments alignments) {
        deferredAlignmentsDropped.addAndGet(alignments.getNumberOfReads());
    }

    @Override
    public void onDeferredAlignmentMappedToClone(VDJCAlignments alignments, CloneAccumulator accumulator) {
        deferredAlignmentsMapped.addAndGet(alignments.getNumberOfReads());
        alignmentsInClones.addAndGet(alignments.getNumberOfReads());
    }

    @Override
    public void onClustered(CloneAccumulator majorClone, CloneAccumulator minorClone, boolean countAdded) {
        readsClustered.addAndGet(minorClone.getCount());
        clonesClustered.incrementAndGet();
        if (!countAdded)
            alignmentsInClones.addAndGet(-minorClone.getCount());
    }

    @Override
    public void onPreClustered(CloneAccumulator majorClone, CloneAccumulator minorClone) {
        clonesPreClustered.incrementAndGet();
        readsPreClustered.addAndGet(minorClone.getCount());
    }

    @Override
    public void onCloneDropped(CloneAccumulator clone) {
        readsDroppedWithClones.addAndGet(clone.getCount());
        clonesDropped.incrementAndGet();
        alignmentsInClones.addAndGet(-clone.getCount());
        coreAlignments.addAndGet(-clone.getCoreCount());
        deferredAlignmentsMapped.addAndGet(-clone.getMappedCount());
        deferred.addAndGet(-clone.getMappedCount());
    }

    public void onClonesetFinished(CloneSet cloneSet) {
        for (Clone clone : cloneSet)
            chainStats.increment(clone);
    }

    public void setTotalReads(long totalReads) {
        this.totalReads = totalReads;
    }

    @Override
    public void writeReport(ReportHelper helper) {
        // Writing common analysis information
        writeSuperReport(helper);

        if (totalReads == -1)
            throw new IllegalStateException("TotalReads count not set.");

        int clonesCount = getCloneCount();

        long alignmentsInClones = getReadsInClones();

        // Alignments in clones before clusterization
        long clusterizationBase = getReadsInClonesBeforeClustering();

        helper.writeField("Final clonotype count", clonesCount)
                .writeField("Average number of reads per clonotype", Util.PERCENT_FORMAT.format(1.0 * alignmentsInClones / clonesCount))
                .writePercentAndAbsoluteField("Reads used in clonotypes, percent of total", alignmentsInClones, totalReads)
                .writePercentAndAbsoluteField("Reads used in clonotypes before clustering, percent of total", clusterizationBase, totalReads)
                .writePercentAndAbsoluteField("Number of reads used as a core, percent of used", coreAlignments.get(), clusterizationBase)
                .writePercentAndAbsoluteField("Mapped low quality reads, percent of used", deferredAlignmentsMapped.get(), clusterizationBase)
                .writePercentAndAbsoluteField("Reads clustered in PCR error correction, percent of used", readsClustered.get(), clusterizationBase)
                .writePercentAndAbsoluteField("Reads pre-clustered due to the similar VJC-lists, percent of used", readsPreClustered.get(), alignmentsInClones)
                .writePercentAndAbsoluteField("Reads dropped due to the lack of a clone sequence",
                        failedToExtractTarget.get(), totalReads)
                .writePercentAndAbsoluteField("Reads dropped due to low quality",
                        droppedAsLowQuality.get(), totalReads)
                .writePercentAndAbsoluteField("Reads dropped due to failed mapping",
                        deferredAlignmentsDropped.get(), totalReads)
                .writePercentAndAbsoluteField("Reads dropped with low quality clones", readsDroppedWithClones.get(), alignmentsInClones)
                .writeField("Clonotypes eliminated by PCR error correction", clonesClustered.get())
                .writeField("Clonotypes dropped as low quality", clonesDropped.get())
                .writeField("Clonotypes pre-clustered due to the similar VJC-lists", clonesPreClustered.get());
    }
}
