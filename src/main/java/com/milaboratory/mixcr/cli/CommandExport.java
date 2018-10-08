package com.milaboratory.mixcr.cli;

import cc.redberry.pipe.OutputPort;
import cc.redberry.pipe.blocks.FilteringPort;
import cc.redberry.primitives.Filter;
import com.milaboratory.core.sequence.AminoAcidSequence;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.TranslationParameters;
import com.milaboratory.mixcr.basictypes.*;
import com.milaboratory.mixcr.export.*;
import com.milaboratory.util.CanReportProgressAndStage;
import com.milaboratory.util.SmartProgressReporter;
import io.repseq.core.Chains;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCLibraryRegistry;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParseResult;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static cc.redberry.primitives.FilterUtil.ACCEPT_ALL;
import static cc.redberry.primitives.FilterUtil.and;


public abstract class CommandExport<T extends VDJCObject> extends ACommandSimpleExport {
    public static String EXPORT_TO_STDOUT = "-";

    /** type class */
    private final Class<T> clazz;

    private CommandExport(Class<T> clazz) { this.clazz = clazz; }

    public static final String DEFAULT_PRESET = "full";

    @Option(description = "Limit export to specific chain (e.g. TRA or IGH) (fractions will be recalculated)",
            names = {"-c", "--chains"})
    public String chains = "ALL";

    @Option(description = "Specify preset of export fields (possible values: 'full', 'min'; 'full' by default)",
            names = {"-p", "--preset"})
    public String preset;

    @Option(description = "Specify preset file of export fields",
            names = {"-pf", "--preset-file"})
    public String presetFile;

    @Option(description = "List available export fields",
            names = {"-lf", "--list-fields"}, hidden = true)
    public void setListFields(boolean b) {
        throwExecutionException("-lf / --list-fields is removed in version 3.0: use help <exportCommand> for help");
    }

    @Deprecated
    @Option(description = "Output short versions of column headers which facilitates analysis with Pandas, R/DataFrames or other data tables processing library.",
            names = {"-s", "--no-spaces"}, hidden = true)
    public void setNoSpaces(boolean b) {
        warn("\"-s\" / \"--no-spaces\" option is deprecated.\nScripting friendly output format now used " +
                "by default.\nUse \"-v\" / \"--with-spaces\" to switch back to human readable format.");
    }

    @Option(description = "Output column headers with spaces.",
            names = {"-v", "--with-spaces"})
    public boolean humanReadable = false;

    public long limit = Long.MAX_VALUE;

    @Option(description = "Output only first N records",
            names = {"-n", "--limit"})
    private void setLimit(long limit) {
        if (limit <= 0)
            throwExecutionException("--limit must be positive");
        this.limit = limit;
    }

    public Chains getChains() {
        return Chains.parse(chains);
    }

    @SuppressWarnings("unchecked")
    public Filter<T> mkFilter() {
        List<Filter<T>> filters = new ArrayList<>();

        final Chains chains = getChains();
        filters.add(object -> {
            for (GeneType gt : GeneType.VJC_REFERENCE) {
                VDJCHit bestHit = object.getBestHit(gt);
                if (bestHit != null && chains.intersects(bestHit.getGene().getChains()))
                    return true;
            }
            return false;
        });

        if (filters.isEmpty())
            return ACCEPT_ALL;

        if (filters.size() == 1)
            return filters.get(0);

        return and(filters.toArray(new Filter[filters.size()]));
    }

    /** auto-generated opts (exporters) injected manually */
    private CommandSpec spec;

    @Override
    public void run0() throws Exception {
        assert spec != null;

        List<FieldData> fields = new ArrayList<>();

        //if preset was explicitly specified
        if (preset != null)
            fields.addAll(presets.get(clazz).get(preset));

        if (presetFile != null)
            fields.addAll(parseFile(presetFile));

        fields.addAll(parseSpec(spec.commandLine().getParseResult()));

        // if no options specified
        if (fields.isEmpty())
            fields.addAll(presets.get(clazz).get(DEFAULT_PRESET));

        OutputMode oMode = humanReadable ? OutputMode.HumanFriendly : OutputMode.ScriptingFriendly;
        List<FieldExtractor<? super T>> extractors = fields
                .stream()
                .map(f -> extractor(f, clazz, oMode))
                .collect(Collectors.toList());

        run1(extractors);
    }

