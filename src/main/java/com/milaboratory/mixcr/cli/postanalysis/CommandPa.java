package com.milaboratory.mixcr.cli.postanalysis;

import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.cli.ACommandWithOutputMiXCR;
import com.milaboratory.mixcr.postanalysis.SetPreprocessorFactory;
import com.milaboratory.mixcr.postanalysis.WeightFunctions;
import com.milaboratory.mixcr.postanalysis.downsampling.ClonesDownsamplingPreprocessorFactory;
import com.milaboratory.mixcr.postanalysis.downsampling.DownsampleValueChooser;
import com.milaboratory.mixcr.postanalysis.preproc.ElementPredicate;
import com.milaboratory.mixcr.postanalysis.preproc.NoPreprocessing;
import com.milaboratory.mixcr.postanalysis.preproc.SelectTop;
import com.milaboratory.util.StringUtil;
import io.repseq.core.Chains;
import io.repseq.core.GeneFeature;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

import static io.repseq.core.Chains.*;
import static java.util.stream.Collectors.toList;

/**
 *
 */
public abstract class CommandPa extends ACommandWithOutputMiXCR {
    public static final NamedChains[] CHAINS = {TRAD_NAMED, TRB_NAMED, TRG_NAMED, IGH_NAMED, IGKL_NAMED};

    @Parameters(description = "cloneset.{clns|clna}... result.json.gz|result.json")
    public List<String> inOut;

    @Option(description = "Use only productive CDR3s.",
            names = {"--only-productive"})
    public boolean onlyProductive = false;

    @Option(description = "Choose downsampling. Possible values: umi-count-[1000|auto]|cumulative-top-[percent]|top-[number]|no-downsampling",
            names = {"--downsampling"},
            required = true)
    public String downsampling;

    @Option(description = "Filter specific chains",
            names = {"-c", "--chains"})
    public String chains = "ALL";

    @Option(description = "Metadata file (csv/tsv). Must have \"sample\" column.",
            names = {"-m", "--meta", "--metadata"})
    public String metadata;
//
//    @Option(description = "Metadata column for chains.",
//            names = {"--chains-column"})
//    public String chainsColumn;

    @Option(description = "Metadata categories used to isolate samples into separate groups",
            names = {"-g", "--group"})
    public List<String> isolationGroups;

    @Option(description = "Tabular results output path (path/table.tsv).",
            names = {"--tables"})
    public String tablesOut;

    @Option(description = "Preprocessor summary output path.",
            names = {"--preproc-tables"})
    public String preprocOut;

    @Override
    protected List<String> getInputFiles() {
        return inOut.subList(0, inOut.size() - 1)
                .stream()
                .flatMap(f -> {
                    if (Files.isDirectory(Paths.get(f))) {
                        try {
                            return Files
                                    .list(Paths.get(f))
                                    .map(Path::toString);
                        } catch (IOException ignored) {
                        }
                    }
                    return Stream.of(f);
                })
                .collect(toList());
    }

    @Override
    protected List<String> getOutputFiles() {
        return Collections.singletonList(out());
    }

    @Override
    public void validate() {
        super.validate();
        if (metadata != null && !metadata.endsWith(".csv") && !metadata.endsWith(".tsv"))
            throwValidationException("Metadata should be .csv or .tsv");
        if (!out().endsWith(".json") && !out().endsWith(".json.gz"))
            throwValidationException("Output file name should ends with .json.gz or .json");
    }

    private String outBase() {
        String out = out();
        if (out.endsWith(".json.gz"))
            return out.substring(0, out.length() - 8);
        else if (out.endsWith(".json"))
            return out.substring(0, out.length() - 5);
        else
            throw new IllegalArgumentException("output extension is illegal");
    }

    private String out() {
        return inOut.get(inOut.size() - 1);
    }

    private String tablesOut() {
        return tablesOut == null ? outBase() + ".tsv" : tablesOut;
    }

    private String preprocOut() {
        return preprocOut == null ? outBase() + ".preproc.tsv" : preprocOut;
    }

    private Path outputPath() {
        return Paths.get(out()).toAbsolutePath();
    }

    /** Get sample id from file name */
    static String getSampleId(String file) {
        return Paths.get(file).getFileName().toString();
    }

    private static int downsamplingValue(String downsampling) {
        return Integer.parseInt(downsampling.substring(downsampling.lastIndexOf("-") + 1));
    }

    private static SetPreprocessorFactory<Clone> parseDownsampling(String downsampling) {
        if (downsampling.equalsIgnoreCase("no-downsampling")) {
            return new NoPreprocessing.Factory<>();
        } else if (downsampling.startsWith("umi-count")) {
            if (downsampling.endsWith("auto"))
                return new ClonesDownsamplingPreprocessorFactory(new DownsampleValueChooser.Auto(), 314);
            else {
                return new ClonesDownsamplingPreprocessorFactory(new DownsampleValueChooser.Fixed(downsamplingValue(downsampling)), 314);
            }
        } else {
            int value = downsamplingValue(downsampling);
            if (downsampling.startsWith("cumulative-top")) {
                return new SelectTop.Factory<>(WeightFunctions.Count, 1.0 * value / 100.0);
            } else if (downsampling.startsWith("top")) {
                return new SelectTop.Factory<>(WeightFunctions.Count, value);
            } else {
                throw new IllegalArgumentException("Illegal downsampling string: " + downsampling);
            }
        }
    }

    protected SetPreprocessorFactory<Clone> downsampling() {
        return downsampling(this.downsampling);
    }

