package com.milaboratory.mixcr.cli;

import com.beust.jcommander.DynamicParameter;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.milaboratory.cli.Action;
import com.milaboratory.cli.ActionHelper;
import com.milaboratory.cli.ActionParameters;
import com.milaboratory.cli.ActionParametersWithOutput;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader;
import com.milaboratory.mixcr.partialassembler.PartialAlignmentsAssembler;
import com.milaboratory.mixcr.partialassembler.PartialAlignmentsAssemblerParameters;
import com.milaboratory.util.SmartProgressReporter;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Dmitry Bolotin
 * @author Stanislav Poslavsky
 */
public final class ActionAssemblePartialAlignments implements Action {
    private final AssemblePartialAlignmentsParameters parameters = new AssemblePartialAlignmentsParameters();

    @Override
    public void go(ActionHelper helper) throws Exception {
        if (parameters.writePartial != null)
            System.err.println("'-p' option is deprecated and will be removed in 2.2. " +
                    "Use '-d' option to drop not-overlapped partial reads.");

        // Saving initial timestamp
        long beginTimestamp = System.currentTimeMillis();
        PartialAlignmentsAssemblerParameters assemblerParameters = PartialAlignmentsAssemblerParameters.getDefault();

        if (!parameters.overrides.isEmpty()) {
            assemblerParameters = JsonOverrider.override(assemblerParameters,
                    PartialAlignmentsAssemblerParameters.class, parameters.overrides);
            if (assemblerParameters == null) {
                System.err.println("Failed to override some parameter.");
                return;
            }
        }

        try (PartialAlignmentsAssembler assembler = new PartialAlignmentsAssembler(assemblerParameters,
                parameters.getOutputFileName(), !parameters.doDropPartial(),
                parameters.getOverlappedOnly())) {
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

    @Parameters(commandDescription = "Assemble clones")
    private static class AssemblePartialAlignmentsParameters extends ActionParametersWithOutput {
        @Parameter(description = "input_file output_file")
        public List<String> parameters;

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

        @Parameter(description = "[Deprecated, enabled by default] Write partial sequences (for recurrent overlapping).",
                names = {"-p", "--write-partial"})
        public Boolean writePartial;

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

        @Override
        public void validate() {
            if (parameters.size() != 2)
                throw new ParameterException("Wrong number of parameters.");
            super.validate();
        }

    }
}
