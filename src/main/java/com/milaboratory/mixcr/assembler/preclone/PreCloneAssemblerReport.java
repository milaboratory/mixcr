/*
 *
 * Copyright (c) 2022, MiLaboratories Inc. All Rights Reserved
 *
 * Before downloading or accessing the software, please read carefully the
 * License Agreement available at:
 * https://github.com/milaboratory/miplots/blob/main/LICENSE
 *
 * By downloading or accessing the software, you accept and agree to be bound
 * by the terms of the License Agreement. If you do not want to agree to the terms
 * of the Licensing Agreement, you must not download or access the software.
 */
package com.milaboratory.mixcr.assembler.preclone;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.mitool.pattern.search.FormatSettings;
import com.milaboratory.mitool.pattern.search.ReportKt;
import com.milaboratory.mixcr.cli.MiXCRReport;
import com.milaboratory.util.ReportHelper;
import io.repseq.core.GeneType;

import java.util.Map;

public class PreCloneAssemblerReport implements MiXCRReport {
    @JsonProperty("inputGroups")
    public final long inputGroups;
    @JsonProperty("inputAlignments")
    public final long inputAlignments;
    @JsonProperty("clonotypes")
    public final long clonotypes;
    @JsonProperty("clonotypesPerGroup")
    public final Map<Integer, Long> clonotypesPerGroup;
    @JsonProperty("coreAlignments")
    public final long coreAlignments;
    @JsonProperty("discardedCoreAlignments")
    public final long discardedCoreAlignments;
    @JsonProperty("empiricallyAssignedAlignments")
    public final long empiricallyAssignedAlignments;
    @JsonProperty("vjEmpiricallyAssignedAlignments")
    public final long vjEmpiricallyAssignedAlignments;
    @JsonProperty("umiEmpiricallyAssignedAlignments")
    public final long umiEmpiricallyAssignedAlignments;
    @JsonProperty("gatEmpiricallyAssignedAlignments")
    public final long gatEmpiricallyAssignedAlignments;
    @JsonProperty("empiricalAssignmentConflicts")
    public final long empiricalAssignmentConflicts;
    @JsonProperty("unassignedAlignments")
    public final long unassignedAlignments;
    @JsonProperty("umiConflicts")
    public final long umiConflicts;
    @JsonProperty("gatConflicts")
    public final long gatConflicts;
    @JsonProperty("geneConflicts")
    public final Map<GeneType, Long> geneConflicts;

    public PreCloneAssemblerReport(long inputGroups,
                                   long inputAlignments,
                                   long clonotypes,
                                   Map<Integer, Long> clonotypesPerGroup,
                                   long coreAlignments,
                                   long discardedCoreAlignments,
                                   long empiricallyAssignedAlignments,
                                   long vjEmpiricallyAssignedAlignments,
                                   long umiEmpiricallyAssignedAlignments,
                                   long gatEmpiricallyAssignedAlignments,
                                   long empiricalAssignmentConflicts,
                                   long unassignedAlignments,
                                   long umiConflicts,
                                   long gatConflicts,
                                   Map<GeneType, Long> geneConflicts) {
        this.inputGroups = inputGroups;
        this.inputAlignments = inputAlignments;
        this.clonotypes = clonotypes;
        this.clonotypesPerGroup = clonotypesPerGroup;
        this.coreAlignments = coreAlignments;
        this.discardedCoreAlignments = discardedCoreAlignments;
        this.empiricallyAssignedAlignments = empiricallyAssignedAlignments;
        this.vjEmpiricallyAssignedAlignments = vjEmpiricallyAssignedAlignments;
        this.umiEmpiricallyAssignedAlignments = umiEmpiricallyAssignedAlignments;
        this.gatEmpiricallyAssignedAlignments = gatEmpiricallyAssignedAlignments;
        this.empiricalAssignmentConflicts = empiricalAssignmentConflicts;
        this.unassignedAlignments = unassignedAlignments;
        this.umiConflicts = umiConflicts;
        this.gatConflicts = gatConflicts;
        this.geneConflicts = geneConflicts;
    }

    @Override
    public void writeReport(ReportHelper helper) {
        helper.writeField("Number of input groups", inputGroups);
        helper.writeField("Number of input alignments", inputAlignments);
        helper.writeField("Number of output pre-clonotypes", clonotypes);
        helper.println("Number of clonotypes per group");
        helper.print(ReportKt.format(clonotypesPerGroup, "  ",
                new StringBuilder(), new FormatSettings(0), 0).toString());
        helper.writePercentAndAbsoluteField("Number of core alignments", coreAlignments, inputAlignments);
        helper.writePercentAndAbsoluteField("Discarded core alignments", discardedCoreAlignments, coreAlignments);
        helper.writePercentAndAbsoluteField("Empirically assigned alignments", empiricallyAssignedAlignments, inputAlignments);
        helper.writePercentAndAbsoluteField("Empirical assignment conflicts", empiricalAssignmentConflicts, inputAlignments);
        helper.writePercentAndAbsoluteField("UMI+VJ-gene empirically assigned alignments", gatEmpiricallyAssignedAlignments, inputAlignments);
        helper.writePercentAndAbsoluteField("VJ-gene empirically assigned alignments", vjEmpiricallyAssignedAlignments, inputAlignments);
        helper.writePercentAndAbsoluteField("UMI empirically assigned alignments", umiEmpiricallyAssignedAlignments, inputAlignments);
        helper.writeField("Number of ambiguous UMIs", umiConflicts);
        for (GeneType gt : GeneType.values()) {
            long value = geneConflicts.get(gt);
            if (value == 0)
                continue;
            helper.writeField("Number of ambiguous " + gt.getLetter() + "-genes", value);
        }
        helper.writeField("Number of ambiguous UMI+V/J-gene combinations", gatConflicts);

        helper.writePercentAndAbsoluteField("Unassigned alignments", unassignedAlignments, inputAlignments);
    }
}
