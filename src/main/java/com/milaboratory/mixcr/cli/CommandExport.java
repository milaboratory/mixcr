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

import cc.redberry.pipe.OutputPort;
import cc.redberry.pipe.OutputPortCloseable;
import cc.redberry.pipe.blocks.FilteringPort;
import cc.redberry.primitives.Filter;
import com.milaboratory.mixcr.basictypes.*;
import com.milaboratory.mixcr.basictypes.tag.TagCount;
import com.milaboratory.mixcr.basictypes.tag.TagsInfo;
import com.milaboratory.mixcr.export.*;
import com.milaboratory.mixcr.util.Concurrency;
import com.milaboratory.util.CanReportProgress;
import com.milaboratory.util.CanReportProgressAndStage;
import com.milaboratory.util.ReportHelper;
import com.milaboratory.util.SmartProgressReporter;
import io.repseq.core.Chains;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCLibraryRegistry;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParseResult;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static cc.redberry.primitives.FilterUtil.ACCEPT_ALL;
import static cc.redberry.primitives.FilterUtil.and;


@Command(separator = " ")
public abstract class CommandExport<T extends VDJCObject> extends MiXCRCommand {
    /** type class */
    private final Class<T> clazz;

    private CommandExport(Class<T> clazz) {
        this.clazz = clazz;
    }

    public static final String DEFAULT_PRESET = "full";

    @Parameters(description = "data.[vdjca|clns|clna]", index = "0")
    public String in;

    @Parameters(description = "table.tsv", index = "1", arity = "0..1")
    public String out = null;

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

    @Override
    protected List<String> getInputFiles() {
        return Collections.singletonList(in);
    }

    @Override
    protected List<String> getOutputFiles() {
        return out == null ? Collections.emptyList() : Collections.singletonList(out);
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

        run1(fields, humanReadable ? OutputMode.HumanFriendly : OutputMode.ScriptingFriendly);
    }

    abstract void run1(List<FieldData> fields, OutputMode oMode) throws Exception;

    @Command(name = "exportAlignments",
            separator = " ",
            sortOptions = true,
            description = "Export V/D/J/C alignments into tab delimited file.")
    public static class CommandExportAlignments extends CommandExport<VDJCAlignments> {
        public CommandExportAlignments() {
            super(VDJCAlignments.class);
        }