    abstract void run1(List<FieldExtractor<? super T>> extractors) throws Exception;

    @Command(name = "exportAlignments",
            separator = " ",
            sortOptions = true,
            description = "Export V/D/J/C alignments into tab delimited file.")
    public static class CommandExportAlignments extends CommandExport<VDJCAlignments> {
        public CommandExportAlignments() {
            super(VDJCAlignments.class);
        }

        @Override
        void run1(List<FieldExtractor<? super VDJCAlignments>> exporters) throws Exception {
            try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(in, VDJCLibraryRegistry.getDefault());
                 InfoWriter<VDJCAlignments> writer = new InfoWriter<>(out)) {
                SmartProgressReporter.startProgressReport("Exporting alignments", reader, System.err);
                writer.attachInfoProviders(exporters);
                writer.ensureHeader();
                VDJCAlignments alignments;
                long count = 0;
                OutputPort<VDJCAlignments> alignmentsPort = new FilteringPort<>(reader, mkFilter());
                while ((alignments = alignmentsPort.take()) != null && count < limit) {
                    writer.put(alignments);
                    ++count;
                }
            }
        }
    }

    @Command(name = "exportClones",
            separator = " ",
            sortOptions = true,
            description = "Export assembled clones into tab delimited file.")
    public static class CommandExportClones extends CommandExport<Clone> {
        @Option(description = "Exclude clones with out-of-frame clone sequences (fractions will be recalculated)",
                names = {"-o", "--filter-out-of-frames"})
        public boolean filterOutOfFrames = false;

        @Option(description = "Exclude sequences containing stop codons (fractions will be recalculated)",
                names = {"-t", "--filter-stops"})
        public boolean filterStops = false;

        @Option(description = "Filter clones by minimal clone fraction",
                names = {"-q", "--minimal-clone-fraction"})
        public float minFraction = 0;

        @Option(description = "Filter clones by minimal clone read count",
                names = {"-m", "--minimal-clone-count"})
        public long minCount = 0;

        public CommandExportClones() {
            super(Clone.class);
        }

        @Override
        public Filter<Clone> mkFilter() {
            final Filter<Clone> superFilter = super.mkFilter();
            final CFilter cFilter = new CFilter(filterOutOfFrames, filterStops);
            return object -> superFilter.accept(object) && cFilter.accept(object);
        }

        @Override
        void run1(List<FieldExtractor<? super Clone>> exporters) throws Exception {
            try (InfoWriter<Clone> writer = new InfoWriter<>(out)) {
                CloneSet set = CloneSetIO.read(in, VDJCLibraryRegistry.getDefault());

                set = CloneSet.transform(set, mkFilter());

                writer.attachInfoProviders(exporters);
                writer.ensureHeader();
                for (int i = 0; i < set.size(); i++) {
                    if (set.get(i).getFraction() < minFraction ||
                            set.get(i).getCount() < minCount) {
                        limit = i;
                        break;
                    }
                }
                ExportClones exportClones = new ExportClones(set, writer, limit);
                SmartProgressReporter.startProgressReport(exportClones, System.err);
                exportClones.run();
            }
        }

        public static final class CFilter implements Filter<Clone> {
            final boolean filterOutOfFrames, filterStopCodons;

            public CFilter(boolean filterOutOfFrames, boolean filterStopCodons) {
                this.filterOutOfFrames = filterOutOfFrames;
                this.filterStopCodons = filterStopCodons;
            }

            @Override
            public boolean accept(Clone clone) {
                if (filterOutOfFrames) {
                    NSequenceWithQuality cdr3 = clone.getFeature(GeneFeature.CDR3);
                    if (cdr3 == null || cdr3.size() % 3 != 0)
                        return false;
                }

                if (filterStopCodons) {
                    for (GeneFeature assemblingFeature : clone.getParentCloneSet().getAssemblingFeatures()) {
                        GeneFeature codingFeature = GeneFeature.getCodingGeneFeature(assemblingFeature);
                        if (codingFeature == null)
                            continue;

                        for (int i = 0; i < clone.numberOfTargets(); ++i) {
                            NSequenceWithQuality codingSeq = clone.getPartitionedTarget(i).getFeature(codingFeature);
                            if (codingSeq == null)
                                continue;
                            TranslationParameters tr = clone.getPartitionedTarget(i).getPartitioning().getTranslationParameters(codingFeature);
                            if (tr == null)
                                return false;
                            if (AminoAcidSequence.translate(codingSeq.getSequence(), tr).containStops())
                                return false;
                        }
                    }
                }

                return true;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (!(o instanceof CFilter)) return false;
                CFilter cFilter = (CFilter) o;
                return filterOutOfFrames == cFilter.filterOutOfFrames &&
                        filterStopCodons == cFilter.filterStopCodons;
            }

            @Override
            public int hashCode() {
                return Objects.hash(filterOutOfFrames, filterStopCodons);
            }
        }

        public static final class ExportClones implements CanReportProgressAndStage {
            final static String stage = "Exporting clones";
            final CloneSet clones;
            final InfoWriter<Clone> writer;
            final long size;
            volatile long current = 0;
            final long limit;

            private ExportClones(CloneSet clones, InfoWriter<Clone> writer, long limit) {
                this.clones = clones;
                this.writer = writer;
                this.size = clones.size();
                this.limit = limit;
            }

            @Override
            public String getStage() {
                return stage;
            }

            @Override
            public double getProgress() {
                return (1.0 * current) / size;
            }

            @Override
            public boolean isFinished() {
                return current == size;
            }

            void run() {
                for (Clone clone : clones.getClones()) {
                    if (current == limit)
                        break;
                    writer.put(clone);
                    ++current;
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    FieldExtractor<T> extractor(FieldData fd, Class clazz, OutputMode m) {
        for (Field f : FieldExtractors.getFields()) {
            if (fd.field.equalsIgnoreCase(f.getCommand()) && f.canExtractFrom(clazz))
                return f.create(m, fd.args);
        }
        throwValidationException("illegal field: " + fd.field);
        return null;
    }

    private static final class FieldData {
        final String field;
        final String[] args;

        FieldData(String field, String[] args) {
            this.field = field;
            this.args = args;
        }

        static FieldData mk(String... args) {
            return new FieldData(args[0], Arrays.copyOfRange(args, 1, args.length));
        }
    }

    public static List<FieldData> parseFile(String file) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            List<FieldData> r = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                line = line.replace("\"", "");
                r.add(FieldData.mk(line.split(" ")));
            }
            return r;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<FieldData> parseSpec(ParseResult parseResult) {
        List<FieldData> r = new ArrayList<>();
        for (OptionSpec opt : parseResult.matchedOptions()) {
            if (!FieldExtractors.hasField(opt.names()[0]))
                continue;
            r.add(new FieldData(opt.names()[0], opt.originalStringValues().toArray(new String[opt.originalStringValues().size()])));
        }
        return r;
    }

    private static final Map<Class, Map<String, List<FieldData>>> presets;

    static {
        presets = new HashMap<>();
        Map<String, List<FieldData>> alignments = new HashMap<>();
        alignments.put("min", Arrays.asList(
                FieldData.mk("-vHit"),
                FieldData.mk("-dHit"),
                FieldData.mk("-jHit"),
                FieldData.mk("-cHit"),
                FieldData.mk("-nFeature", "CDR3")
        ));

        alignments.put("full", Arrays.asList(
                FieldData.mk("-sequence"),
                FieldData.mk("-quality"),
                FieldData.mk("-vHitsWithScore"),
                FieldData.mk("-dHitsWithScore"),
                FieldData.mk("-jHitsWithScore"),
                FieldData.mk("-cHitsWithScore"),
                FieldData.mk("-vAlignments"),
                FieldData.mk("-dAlignments"),
                FieldData.mk("-jAlignments"),
                FieldData.mk("-cAlignments"),
                FieldData.mk("-nFeature", "FR1"),
                FieldData.mk("-minFeatureQuality", "FR1"),
                FieldData.mk("-nFeature", "CDR1"),
                FieldData.mk("-minFeatureQuality", "CDR1"),
                FieldData.mk("-nFeature", "FR2"),
                FieldData.mk("-minFeatureQuality", "FR2"),
                FieldData.mk("-nFeature", "CDR2"),
                FieldData.mk("-minFeatureQuality", "CDR2"),
                FieldData.mk("-nFeature", "FR3"),
                FieldData.mk("-minFeatureQuality", "FR3"),
                FieldData.mk("-nFeature", "CDR3"),
                FieldData.mk("-minFeatureQuality", "CDR3"),
                FieldData.mk("-nFeature", "FR4"),
                FieldData.mk("-minFeatureQuality", "FR4"),
                FieldData.mk("-aaFeature", "FR1"),
                FieldData.mk("-aaFeature", "CDR1"),
                FieldData.mk("-aaFeature", "FR2"),
                FieldData.mk("-aaFeature", "CDR2"),
                FieldData.mk("-aaFeature", "FR3"),
                FieldData.mk("-aaFeature", "CDR3"),
                FieldData.mk("-aaFeature", "FR4"),
                FieldData.mk("-defaultAnchorPoints")));

        alignments.put("fullImputed", alignments.get("full").stream().map(p -> {
            switch (p.field) {
                case "-nFeature": return new FieldData("-nFeatureImputed", p.args);
                case "-aaFeature": return new FieldData("-aaFeatureImputed", p.args);
                default: return p;
            }
        }).collect(Collectors.toList()));

        presets.put(VDJCAlignments.class, alignments);

        Map<String, List<FieldData>> clones = new HashMap<>();
        clones.put("min", Arrays.asList(
                FieldData.mk("-count"),
                FieldData.mk("-vHit"),
                FieldData.mk("-dHit"),
                FieldData.mk("-jHit"),
                FieldData.mk("-cHit"),
                FieldData.mk("-nFeature", "CDR3")
        ));

        clones.put("fullNoId", new ArrayList<FieldData>(alignments.get("full")) {{
            add(0, FieldData.mk("-count"));
            add(1, FieldData.mk("-fraction"));
        }});

        clones.put("fullNoIdImputed", new ArrayList<FieldData>(alignments.get("fullImputed")) {{
            add(0, FieldData.mk("-count"));
            add(1, FieldData.mk("-fraction"));
        }});

        clones.put("full", new ArrayList<FieldData>(clones.get("fullNoId")) {{
            add(0, FieldData.mk("-cloneId"));
        }});

        clones.put("fullImputed", new ArrayList<FieldData>(clones.get("fullNoIdImputed")) {{
            add(0, FieldData.mk("-cloneId"));
        }});
        presets.put(Clone.class, clones);
    }

    /**
     * Creates command spec for given type (Clone / VDJAlignments)
     */
    public static <T extends VDJCObject> CommandSpec mkCommandSpec(CommandExport<T> export) {
        CommandSpec spec = CommandSpec.forAnnotatedObject(export);
        export.spec = spec; // inject spec manually
        for (Field field : FieldExtractors.getFields()) {
            if (!field.canExtractFrom(export.clazz))
                continue;
            spec.addOption(OptionSpec
                    .builder(field.getCommand())
                    .description(field.getDescription())
                    .required(false)
                    .arity(String.valueOf(field.nArguments()))
                    .descriptionKey(field.getCommand() + " " + field.metaVars())
                    .build());
        }
        return spec;
    }

    /**
     * Creates command spec for given type VDJAlignments
     */
    public static CommandSpec mkAlignmentsSpec() {
        return mkCommandSpec(new CommandExportAlignments());
    }

    /**
     * Creates command spec for given type VDJAlignments
     */
    public static CommandSpec mkClonesSpec() {
        return mkCommandSpec(new CommandExportClones());
    }
}
