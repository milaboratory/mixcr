package com.milaboratory.mixcr.cli;

import com.beust.jcommander.Parameter;
import com.milaboratory.cli.Action;
import com.milaboratory.cli.ActionHelper;
import com.milaboratory.cli.ActionParameters;
import com.milaboratory.cli.ActionParametersWithOutput;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsWriter;

import java.util.List;

/**
 * @author Stanislav Poslavsky
 */
public class ActionExtendCD3 implements Action {
    private final ExtendCD3Parameters parameters = new ExtendCD3Parameters();

    @Override
    public void go(ActionHelper helper) throws Exception {
        try (final VDJCAlignmentsReader reader = new VDJCAlignmentsReader(parameters.getInput());
             final VDJCAlignmentsWriter writer = new VDJCAlignmentsWriter(parameters.getOutput())) {

            writer.header(reader.getParameters(), reader.getUsedGenes());
            VDJCAlignments al;
            long extended = 0;
            while ((al = reader.take()) != null) {

                ++extended;
            }

            writer.setNumberOfProcessedReads(reader.getNumberOfReads());
        }
    }

    @Override
    public String command() {
        return "extendCDR3";
    }

    @Override
    public ActionParameters params() {
        return parameters;
    }

    private static final class ExtendCD3Parameters extends ActionParametersWithOutput {
        @Parameter(description = "input.vdjca[.gz] output.vdjca[.gz]")
        public List<String> parameters;

        @Parameter(description = "Filter export to a specific protein chain gene (e.g. TRA or IGH).",
                names = {"-c", "--chains"})
        public String chain = "TCR";

        private String getInput() { return parameters.get(0); }

        private String getOutput() { return parameters.get(1); }

        @Override
        protected List<String> getOutputFiles() {
            return parameters.subList(1, parameters.size());
        }
    }
}
