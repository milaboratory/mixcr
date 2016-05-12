package com.milaboratory.mixcr.cli;

import com.beust.jcommander.DynamicParameter;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.milaboratory.mitools.cli.Action;
import com.milaboratory.mitools.cli.ActionHelper;
import com.milaboratory.mitools.cli.ActionParameters;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader;
import com.milaboratory.mixcr.partialassembler.PartialAlignmentsAssembler;
import com.milaboratory.mixcr.partialassembler.PartialAlignmentsAssemblerParameters;
import com.milaboratory.mixcr.reference.LociLibraryManager;
import com.milaboratory.util.SmartProgressReporter;

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
        PartialAlignmentsAssemblerParameters assemblerParameters = PartialAlignmentsAssemblerParameters.getDefault();

        if (!parameters.overrides.isEmpty()) {
            assemblerParameters = JsonOverrider.override(assemblerParameters, PartialAlignmentsAssemblerParameters.class, parameters.overrides);
            if (assemblerParameters == null) {
                System.err.println("Failed to override some parameter.");
                return;
            }
        }

        long start = System.currentTimeMillis();
        try (PartialAlignmentsAssembler assembler = new PartialAlignmentsAssembler(assemblerParameters, parameters.getOutputFileName(),
                parameters.getWritePartial(), parameters.getOverlappedOnly())) {
            try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(parameters.getInputFileName(), LociLibraryManager.getDefault())) {
                SmartProgressReporter.startProgressReport("Building index", reader);
                assembler.buildLeftPartsIndex(reader);
            }
            try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(parameters.getInputFileName(), LociLibraryManager.getDefault())) {
                SmartProgressReporter.startProgressReport("Searching for overlaps", reader);
                assembler.searchOverlaps(reader);
            }

            if (parameters.report != null)
                Util.writeReport(parameters.getInputFileName(), parameters.getOutputFileName(),
                        helper.getCommandLineArguments(), parameters.report, assembler,
                        System.currentTimeMillis() - start);
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

    @Parameters(commandDescription = "Assemble clones",
            optionPrefixes = "-")
    private static class AssemblePartialAlignmentsParameters extends ActionParameters {
        @Parameter(description = "input_file output_file")
        public List<String> parameters;

        @DynamicParameter(names = "-O", description = "Overrides base values of parameters.")
        public Map<String, String> overrides = new HashMap<>();

        @Parameter(description = "Report file.",
                names = {"-r", "--report"})
        public String report;

        @Parameter(description = "Write only overlapped sequences (needed for testing).",
                names = {"-o", "--overlapped-only"})
        public Boolean overlappedOnly;

        @Parameter(description = "Write partial sequences (for recurrent overlapping).",
                names = {"-p", "--write-partial"})
        public Boolean writePartial;

        public String getInputFileName() {
            return parameters.get(0);
        }

        public String getOutputFileName() {
            return parameters.get(1);
        }

        public Boolean getOverlappedOnly() {
            return overlappedOnly != null && overlappedOnly;
        }

        public Boolean getWritePartial() {
            return writePartial != null && writePartial;
        }

        @Override
        public void validate() {
            if (parameters.size() != 2)
                throw new ParameterException("Wrong number of parameters.");
            super.validate();
        }

    }
}
