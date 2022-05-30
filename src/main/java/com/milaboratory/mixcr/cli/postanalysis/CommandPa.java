package com.milaboratory.mixcr.cli.postanalysis;

import com.milaboratory.mixcr.cli.ACommandWithOutputMiXCR;
import com.milaboratory.mixcr.cli.CommonDescriptions;
import com.milaboratory.mixcr.postanalysis.downsampling.DownsamplingUtil;
import com.milaboratory.util.StringUtil;
import io.repseq.core.Chains;
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

    @Option(description = CommonDescriptions.ONLY_PRODUCTIVE,
            names = {"--only-productive"})
    public boolean onlyProductive = false;

    @Option(description = CommonDescriptions.DOWNSAMPLING_DROPO_UTLIERS,
            names = {"--drop-outliers"})
    public boolean dropOutliers = false;

    @Option(description = CommonDescriptions.DOWNSAMPLING,
            names = {"--default-downsampling"},
            required = true)
    public String defaultDownsampling;

    @Option(description = "Filter specified chains",
            names = {"--chains"})
    public String chains = "ALL";

    @Option(description = CommonDescriptions.METADATA,
            names = {"--metadata"})
    public String metadata;

    @Option(description = "Metadata categories used to isolate samples into separate groups",
            names = {"--group"})
    public List<String> isolationGroups;

    @Option(description = "Tabular results output path (path/table.tsv).",
            names = {"--tables"})
    public String tablesOut;

    @Option(description = "Preprocessor summary output path.",
            names = {"--preproc-tables"})
    public String preprocOut;

    @Option(names = {"-O"}, description = "Overrides default postanalysis settings")
    public Map<String, String> overrides = new HashMap<>();

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
        if (!out().endsWith(".json") && !out().endsWith(".json.gz"))
            throwValidationException("Output file name should ends with .json.gz or .json");
        try {
            DownsamplingUtil.parseDownsampling(defaultDownsampling, dropOutliers, 0);
        } catch (Throwable t) {
            throwValidationException("Illegal downsampling string: " + defaultDownsampling);
        }
        if (metadata != null && !metadata.endsWith(".csv") && !metadata.endsWith(".tsv"))
            throwValidationException("Metadata should be .csv or .tsv");
        if (metadata != null) {
            if (!metadata().containsKey("sample"))
                throwValidationException("Metadata must contain 'sample' column");
            List<String> samples = getInputFiles();
            Map<String, String> mapping = StringUtil.matchLists(
                    samples,
                    metadata().get("sample").stream()
                            .map(Object::toString).collect(toList())
            );
            if (mapping.size() < samples.size() || mapping.values().stream().anyMatch(Objects::isNull))
                throwValidationException("Metadata samples does not match input file names.");
        }
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
                        Chains.getNamedChains(group.group.get(chainsColumn).toString().toUpperCase()), group.group), group.samples));
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
