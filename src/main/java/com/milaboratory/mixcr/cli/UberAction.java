package com.milaboratory.mixcr.cli;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.milaboratory.cli.Action;
import com.milaboratory.cli.ActionHelper;
import com.milaboratory.cli.ActionParameters;
import com.milaboratory.cli.ActionParametersWithOutput;
import com.milaboratory.mixcr.cli.ActionAlign.AlignParameters;
import com.milaboratory.mixcr.cli.ActionAssemble.AssembleParameters;
import com.milaboratory.mixcr.cli.ActionAssemblePartialAlignments.AssemblePartialAlignmentsParameters;
import com.milaboratory.mixcr.cli.ActionExportClones.CloneExportParameters;
import com.milaboratory.mixcr.cli.ActionExtend.ExtendParameters;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.*;

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
        new ActionAlign(uberParameters.mkAlignerParameters()).go(helper);
        String fileWithAlignments = uberParameters.fNameForAlignments();

        // --- Running partial alignments
        for (int round = 0; round < uberParameters.nAssemblePartialRounds; ++round) {
            String fileWithParAlignments = uberParameters.fNameForParAlignments(round);
            new ActionAssemblePartialAlignments(uberParameters.mkAssemblePartialParameters(fileWithAlignments, fileWithParAlignments)).go(helper);
            fileWithAlignments = fileWithParAlignments;
        }

        // --- Running alignments extender
        if (uberParameters.doExtendAlignments) {
            String fileWithExtAlignments = uberParameters.fNameForExtenedAlignments();
            new ActionExtend(uberParameters.mkExtendParameters(fileWithAlignments, fileWithExtAlignments)).go(helper);
            fileWithAlignments = fileWithExtAlignments;
        }

        // --- Running assembler
        String fileWithClones = uberParameters.fNameForClones();
        new ActionAssemble(uberParameters.mkAssembleParameters(fileWithAlignments, fileWithClones)).go(helper);

        // --- Running export
        String[] exportParameters = uberParameters.mkExportParametersArray(fileWithClones, uberParameters.fNameForExportClones());
        ActionExportClones export = new ActionExportClones(uberParameters.mkExportParameters(exportParameters));
        export.parseParameters(exportParameters);
        export.go(helper);
    }

    @Override
    public ActionParameters params() {
        return uberParameters;
    }

    public static class UberActionParameters extends ActionParametersWithOutput implements Serializable {
        @Parameter(description = "input_file1 [input_file2] analysisOutputName")
        public List<String> files = new ArrayList<>();

        @Parameter(names = {"-s", "--species"},
                description = "Species (organism), as specified in library file or taxon id. " +
                        "Possible values: hs, HomoSapiens, musmusculus, mmu, hsa, 9606, 10090 etc.",
                required = true)
        public String species = "hs";

        @Parameter(names = {"--resume"}, description = "Try to resume aborted execution")
        public boolean resume = false;

        static String defaultReport = "mixcr_report_" + new SimpleDateFormat("yyyy-MM-dd-HH:mm:ss.SSS").format(new Date()) + "." + (System.nanoTime() % 1000L) + ".txt";

        @Parameter(names = {"-r", "--report"}, description = "Report file.")
        public String report = defaultReport;

        private <T extends ActionParametersWithOutput> T inheritOptionsAndValidate(T parameters) {
            if (resume && parameters instanceof ActionParametersWithResumeOption)
                ((ActionParametersWithResumeOption) parameters).resume = true;
            if (isForceOverwrite())
                parameters.force = true;
            parameters.validate();
            return parameters;
        }

        @Parameter(names = "--align", description = "Additional parameters for align step specified with double quotes (e.g --align \"--limit 1000\" --align \"-OminSumScore=100\" etc.", variableArity = true)
        public List<String> alignParameters = new ArrayList<>();
        /** pre-defined (hidden from the user) parameters */
        protected List<String> initialAlignParameters = new ArrayList<>();

        /** Prepare parameters for align */
        public AlignParameters mkAlignerParameters() {
            // align parameters
            List<String> alignParameters = new ArrayList<>(initialAlignParameters);

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
            alignParameters.add(fNameForAlignments());

            // parse parameters
            AlignParameters ap = new AlignParameters();
            new JCommander(ap).parse(
                    alignParameters
                            .stream()
                            .flatMap(s -> Arrays.stream(s.split(" ")))
                            .toArray(String[]::new));
            return inheritOptionsAndValidate(ap);
        }

        @Parameter(names = "--assemblePartial", description = "Additional parameters for assemblePartial step specified with double quotes (e.g --assemblePartial \"--overlappedOnly\" --assemblePartial \"-OkOffset=0\" etc.", variableArity = true)
        public List<String> assemblePartialParameters = new ArrayList<>();

        /** Build parameters for assemble partial */
        public AssemblePartialAlignmentsParameters mkAssemblePartialParameters(String input, String output) {
            List<String> assemblePartialParameters = new ArrayList<>();

            // add report file
            assemblePartialParameters.add("--report");
            assemblePartialParameters.add(report);

            // add all override parameters
            assemblePartialParameters.addAll(this.assemblePartialParameters);

            assemblePartialParameters.add(input);
            assemblePartialParameters.add(output);

            // parse parameters
            AssemblePartialAlignmentsParameters ap = new AssemblePartialAlignmentsParameters();
            new JCommander(ap).parse(
                    assemblePartialParameters
                            .stream()
                            .flatMap(s -> Arrays.stream(s.split(" ")))
                            .toArray(String[]::new));
            return inheritOptionsAndValidate(ap);
        }

        @Parameter(names = "--extend", description = "Additional parameters for extend step specified with double quotes (e.g --extend \"--chains TRB\" --extend \"--quality 0\" etc.", variableArity = true)
        public List<String> extendAlignmentsParameters = new ArrayList<>();

        /** Build parameters for extender */
        public ExtendParameters mkExtendParameters(String input, String output) {
            List<String> extendParameters = new ArrayList<>();

            // add report file
            extendParameters.add("--report");
            extendParameters.add(report);

            // add all override parameters
            extendParameters.addAll(this.extendAlignmentsParameters);

            extendParameters.add(input);
            extendParameters.add(output);

            // parse parameters
            ExtendParameters ap = new ExtendParameters();
            new JCommander(ap).parse(
                    extendParameters
                            .stream()
                            .flatMap(s -> Arrays.stream(s.split(" ")))
                            .toArray(String[]::new));
            return inheritOptionsAndValidate(ap);
        }

        @Parameter(names = "--assemble", description = "Additional parameters for assemble step specified with double quotes (e.g --assemble \"-OassemblingFeatures=[V5UTR+L1+L2+FR1,FR3+CDR3]\" --assemble \"-ObadQualityThreshold=0\" etc.", variableArity = true)
        public List<String> assembleParameters = new ArrayList<>();

        /** Build parameters for assemble */
        public AssembleParameters mkAssembleParameters(String input, String output) {
            List<String> assembleParameters = new ArrayList<>();

            // add report file
            assembleParameters.add("--report");
            assembleParameters.add(report);

            // we always write clna
            assembleParameters.add("--write-alignments");

            // add all override parameters
            assembleParameters.addAll(this.assembleParameters);

            assembleParameters.add(input);
            assembleParameters.add(output);

            // parse parameters
            AssembleParameters ap = new AssembleParameters();
            new JCommander(ap).parse(
                    assembleParameters
                            .stream()
                            .flatMap(s -> Arrays.stream(s.split(" ")))
                            .toArray(String[]::new));
            return inheritOptionsAndValidate(ap);
        }

        @Parameter(names = "--export", description = "Additional parameters for exportClones step specified with double quotes (e.g --export \"-p full\" --export \"-cloneId\" etc.", variableArity = true)
        public List<String> exportParameters = new ArrayList<>();

        public String[] mkExportParametersArray(String input, String output) {
            List<String> exportParameters = new ArrayList<>();
            // add all override parameters
            exportParameters.addAll(this.exportParameters);

            exportParameters.add(input);
            exportParameters.add(output);

            return exportParameters.stream()
                    .flatMap(s -> Arrays.stream(s.split(" ")))
                    .toArray(String[]::new);
        }

        /** Build parameters for export */
        public CloneExportParameters mkExportParameters(String[] array) {
            // parse parameters
            CloneExportParameters ep = new CloneExportParameters();
            new JCommander(ep).parse(array);
            return inheritOptionsAndValidate(ep);
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

        public String fNameForAlignments() {
            return outputNamePattern() + ".vdjca";
        }

        public String fNameForParAlignments(int round) {
            return outputNamePattern() + ".rescued_" + round + ".vdjca";
        }

        public String fNameForExtenedAlignments() {
            return outputNamePattern() + ".extended.vdjca";
        }

        public String fNameForClones() {
            return outputNamePattern() + ".clna";
        }

        public String fNameForExportClones() {
            return outputNamePattern() + ".clones.txt";
        }

        @Override
        protected List<String> getOutputFiles() {
            return Collections.singletonList(fNameForExportClones());
        }

        @Override
        public void handleExistenceOfOutputFile(String outFileName) {
            if (!isForceOverwrite())
                throw new ParameterException("The destination file " + outFileName +
                        " already exists. Either remove it or use -f option to overwrite it (in this case you can also" +
                        " specify --resume option to prevent re-analyzing of intermediate files). ");
        }

        @Override
        public void validate() {
            super.validate();
            if (report.equals(defaultReport))
                System.err.println("NOTE: report file is not specified, using " + defaultReport + " to write report.");
        }
    }

    ////////////////////////////////////////////////// Implementation //////////////////////////////////////////////////

    @Parameters(commandDescription = "Analyze RepSeq data")
    public static class RepSeqParameters extends UberActionParameters {
        {
            nAssemblePartialRounds = 0;
            doExtendAlignments = false;
        }

        @Override
        public List<String> forceHideParameters() {
            return Arrays.asList("--assemblePartial", "--extend", "--assemble-partial-rounds", "--do-extend-alignments");
        }

        @Parameter(names = "--b-cells", description = "Use algorithms better optimized for B-cell data")
        public boolean bCellSpecific = false;

        @Override
        public AlignParameters mkAlignerParameters() {
            if (bCellSpecific)
                initialAlignParameters.addAll(Arrays.asList("-p", "kaligner2"));
            return super.mkAlignerParameters();
        }
    }

    /** Default pipeline for processing RepSeq type data */
    public static class UberRepSeq extends UberAction {
        public UberRepSeq() {
            super(new RepSeqParameters());
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
            initialAlignParameters.add("-p");
            initialAlignParameters.add("rna-seq");
        }
    }

    /** Default pipeline for processing RnaSeq type data */
    public static class UberRnaSeq extends UberAction {
        public UberRnaSeq() {
            super(new RnaSeqParameters());
        }

        @Override
        public String command() { return "analyze-rna-seq"; }
    }
}
