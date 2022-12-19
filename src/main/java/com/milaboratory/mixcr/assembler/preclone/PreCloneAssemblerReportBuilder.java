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

import com.milaboratory.mitool.report.ConcurrentAtomicLongMap;
import com.milaboratory.util.ReportBuilder;
import io.repseq.core.GeneType;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

public final class PreCloneAssemblerReportBuilder implements ReportBuilder {
    final AtomicLong inputGroups = new AtomicLong();
    final AtomicLong groupsWithNoAssemblingFeature = new AtomicLong();
    final AtomicLong inputAlignments = new AtomicLong();
    final AtomicLong inputAssemblingFeatureSequences = new AtomicLong();
    final AtomicLong clonotypes = new AtomicLong();
    final AtomicLong assemblingFeatureSequencesInZeroPreClones = new AtomicLong();
    final ConcurrentAtomicLongMap<Integer> clonotypesPerGroup = new ConcurrentAtomicLongMap<>();
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
    final AtomicLong coreClonotypesDroppedByTagSuffix = new AtomicLong();
    final AtomicLong coreAlignmentsDroppedByTagSuffix = new AtomicLong();

    Map<GeneType, Long> getGeneConflictsMap() {
        Map<GeneType, Long> r = new EnumMap<>(GeneType.class);
        for (GeneType gt : GeneType.values()) {
            long value = geneConflicts.get(gt.ordinal());
            if (value == 0)
                continue;
            r.put(gt, value);
        }
        return r;
    }

    @Override
    public PreCloneAssemblerReport buildReport() {
        return new PreCloneAssemblerReport(
                inputGroups.get(),
                groupsWithNoAssemblingFeature.get(),
                inputAlignments.get(),
                inputAssemblingFeatureSequences.get(),
                clonotypes.get(),
                assemblingFeatureSequencesInZeroPreClones.get(),
                clonotypesPerGroup.getImmutable(),
                coreAlignments.get(),
                discardedCoreAlignments.get(),
                empiricallyAssignedAlignments.get(),
                vjEmpiricallyAssignedAlignments.get(),
                umiEmpiricallyAssignedAlignments.get(),
                gatEmpiricallyAssignedAlignments.get(),
                empiricalAssignmentConflicts.get(),
                unassignedAlignments.get(),
                umiConflicts.get(),
                gatConflicts.get(),
                getGeneConflictsMap(),
                coreClonotypesDroppedByTagSuffix.get(),
                coreAlignmentsDroppedByTagSuffix.get()
        );
    }
}
