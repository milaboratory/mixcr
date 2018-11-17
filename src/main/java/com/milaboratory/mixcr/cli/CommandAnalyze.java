package com.milaboratory.mixcr.cli;

import com.milaboratory.mixcr.assembler.CloneAssemblerParameters;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import io.repseq.core.Chains;
import io.repseq.core.GeneFeature;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public abstract class CommandAnalyze extends ACommandWithOutput {
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
            super(_Chains.class);
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

    @Option(names = "--starting-material",
            completionCandidates = _StartingMaterialCandidates.class,
            description = "Starting material. @|bold Possible values: ${COMPLETION-CANDIDATES}|@",
            required = true)
    public _StartingMaterial startingMaterial;

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

//     @Option(names = {"--overwrite-if-required"}, description = "Overwrite output file if it is corrupted or if it was generated from different input file \" +\n" +
//             "                    \"or with different parameters. -f / --force-overwrite overrides this option.")
//     public boolean overwriteIfRequired = false;

    public String getReport() {
        if (report == null)
            return fNameForReport();
        else
            return report;
    }

    private <T extends ACommandWithOutput> T inheritOptionsAndValidate(T parameters) {
        if (forceOverwrite)
            parameters.forceOverwrite = true;
        if (parameters instanceof ACommandWithSmartOverwrite)
            ((ACommandWithSmartOverwrite) parameters).overwriteIfRequired = true;

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

    boolean forceUseShotgunOps() { return false; }

    CommandAlign mkAlign() {
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

        if (!forceUseShotgunOps() && !chains.intersects(Chains.TCR))
            alignParameters.add("-p kAligner2");
        else
            alignParameters.add("-p rna-seq"); // use always rna-seq by default

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

        switch (startingMaterial) {
            case rna:
                ap.getAlignerParameters()
                        .getVAlignerParameters()
                        .setGeneFeatureToAlign(GeneFeature.VTranscriptWithout5UTRWithP);
                break;
            case dna:
                ap.getAlignerParameters()
                        .getVAlignerParameters()
                        .setGeneFeatureToAlign(GeneFeature.VGeneWithP);
                break;
        }

        return ap;
    }

    @Option(names = "--assemblePartial",
            description = "Additional parameters for assemblePartial step specified with double quotes (e.g --assemblePartial \"--overlappedOnly\" --assemblePartial \"-OkOffset=0\" etc.",
            arity = "1")
    public List<String> assemblePartialParameters = new ArrayList<>();

    /** Build parameters for assemble partial */
    public final CommandAssemblePartialAlignments mkAssemblePartial(String input, String output) {
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
            arity = "1")
    public List<String> extendAlignmentsParameters = new ArrayList<>();

    /** Build parameters for extender */
    public final CommandExtend mkExtend(String input, String output) {
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
            arity = "1")
    public List<String> assembleParameters = new ArrayList<>();

    /** Build parameters for assemble */
    public CommandAssemble getAssemble(String input, String output) {
        return inheritOptionsAndValidate(mkAssemble(input, output));
    }

    /** Build parameters for assemble */
    CommandAssemble mkAssemble(String input, String output) {
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
            arity = "1")
    public List<String> exportParameters = new ArrayList<>();

    /** Build parameters for export */
    public final CommandExport.CommandExportClones mkExport(String input, String output) {
        List<String> exportParameters = new ArrayList<>();
        // add all override parameters
        exportParameters.addAll(this.exportParameters);

        if (exportGermline)
            exportParameters.add("-p fullImputed");
        if (onlyProductive) {
            exportParameters.add("--filter-out-of-frames");
            exportParameters.add("--filter-stops");
        }

        exportParameters.add("--force-overwrite");
        exportParameters.add("--chains");
        exportParameters.add(chains.toString());

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
        super.validate();
        if (report == null)
            warn("NOTE: report file is not specified, using " + getReport() + " to write report.");
    }

    @Override
    public void run0() throws Exception {
        // --- Running alignments
        getAlign().run();
        String fileWithAlignments = fNameForAlignments();

        // --- Running partial alignments
        for (int round = 0; round < nAssemblePartialRounds; ++round) {
            String fileWithParAlignments = fNameForParAlignments(round);
            mkAssemblePartial(fileWithAlignments, fileWithParAlignments).run();
            fileWithAlignments = fileWithParAlignments;
        }

        // --- Running alignments extender
        if (!doNotExtendAlignments) {
            String fileWithExtAlignments = fNameForExtenedAlignments();
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
                    mkExport(fileWithClones, fNameForExportClones(chain)).run();
            else
                for (String chain : new String[]{"ALL", "TRA", "TRB", "TRG", "TRD", "IGH", "IGK", "IGL"}) {
                    CommandExport.CommandExportClones export = mkExport(fileWithClones, fNameForExportClones(chain));
                    export.chains = chain;
                    export.run();
                }
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
        }

        @Override
        CommandAlign mkAlign() {
            CommandAlign align = super.mkAlign();

            VDJCAlignerParameters alignmentParameters = align.getAlignerParameters();
            alignmentParameters.getVAlignerParameters().getParameters().setFloatingLeftBound(
                    vPrimers == _5EndPrimers.vPrimers && adapters == _Adapters.adaptersPresent
            );

            alignmentParameters.getJAlignerParameters().getParameters().setFloatingRightBound(
                    jcPrimers == _3EndPrimers.jPrimers && adapters == _Adapters.adaptersPresent
            );

            alignmentParameters.getCAlignerParameters().getParameters().setFloatingRightBound(
                    jcPrimers == _3EndPrimers.cPrimers && adapters == _Adapters.adaptersPresent
            );

            return align;
        }

        @Override
        public CommandAssemble mkAssemble(String input, String output) {
            CommandAssemble assemble = super.mkAssemble(input, output);
            CloneAssemblerParameters cloneAssemblyParameters = assemble.getCloneAssemblerParameters();
            cloneAssemblyParameters.setAssemblingFeatures(new GeneFeature[]{assemblingFeature});
            cloneAssemblyParameters.updateFrom(getAlign().getAlignerParameters());
            return assemble;
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
            description = "Analyze random fragments (RNA-Seq, Exome-Seq, etc).")
    public static class CommandShotgun extends CommandAnalyze {
        public CommandShotgun() {
            chains = Chains.ALL;
            nAssemblePartialRounds = 2;
            doNotExtendAlignments = false;
        }

        @Override
        boolean forceUseShotgunOps() {
            return true;
        }

        @Override
        CommandAlign mkAlign() {
            CommandAlign align = super.mkAlign();
            VDJCAlignerParameters alignmentParameters = align.getAlignerParameters();
            alignmentParameters.getVAlignerParameters().getParameters().setFloatingLeftBound(false);
            alignmentParameters.getJAlignerParameters().getParameters().setFloatingRightBound(false);
            alignmentParameters.getCAlignerParameters().getParameters().setFloatingRightBound(false);
            return align;
        }

        @Override
        public CommandAssemble mkAssemble(String input, String output) {
            CommandAssemble assemble = super.mkAssemble(input, output);
            CloneAssemblerParameters cloneAssemblyParameters = assemble.getCloneAssemblerParameters();
            cloneAssemblyParameters.setAssemblingFeatures(new GeneFeature[]{GeneFeature.CDR3});
            cloneAssemblyParameters.updateFrom(getAlign().getAlignerParameters());
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