        @Override
        void run1(List<FieldData> fields, OutputMode oMode) throws Exception {
            try (AlignmentsAndHeader readerAndHeader = openAlignmentsPort(in);
                 InfoWriter<VDJCAlignments> writer = new InfoWriter<>(out)) {
                OutputPortCloseable<VDJCAlignments> reader = readerAndHeader.port;
                List<FieldExtractor<? super VDJCAlignments>> exporters = fields
                        .stream()
                        .flatMap(f -> extractor(f, VDJCAlignments.class, readerAndHeader.header, oMode).stream())
                        .collect(Collectors.toList());
                if (reader instanceof CanReportProgress)
                    SmartProgressReporter.startProgressReport("Exporting alignments", (CanReportProgress) reader, System.err);
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

        @Option(description = "Split clones by tag values",
                names = {"--split-by-tag"})
        public String splitByTag;

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
        void run1(List<FieldData> fields, OutputMode oMode) throws Exception {
            try (InfoWriter<Clone> writer = new InfoWriter<>(out)) {
                CloneSet initialSet = CloneSetIO.read(in, VDJCLibraryRegistry.getDefault());
                CloneSet set = CloneSet.transform(initialSet, mkFilter());
                List<FieldExtractor<? super Clone>> exporters = fields
                        .stream()
                        .flatMap(f -> extractor(f, Clone.class, initialSet, oMode).stream())
                        .collect(Collectors.toList());

                writer.attachInfoProviders(exporters);
                writer.ensureHeader();
                for (int i = 0; i < set.size(); i++) {
                    if (set.get(i).getFraction() < minFraction ||
                            set.get(i).getCount() < minCount) {
                        limit = i;
                        break;
                    }
                }
                TagsInfo tagsInfo = set.getTagsInfo();
                ExportClones exportClones = new ExportClones(
                        set, writer, limit,
                        splitByTag == null ? 0 : (tagsInfo.indexOf(splitByTag) + 1)
                );
                SmartProgressReporter.startProgressReport(exportClones, System.err);
                exportClones.run();
                if (initialSet.size() > set.size()) {
                    double
                            initialCount = initialSet.getClones().stream().mapToDouble(Clone::getCount).sum(),
                            count = set.getClones().stream().mapToDouble(Clone::getCount).sum();
                    int di = initialSet.size() - set.size();
                    double cdi = initialCount - count;
                    warn("Filtered " + set.size() + " of " + initialSet.size() + " clones (" + ReportHelper.PERCENT_FORMAT.format(100.0 * di / initialSet.size()) + "%).");
                    warn("Filtered " + count + " of " + initialCount + " reads (" + ReportHelper.PERCENT_FORMAT.format(100.0 * cdi / initialCount) + "%).");
                }
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
                    if (clone.isOutOfFrameOrAbsent(GeneFeature.CDR3))
                        return false;
                }

                if (filterStopCodons)
                    for (GeneFeature assemblingFeature : clone.getParentCloneSet().getAssemblingFeatures())
                        if (clone.containsStopsOrAbsent(assemblingFeature))
                            return false;

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
            final int splitByLevel;

            private ExportClones(CloneSet clones, InfoWriter<Clone> writer, long limit, int splitByLevel) {
                this.clones = clones;
                this.writer = writer;
                this.size = clones.size();
                this.limit = limit;
                this.splitByLevel = splitByLevel;
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

                    Stream<Clone> stream = Stream.of(clone);

                    if (splitByLevel > 0) {
                        stream = stream.flatMap(cl -> {
                            TagCount tagCount = cl.getTagCount();
                            double sum = tagCount.sum();
                            return Arrays.stream(tagCount.splitBy(splitByLevel))
                                    .map(tc -> new Clone(clone.getTargets(), clone.getHits(),
                                            tc, 1.0 * cl.getCount() * tc.sum() / sum, clone.getId(), clone.getGroup()));
                        });
                    }
                    stream.forEach(writer::put);

                    ++current;
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static <E> List<FieldExtractor<E>> extractor(FieldData fd, Class<E> clazz, VDJCFileHeaderData header, OutputMode m) {
        for (Field f : FieldExtractors.getFields()) {
            if (fd.field.equalsIgnoreCase(f.getCommand()) && f.canExtractFrom(clazz)) {
                if (f.nArguments() == 0) {
                    if (!(fd.args.length == 0 || (
                            fd.args.length == 1 && (fd.args[0].equalsIgnoreCase("true") || fd.args[0].equalsIgnoreCase("false")))))
                        throw new RuntimeException();
                    return Collections.singletonList(f.create(m, header, new String[0]));
                } else {
                    int i = 0;
                    ArrayList<FieldExtractor<E>> extractors = new ArrayList<>();
                    while (i < fd.args.length) {
                        extractors.add(f.create(m, header, Arrays.copyOfRange(fd.args, i, i + f.nArguments())));
                        i += f.nArguments();
                    }
                    return extractors;
                }
            }
        }
        throw new IllegalArgumentException("illegal field: " + fd.field);
    }

    public static final class FieldData {
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

            int arity = opt.arity().min();
            String[] actualValue = new String[0];
            if (arity > 0) {
                String[] value = opt.getValue();
                actualValue = Arrays.copyOf(value, arity);
                opt.setValue(Arrays.copyOfRange(value, arity, value.length));
            }
            r.add(new FieldData(opt.names()[0], actualValue));
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
                FieldData.mk("-targetSequences"),
                FieldData.mk("-targetQualities"),
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
                case "-nFeature":
                    return new FieldData("-nFeatureImputed", p.args);
                case "-aaFeature":
                    return new FieldData("-aaFeatureImputed", p.args);
                default:
                    return p;
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
        addOptionsToSpec(spec, export.clazz);
        return spec;
    }

    public static void addOptionsToSpec(CommandSpec spec, Class<?> clazz) {
        for (Field<?> field : FieldExtractors.getFields()) {
            if (!field.canExtractFrom(clazz))
                continue;
            spec.addOption(OptionSpec
                    .builder(field.getCommand())
                    .description(field.getDescription())
                    .required(false)
                    .type(field.nArguments() > 0 ? String[].class : boolean.class)
                    .arity(String.valueOf(field.nArguments()))
                    .descriptionKey(field.getCommand() + " " + field.metaVars())
                    .build());
        }
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

    public interface OPAWithReport extends OutputPortCloseable<VDJCAlignments>, CanReportProgress {
    }

    public static class AlignmentsAndHeader implements AutoCloseable {
        public final OutputPortCloseable<VDJCAlignments> port;
        public final VDJCFileHeaderData header;

        public AlignmentsAndHeader(OutputPortCloseable<VDJCAlignments> port, VDJCFileHeaderData header) {
            this.port = port;
            this.header = header;
        }

        @Override
        public void close() {
            port.close();
        }
    }

    public static AlignmentsAndHeader openAlignmentsPort(String in) {
        try {
            switch (IOUtil.extractFileType(Paths.get(in))) {
                case VDJCA:
                    VDJCAlignmentsReader vdjcaReader = null;
                    vdjcaReader = new VDJCAlignmentsReader(in, VDJCLibraryRegistry.getDefault());
                    return new AlignmentsAndHeader(vdjcaReader, vdjcaReader);
                case CLNA:
                    ClnAReader clnaReader = new ClnAReader(in, VDJCLibraryRegistry.getDefault(), Concurrency.noMoreThan(4));
                    OutputPortCloseable<VDJCAlignments> source = clnaReader.readAllAlignments();
                    return new AlignmentsAndHeader(
                            new OutputPortCloseable<VDJCAlignments>() {
                                @Override
                                public void close() {
                                    try {
                                        source.close();
                                        clnaReader.close();
                                    } catch (IOException e) {
                                        throw new RuntimeException(e);
                                    }
                                }

                                @Override
                                public VDJCAlignments take() {
                                    return source.take();
                                }
                            }, clnaReader);
                case CLNS:
                    throw new RuntimeException("Can't export alignments from *.clns file: " + in);
                default:
                    throw new RuntimeException();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
