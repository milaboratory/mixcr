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

import com.milaboratory.mitool.report.ParseReport;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerEventListener;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignmentFailCause;
import io.repseq.core.GeneType;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

public final class AlignerReportBuilder extends AbstractCommandReportBuilder<AlignerReportBuilder> implements VDJCAlignerEventListener {
    private final ChainUsageStatsBuilder chainUsageBuilder = new ChainUsageStatsBuilder();
    private final AtomicLongArray fails = new AtomicLongArray(VDJCAlignmentFailCause.values().length);
    private final AtomicLong successfullyAligned = new AtomicLong(0);
    // private final AtomicLong droppedNoBarcode = new AtomicLong(0);
    // private final AtomicLong droppedBarcodeNotInWhitelist = new AtomicLong(0);
    private final AtomicLong chimeras = new AtomicLong(0);
    private final AtomicLong alignedSequenceOverlap = new AtomicLong(0);
    private final AtomicLong alignmentAidedOverlaps = new AtomicLong(0);
    private final AtomicLong noCDR3PartsAlignments = new AtomicLong(0);
    private final AtomicLong partialAlignments = new AtomicLong(0);
    private final AtomicLong overlappedAndNotAligned = new AtomicLong(0);
    private final AtomicLong pairedEndAlignmentConflicts = new AtomicLong(0);
    private final AtomicLong vChimeras = new AtomicLong(0);
    private final AtomicLong jChimeras = new AtomicLong(0);
    private final AtomicLong realignedWithForcedNonFloatingBound = new AtomicLong(0);
    private final AtomicLong realignedWithForcedNonFloatingRightBoundInLeftRead = new AtomicLong(0);
    private final AtomicLong realignedWithForcedNonFloatingLeftBoundInRightRead = new AtomicLong(0);
    private ReadTrimmerReportBuilder trimmingReportBuilder;

    private ParseReport tagReport = null;

    public AlignerReportBuilder() {
    }

    public void setTrimmingReportBuilder(ReadTrimmerReportBuilder trimmingReport) {
        this.trimmingReportBuilder = trimmingReport;
    }

    public ParseReport getTagReportBuilder() {
        return tagReport;
    }

    public void setTagReportBuilder(ParseReport tagReport) {
        this.tagReport = tagReport;
    }

    public long getTotalReadsProcessed() {
        long total = 0;
        for (int i = 0; i < fails.length(); ++i)
            total += fails.get(i);
        total += successfullyAligned.get();
        return total;
    }

    public long getNotAligned() {
        long val = 0;
        for (int i = 0; i < fails.length(); i++)
            val += fails.get(i);
        return val;
    }

    public Map<VDJCAlignmentFailCause, Long> getFailsMap() {
        Map<VDJCAlignmentFailCause, Long> map = new HashMap<>();
        for (VDJCAlignmentFailCause cause : VDJCAlignmentFailCause.values())
            map.put(cause, fails.get(cause.ordinal()));
        return map;
    }

    public long getOverlapped() {
        return getOverlappedAndAligned() + overlappedAndNotAligned.get();
    }

    public long getOverlappedAndAligned() {
        return alignedSequenceOverlap.get() + alignmentAidedOverlaps.get();
    }

    @Override
    public void onFailedAlignment(VDJCAlignmentFailCause cause) {
        fails.incrementAndGet(cause.ordinal());
    }

    @Override
    public void onSuccessfulAlignment(VDJCAlignments alignment) {
        successfullyAligned.incrementAndGet();
        chainUsageBuilder.increment(alignment);
    }

    @Override
    public void onSuccessfulSequenceOverlap(VDJCAlignments alignments) {
        if (alignments == null)
            overlappedAndNotAligned.incrementAndGet();
        else
            alignedSequenceOverlap.incrementAndGet();
    }

    @Override
    public void onSuccessfulAlignmentOverlap(VDJCAlignments alignments) {
        if (alignments == null)
            throw new IllegalArgumentException();
        alignmentAidedOverlaps.incrementAndGet();
    }

    @Override
    public void onTopHitSequenceConflict(VDJCAlignments alignments, GeneType geneType) {
        pairedEndAlignmentConflicts.incrementAndGet();
    }

    @Override
    public void onSegmentChimeraDetected(GeneType geneType, VDJCAlignments alignments) {
        switch (geneType) {
            case Variable:
                vChimeras.incrementAndGet();
                return;
            case Joining:
                jChimeras.incrementAndGet();
                return;
            default:
                throw new IllegalArgumentException(geneType.toString());
        }
    }

    public void onChimera() {
        chimeras.incrementAndGet();
    }

    @Override
    public void onNoCDR3PartsAlignment() {
        noCDR3PartsAlignments.incrementAndGet();
    }

    @Override
    public void onPartialAlignment() {
        partialAlignments.incrementAndGet();
    }

    @Override
    public void onRealignmentWithForcedNonFloatingBound(boolean forceLeftEdgeInRight, boolean forceRightEdgeInLeft) {
        realignedWithForcedNonFloatingBound.getAndIncrement();
        if (forceRightEdgeInLeft)
            realignedWithForcedNonFloatingRightBoundInLeftRead.incrementAndGet();
        if (forceRightEdgeInLeft)
            realignedWithForcedNonFloatingLeftBoundInRightRead.incrementAndGet();
    }

    @Override
    protected AlignerReportBuilder that() {
        return this;
    }

    @Override
    public AlignerReport buildReport() {
        return new AlignerReport(
                getDate(),
                getCommandLine(),
                getInputFiles(),
                getOutputFiles(),
                getExecutionTimeMillis(),
                getVersion(),
                trimmingReportBuilder == null ? null : trimmingReportBuilder.buildReport(),
                getTotalReadsProcessed(),
                successfullyAligned.get(),
                getNotAligned(),
                new TreeMap(getFailsMap()),
                chimeras.get(),
                getOverlapped(),
                alignmentAidedOverlaps.get(),
                getOverlappedAndAligned(),
                overlappedAndNotAligned.get(),
                pairedEndAlignmentConflicts.get(),
                vChimeras.get(),
                jChimeras.get(),
                chainUsageBuilder.buildReport(),
                realignedWithForcedNonFloatingBound.get(),
                realignedWithForcedNonFloatingRightBoundInLeftRead.get(),
                realignedWithForcedNonFloatingLeftBoundInRightRead.get(),
                noCDR3PartsAlignments.get(),
                partialAlignments.get(),
                tagReport
        );
    }
}
