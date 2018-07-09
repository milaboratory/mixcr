package com.milaboratory.mixcr.cli;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.milaboratory.cli.Action;
import com.milaboratory.cli.ActionHelper;
import com.milaboratory.cli.ActionParameters;
import com.milaboratory.cli.ActionParametersWithOutput;
import com.milaboratory.mixcr.basictypes.ClnAReader;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader;
import com.milaboratory.mixcr.cli.ActionAlign.AlignParameters;
import com.milaboratory.mixcr.cli.ActionAssemble.AssembleParameters;
import com.milaboratory.mixcr.cli.ActionAssemblePartialAlignments.AssemblePartialAlignmentsParameters;
import com.milaboratory.mixcr.cli.ActionExportClones.CloneExportParameters;
import com.milaboratory.mixcr.cli.ActionExtend.ExtendActionParameters;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import io.repseq.core.VDJCLibraryRegistry;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 *
 */
public abstract class UberAction implements Action {
    /** the main parameters */
    public final UberActionParameters uberParameters;

    public UberAction(UberActionParameters uberParameters) {
        this.uberParameters = uberParameters;
    }

    @Override
    public void go(ActionHelper helper) throws Exception {
        // --- Running alignments
        AlignParameters alignParameters = uberParameters.mkAlignerParameters();

        // Actual alignments file
        String alignmentsFile = null;
        // first check that the align file is already exist
        if (uberParameters.resume && new File(uberParameters.vdjcaFileName()).exists()) {
            try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(uberParameters.vdjcaFileName())) {
                VDJCAlignerParameters parameters = reader.getParameters();
                if (parameters.equals(alignParameters.getAlignerParameters())) {
                    System.out.println("TODO"); // FIXME
                    alignmentsFile = uberParameters.vdjcaFileName();
                }
            }
        }

        if (alignmentsFile == null) {
            // need to run align

            assert alignParameters.report.equals(uberParameters.report);
            assert alignParameters.getOutputName().equals(uberParameters.vdjcaFileName());

            new ActionAlign(alignParameters).go(helper);
            alignmentsFile = uberParameters.vdjcaFileName();
        }


