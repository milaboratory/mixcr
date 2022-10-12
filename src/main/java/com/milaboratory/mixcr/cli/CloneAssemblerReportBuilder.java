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

import com.google.common.util.concurrent.AtomicDouble;
import com.milaboratory.mitool.refinement.gfilter.KeyedFilterReport;
import com.milaboratory.mixcr.assembler.ClonalSequenceExtractionListener;
import com.milaboratory.mixcr.assembler.CloneAccumulator;
import com.milaboratory.mixcr.assembler.CloneAssemblerListener;
import com.milaboratory.mixcr.assembler.preclone.PreClone;
import com.milaboratory.mixcr.assembler.preclone.PreCloneAssemblerReportBuilder;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.CloneSet;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class CloneAssemblerReportBuilder
        extends AbstractCommandReportBuilder<CloneAssemblerReportBuilder>
        implements CloneAssemblerListener, ClonalSequenceExtractionListener {
    private final ChainUsageStatsBuilder clonalChainUsage = new ChainUsageStatsBuilder();
    private PreCloneAssemblerReportBuilder preCloneAssemblerReportBuilder;
    long totalReadsProcessed = -1;
    final AtomicInteger initialClonesCreated = new AtomicInteger();
    final AtomicLong readsDroppedTooShortClonalSequence = new AtomicLong();
    final AtomicLong readsDroppedNoTargetSequence = new AtomicLong();
    final AtomicLong readsDroppedLowQuality = new AtomicLong();
    final AtomicLong readsInClones = new AtomicLong();
    final AtomicLong coreReads = new AtomicLong();
    final AtomicLong deferred = new AtomicLong();
    final AtomicLong readsDroppedFailedMapping = new AtomicLong();
    final AtomicLong lowQualityRescued = new AtomicLong();
    final AtomicInteger clonesClustered = new AtomicInteger();
    final AtomicInteger clonesDroppedAsLowQuality = new AtomicInteger();
    final AtomicLong readsDroppedWithLowQualityClones = new AtomicLong();
    final AtomicInteger clonesPreClustered = new AtomicInteger();
    final AtomicLong readsPreClustered = new AtomicLong();
    final AtomicLong readsClustered = new AtomicLong();
    final AtomicInteger clonesFilteredInPostFiltering = new AtomicInteger();
    final AtomicDouble readsFilteredInPostFiltering = new AtomicDouble();
    List<KeyedFilterReport> postFilteringReports = null;

    public void setPreCloneAssemblerReportBuilder(PreCloneAssemblerReportBuilder preCloneAssemblerReport) {
        if (this.preCloneAssemblerReportBuilder != null)
            throw new IllegalStateException("Pre-clone assembler report already set.");
        this.preCloneAssemblerReportBuilder = preCloneAssemblerReport;
    }

    int getCloneCount() {
        return initialClonesCreated.get() - clonesClustered.get() - clonesDroppedAsLowQuality.get() - clonesPreClustered.get() - clonesFilteredInPostFiltering.get();
    }

    @Override
    public void onNewCloneCreated(CloneAccumulator accumulator) {
        initialClonesCreated.incrementAndGet();
    }

    @Override
    public void onFailedToExtractClonalSequence(VDJCAlignments al) {
        readsDroppedNoTargetSequence.incrementAndGet();
    }

    @Override
    public void onTooShortClonalSequence(PreClone preClone) {
        readsDroppedTooShortClonalSequence.addAndGet(preClone.getNumberOfReads());
    }

    @Override
    public void onTooManyLowQualityPoints(PreClone preClone) {
        readsDroppedLowQuality.addAndGet(preClone.getNumberOfReads());
    }

    @Override
    public void onAlignmentDeferred(PreClone preClone) {
        deferred.addAndGet(preClone.getNumberOfReads());
    }

    @Override
    public void onAlignmentAddedToClone(PreClone preClone, CloneAccumulator accumulator) {
        coreReads.addAndGet(preClone.getNumberOfReads());
        readsInClones.addAndGet(preClone.getNumberOfReads());
    }

    @Override
    public void onNoCandidateFoundForDeferredAlignment(PreClone preClone) {
        readsDroppedFailedMapping.addAndGet(preClone.getNumberOfReads());
    }

    @Override
    public void onDeferredAlignmentMappedToClone(PreClone preClone, CloneAccumulator accumulator) {
        lowQualityRescued.addAndGet(preClone.getNumberOfReads());
        readsInClones.addAndGet(preClone.getNumberOfReads());
    }

    @Override
    public void onClustered(CloneAccumulator majorClone, CloneAccumulator minorClone, boolean countAdded) {
        readsClustered.addAndGet(minorClone.getCount());
        clonesClustered.incrementAndGet();
        if (!countAdded)
            readsInClones.addAndGet(-minorClone.getCount());
    }

    @Override
    public void onPreClustered(CloneAccumulator majorClone, CloneAccumulator minorClone) {
        clonesPreClustered.incrementAndGet();
        readsPreClustered.addAndGet(minorClone.getCount());
    }

    @Override
    public void onCloneDropped(CloneAccumulator clone) {
        readsDroppedWithLowQualityClones.addAndGet(clone.getCount());
        clonesDroppedAsLowQuality.incrementAndGet();
        readsInClones.addAndGet(-clone.getCount());
        coreReads.addAndGet(-clone.getCoreCount());
        lowQualityRescued.addAndGet(-clone.getMappedCount());
        deferred.addAndGet(-clone.getMappedCount());
    }

    public void onClonesetFinished(CloneSet cloneSet) {
        for (Clone clone : cloneSet)
            clonalChainUsage.increment(clone);
    }

    @Override
    public void onPostFiltering(List<Clone> before, List<Clone> after, List<KeyedFilterReport> reports) {
        this.clonesFilteredInPostFiltering.set(before.size() - after.size());
        this.readsFilteredInPostFiltering.set(before.stream().mapToDouble(Clone::getCount).sum() -
                after.stream().mapToDouble(Clone::getCount).sum());
        this.postFilteringReports = reports;
    }

    public void setTotalReads(long totalReads) {
        this.totalReadsProcessed = totalReads;
    }

    public long getReadsInClonesBeforeClustering() {
        return lowQualityRescued.get() + coreReads.get();
    }

    @Override
    protected CloneAssemblerReportBuilder that() {
        return this;
    }

    @Override
    public CloneAssemblerReport buildReport() {
        return new CloneAssemblerReport(
                getDate(),
                getCommandLine(),
                getInputFiles(),
                getOutputFiles(),
                getExecutionTimeMillis(),
                getVersion(),
                preCloneAssemblerReportBuilder == null ? null : preCloneAssemblerReportBuilder.buildReport(),
                totalReadsProcessed,
                initialClonesCreated.get(),
                readsDroppedNoTargetSequence.get(),
                readsDroppedTooShortClonalSequence.get(),
                readsDroppedLowQuality.get(),
                coreReads.get(),
                readsDroppedFailedMapping.get(),
                lowQualityRescued.get(),
                clonesClustered.get(),
                readsClustered.get(),
                getCloneCount(),
                clonesDroppedAsLowQuality.get(),
                clonesPreClustered.get(),
                readsPreClustered.get(),
                readsInClones.get(),
                getReadsInClonesBeforeClustering(),
                readsDroppedWithLowQualityClones.get(),
                clonalChainUsage.buildReport(),
                clonesFilteredInPostFiltering.get(),
                readsFilteredInPostFiltering.get(),
                postFilteringReports
        );
    }
}
