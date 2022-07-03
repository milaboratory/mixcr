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
package com.milaboratory.mixcr.assembler.preclone;

import com.milaboratory.mitool.pattern.search.FormatSettings;
import com.milaboratory.mitool.pattern.search.ReportKt;
import com.milaboratory.mitool.report.ConcurrentAtomicLongMap;
import com.milaboratory.util.Report;
import com.milaboratory.util.ReportHelper;
import io.repseq.core.GeneType;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

public final class PreCloneAssemblerReport implements Report {
    final AtomicLong inputGroups = new AtomicLong();
    final AtomicLong inputAlignments = new AtomicLong();
    final AtomicLong clonotypes = new AtomicLong();
    final ConcurrentAtomicLongMap<Integer> clonotypesPerGroup = new ConcurrentAtomicLongMap<>();
    final AtomicLong coreClonotypesDroppedByTagSuffix = new AtomicLong();
    final AtomicLong coreAlignmentsDroppedByTagSuffix = new AtomicLong();
    final AtomicLong coreAlignments = new AtomicLong();
    final AtomicLong discardedCoreAlignments = new AtomicLong();
    final AtomicLong empiricallyAssignedAlignments = new AtomicLong();
    final AtomicLong vjEmpiricallyAssignedAlignments = new AtomicLong();
    final AtomicLong umiEmpiricallyAssignedAlignments = new AtomicLong();
    final AtomicLong gatEmpiricallyAssignedAlignments = new AtomicLong();
    final AtomicLong empiricalAssignmentConflicts = new AtomicLong();
    final AtomicLong unassignedAlignments = new AtomicLong();
    final AtomicLong umiConflicts = new AtomicLong();
    final AtomicLong gatConflicts = new AtomicLong();
    final AtomicLongArray geneConflicts = new AtomicLongArray(GeneType.values().length);

    @Override
    public void writeReport(ReportHelper helper) {
        helper.writeField("Number of input groups", inputGroups.get());
        helper.writeField("Number of input alignments", inputAlignments.get());
        helper.writeField("Number of output pre-clonotypes", clonotypes.get());
        helper.print("Number of clonotypes per group");
        helper.print(ReportKt.binFormat(clonotypesPerGroup.getImmutable(), "  ",
                new StringBuilder(), new FormatSettings(0.0, Integer.MAX_VALUE, 0.05)).toString());
        helper.writePercentAndAbsoluteField("Number of core alignments", coreAlignments.get(), inputAlignments.get());
        helper.writePercentAndAbsoluteField("Discarded core alignments", discardedCoreAlignments.get(), coreAlignments.get());
        helper.writePercentAndAbsoluteField("Empirically assigned alignments", empiricallyAssignedAlignments.get(), inputAlignments.get());
        helper.writePercentAndAbsoluteField("Empirical assignment conflicts", empiricalAssignmentConflicts.get(), inputAlignments.get());
        helper.writePercentAndAbsoluteField("UMI+VJ-gene empirically assigned alignments", gatEmpiricallyAssignedAlignments.get(), inputAlignments.get());
        helper.writePercentAndAbsoluteField("VJ-gene empirically assigned alignments", vjEmpiricallyAssignedAlignments.get(), inputAlignments.get());
        helper.writePercentAndAbsoluteField("UMI empirically assigned alignments", umiEmpiricallyAssignedAlignments.get(), inputAlignments.get());
        helper.writeField("Number of ambiguous UMIs", umiConflicts.get());
        for (GeneType gt : GeneType.values()) {
            long value = geneConflicts.get(gt.ordinal());
            if (value == 0)
                continue;
            helper.writeField("Number of ambiguous " + gt.getLetter() + "-genes", value);
        }
        helper.writeField("Number of ambiguous UMI+V/J-gene combinations", gatConflicts.get());

        helper.writePercentAndAbsoluteField("Unassigned alignments", unassignedAlignments.get(), inputAlignments.get());

    }
}
