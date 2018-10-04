package com.milaboratory.mixcr.cli.newcli;

import io.repseq.core.GeneFeature;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CommandAnalyze extends ACommandWithOutput {
    @Parameters(description = "input_file1 [input_file2] analysisOutputName")
    public List<String> files = new ArrayList<>();

    @Option(names = {"-s", "--species"},
            description = "Species (organism), as specified in library file or taxon id. " +
                    "Possible values: hs, HomoSapiens, musmusculus, mmu, hsa, 9606, 10090 etc.",
            required = true)
    public String species = "hs";

    @Option(names = {"--resume"}, description = "Try to resume aborted execution")
    public boolean resume = false;

    enum SourceType {rna, dna}

    @Option(names = "--source-type", descriptionKey = "Source type", required = true)
    public SourceType sourceType;

    @Option(names = "--export-germline", descriptionKey = "Export germline segments")
    public boolean exportGermline = false;

    @Option(names = "--only-productive", descriptionKey = "Filter out-of-frame and stop-codons in export")
    public boolean onlyProductive = false;

    @Option(names = "--contig-assembly", descriptionKey = "Assemble full-length sequences. NOTE: this will take additional time.")
    public boolean contigAssembly = false;

    @Option(names = {"-r", "--report"}, description = "Report file.")
    public String report = null;

    public String getReport() {
        if (report == null)
            return fNameForReport();
        else
            return report;
    }

    private <T extends ACommandWithOutput> T inheritOptionsAndValidate(T parameters) {
        if (resume && parameters instanceof ACommandWithResume)
            ((ACommandWithResume) parameters).resume = true;
        if (isForceOverwrite() && !resume)
            parameters.force = true;
        parameters.validate();
        return parameters;
    }

    @Option(names = "--align",
            description = "Additional parameters for align step specified with double quotes (e.g --align \"--limit 1000\" --align \"-OminSumScore=100\" etc.",
            arity = "0..*")
    public List<String> alignParameters = new ArrayList<>();
    /** pre-defined (hidden from the user) parameters */
    protected List<String> initialAlignParameters = new ArrayList<>();

    /** Prepare parameters for align */
    public CommandAlign mkAlign() {
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
        alignParameters.add(getReport());

        // add all override parameters
        alignParameters.addAll(this.alignParameters);

        // put input fastq files & output vdjca
        alignParameters.addAll(getInputFiles());
        alignParameters.add(fNameForAlignments());

        // parse parameters
        CommandAlign ap = new CommandAlign();
        ap.spec = this.spec;
        new CommandLine(ap).parse(
                alignParameters
                        .stream()
                        .flatMap(s -> Arrays.stream(s.split(" ")))
                        .toArray(String[]::new));
        CommandAlign al = inheritOptionsAndValidate(ap);

        switch (sourceType) {
            case rna:
                al.getAlignerParameters()
                        .getVAlignerParameters()
                        .setGeneFeatureToAlign(GeneFeature.VTranscriptWithout5UTRWithP);
                break;
            case dna:
                al.getAlignerParameters()
                        .getVAlignerParameters()
                        .setGeneFeatureToAlign(GeneFeature.VGeneWithP);
                break;
        }

        return al;
    }

    @Option(names = "--assemblePartial",
            description = "Additional parameters for assemblePartial step specified with double quotes (e.g --assemblePartial \"--overlappedOnly\" --assemblePartial \"-OkOffset=0\" etc.",
            arity = "0..*")
    public List<String> assemblePartialParameters = new ArrayList<>();

    /** Build parameters for assemble partial */
    public CommandAssemblePartialAlignments mkAssemblePartial(String input, String output) {
        List<String> assemblePartialParameters = new ArrayList<>();

        // add report file
        assemblePartialParameters.add("--report");
        assemblePartialParameters.add(getReport());

        // add all override parameters
        assemblePartialParameters.addAll(this.assemblePartialParameters);

        assemblePartialParameters.add(input);
        assemblePartialParameters.add(output);

        // parse parameters
        CommandAssemblePartialAlignments ap = new CommandAssemblePartialAlignments();
        new CommandLine(ap).parse(
                assemblePartialParameters
                        .stream()
                        .flatMap(s -> Arrays.stream(s.split(" ")))
                        .toArray(String[]::new));
        return inheritOptionsAndValidate(ap);
    }

    @Option(names = "--extend",
            description = "Additional parameters for extend step specified with double quotes (e.g --extend \"--chains TRB\" --extend \"--quality 0\" etc.",
            arity = "0..*")
    public List<String> extendAlignmentsParameters = new ArrayList<>();

    /** Build parameters for extender */
    public CommandExtend mkExtend(String input, String output) {
        List<String> extendParameters = new ArrayList<>();

        // add report file
        extendParameters.add("--report");
        extendParameters.add(getReport());

        // add all override parameters
        extendParameters.addAll(this.extendAlignmentsParameters);

        extendParameters.add(input);
        extendParameters.add(output);

        // parse parameters
        CommandExtend ap = new CommandExtend();
        new CommandLine(ap).parse(
                extendParameters
                        .stream()
                        .flatMap(s -> Arrays.stream(s.split(" ")))
                        .toArray(String[]::new));
        return inheritOptionsAndValidate(ap);
    }

    @Option(names = "--assemble",
            description = "Additional parameters for assemble step specified with double quotes (e.g --assemble \"-OassemblingFeatures=[V5UTR+L1+L2+FR1,FR3+CDR3]\" --assemble \"-ObadQualityThreshold=0\" etc.",
            arity = "0..*")
    public List<String> assembleParameters = new ArrayList<>();

    /** Build parameters for assemble */
    public CommandAssemble mkAssemble(String input, String output) {
        List<String> assembleParameters = new ArrayList<>();

        // add report file
        assembleParameters.add("--report");
        assembleParameters.add(getReport());

        // we always write clna
        assembleParameters.add("--write-alignments");

        // add all override parameters
        assembleParameters.addAll(this.assembleParameters);

        assembleParameters.add(input);
        assembleParameters.add(output);

        // parse parameters
        CommandAssemble ap = new CommandAssemble();
        new CommandLine(ap).parse(
                assembleParameters
                        .stream()
                        .flatMap(s -> Arrays.stream(s.split(" ")))
                        .toArray(String[]::new));
        return inheritOptionsAndValidate(ap);
    }

    @Option(names = "--assembleContigs",
            description = "Additional parameters for assemble contigs step specified with double quotes",
            arity = "0..*")
    public List<String> assembleContigParameters = new ArrayList<>();

    /** Build parameters for assemble */
    public CommandAssembleContigs mkAssembleContigs(String input, String output) {
        List<String> assembleContigParameters = new ArrayList<>();

        // add report file
        assembleContigParameters.add("--report");
        assembleContigParameters.add(getReport());

        // add all override parameters
        assembleContigParameters.addAll(this.assembleContigParameters);

        assembleContigParameters.add(input);
        assembleContigParameters.add(output);

        // parse parameters
        CommandAssembleContigs ap = new CommandAssembleContigs();
        new CommandLine(ap).parse(
                assembleContigParameters
                        .stream()
                        .flatMap(s -> Arrays.stream(s.split(" ")))
                        .toArray(String[]::new));
        return inheritOptionsAndValidate(ap);
    }

    @Option(names = "--export",
            description = "Additional parameters for exportClones step specified with double quotes (e.g --export \"-p full\" --export \"-cloneId\" etc.",
            arity = "0..*")
    public List<String> exportParameters = new ArrayList<>();

    /** Build parameters for export */
    public CommandExport.CommandExportClones mkExport(String input, String output) {
        List<String> exportParameters = new ArrayList<>();
        // add all override parameters
        exportParameters.addAll(this.exportParameters);
        if (exportGermline)
            exportParameters.add("-p fullImputed");
        if (onlyProductive) {
            exportParameters.add("--filter-out-of-frames");
            exportParameters.add("--filter-stops");
        }

        exportParameters.add(input);
        exportParameters.add(output);

        String[] array = exportParameters.stream()
                .flatMap(s -> Arrays.stream(s.split(" ")))
                .toArray(String[]::new);

        // parse parameters
        CommandLine cmd = new CommandLine(CommandExport.mkClonesSpec());
        cmd.parse(array);
        return inheritOptionsAndValidate((CommandExport.CommandExportClones) cmd.getCommandSpec().userObject());
    }

    /** number of rounds for assemblePartial */
    @Option(names = "--assemble-partial-rounds", description = "Number of rounds of assemblePartial")
    public int nAssemblePartialRounds;

    /** whether to perform TCR alignments extension */
    @Option(names = "--do-extend-alignments", description = "Specified whether to perform TCR alignments extension")
    public boolean doExtendAlignments;

    /** input raw sequencing data files */
    @Override
    public List<String> getInputFiles() {
        return files.subList(0, files.size() - 1);
    }

    /** the pattern of output file name ("myOutput" will produce "myOutput.vdjca", "myOutput.clns" etc files) */
    String outputNamePattern() {
        return files.get(files.size() - 1);
    }

    public String fNameForReport() {
        return outputNamePattern() + ".report";
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

    public String fNameForContigs() {
        return outputNamePattern() + "_contigs.clna";
    }

    public String fNameForExportClones() {
        return outputNamePattern() + ".clones.txt";
    }

    @Override
    public List<String> getOutputFiles() {
        return Collections.singletonList(fNameForExportClones());
    }

    @Override
    public void handleExistenceOfOutputFile(String outFileName) {
        if (!isForceOverwrite())
            throwValidationException("The destination file " + outFileName +
                    " already exists. Either remove it or use -f option to overwrite it (in this case you can also" +
                    " specify --resume option to prevent re-analyzing of intermediate files). ");
    }

    @Override
    public void validate() {
        super.validate();
        if (report == null)
            warn("NOTE: report file is not specified, using " + getReport() + " to write report.");
    }

    @Override
    public void run0() throws Exception {
        // --- Running alignments
        mkAlign().run();
        String fileWithAlignments = fNameForAlignments();

        // --- Running partial alignments
        for (int round = 0; round < nAssemblePartialRounds; ++round) {
            String fileWithParAlignments = fNameForParAlignments(round);
            mkAssemblePartial(fileWithAlignments, fileWithParAlignments).run();
            fileWithAlignments = fileWithParAlignments;
        }

        // --- Running alignments extender
        if (doExtendAlignments) {
            String fileWithExtAlignments = fNameForExtenedAlignments();
            mkExtend(fileWithAlignments, fileWithExtAlignments).run();
            fileWithAlignments = fileWithExtAlignments;
        }

        // --- Running assembler
        String fileWithClones = fNameForClones();
        mkAssemble(fileWithAlignments, fileWithClones).run();

        if (contigAssembly) {
            String fileWithContigs = fNameForContigs();
            mkAssembleContigs(fileWithClones, fileWithContigs).run();
            fileWithClones = fileWithContigs;
        }

        // --- Running export
        mkExport(fileWithClones, fNameForExportClones()).run();
    }


    @Command(name = "analyze",
            sortOptions = true,
            separator = " ",
            description = "Uber analysis.",
            subcommands = {CommandShotgun.class})
    public static class CommandAnalyzeMain {}

    enum Chains {tcr, bcr, xcr, tra, trb, trd, trg}

    @Command(name = "shotgun",
            sortOptions = true,
            separator = " ",
            description = "Shotgun anaysis.")
    public static class CommandShotgun extends CommandAnalyze {

        @Option(names = "--receptor-type", descriptionKey = "Receptor type", required = false)
        public Chains chains;


    }
}
