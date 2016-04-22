package com.milaboratory.mixcr.cli;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.milaboratory.mitools.cli.Action;
import com.milaboratory.mitools.cli.ActionHelper;
import com.milaboratory.mitools.cli.ActionParameters;
import com.milaboratory.mitools.merger.QualityMergingAlgorithm;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader;
import com.milaboratory.mixcr.partialassembler.PartialAlignmentsAssembler;
import com.milaboratory.mixcr.partialassembler.PartialAlignmentsAssemblerParameters;
import com.milaboratory.mixcr.reference.LociLibraryManager;
import com.milaboratory.util.SmartProgressReporter;

import java.util.List;

/**
 * @author Dmitry Bolotin
 * @author Stanislav Poslavsky
 */
public final class ActionAssemblePartialAlignments implements Action {
    private final AssemblePartialAlignmentsParameters parameters = new AssemblePartialAlignmentsParameters();

    @Override
    public void go(ActionHelper helper) throws Exception {

        PartialAlignmentsAssemblerParameters assParameters = new PartialAlignmentsAssemblerParameters(12, 0, 18,
                45, QualityMergingAlgorithm.SumSubtraction);

        long start = System.currentTimeMillis();
        try (PartialAlignmentsAssembler assembler = new PartialAlignmentsAssembler(assParameters, parameters.getOutputFileName())) {
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

        @Parameter(description = "Report file.",
                names = {"-r", "--report"})
        public String report;

        public String getInputFileName() {
            return parameters.get(0);
        }

        public String getOutputFileName() {
            return parameters.get(1);
        }

        @Override
        public void validate() {
            if (parameters.size() != 2)
                throw new ParameterException("Wrong number of parameters.");
            super.validate();
        }

    }
}
