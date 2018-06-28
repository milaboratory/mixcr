package com.milaboratory.mixcr.cli;

import com.beust.jcommander.Parameter;
import com.milaboratory.cli.Action;
import com.milaboratory.cli.ActionHelper;
import com.milaboratory.cli.ActionParameters;
import com.milaboratory.cli.ActionParametersWithOutput;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *
 */
public abstract class UberAction implements Action {
    public UberActionParameters uberParameters;

    @Override
    public void go(ActionHelper helper) throws Exception {
        // --- Running alignments

        ActionAlign.AlignParameters alignParameters = new ActionAlign.AlignParameters();
        // Actual alignments file
        String alignmentsFile = null;
        // first check that the align file is already exist
        if (uberParameters.resume && new File(uberParameters.vdjcaFileName()).exists()) {
            try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(uberParameters.vdjcaFileName())) {
                VDJCAlignerParameters parameters = reader.getParameters();
                if (parameters.equals(alignParameters.getAlignerParameters()))
                    alignmentsFile = uberParameters.vdjcaFileName();
            }
        }

        if (alignmentsFile == null) {
            // need to run align

            // put input fastq files & output vdjca
            alignParameters.parameters.addAll(uberParameters.inputFiles());
            alignParameters.parameters.add(uberParameters.vdjcaFileName());
            alignParameters.report = uberParameters.report;

            ActionAlign align = new ActionAlign(alignParameters);
            align.go(helper);

            alignmentsFile = uberParameters.vdjcaFileName();
        }


    }

    @Override
    public ActionParameters params() {
        return uberParameters;
    }

    public static class UberActionParameters extends ActionParametersWithOutput {
        @Parameter(description = "input_file1 [input_file2] output_file", variableArity = true)
        public List<String> files = new ArrayList<>();

        @Parameter(description = "Resume execution", names = {"--resume"})
        public boolean resume = false;

        @Parameter(description = "Report file.",
                names = {"-r", "--report"})
        public String report;

        @Parameter(names = "--align", description = "Align parameters", variableArity = true)
        public List<String> alignParameters = new ArrayList<>();

        List<String> inputFiles() {
            return files.subList(0, files.size() - 1);
        }

        String outputName() {
            return files.get(files.size() - 1);
        }

        public String vdjcaFileName() {
            return outputName() + ".vdjca";
        }

        public String partialAlignmentsFileName(int round) {
            return outputName() + ".rescued" + round + ".vdjca";
        }

        public String extendedFileName() {
            return outputName() + ".extended.vdjca";
        }

        public String clnaFileName() {
            return outputName() + ".clna";
        }

        public String exportClonezFileName() {
            return outputName() + ".clones.txt";
        }

        /** number of rounds for assemblePartial */
        int nAssemblePartialRounds;

        /** whether to perform TCR alignments extension */
        boolean doExtendAlignments;

        @Override
        protected List<String> getOutputFiles() {
            return Collections.singletonList(files.get(files.size() - 1));
        }
    }
}