    protected SetPreprocessorFactory<Clone> downsampling(String downsamplingStr) {
        SetPreprocessorFactory<Clone> downsampling =
                parseDownsampling(downsamplingStr);

        if (onlyProductive) {
            List<ElementPredicate<Clone>> filters = new ArrayList<>();
            filters.add(new ElementPredicate.NoStops(GeneFeature.CDR3));
            filters.add(new ElementPredicate.NoOutOfFrames(GeneFeature.CDR3));
            downsampling = downsampling.filterFirst(filters);
        }

        return downsampling;
    }

    private Map<String, List<Object>> _metadata = null;

    protected Map<String, List<Object>> metadata() {
        if (metadata == null)
            return null;
        if (_metadata != null)
            return _metadata;
        List<String> content;
        try {
            content = Files.readAllLines(Paths.get(metadata).toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (content.isEmpty())
            return null;
        String sep = metadata.endsWith(".csv") ? "," : "\t";

        Map<String, List<Object>> result = new HashMap<>();
        String[] header = content.get(0).split(sep);
        for (int iRow = 1; iRow < content.size(); iRow++) {
            String[] row = content.get(iRow).split(sep);
            for (int iCol = 0; iCol < row.length; iCol++) {
                result.computeIfAbsent(header[iCol], __ -> new ArrayList<>()).add(row[iCol]);
            }
        }

        for (Map.Entry<String, List<Object>> e : result.entrySet()) {
            if (e.getValue().stream().anyMatch(n -> !StringUtil.isNumber((String) n)))
                continue;

            // set to double
            e.setValue(e.getValue().stream().map(s -> Double.parseDouble((String) s)).collect(toList()));
        }

        return _metadata = result;
    }

    private static class SamplesGroup {
        /** sample names */
        final List<String> samples;
        /** metadata category = value */
        final Map<String, Object> group;

        public SamplesGroup(List<String> samples, Map<String, Object> group) {
            this.samples = samples;
            this.group = group;
        }
    }

    private static final String[] CHAINS_COLUMN_NAMES = {"chain", "chains"};

    private String chainsColumn() {
        Map<String, List<Object>> metadata = metadata();
        if (metadata == null)
            return null;
        return metadata.keySet().stream().filter(
                col -> Arrays.stream(CHAINS_COLUMN_NAMES).anyMatch(col::equalsIgnoreCase)
        ).findFirst().orElse(null);
    }

    /** group samples into isolated groups */
    protected List<SamplesGroup> groupSamples() {
        String chainsColumn = chainsColumn();
        if ((isolationGroups == null || isolationGroups.isEmpty()) && chainsColumn == null) {
            return Collections.singletonList(new SamplesGroup(getInputFiles(), Collections.emptyMap()));
        }

        Map<String, List<Object>> metadata = metadata();
        @SuppressWarnings({"unchecked", "rawtypes"})
        List<String> mSamples = (List) metadata.get("sample");
        List<String> qSamples = getInputFiles();

        Map<String, String> sample2meta = StringUtil.matchLists(qSamples, mSamples);
        for (Map.Entry<String, String> e : sample2meta.entrySet()) {
            if (e.getValue() == null)
                throw new IllegalArgumentException("Malformed metadata: can't find metadata row for sample " + e.getKey());
        }
        Map<String, String> meta2sample = new HashMap<>();
        for (Map.Entry<String, String> e : sample2meta.entrySet()) {
            meta2sample.put(e.getValue(), e.getKey());
        }

        int nRows = metadata.entrySet().iterator().next().getValue().size();
        Map<Map<String, Object>, List<String>> samplesByGroup = new HashMap<>();
        for (int i = 0; i < nRows; i++) {
            Map<String, Object> group = new HashMap<>();
            for (String igr : isolationGroups == null ? Collections.singletonList(chainsColumn) : isolationGroups) {
                group.put(igr, metadata.get(igr).get(i));
            }
            if (chainsColumn != null)
                group.put(chainsColumn, metadata.get(chainsColumn).get(i));

            String sample = (String) metadata.get("sample").get(i);
            samplesByGroup.computeIfAbsent(group, __ -> new ArrayList<>())
                    .add(meta2sample.get(sample));
        }

        return samplesByGroup.entrySet().stream().map(e ->
                        new SamplesGroup(e.getValue(), e.getKey()))
                .collect(toList());
    }

    @Override
    public void run0() throws Exception {
        List<PaResultByGroup> results = new ArrayList<>();
        Chains c = Chains.parse(chains);
        String chainsColumn = chainsColumn();
        for (SamplesGroup group : groupSamples()) {
            if (chainsColumn != null)
                results.add(run(new IsolationGroup(
                        Chains.getNamedChains(group.group.get(chainsColumn).toString()), group.group), group.samples));
            else
                for (NamedChains knownChains : CHAINS) {
                    if (c.intersects(knownChains.chains)) {
                        results.add(run(new IsolationGroup(knownChains, group.group), group.samples));
                    }
                }
        }
        PaResult result = new PaResult(metadata(), isolationGroups, results);
        try {
            Files.createDirectories(outputPath().getParent());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        PaResult.writeJson(outputPath(), result);

        // export tables & preprocessing summary
        new CommandPaExportTables(result, tablesOut()).run0();
        new CommandPaExportTablesPreprocSummary(result, preprocOut()).run0();
    }

    abstract PaResultByGroup run(IsolationGroup group, List<String> samples);

    @CommandLine.Command(name = "postanalysis",
            separator = " ",
            description = "Run postanalysis routines.",
            subcommands = {
                    CommandLine.HelpCommand.class
            })
    public static class CommandPostanalysisMain {}
}
