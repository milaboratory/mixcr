package com.milaboratory.mixcr.cli;

import com.fasterxml.jackson.annotation.*;
import com.milaboratory.cli.ActionConfiguration;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsWriter;
import com.milaboratory.mixcr.partialassembler.PartialAlignmentsAssembler;
import com.milaboratory.mixcr.partialassembler.PartialAlignmentsAssemblerParameters;
import com.milaboratory.util.SmartProgressReporter;
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
            names = {"--json-report"})
    public String jsonReport = null;

    @Option(description = "Write only overlapped sequences (needed for testing).",
            names = {"-o", "--overlapped-only"})
    public boolean overlappedOnly = false;

    @Option(description = "Drop partial sequences which were not assembled. Can be used to reduce output file " +
            "size if no additional rounds of 'assemblePartial' are required.",
            names = {"-d", "--drop-partial"})
    public boolean dropPartial = false;

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
        VDJCAlignmentsWriter writer = new VDJCAlignmentsWriter(out);
        writer.setPipelineConfiguration(getFullPipelineConfiguration());
        try (PartialAlignmentsAssembler assembler = new PartialAlignmentsAssembler(assemblerParameters,
                writer, !dropPartial, overlappedOnly)) {
            this.report = assembler;
            ReportWrapper report = new ReportWrapper(ASSEMBLE_PARTIAL_COMMAND_NAME, assembler);
            report.setStartMillis(beginTimestamp);
            report.setInputFiles(in);
            report.setOutputFiles(out);
            report.setCommandLine(getCommandLineArguments());

            try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(in)) {
                SmartProgressReporter.startProgressReport("Building index", reader);
                assembler.buildLeftPartsIndex(reader);
            }
            try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(in)) {
                SmartProgressReporter.startProgressReport("Searching for overlaps", reader);
                assembler.searchOverlaps(reader);
            }

            report.setFinishMillis(System.currentTimeMillis());

            // Writing report to stout
            System.out.println("============= Report ==============");
            Util.writeReportToStdout(report);

            if (assembler.leftPartsLimitReached()) {
                warn("WARNING: too many partial alignments detected, consider skipping assemblePartial (enriched library?). /leftPartsLimitReached/");
                leftPartsLimitReached = true;
            }

            if (assembler.maxRightMatchesLimitReached()) {
                warn("WARNING: too many partial alignments detected, consider skipping assemblePartial (enriched library?). /maxRightMatchesLimitReached/");
                maxRightMatchesLimitReached = true;
            }

            if (reportFile != null)
                Util.writeReport(reportFile, report);

            if (jsonReport != null)
                Util.writeJsonReport(jsonReport, report);
        }
    }

    @JsonAutoDetect(
            fieldVisibility = JsonAutoDetect.Visibility.ANY,
            isGetterVisibility = JsonAutoDetect.Visibility.NONE,
            getterVisibility = JsonAutoDetect.Visibility.NONE)
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
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
