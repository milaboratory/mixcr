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

import com.fasterxml.jackson.annotation.JsonCreator;
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
    @JsonProperty("groupsWithNoAssemblingFeature")
    public final long groupsWithNoAssemblingFeature;
    @JsonProperty("inputAlignments")
    public final long inputAlignments;
    @JsonProperty("inputAssemblingFeatureSequences")
    public final long inputAssemblingFeatureSequences;
    @JsonProperty("clonotypes")
    public final long clonotypes;
    @JsonProperty("assemblingFeatureSequencesInZeroPreClones")
    public final long assemblingFeatureSequencesInZeroPreClones;
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
    @JsonProperty("coreClonotypesDroppedByTagSuffix")
    public final long coreClonotypesDroppedByTagSuffix;
    @JsonProperty("coreAlignmentsDroppedByTagSuffix")
    public final long coreAlignmentsDroppedByTagSuffix;

    @JsonCreator
    public PreCloneAssemblerReport(
            @JsonProperty("inputGroups") long inputGroups,
            @JsonProperty("groupsWithNoAssemblingFeature") long groupsWithNoAssemblingFeature,
            @JsonProperty("inputAlignments") long inputAlignments,
            @JsonProperty("inputAssemblingFeatureSequences") long inputAssemblingFeatureSequences,
            @JsonProperty("clonotypes") long clonotypes,
            @JsonProperty("assemblingFeatureSequencesInZeroPreClones") long assemblingFeatureSequencesInZeroPreClones,
            @JsonProperty("clonotypesPerGroup") Map<Integer, Long> clonotypesPerGroup,
            @JsonProperty("coreAlignments") long coreAlignments,
            @JsonProperty("discardedCoreAlignments") long discardedCoreAlignments,
            @JsonProperty("empiricallyAssignedAlignments") long empiricallyAssignedAlignments,
            @JsonProperty("vjEmpiricallyAssignedAlignments") long vjEmpiricallyAssignedAlignments,
            @JsonProperty("umiEmpiricallyAssignedAlignments") long umiEmpiricallyAssignedAlignments,
            @JsonProperty("gatEmpiricallyAssignedAlignments") long gatEmpiricallyAssignedAlignments,
            @JsonProperty("empiricalAssignmentConflicts") long empiricalAssignmentConflicts,
            @JsonProperty("unassignedAlignments") long unassignedAlignments,
            @JsonProperty("umiConflicts") long umiConflicts,
            @JsonProperty("gatConflicts") long gatConflicts,
            @JsonProperty("geneConflicts") Map<GeneType, Long> geneConflicts,
            @JsonProperty("coreClonotypesDroppedByTagSuffix") long coreClonotypesDroppedByTagSuffix,
            @JsonProperty("coreAlignmentsDroppedByTagSuffix") long coreAlignmentsDroppedByTagSuffix
    ) {
        this.inputGroups = inputGroups;
        this.groupsWithNoAssemblingFeature = groupsWithNoAssemblingFeature;
        this.inputAlignments = inputAlignments;
        this.inputAssemblingFeatureSequences = inputAssemblingFeatureSequences;
        this.clonotypes = clonotypes;
        this.assemblingFeatureSequencesInZeroPreClones = assemblingFeatureSequencesInZeroPreClones;
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
        this.coreClonotypesDroppedByTagSuffix = coreClonotypesDroppedByTagSuffix;
        this.coreAlignmentsDroppedByTagSuffix = coreAlignmentsDroppedByTagSuffix;
    }

    @Override
    public void writeReport(ReportHelper helper) {
        helper.writeField("Number of input groups", inputGroups);
        helper.writeField("Number of groups with no assembling feature", groupsWithNoAssemblingFeature);
        helper.writeField("Number of input alignments", inputAlignments);
        helper.writeField("Number of output pre-clonotypes", clonotypes);
        helper.writePercentAndAbsoluteField("Number alignments with assembling feature",
                inputAssemblingFeatureSequences,
                inputAlignments);
        helper.print("Number of clonotypes per group:");
        helper.print(ReportKt.format(clonotypesPerGroup, "  ",
                new StringBuilder(), new FormatSettings(0.0, Integer.MAX_VALUE, 0.05)).toString());
        helper.writeField("Number of assembling feature sequences in groups with zero pre-clonotypes",
                assemblingFeatureSequencesInZeroPreClones);
        helper.writePercentAndAbsoluteField("Number of core alignments", coreAlignments, inputAlignments);
        helper.writePercentAndAbsoluteField("Discarded core alignments", discardedCoreAlignments, coreAlignments);
        helper.writePercentAndAbsoluteField("Empirically assigned alignments", empiricallyAssignedAlignments, inputAlignments);
        helper.writePercentAndAbsoluteField("Empirical assignment conflicts", empiricalAssignmentConflicts, inputAlignments);
        helper.writePercentAndAbsoluteField("UMI+VJ-gene empirically assigned alignments", gatEmpiricallyAssignedAlignments, inputAlignments);
        helper.writePercentAndAbsoluteField("VJ-gene empirically assigned alignments", vjEmpiricallyAssignedAlignments, inputAlignments);
        helper.writePercentAndAbsoluteField("UMI empirically assigned alignments", umiEmpiricallyAssignedAlignments, inputAlignments);
        helper.writeField("Number of ambiguous UMIs", umiConflicts);
        for (GeneType gt : GeneType.values()) {
            Long value = geneConflicts.get(gt);
            if (value == null || value == 0)
                continue;
            helper.writeField("Number of ambiguous " + gt.getLetter() + "-genes", value);
        }
        helper.writeField("Number of ambiguous UMI+V/J-gene combinations", gatConflicts);

        helper.writePercentAndAbsoluteField("Unassigned alignments", unassignedAlignments, inputAlignments);
    }
}
