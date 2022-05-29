package com.milaboratory.mixcr.tags;

import com.milaboratory.mitool.pattern.search.FormatSettings;
import com.milaboratory.mitool.pattern.search.ReportKt;
import com.milaboratory.mitool.report.ConcurrentAtomicLongMap;
import com.milaboratory.util.Report;
import com.milaboratory.util.ReportHelper;

import java.util.concurrent.atomic.AtomicLong;

public final class PreCloneAssemblerReport implements Report {
    final AtomicLong inputGroups = new AtomicLong();
    final AtomicLong inputAlignments = new AtomicLong();
    final AtomicLong clonotypes = new AtomicLong();
    final ConcurrentAtomicLongMap<Integer> clonotypesPerGroup = new ConcurrentAtomicLongMap<>();
    final AtomicLong coreAlignments = new AtomicLong();
    final AtomicLong discardedCoreAlignments = new AtomicLong();
    final AtomicLong vjEmpiricallyAssignedAlignments = new AtomicLong();
    final AtomicLong umiEmpiricallyAssignedAlignments = new AtomicLong();
    final AtomicLong empiricalAssignmentConflicts = new AtomicLong();
    final AtomicLong unassignedAlignments = new AtomicLong();

    @Override
    public void writeReport(ReportHelper helper) {
        helper.writeField("Number of input groups", inputGroups.get());
        helper.writeField("Number of input alignments", inputAlignments.get());
        helper.writeField("Number of output clonotypes", clonotypes.get());
        helper.println("Number of clonotypes per group");
        helper.print(ReportKt.format(clonotypesPerGroup.getImmutable(), "  ",
                new StringBuilder(), new FormatSettings(0), 0).toString());
        helper.writePercentAndAbsoluteField("Number of core alignments", coreAlignments.get(), inputAlignments.get());
        helper.writePercentAndAbsoluteField("Discarded core alignments", discardedCoreAlignments.get(), coreAlignments.get());
        helper.writePercentAndAbsoluteField("VJ-gene empirically assigned alignments", vjEmpiricallyAssignedAlignments.get(), inputAlignments.get());
        helper.writePercentAndAbsoluteField("UMI empirically assigned alignments", umiEmpiricallyAssignedAlignments.get(), inputAlignments.get());
        helper.writePercentAndAbsoluteField("Empirical assignment conflicts", empiricalAssignmentConflicts.get(), inputAlignments.get());
        helper.writePercentAndAbsoluteField("Unassigned alignments", unassignedAlignments.get(), inputAlignments.get());

    }
}
