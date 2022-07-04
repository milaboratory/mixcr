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

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPort;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.milaboratory.cli.ActionConfiguration;
import com.milaboratory.mitool.helpers.GroupOP;
import com.milaboratory.mitool.helpers.PipeKt;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsWriter;
import com.milaboratory.mixcr.basictypes.tag.TagTuple;
import com.milaboratory.mixcr.basictypes.tag.TagType;
import com.milaboratory.mixcr.partialassembler.PartialAlignmentsAssembler;
import com.milaboratory.mixcr.partialassembler.PartialAlignmentsAssemblerParameters;
import com.milaboratory.util.JsonOverrider;
import com.milaboratory.util.ReportUtil;
import com.milaboratory.util.SmartProgressReporter;
import kotlin.jvm.functions.Function1;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static com.milaboratory.mixcr.cli.CommandAssemblePartialAlignments.ASSEMBLE_PARTIAL_COMMAND_NAME;

@Command(name = ASSEMBLE_PARTIAL_COMMAND_NAME,
        sortOptions = true,
        separator = " ",
        description = "Assembles partially aligned reads into longer sequences.")
public class CommandAssemblePartialAlignments extends ACommandWithSmartOverwriteWithSingleInputMiXCR {
    static final String ASSEMBLE_PARTIAL_COMMAND_NAME = "assemblePartial";

    @Option(names = "-O", description = "Overrides default parameter values.")
    public Map<String, String> overrides = new HashMap<>();

    @Option(description = CommonDescriptions.REPORT,
            names = {"-r", "--report"})
    public String reportFile;

    @Option(description = CommonDescriptions.JSON_REPORT,
            names = {"-j", "--json-report"})
    public String jsonReport = null;

    @Option(description = "Write only overlapped sequences (needed for testing).",
            names = {"-o", "--overlapped-only"})
    public boolean overlappedOnly = false;

    @Option(description = "Drop partial sequences which were not assembled. Can be used to reduce output file " +
            "size if no additional rounds of 'assemblePartial' are required.",
            names = {"-d", "--drop-partial"})
    public boolean dropPartial = false;

    @Option(description = "Overlap sequences on the cell level instead of UMIs for tagged data with molecular and cell barcodes",
            names = {"--cell-level"})
    public boolean cellLevel = false;

    private PartialAlignmentsAssemblerParameters assemblerParameters;

    public PartialAlignmentsAssemblerParameters getPartialAlignmentsAssemblerParameters() {
        if (assemblerParameters != null)
            return assemblerParameters;

        PartialAlignmentsAssemblerParameters assemblerParameters = PartialAlignmentsAssemblerParameters.getDefault();

        if (!overrides.isEmpty()) {
            assemblerParameters = JsonOverrider.override(assemblerParameters,
                    PartialAlignmentsAssemblerParameters.class, overrides);
            if (assemblerParameters == null) {
                throw new IllegalArgumentException("Failed to override some parameter.");
            }
        }
        return this.assemblerParameters = assemblerParameters;
    }


    @Override
    public ActionConfiguration getConfiguration() {
        return new AssemblePartialConfiguration(getPartialAlignmentsAssemblerParameters(), dropPartial, overlappedOnly);
    }

    public PartialAlignmentsAssembler report;

    public boolean leftPartsLimitReached, maxRightMatchesLimitReached;

