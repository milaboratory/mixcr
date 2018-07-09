package com.milaboratory.mixcr.cli;

import com.beust.jcommander.DynamicParameter;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.cli.Action;
import com.milaboratory.cli.ActionHelper;
import com.milaboratory.cli.ActionParameters;
import com.milaboratory.mixcr.basictypes.ActionConfiguration;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsWriter;
import com.milaboratory.mixcr.partialassembler.PartialAlignmentsAssembler;
import com.milaboratory.mixcr.partialassembler.PartialAlignmentsAssemblerParameters;
import com.milaboratory.util.SmartProgressReporter;

import java.util.*;

/**
 * @author Dmitry Bolotin
 * @author Stanislav Poslavsky
 */
public final class ActionAssemblePartialAlignments implements Action {
    private final AssemblePartialAlignmentsParameters parameters;

    public ActionAssemblePartialAlignments(AssemblePartialAlignmentsParameters parameters) {
        this.parameters = parameters;
    }

    public ActionAssemblePartialAlignments() {
        this(new AssemblePartialAlignmentsParameters());
    }

    public PartialAlignmentsAssembler report;

    public boolean leftPartsLimitReached, maxRightMatchesLimitReached;

    @Override
    public void go(ActionHelper helper) throws Exception {
        // Saving initial timestamp
        long beginTimestamp = System.currentTimeMillis();

        PartialAlignmentsAssemblerParameters assemblerParameters = parameters.getPartialAlignmentsAssemblerParameters();
        VDJCAlignmentsWriter writer = new VDJCAlignmentsWriter(parameters.getOutputFileName());
        writer.writeConfiguration(parameters.getFullPipelineConfiguration());
        try (PartialAlignmentsAssembler assembler = new PartialAlignmentsAssembler(assemblerParameters,
                writer, !parameters.doDropPartial(), parameters.getOverlappedOnly())) {
            this.report = assembler;
            ReportWrapper report = new ReportWrapper(command(), assembler);
            report.setStartMillis(beginTimestamp);
            report.setInputFiles(parameters.getInputFileName());
            report.setOutputFiles(parameters.getOutputFileName());
            report.setCommandLine(helper.getCommandLineArguments());

            try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(parameters.getInputFileName())) {
                SmartProgressReporter.startProgressReport("Building index", reader);
                assembler.buildLeftPartsIndex(reader);
            }
            try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(parameters.getInputFileName())) {
                SmartProgressReporter.startProgressReport("Searching for overlaps", reader);
                assembler.searchOverlaps(reader);
            }

            report.setFinishMillis(System.currentTimeMillis());

            // Writing report to stout
            System.out.println("============= Report ==============");
            Util.writeReportToStdout(report);

            if (assembler.leftPartsLimitReached()) {
                System.out.println("WARNING: too many partial alignments detected, consider skipping assemblePartial (enriched library?). /leftPartsLimitReached/");
                leftPartsLimitReached = true;
            }

            if (assembler.maxRightMatchesLimitReached()) {
                System.out.println("WARNING: too many partial alignments detected, consider skipping assemblePartial (enriched library?). /maxRightMatchesLimitReached/");
                maxRightMatchesLimitReached = true;
            }

            if (parameters.report != null)
                Util.writeReport(parameters.report, report);

            if (parameters.jsonReport != null)
                Util.writeJsonReport(parameters.jsonReport, report);
        }
    }

    @Override
    public String command() {
        return "assemblePartial";
    }

    @Override
    public ActionParameters params() {
        return parameters;
    }

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
            return "assemblePartial";
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

    @Parameters(commandDescription = "Assemble clones")
    public static class AssemblePartialAlignmentsParameters extends ActionParametersWithResume.ActionParametersWithResumeWithBinaryInput {
        @Parameter(description = "input_file output_file")
        public List<String> parameters = new ArrayList<>();

        @DynamicParameter(names = "-O", description = "Overrides default parameter values.")
        public Map<String, String> overrides = new HashMap<>();

        @Parameter(description = "Report file.",
                names = {"-r", "--report"})
        public String report;

        @Parameter(description = "JSON report file.",
                names = {"--json-report"})
        public String jsonReport = null;

        @Parameter(description = "Write only overlapped sequences (needed for testing).",
                names = {"-o", "--overlapped-only"})
        public Boolean overlappedOnly;

        @Parameter(description = "Drop partial sequences which were not assembled. Can be used to reduce output file " +
                "size if no additional rounds of 'assemblePartial' are required.",
                names = {"-d", "--drop-partial"})
        public Boolean dropPartial;

        public String getInputFileName() {
            return parameters.get(0);
        }

        public String getOutputFileName() {
            return parameters.get(1);
        }

        public Boolean getOverlappedOnly() {
            return overlappedOnly != null && overlappedOnly;
        }

        public Boolean doDropPartial() {
            return dropPartial != null && dropPartial;
        }

        @Override
        protected List<String> getOutputFiles() {
            return Collections.singletonList(getOutputFileName());
        }

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
        public List<String> getInputFiles() {
            return parameters.subList(0, 1);
        }

        @Override
        public ActionConfiguration getConfiguration() {
            return new AssemblePartialConfiguration(getPartialAlignmentsAssemblerParameters(), doDropPartial(), getOverlappedOnly());
        }

        @Override
        public void validate() {
            if (parameters.size() != 2)
                throw new ParameterException("Wrong number of parameters.");
            super.validate();
        }
    }
}
