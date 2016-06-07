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

import com.milaboratory.mixcr.assembler.CloneAccumulator;
import com.milaboratory.mixcr.assembler.CloneAssemblerListener;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class CloneAssemblerReport implements CloneAssemblerListener, ReportWriter {
    long totalReads = -1;
    final AtomicInteger clonesCreated = new AtomicInteger();
    final AtomicLong failedToExtractTarget = new AtomicLong();
    final AtomicLong droppedAsLowQuality = new AtomicLong();
    final AtomicLong deferred = new AtomicLong();
    final AtomicLong coreAlignments = new AtomicLong();
    final AtomicLong deferredAlignmentsDropped = new AtomicLong();
    final AtomicLong deferredAlignmentsMapped = new AtomicLong();
    final AtomicInteger clonesClustered = new AtomicInteger();
    final AtomicInteger clonesDropped = new AtomicInteger();
    final AtomicLong readsDroppedWithClones = new AtomicLong();
    final AtomicInteger clonesPreClustered = new AtomicInteger();
    final AtomicLong readsPreClustered = new AtomicLong();
    final AtomicLong readsClustered = new AtomicLong();

    public long getTotalReads() {
        return totalReads;
    }

    public int getClonesCreated() {
        return clonesCreated.get();
    }

    public long getFailedToExtractTarget() {
        return failedToExtractTarget.get();
    }

    public long getDroppedAsLowQuality() {
        return droppedAsLowQuality.get();
    }

    public long getDeferred() {
        return deferred.get();
    }

    public long getCoreAlignments() {
        return coreAlignments.get();
    }

    public long getDeferredAlignmentsDropped() {
        return deferredAlignmentsDropped.get();
    }

    public long getDeferredAlignmentsMapped() {
        return deferredAlignmentsMapped.get();
    }

    public int getClonesClustered() {
        return clonesClustered.get();
    }

    public long getReadsClustered() {
        return readsClustered.get();
    }

    public int getCloneCount() {
        return clonesCreated.get() - clonesClustered.get() - clonesDropped.get() - clonesPreClustered.get();
    }

    public int getClonesDropped() {
        return clonesDropped.get();
    }

    public long getAlignmentsInClones() {
        return coreAlignments.get() + deferredAlignmentsMapped.get() - readsDroppedWithClones.get();
    }

    public long getReadsDroppedWithClones() {
        return readsDroppedWithClones.get();
    }

    @Override
    public void onNewCloneCreated(CloneAccumulator accumulator) {
        clonesCreated.incrementAndGet();
    }

    @Override
    public void onFailedToExtractTarget(VDJCAlignments alignments) {
        failedToExtractTarget.incrementAndGet();
    }

    @Override
    public void onTooManyLowQualityPoints(VDJCAlignments alignments) {
        droppedAsLowQuality.incrementAndGet();
    }

    @Override
    public void onAlignmentDeferred(VDJCAlignments alignments) {
        deferred.incrementAndGet();
    }

    @Override
    public void onAlignmentAddedToClone(VDJCAlignments alignments, CloneAccumulator accumulator) {
        coreAlignments.incrementAndGet();
    }

    @Override
    public void onNoCandidateFoundForDeferredAlignment(VDJCAlignments alignments) {
        deferredAlignmentsDropped.incrementAndGet();
    }

    @Override
    public void onDeferredAlignmentMappedToClone(VDJCAlignments alignments, CloneAccumulator accumulator) {
        deferredAlignmentsMapped.incrementAndGet();
    }

    @Override
    public void onClustered(CloneAccumulator majorClone, CloneAccumulator minorClone) {
        readsClustered.addAndGet(minorClone.getCount());
        clonesClustered.incrementAndGet();
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
    }

    public void setTotalReads(long totalReads) {
        this.totalReads = totalReads;
    }

    @Override
    public void writeReport(ReportHelper helper) {
        if (totalReads == -1)
            throw new IllegalStateException("TotalReads count not set.");

        int clonesCount = getCloneCount();

        long alignmentsInClones = getAlignmentsInClones();

        if (deferred.get() != deferredAlignmentsDropped.get() + deferredAlignmentsMapped.get())
            throw new RuntimeException();

        helper.writeField("Final clonotype count", clonesCount)
                .writeField("Total reads used in clonotypes", alignmentsInClones)
                .writePercentField("Reads used, percent of total", alignmentsInClones, totalReads)
                .writePercentField("Reads used as core, percent of used", coreAlignments.get(), alignmentsInClones)
                .writePercentField("Reads dropped with low quality clones", readsDroppedWithClones.get(), alignmentsInClones)
                .writePercentField("Mapped low quality reads, percent of used", deferredAlignmentsMapped.get(), alignmentsInClones)
                .writePercentField("Reads clustered in PCR error correction, percent of used", readsClustered.get(), alignmentsInClones)
                .writePercentField("Reads pre-clustered due to the similar VJC-lists", readsPreClustered.get(), alignmentsInClones)
                .writeField("Clonotypes eliminated by PCR error correction", clonesClustered.get())
                .writePercentField("Percent of reads dropped due to the lack of clonal sequence",
                        failedToExtractTarget.get(), totalReads)
                .writePercentField("Percent of reads dropped due to low quality",
                        droppedAsLowQuality.get(), totalReads)
                .writePercentField("Percent of reads dropped due to failed mapping",
                        deferredAlignmentsDropped.get(), totalReads);
    }
}