    @Override
    public void run1() throws Exception {
        // Saving initial timestamp
        long beginTimestamp = System.currentTimeMillis();

        PartialAlignmentsAssemblerParameters assemblerParameters = getPartialAlignmentsAssemblerParameters();

        // Two readers will be used to feed two-pass assemble partial algorithm
        try (VDJCAlignmentsReader reader1 = new VDJCAlignmentsReader(in);
             VDJCAlignmentsReader reader2 = new VDJCAlignmentsReader(in);
             VDJCAlignmentsWriter writer = new VDJCAlignmentsWriter(out)) {

            int groupingDepth = reader1.getTagsInfo().getDepthFor(cellLevel ? TagType.Cell : TagType.Molecule);

            writer.header(reader1.getParameters(), reader1.getUsedGenes(), getFullPipelineConfiguration(),
                    // output data will be grouped only up to a groupingDepth
                    reader1.getTagsInfo().setSorted(groupingDepth));

            PartialAlignmentsAssembler assembler = new PartialAlignmentsAssembler(assemblerParameters, reader1.getParameters(),
                    reader1.getUsedGenes(), !dropPartial, overlappedOnly,
                    writer::write);

            this.report = assembler;
            ReportWrapper report = new ReportWrapper(ASSEMBLE_PARTIAL_COMMAND_NAME, assembler);
            report.setStartMillis(beginTimestamp);
            report.setInputFiles(in);
            report.setOutputFiles(out);
            report.setCommandLine(getCommandLineArguments());

            if (reader1.getTagsInfo() != null && !reader1.getTagsInfo().hasNoTags()) {
                SmartProgressReporter.startProgressReport("Running assemble partial", reader1);

                // This processor strips all non-key information from the
                Function1<VDJCAlignments, TagTuple> key = al -> al.getTagCount().asKeyPrefixOrError(groupingDepth);
                OutputPort<GroupOP<VDJCAlignments, TagTuple>> groups1 = PipeKt.group(
                        CUtils.wrap(reader1, VDJCAlignments::ensureKeyTags), key);
                OutputPort<GroupOP<VDJCAlignments, TagTuple>> groups2 = PipeKt.group(
                        CUtils.wrap(reader2, VDJCAlignments::ensureKeyTags), key);

                for (GroupOP<VDJCAlignments, TagTuple> grp1 : CUtils.it(groups1)) {
                    assembler.buildLeftPartsIndex(grp1);
                    grp1.close(); // Drain leftover alignments in the group if not yet done
                    try (GroupOP<VDJCAlignments, TagTuple> grp2 = groups2.take()) {
                        assert grp2.getKey().equals(grp1.getKey()) : grp1.getKey() + " != " + grp2.getKey();
                        assembler.searchOverlaps(grp2);
                    }
                }
            } else {
                SmartProgressReporter.startProgressReport("Building index", reader1);
                assembler.buildLeftPartsIndex(reader1);
                SmartProgressReporter.startProgressReport("Searching for overlaps", reader2);
                assembler.searchOverlaps(reader2);
            }

            report.setFinishMillis(System.currentTimeMillis());

            // Writing report to stout
            System.out.println("============= Report ==============");
            ReportUtil.writeReportToStdout(report);

            if (assembler.leftPartsLimitReached()) {
                warn("WARNING: too many partial alignments detected, consider skipping assemblePartial (enriched library?). /leftPartsLimitReached/");
                leftPartsLimitReached = true;
            }

            if (assembler.maxRightMatchesLimitReached()) {
                warn("WARNING: too many partial alignments detected, consider skipping assemblePartial (enriched library?). /maxRightMatchesLimitReached/");
                maxRightMatchesLimitReached = true;
            }

            if (reportFile != null)
                ReportUtil.appendReport(reportFile, report);

            if (jsonReport != null)
                ReportUtil.appendJsonReport(jsonReport, report);

            writer.setNumberOfProcessedReads(reader1.getNumberOfReads() - assembler.overlapped.get());
        }
    }

    @JsonAutoDetect(
            fieldVisibility = JsonAutoDetect.Visibility.ANY,
            isGetterVisibility = JsonAutoDetect.Visibility.NONE,
            getterVisibility = JsonAutoDetect.Visibility.NONE)
    @JsonTypeInfo(
            use = JsonTypeInfo.Id.CLASS,
            include = JsonTypeInfo.As.PROPERTY,
            property = "type")
    public static class AssemblePartialConfiguration implements ActionConfiguration {
        public final PartialAlignmentsAssemblerParameters parameters;
        public final boolean dropPartial;
        public final boolean overlappedOnly;

        @JsonCreator
        public AssemblePartialConfiguration(@JsonProperty("parameters") PartialAlignmentsAssemblerParameters parameters,
                                            @JsonProperty("dropPartial") boolean dropPartial,
                                            @JsonProperty("overlappedOnly") boolean overlappedOnly) {
            this.parameters = parameters;
            this.dropPartial = dropPartial;
            this.overlappedOnly = overlappedOnly;
        }

        @Override
        public String actionName() {
            return ASSEMBLE_PARTIAL_COMMAND_NAME;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            AssemblePartialConfiguration that = (AssemblePartialConfiguration) o;
            return dropPartial == that.dropPartial &&
                    overlappedOnly == that.overlappedOnly &&
                    Objects.equals(parameters, that.parameters);
        }

        @Override
        public int hashCode() {
            return Objects.hash(parameters, dropPartial, overlappedOnly);
        }
    }
}
