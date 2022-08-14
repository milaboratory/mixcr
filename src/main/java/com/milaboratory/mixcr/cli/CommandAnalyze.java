/*
 * Copyright (c) 2014-2022, MiLaboratories Inc. All Rights Reserved
 *
 * Before downloading or accessing the software, please read carefully the
 * License Agreement available at:
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 *
 * By downloading or accessing the software, you accept and agree to be bound
 * by the terms of the License Agreement. If you do not want to agree to the terms
 * of the Licensing Agreement, you must not download or access the software.
 */
package com.milaboratory.mixcr.cli;

import com.milaboratory.mixcr.assembler.CloneAssemblerParameters;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import com.milaboratory.util.JsonOverrider;
import io.repseq.core.Chains;
import io.repseq.core.GeneFeature;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public abstract class CommandAnalyze extends MiXCRCommand {
    private static <T extends WithNameWithDescription> T parse0(Class<? extends T> clazz, String v) {
        T[] ts = clazz.getEnumConstants();
        for (T t : ts)
            if (t.key().equalsIgnoreCase(v))
                return t;
        return null;
    }

    interface WithNameWithDescription {
        String key();

        String description();
    }

    enum _StartingMaterial implements WithNameWithDescription {
        rna("RNA"),
        dna("Genomic DNA");
        final String key, description;

        _StartingMaterial(String description) {
            this.key = this.toString();
            this.description = description;
        }

        @Override
        public String key() {
            return key;
        }

        @Override
        public String description() {
            return description;
        }

        static _StartingMaterial parse(String v) {
            return parse0(_StartingMaterial.class, v);
        }
    }


    enum _Chains implements WithNameWithDescription {
        tcr("All T-cell receptor types (TRA/TRB/TRG/TRD)", Chains.TCR),
        bcr("All B-cell receptor types (IGH/IGK/IGL/TRD)", Chains.IG),
        xcr("All T- and B-cell receptor types", Chains.ALL),
        tra("TRA chain", Chains.TRA),
        trb("TRB chain", Chains.TRB),
        trd("TRD chain", Chains.TRD),
        trg("TRG chain", Chains.TRG),
        igh("IGH chain", Chains.IGH),
        igk("IGK chain", Chains.IGK),
        igl("IGL chain", Chains.IGL);
        final String key, description;
        final Chains chains;

        _Chains(String description, Chains chains) {
            this.key = this.toString();
            this.description = description;
            this.chains = chains;
        }

        @Override
        public String key() {
            return key;
        }

        @Override
        public String description() {
            return description;
        }
    }


    enum _5EndPrimers implements WithNameWithDescription {
        noVPrimers("no-v-primers", "No V gene primers (e.g. 5’RACE with template switch oligo or a like)"),
        vPrimers("v-primers", "V gene single primer / multiplex");
        final String key, description;

        _5EndPrimers(String key, String description) {
            this.key = key;
            this.description = description;
        }

        @Override
        public String key() {
            return key;
        }

        @Override
        public String description() {
            return description;
        }

        static _5EndPrimers parse(String v) {
            return parse0(_5EndPrimers.class, v);
        }
    }

    enum _3EndPrimers implements WithNameWithDescription {
        jPrimers("j-primers", "J gene single primer / multiplex"),
        jcPrimers("j-c-intron-primers", "J-C intron single primer / multiplex"),
        cPrimers("c-primers", "C gene single primer / multiplex (e.g. IGHC primers specific to different immunoglobulin isotypes)");
        final String key, description;

        _3EndPrimers(String key, String description) {
            this.key = key;
            this.description = description;
        }

        @Override
        public String key() {
            return key;
        }

        @Override
        public String description() {
            return description;
        }

        static _3EndPrimers parse(String v) {
            return parse0(_3EndPrimers.class, v);
        }
    }

    enum _Adapters implements WithNameWithDescription {
        adaptersPresent("adapters-present", "May be present"),
        noAdapters("no-adapters", "Absent / nearly absent / trimmed");
        final String key, description;

        _Adapters(String key, String description) {
            this.key = key;
            this.description = description;
        }

        @Override
        public String key() {
            return key;
        }

        @Override
        public String description() {
            return description;
        }

        static _Adapters parse(String v) {
            return parse0(_Adapters.class, v);
        }
    }

    private static abstract class EnumCandidates extends ArrayList<String> {
        EnumCandidates(Class<? extends WithNameWithDescription> _enum) {
            super(Arrays.stream(_enum.getEnumConstants()).map(WithNameWithDescription::key).collect(Collectors.toList()));
        }
    }

    static class _StartingMaterialCandidates extends EnumCandidates {
        _StartingMaterialCandidates() {
            super(_StartingMaterial.class);
        }
    }

    static class _ChainsCandidates extends EnumCandidates {
        _ChainsCandidates() {
            super(_Chains.class);
        }
    }

    static class _5EndCandidates extends EnumCandidates {
        _5EndCandidates() {
            super(_5EndPrimers.class);
        }
    }

    static class _3EndCandidates extends EnumCandidates {
        _3EndCandidates() {
            super(_3EndPrimers.class);
        }
    }

    static class _AdaptersCandidates extends EnumCandidates {
        _AdaptersCandidates() {
            super(_Adapters.class);
        }
    }


    ///////////////////////////////////////////// Common options /////////////////////////////////////////////

    @Parameters(description = "input_file1 [input_file2] analysisOutputName")
    public List<String> inOut = new ArrayList<>();

    @Option(description = CommonDescriptions.SPECIES,
            names = {"-s", "--species"},
            required = true)
    public String species = "hs";

    @Option(description = "Aligner parameters preset",
            names = {"--align-preset"})
    public String alignPreset = null;

    public Chains chains = Chains.ALL;

    @Option(names = "--receptor-type",
            completionCandidates = _ChainsCandidates.class,
            description = "Receptor type. Possible values: ${COMPLETION-CANDIDATES}",
            required = false /* This will be overridden for amplicon */)
    public void setChains(String chains) {
        _Chains c = parse0(_Chains.class, chains);
        if (c == null)
            throwValidationException("Illegal value " + chains + " for --receptor-type option.");
        this.chains = c.chains;
    }

    public _StartingMaterial startingMaterial;

    @Option(names = "--starting-material",
            completionCandidates = _StartingMaterialCandidates.class,
            description = "Starting material. @|bold Possible values: ${COMPLETION-CANDIDATES}|@",
            required = true)
    public void setStartingMaterial(String value) {
        startingMaterial = _StartingMaterial.parse(value);
        if (startingMaterial == null)
            throwValidationException("Illegal value for --starting-material parameter: " + value);
    }

    @Option(names = "--impute-germline-on-export", description = "Export germline segments")
    public boolean exportGermline = false;

    @Option(names = "--only-productive", description = "Filter out-of-frame sequences and clonotypes with stop-codons in " +
            "clonal sequence export")
    public boolean onlyProductive = false;

    @Option(names = "--contig-assembly", description = "Assemble longest possible sequences from input data. " +
            "Useful for shotgun-like data." +
            "%nNOTE: this will substantially increase analysis time.")
    public boolean contigAssembly = false;

    @Option(names = {"--no-export"}, description = "Do not export clonotypes to tab-delimited file.")
    public boolean noExport = false;

    @Option(names = {"-r", "--report"}, description = "Report file path")
    public String report = null;

    @Option(names = {"-j", "--json-report"}, description = "Output json reports for each of the analysis steps. " +
            "Individual file will be created for each type of analysis step, value specified for this option will be used as a prefix.")
    public String jsonReport = null;

    @Option(names = {"-b", "--library"}, description = "V/D/J/C gene library")
    public String library = "default";

    public int threads = Runtime.getRuntime().availableProcessors();

    @Option(description = "Processing threads",
            names = {"-t", "--threads"})
    public void setThreads(int threads) {
        if (threads <= 0)
            throwValidationException("ERROR: -t / --threads must be positive", false);
        this.threads = threads;
    }

//     @Option(names = {"--overwrite-if-required"}, description = "Overwrite output file if it is corrupted or if it was generated from different input file \" +\n" +
//             "                    \"or with different parameters. -f / --force-overwrite overrides this option.")
//     public boolean overwriteIfRequired = false;

    public String getReport() {
        if (report == null)
            return fNameForReport();
        else
            return report;
    }

    private <T extends MiXCRCommand> T inheritOptionsAndValidate(T parameters) {
        if (forceOverwrite)
            parameters.forceOverwrite = true;

        parameters.quiet = true;
        parameters.validate();
        parameters.quiet = false;
        return parameters;
    }

    @Option(names = "--align",
            description = "Additional parameters for align step specified with double quotes (e.g --align \"--limit 1000\" --align \"-OminSumScore=100\" etc.",
            arity = "1")
    public List<String> alignParameters = new ArrayList<>();
    /** pre-defined (hidden from the user) parameters */
    protected List<String> initialAlignParameters = new ArrayList<>();

    private CommandAlign cmdAlign = null;

    /** Prepare parameters for align */
    public final CommandAlign getAlign() {
        if (cmdAlign != null)
            return cmdAlign;
        return cmdAlign = inheritOptionsAndValidate(mkAlign());
    }

    String forceAlignmentParameters() {return alignPreset;}

    boolean include5UTRInRNA() {return true;}

    Collection<String> pipelineSpecificAlignParameters() {
        return Collections.emptyList();
    }

    Collection<String> pipelineSpecificAssembleParameters() {
        return Collections.emptyList();
    }

    private void inheritThreads(List<String> args, List<String> specificArgs) {
        if (specificArgs.stream().noneMatch(s -> s.contains("--threads ") || s.contains("-t "))) {
            args.add("--threads");
            args.add(Integer.toString(threads));
        }
    }

    void addReportOptions(String step, List<String> options) {
        // add report file
        options.add("--report");
        options.add(getReport());

        // add json report file
        if (jsonReport != null) {
            options.add("--json-report");
            String pref;
            if (Files.isDirectory(Paths.get(jsonReport)))
                pref = jsonReport + (jsonReport.endsWith(File.separator) ? "" : File.separator);
            else
                pref = jsonReport + ".";
            options.add(pref + step + ".jsonl");
        }
    }

    protected abstract boolean needCorrectAndSortTags();

    CommandAlign mkAlign() {
        // align parameters
        List<String> alignParameters = new ArrayList<>(initialAlignParameters);

        // add pre-defined parameters first (may be overriden)
        if (nAssemblePartialRounds > 0)
            alignParameters.add("-OallowPartialAlignments=true");

        // add required parameters (for JCommander)
        alignParameters.add("--species");
        alignParameters.add(species);

        alignParameters.add("--library");
        alignParameters.add(library);

        inheritThreads(alignParameters, this.alignParameters);

        // adding report options
        addReportOptions("align", alignParameters);

        if (forceAlignmentParameters() == null) {
            if (!chains.intersects(Chains.TCR))
                alignParameters.add("-p kAligner2");
            else
                alignParameters.add("-p rna-seq");
        } else
            alignParameters.add("-p " + forceAlignmentParameters());

        // add v feature to align
        switch (startingMaterial) {
            case rna:
                alignParameters.add("-OvParameters.geneFeatureToAlign=" +
                        (include5UTRInRNA()
                                ? "VTranscriptWithP"
                                : "VTranscriptWithout5UTRWithP"));
                break;
            case dna:
                alignParameters.add("-OvParameters.geneFeatureToAlign=VGeneWithP");
                break;
        }

        // pipeline specific parameters
        alignParameters.addAll(this.pipelineSpecificAlignParameters());

        // add all override parameters
        alignParameters.addAll(this.alignParameters
                .stream()
                .flatMap(s -> Arrays.stream(s.split(" ")))
                .collect(Collectors.toList()));

        // put input fastq files & output vdjca
        alignParameters.addAll(getInputFiles());
        alignParameters.add(fNameForAlignments());

        // parse parameters
        CommandAlign ap = new CommandAlign();
        ap.spec = this.spec;
        new CommandLine(ap).parseArgs(alignParameters.toArray(new String[0]));
        return ap;
    }

    @Option(names = "--correctAndSortTagParameters",
            description = "Additional parameters for correctAndSortTagParameters step specified with double quotes.",
            arity = "1")
    public List<String> correctAndSortTagsParameters = new ArrayList<>();

    /** Build parameters for assemble partial */
    public final CommandCorrectAndSortTags mkCorrectAndSortTags(String input, String output) {
        List<String> correctAndSortTagsParameters = new ArrayList<>();

        // adding report options
        addReportOptions("correctAndSortTags", correctAndSortTagsParameters);

        // add all override parameters
        correctAndSortTagsParameters.addAll(this.correctAndSortTagsParameters
                .stream()
                .flatMap(s -> Arrays.stream(s.split(" ")))
                .collect(Collectors.toList()));

        correctAndSortTagsParameters.add(input);
        correctAndSortTagsParameters.add(output);

        // parse parameters
        CommandCorrectAndSortTags ap = new CommandCorrectAndSortTags();
        new CommandLine(ap).parseArgs(correctAndSortTagsParameters.toArray(new String[0]));
        return inheritOptionsAndValidate(ap);
    }

    @Option(names = "--assemblePartial",
            description = "Additional parameters for assemblePartial step specified with double quotes (e.g --assemblePartial \"--overlappedOnly\" --assemblePartial \"-OkOffset=0\" etc.",
            arity = "1")
    public List<String> assemblePartialParameters = new ArrayList<>();

    /** Build parameters for assemble partial */
    public final CommandAssemblePartialAlignments mkAssemblePartial(String input, String output) {
        List<String> assemblePartialParameters = new ArrayList<>();

        // adding report options
        addReportOptions("assemblePartial", assemblePartialParameters);

        // add all override parameters
        assemblePartialParameters.addAll(this.assemblePartialParameters
                .stream()
                .flatMap(s -> Arrays.stream(s.split(" ")))
                .collect(Collectors.toList()));

        assemblePartialParameters.add(input);
        assemblePartialParameters.add(output);

        // parse parameters
        CommandAssemblePartialAlignments ap = new CommandAssemblePartialAlignments();
        new CommandLine(ap).parseArgs(assemblePartialParameters.toArray(new String[0]));
        return inheritOptionsAndValidate(ap);
    }

    @Option(names = "--extend",
            description = "Additional parameters for extend step specified with double quotes (e.g --extend \"--chains TRB\" --extend \"--quality 0\" etc.",
            arity = "1")
    public List<String> extendAlignmentsParameters = new ArrayList<>();

    /** Build parameters for extender */
    public final CommandExtend mkExtend(String input, String output) {
        List<String> extendParameters = new ArrayList<>();

        // adding report options
        addReportOptions("extend", extendParameters);

        inheritThreads(extendParameters, this.extendAlignmentsParameters);

        // add all override parameters
        extendParameters.addAll(this.extendAlignmentsParameters
                .stream()
                .flatMap(s -> Arrays.stream(s.split(" ")))
                .collect(Collectors.toList()));

        extendParameters.add(input);
        extendParameters.add(output);

        // parse parameters
        CommandExtend ap = new CommandExtend();
        new CommandLine(ap).parseArgs(extendParameters.toArray(new String[0]));
        return inheritOptionsAndValidate(ap);
    }

    @Option(names = "--assemble",
            description = "Additional parameters for assemble step specified with double quotes (e.g --assemble \"-OassemblingFeatures=[V5UTR+L1+L2+FR1,FR3+CDR3]\" --assemble \"-ObadQualityThreshold=0\" etc.",
            arity = "1")
    public List<String> assembleParameters = new ArrayList<>();

    /** Build parameters for assemble */
    public CommandAssemble getAssemble(String input, String output) {
        return inheritOptionsAndValidate(mkAssemble(input, output));
    }

    /** Build parameters for assemble */
    CommandAssemble mkAssemble(String input, String output) {
        List<String> assembleParameters = new ArrayList<>();

        // adding report options
        addReportOptions("assemble", assembleParameters);

        if (contigAssembly)
            assembleParameters.add("--write-alignments");

        inheritThreads(assembleParameters, this.assembleParameters);

        // pipeline specific parameters
        assembleParameters.addAll(this.pipelineSpecificAssembleParameters());

        // add all override parameters
        assembleParameters.addAll(this.assembleParameters
                .stream()
                .flatMap(s -> Arrays.stream(s.split(" ")))
                .collect(Collectors.toList()));

        assembleParameters.add(input);
        assembleParameters.add(output);

        // parse parameters
        CommandAssemble ap = new CommandAssemble();
        new CommandLine(ap).parseArgs(assembleParameters.toArray(new String[0]));
        ap.getCloneAssemblerParameters().updateFrom(mkAlign().getAlignerParameters());
        return ap;
    }

    @Option(names = "--assembleContigs",
            description = "Additional parameters for assemble contigs step specified with double quotes",
            arity = "1")
    public List<String> assembleContigParameters = new ArrayList<>();

    /** Build parameters for assemble */
    public final CommandAssembleContigs mkAssembleContigs(String input, String output) {
        List<String> assembleContigParameters = new ArrayList<>();

        // adding report options
        addReportOptions("assembleContigs", assembleContigParameters);

        inheritThreads(assembleContigParameters, this.assembleContigParameters);

        // add all override parameters
        assembleContigParameters.addAll(this.assembleContigParameters
                .stream()
                .flatMap(s -> Arrays.stream(s.split(" ")))
                .collect(Collectors.toList()));

        assembleContigParameters.add(input);
        assembleContigParameters.add(output);

        // parse parameters
        CommandAssembleContigs ap = new CommandAssembleContigs();
        new CommandLine(ap).parseArgs(assembleContigParameters.toArray(new String[0]));
        return inheritOptionsAndValidate(ap);
    }

    @Option(names = "--export",
            description = "Additional parameters for exportClones step specified with double quotes (e.g --export \"-p full\" --export \"-cloneId\" etc.",
            arity = "1")
    public List<String> exportParameters = new ArrayList<>();

    /** Build parameters for export */
    public final CommandExport.CommandExportClones mkExport(String input, String output, String chains) {
        List<String> exportParameters = new ArrayList<>();

        exportParameters.add("--force-overwrite");
        exportParameters.add("--chains");
        exportParameters.add(chains);

        if (onlyProductive) {
            exportParameters.add("--filter-out-of-frames");
            exportParameters.add("--filter-stops");
        }

        if (exportGermline)
            exportParameters.add("-p fullImputed");
        // TODO ? else exportParameters.add("-p full"); // for the consistent additional parameter behaviour

        // add all override parameters
        exportParameters.addAll(this.exportParameters);

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
    @Option(names = "--do-not-extend-alignments", description = "Skip TCR alignments extension")
    public boolean doNotExtendAlignments;

    /** input raw sequencing data files */
    @Override
    public List<String> getInputFiles() {
        return inOut.subList(0, inOut.size() - 1);
    }

    @Override
    public List<String> getOutputFiles() {
        return Collections.emptyList();
    }

    /** the pattern of output file name ("myOutput" will produce "myOutput.vdjca", "myOutput.clns" etc files) */
    String outputNamePattern() {
        return inOut.get(inOut.size() - 1);
    }

    public String fNameForReport() {
        return outputNamePattern() + ".report";
    }

    public String fNameForAlignments() {
        return outputNamePattern() + ".vdjca";
    }

    public String fNameForCorrectedAlignments() {
        return outputNamePattern() + ".corrected.vdjca";
    }

    public String fNameForParAlignments(int round) {
        return outputNamePattern() + ".rescued_" + round + ".vdjca";
    }

    public String fNameForExtendedAlignments() {
        return outputNamePattern() + ".extended.vdjca";
    }

    public String fNameForClones() {
        return outputNamePattern() + (contigAssembly ? ".clna" : ".clns");
    }

    public String fNameForContigs() {
        return outputNamePattern() + ".contigs.clns";
    }

    public String fNameForExportClones(String chains) {
        return outputNamePattern() + ".clonotypes." + chains + ".txt";
    }

    @Override
    public void handleExistenceOfOutputFile(String outFileName) {
        // Do nothing
    }

    @Override
    public void validate() {
        // don't invoke parent validation of input/output existelnce
        if (report == null)
            warn("NOTE: report file is not specified, using " + getReport() + " to write report.");
        if (new File(outputNamePattern()).exists())
            throwValidationException("Output file name prefix, matches the existing file name. Most probably you " +
                    "specified paired-end file names but forgot to specify output file name prefix.", false);
    }

    @Override
    public void run0() {
        JsonOverrider.suppressSameValueOverride = true;

        // --- Running alignments
        getAlign().run();
        String fileWithAlignments = fNameForAlignments();

        // --- Running correctAndSortTags
        if (needCorrectAndSortTags()) {
            String correctedVDJCA = fNameForCorrectedAlignments();
            mkCorrectAndSortTags(fileWithAlignments, correctedVDJCA).run();
            fileWithAlignments = correctedVDJCA;
        }

        // --- Running partial alignments
        for (int round = 0; round < nAssemblePartialRounds; ++round) {
            String fileWithParAlignments = fNameForParAlignments(round);
            mkAssemblePartial(fileWithAlignments, fileWithParAlignments).run();
            fileWithAlignments = fileWithParAlignments;
        }

        // --- Running alignments extender
        if (!doNotExtendAlignments) {
            String fileWithExtAlignments = fNameForExtendedAlignments();
            mkExtend(fileWithAlignments, fileWithExtAlignments).run();
            fileWithAlignments = fileWithExtAlignments;
        }

        // --- Running assembler
        String fileWithClones = fNameForClones();
        getAssemble(fileWithAlignments, fileWithClones).run();

        if (contigAssembly) {
            String fileWithContigs = fNameForContigs();
            mkAssembleContigs(fileWithClones, fileWithContigs).run();
            fileWithClones = fileWithContigs;
        }

        if (!noExport)
            // --- Running export
            if (!chains.equals(Chains.ALL))
                for (String chain : chains)
                    mkExport(fileWithClones, fNameForExportClones(chain), chain).run();
            else
                for (String chain : new String[]{"ALL", "TRA", "TRB", "TRG", "TRD", "IGH", "IGK", "IGL"})
                    mkExport(fileWithClones, fNameForExportClones(chain), chain).run();
    }


    ///////////////////////////////////////////// Amplicon /////////////////////////////////////////////

    @Command(name = "amplicon",
            sortOptions = false,
            separator = " ",
            description = "Analyze targeted TCR/IG library amplification (5'RACE, Amplicon, Multiplex, etc).")
    public static class CommandAmplicon extends CommandAnalyze {
        public CommandAmplicon() {
            doNotExtendAlignments = true;
            nAssemblePartialRounds = 0;
        }

        private _5EndPrimers vPrimers;

        @Option(names = "--extend-alignments",
                description = "Extend alignments",
                required = false)
        public void setDoExtendAlignments(boolean ignore) {
            doNotExtendAlignments = false;
        }

        @Option(names = "--5-end",
                completionCandidates = _5EndCandidates.class,
                description = "5'-end of the library. @|bold Possible values: ${COMPLETION-CANDIDATES}|@",
                required = true)
        public void set5End(String value) {
            vPrimers = _5EndPrimers.parse(value);
            if (vPrimers == null)
                throwValidationException("Illegal value for --5-end parameter: " + value);
        }

        private _3EndPrimers jcPrimers;

        @Option(names = "--3-end",
                completionCandidates = _3EndCandidates.class,
                description = "3'-end of the library. @|bold Possible values: ${COMPLETION-CANDIDATES}|@",
                required = true)
        public void set3End(String value) {
            jcPrimers = _3EndPrimers.parse(value);
            if (jcPrimers == null)
                throwValidationException("Illegal value for --3-end parameter: " + value);
        }

        private _Adapters adapters;

        @Option(names = "--adapters",
                completionCandidates = _AdaptersCandidates.class,
                description = "Presence of PCR primers and/or adapter sequences. If sequences of primers used for PCR or adapters are present in sequencing data, it may influence the accuracy of V, J and C gene segments identification and CDR3 mapping. @|bold Possible values: ${COMPLETION-CANDIDATES}|@",
                required = true)
        public void setAdapters(String value) {
            adapters = _Adapters.parse(value);
            if (adapters == null)
                throwValidationException("Illegal value for --adapters parameter: " + value);
        }

        private GeneFeature assemblingFeature = GeneFeature.CDR3;

        @Option(names = "--region-of-interest",
                description = "MiXCR will use only reads covering the whole target region; reads which partially cover selected region will be dropped during clonotype assembly. All non-CDR3 options require long high-quality paired-end data. See https://mixcr.readthedocs.io/en/master/geneFeatures.html for details.",
                required = false)
        private void setRegionOfInterest(String v) {
            try {
                assemblingFeature = GeneFeature.parse(v);
            } catch (Exception e) {
                throwValidationException("Illegal gene feature: " + v);
            }
            if (!assemblingFeature.contains(GeneFeature.ShortCDR3))
                throwValidationException("--region-of-interest must cover CDR3");
        }

        @Option(description = "UMI pattern to extract from the read.",
                names = {"--umi-pattern"})
        public String umiPattern;

        @Option(description = "UMI pattern name from the built-in list.",
                names = {"--tag-pattern-name"})
        public String umiPatternName;

        @Option(description = "Read UMI pattern from a file.",
                names = {"--umi-pattern-file"})
        public String umiPatternFile;

        @Override
        boolean include5UTRInRNA() {
            // (1) [ adapters == _Adapters.noAdapters ]
            // If user specified that no adapter sequences are present in the data
            // we can safely extend reference V region to cover 5'UTR, as there is
            // no chance of false alignment extension over non-mRNA derived sequence
            //
            // (2) If [ vPrimers == _5EndPrimers.vPrimers && adapters == _Adapters.adaptersPresent ]
            // VAlignerParameters.floatingLeftBound will be true, so it is also safe to add 5'UTR to the
            // reference as the alignment will not be extended if sequences don't match.
            //
            // In all other cases addition of 5'UTR to the reference may lead to false extension of V alignment
            // over adapter sequence.
            // return adapters == _Adapters.noAdapters || vPrimers == _5EndPrimers.vPrimers; // read as adapters == _Adapters.noAdapters || floatingV()
            return true;
        }

        boolean floatingV() {
            return vPrimers == _5EndPrimers.vPrimers || adapters == _Adapters.adaptersPresent;
        }

        boolean floatingJ() {
            return jcPrimers == _3EndPrimers.jPrimers && adapters == _Adapters.adaptersPresent;
        }

        boolean floatingC() {
            return jcPrimers == _3EndPrimers.cPrimers && adapters == _Adapters.adaptersPresent;
        }

        @Override
        protected boolean needCorrectAndSortTags() {
            return umiPattern != null || umiPatternName != null || umiPatternFile != null;
        }

        @Override
        Collection<String> pipelineSpecificAlignParameters() {
            List<String> list = new ArrayList<>(Arrays.asList(
                    "-OvParameters.parameters.floatingLeftBound=" + floatingV(),
                    "-OjParameters.parameters.floatingRightBound=" + floatingJ(),
                    "-OcParameters.parameters.floatingRightBound=" + floatingC()
            ));
            if (umiPattern != null) {
                if (umiPattern.toLowerCase().contains("cell"))
                    throw new IllegalArgumentException("UMI pattern can't contain cell barcodes.");

                list.add("--tag-pattern");
                list.add(umiPattern);
            }
            if (umiPatternName != null) {
                list.add("--tag-pattern-name");
                list.add(umiPatternName);
            }
            if (umiPatternFile != null) {
                list.add("--tag-pattern-file");
                list.add(umiPatternFile);
            }
            return list;
        }

        @Override
        Collection<String> pipelineSpecificAssembleParameters() {
            return Arrays.asList(
                    "-OassemblingFeatures=\"[" + GeneFeature.encode(assemblingFeature).replaceAll(" ", "") + "]\"",
                    "-OseparateByV=" + !floatingV(),
                    "-OseparateByJ=" + !floatingJ(),
                    "-OseparateByC=" + !(floatingC() || floatingJ())
            );
        }
    }

    public static CommandSpec mkAmplicon() {
        CommandSpec spec = CommandSpec.forAnnotatedObject(CommandAmplicon.class);
        for (OptionSpec option : spec.options()) {
            String name = option.names()[0];
            if (name.equals("--assemblePartial")
                    || name.equals("--extend")
                    || name.equals("--assemble-partial-rounds")
                    || name.equals("--do-extend-alignments")) {

                try {
                    Field hidden = OptionSpec.class.getSuperclass().getDeclaredField("hidden");
                    hidden.setAccessible(true);
                    hidden.setBoolean(option, true);
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }

            if (name.equals("--chains")) {
                try {
                    Field hidden = OptionSpec.class.getSuperclass().getDeclaredField("required");
                    hidden.setAccessible(true);
                    hidden.setBoolean(option, true);
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return spec;
    }

    ///////////////////////////////////////////// Shotgun /////////////////////////////////////////////

    @Command(name = "shotgun",
            sortOptions = false,
            separator = " ",
            description = "Analyze random-fragmented data (like RNA-Seq, Exome-Seq, etc). " +
                    "This pipeline assumes the data contain no adapter / primer sequences. " +
                    "Adapter trimming must be performed for the data containing any artificial sequence parts " +
                    "(e.g. single-cell / molecular-barcoded data).")
    public static class CommandShotgun extends CommandAnalyze {
        public CommandShotgun() {
            chains = Chains.ALL;
            nAssemblePartialRounds = 2;
            doNotExtendAlignments = false;
        }

        @Override
        protected boolean needCorrectAndSortTags() {
            return false;
        }

        @Override
        String forceAlignmentParameters() {
            return alignPreset == null
                    ? "rna-seq"
                    : alignPreset;
        }

        @Override
        CommandAlign mkAlign() {
            CommandAlign align = super.mkAlign();
            VDJCAlignerParameters alignmentParameters = align.getAlignerParameters();
            if (alignmentParameters.getVAlignerParameters().getParameters().isFloatingLeftBound())
                throwValidationException("'shotgun' pipeline requires '-OvParameters.parameters.floatingLeftBound=false'.");
            if (alignmentParameters.getJAlignerParameters().getParameters().isFloatingRightBound())
                throwValidationException("'shotgun' pipeline requires '-OjParameters.parameters.floatingRightBound=false'.");
            if (alignmentParameters.getCAlignerParameters().getParameters().isFloatingRightBound())
                throwValidationException("'shotgun' pipeline requires '-OcParameters.parameters.floatingRightBound=false'.");
            return align;
        }

        @Override
        Collection<String> pipelineSpecificAssembleParameters() {
            return Arrays.asList(
                    "-OseparateByV=true",
                    "-OseparateByJ=true"
            );
        }

        @Override
        public CommandAssemble mkAssemble(String input, String output) {
            CommandAssemble assemble = super.mkAssemble(input, output);
            CloneAssemblerParameters cloneAssemblyParameters = assemble.getCloneAssemblerParameters();

            if (!Arrays.equals(cloneAssemblyParameters.getAssemblingFeatures(), new GeneFeature[]{GeneFeature.CDR3}))
                throwValidationException("'shotgun' pipeline can only use CDR3 as assembling feature. " +
                        "See --contig-assembly and --impute-germline-on-export options if you want to " +
                        "cover wider part of the receptor sequence.");

            return assemble;
        }
    }

    public static CommandSpec mkShotgun() {
        return CommandSpec.forAnnotatedObject(CommandShotgun.class);
    }

    @Command(name = "analyze",
            separator = " ",
            description = "Run full MiXCR pipeline for specific input.",
            subcommands = {
                    CommandLine.HelpCommand.class,
                    //CommandAmplicon.class, // will be added programmatically in Main
                    //CommandShotgun.class   // will be added programmatically in Main
            })
    public static class CommandAnalyzeMain {
    }
}