        // --- Running partial alignments
        int nAssemblePartialRounds = uberParameters.nAssemblePartialRounds;
        if (nAssemblePartialRounds > 0) {
            AssemblePartialAlignmentsParameters assemblePartialParameters = uberParameters.mkAssemblePartialParameters();
            for (int round = 0; round < nAssemblePartialRounds; ++round) {
                String parAlignmentsFile = uberParameters.partialAlignmentsFileName(round);
                // check whether file is already there
                if (uberParameters.resume && new File(parAlignmentsFile).exists()) {
                    try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(parAlignmentsFile)) {
                        VDJCAlignerParameters parameters = reader.getParameters();
                        if (parameters.equals(alignParameters.getAlignerParameters())) {
                            System.out.println("TODO"); // FIXME
                            alignmentsFile = parAlignmentsFile;
                            continue;
                        }
                    }
                }

                // add input and output files
                assemblePartialParameters.parameters.clear();
                assemblePartialParameters.parameters.add(alignmentsFile);
                assemblePartialParameters.parameters.add(parAlignmentsFile);
                alignmentsFile = parAlignmentsFile;

                new ActionAssemblePartialAlignments(assemblePartialParameters).go(helper);
            }
        }

        // --- Running alignments extender
        if (uberParameters.doExtendAlignments) {
            ExtendActionParameters extendParameters = uberParameters.mkExtendParameters();
            String extAlignmentsFile = null;
            // check whether file is already there
            if (uberParameters.resume && new File(uberParameters.extendedFileName()).exists()) {
                try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(uberParameters.extendedFileName())) {
                    VDJCAlignerParameters parameters = reader.getParameters();
                    if (parameters.equals(alignParameters.getAlignerParameters())) {
                        System.out.println("TODO"); // FIXME
                        extAlignmentsFile = uberParameters.extendedFileName();
                    }
                }
            }

            if (extAlignmentsFile == null) {
                // need to run extend
                extAlignmentsFile = uberParameters.extendedFileName();
                // add input & output
                extendParameters.parameters.add(alignmentsFile);
                extendParameters.parameters.add(extAlignmentsFile);
                alignmentsFile = extAlignmentsFile;
                new ActionExtend(extendParameters).go(helper);
            }
        }

        // --- Running assembler
        AssembleParameters assembleParameters = uberParameters.mkAssembleParameters();
        String clnaFile = null;
        // check whether file is already there
        if (uberParameters.resume && new File(uberParameters.clnaFileName()).exists()) {
            try (ClnAReader reader = new ClnAReader(uberParameters.clnaFileName(), VDJCLibraryRegistry.getDefault())) {
                if (reader.getAssemblerParameters().equals(assembleParameters.getCloneAssemblerParameters()))
                    clnaFile = uberParameters.clnaFileName();
            }
        }
        if (clnaFile == null) {
            // need to run extend
            clnaFile = uberParameters.clnaFileName();
            // add input & output
            assembleParameters.parameters.add(alignmentsFile);
            assembleParameters.parameters.add(clnaFile);

            new ActionAssemble(assembleParameters).go(helper);
        }

        // --- Running export
        CloneExportParameters exportParameters = uberParameters.mkExportParameters();
        ActionExportClones export = new ActionExportClones(exportParameters);

        ArrayList<String> epArgs = new ArrayList<>(uberParameters.exportParameters);
        // add in & out
        epArgs.add(clnaFile);
        epArgs.add(uberParameters.exportClonesFileName());

        export.parseParameters(epArgs
                .stream()
                .flatMap(s -> Arrays.stream(s.split(" "))).toArray(String[]::new));
        export.go(helper);
    }

    @Override
    public ActionParameters params() {
        return uberParameters;
    }

    public static class UberActionParameters extends ActionParametersWithOutput implements Serializable {
        @Parameter(description = "input_file1 [input_file2] output_file", variableArity = true)
        public List<String> files = new ArrayList<>();

        @Parameter(description = "Species (organism), as specified in library file or taxon id. " +
                "Possible values: hs, HomoSapiens, musmusculus, mmu, hsa, 9606, 10090 etc.",
                names = {"-s", "--species"},
                required = true)
        public String species = "hs";

        @Parameter(description = "Resume execution.", names = {"--resume"})
        public boolean resume = false;

        @Parameter(description = "Report file.", names = {"-r", "--report"})
        public String report = "report.txt";

        @Parameter(names = "--align", description = "Align parameters", variableArity = true)
        public List<String> alignParameters = new ArrayList<>();

        /** Prepare parameters for align */
        public AlignParameters mkAlignerParameters() {
            AlignParameters ap = new AlignParameters() {
                @Override
                public void validate() { } // discard validation
            };

            // align parameters
            List<String> alignParameters = new ArrayList<>();
            // add pre-defined parameters first (may be overriden)

            if (nAssemblePartialRounds > 0)
                alignParameters.add("-OallowPartialAlignments=true");

            // add required parameters (for JCommander)
            alignParameters.add("--species");
            alignParameters.add(species);

            // add report file
            alignParameters.add("--report");
            alignParameters.add(report);

            // add all override parameters
            alignParameters.addAll(this.alignParameters);

            // put input fastq files & output vdjca
            alignParameters.addAll(getInputFiles());
            alignParameters.add(vdjcaFileName());

            // parse parameters
            new JCommander(ap).parse(
                    alignParameters
                            .stream()
                            .flatMap(s -> Arrays.stream(s.split(" ")))
                            .toArray(String[]::new));
            return ap;
        }

        @Parameter(names = "--assemblePartial", description = "Partial assembler parameters", variableArity = true)
        public List<String> assemblePartialParameters = new ArrayList<>();

        /** Build parameters for assemble partial (no output specified) */
        public AssemblePartialAlignmentsParameters mkAssemblePartialParameters() {
            AssemblePartialAlignmentsParameters ap = new AssemblePartialAlignmentsParameters() {
                @Override
                public void validate() { }
            };

            List<String> assemblePartialParameters = new ArrayList<>();

            // add report file
            assemblePartialParameters.add("--report");
            assemblePartialParameters.add(report);

            // add all override parameters
            assemblePartialParameters.addAll(this.assemblePartialParameters);

            // parse parameters
            new JCommander(ap).parse(
                    assemblePartialParameters
                            .stream()
                            .flatMap(s -> Arrays.stream(s.split(" ")))
                            .toArray(String[]::new));
            return ap;
        }

        @Parameter(names = "--extend", description = "Extend alignments parameters", variableArity = true)
        public List<String> extendAlignmentsParameters = new ArrayList<>();

        /** Build parameters for extender (no output specified) */
        public ExtendActionParameters mkExtendParameters() {
            ExtendActionParameters ap = new ExtendActionParameters() {
                @Override
                public void validate() { }
            };

            List<String> extendParameters = new ArrayList<>();

            // add report file
            extendParameters.add("--report");
            extendParameters.add(report);

            // add all override parameters
            extendParameters.addAll(this.extendAlignmentsParameters);

            // parse parameters
            new JCommander(ap).parse(
                    extendParameters
                            .stream()
                            .flatMap(s -> Arrays.stream(s.split(" ")))
                            .toArray(String[]::new));
            return ap;
        }

        @Parameter(names = "--assemble", description = "Assemble parameters", variableArity = true)
        public List<String> assembleParameters = new ArrayList<>();

        /** Build parameters for assemble (no output specified) */
        public AssembleParameters mkAssembleParameters() {
            AssembleParameters ap = new AssembleParameters() {
                @Override
                public void validate() { }
            };

            List<String> assembleParameters = new ArrayList<>();

            // add report file
            assembleParameters.add("--report");
            assembleParameters.add(report);

            // we always write clna
            assembleParameters.add("--write-alignments");

            // add all override parameters
            assembleParameters.addAll(this.assembleParameters);

            // parse parameters
            new JCommander(ap).parse(
                    assembleParameters
                            .stream()
                            .flatMap(s -> Arrays.stream(s.split(" ")))
                            .toArray(String[]::new));
            return ap;
        }

        @Parameter(names = "--export", description = "Export clones parameters", variableArity = true)
        public List<String> exportParameters = new ArrayList<>();

        /** Build parameters for export */
        public CloneExportParameters mkExportParameters() {
            CloneExportParameters ep = new CloneExportParameters() {
                @Override
                public void validate() { }
            };

            List<String> exportParameters = new ArrayList<>();
            // add all override parameters
            exportParameters.addAll(this.exportParameters);

            // parse parameters
            new JCommander(ep).parse(
                    exportParameters
                            .stream()
                            .flatMap(s -> Arrays.stream(s.split(" ")))
                            .toArray(String[]::new));
            return ep;
        }

        /** number of rounds for assemblePartial */
        @Parameter(names = "--assemble-partial-rounds", description = "Number of rounds of assemblePartial")
        public int nAssemblePartialRounds;

        /** whether to perform TCR alignments extension */
        @Parameter(names = "--do-extend-alignments", description = "Specified whether to perform TCR alignments extension")
        public boolean doExtendAlignments;

        UberActionParameters() {}

        /** input raw sequencing data files */
        List<String> getInputFiles() {
            return files.subList(0, files.size() - 1);
        }

        /** the pattern of output file name ("myOutput" will produce "myOutput.vdjca", "myOutput.clns" etc files) */
        String outputNamePattern() {
            return files.get(files.size() - 1);
        }

        public String vdjcaFileName() {
            return outputNamePattern() + ".vdjca";
        }

        public String partialAlignmentsFileName(int round) {
            return outputNamePattern() + ".rescued_" + round + ".vdjca";
        }

        public String extendedFileName() {
            return outputNamePattern() + ".extended.vdjca";
        }

        public String clnaFileName() {
            return outputNamePattern() + ".clna";
        }

        public String exportClonesFileName() {
            return outputNamePattern() + ".clones.txt";
        }

        @Override
        protected List<String> getOutputFiles() {
            return Collections.singletonList(exportClonesFileName());
        }
    }

    ////////////////////////////////////////////////// Implementation //////////////////////////////////////////////////

    @Parameters(commandDescription = "Analyze RepSeq data")
    public static class RepSeqParameters extends UberActionParameters {
        @Override
        public List<String> forceHideParameters() {
            return Arrays.asList("--assemblePartial", "--extend", "--assemble-partial-rounds", "--do-extend-alignments");
        }
    }

    /** Default pipeline for processing RepSeq type data */
    public static class UberRepSeq extends UberAction {
        public UberRepSeq() {
            super(new RepSeqParameters());
            uberParameters.nAssemblePartialRounds = 0;
            uberParameters.doExtendAlignments = false;
        }

        @Override
        public String command() {
            return "analyze-rep-seq";
        }
    }

    @Parameters(commandDescription = "Analyze RNA-Seq data")
    public static class RnaSeqParameters extends UberActionParameters {
        {
            nAssemblePartialRounds = 2;
            doExtendAlignments = true;
        }
    }

    /** Default pipeline for processing RnaSeq type data */
    public static class UberRnaSeq extends UberAction {
        public UberRnaSeq() {
            super(new RnaSeqParameters());
            uberParameters.alignParameters.add("-p");
            uberParameters.alignParameters.add("rna-seq");
        }

        @Override
        public String command() { return "analyze-rna-seq"; }
    }
}
