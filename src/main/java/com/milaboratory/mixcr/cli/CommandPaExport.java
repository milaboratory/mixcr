package com.milaboratory.mixcr.cli;

import com.milaboratory.miplots.ExportKt;
import com.milaboratory.miplots.Position;
import com.milaboratory.miplots.stat.util.TestMethod;
import com.milaboratory.miplots.stat.xcontinious.CorrelationMethod;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.cli.CommandPa.PaResult;
import com.milaboratory.mixcr.cli.CommandPa.PaResultByChain;
import com.milaboratory.mixcr.postanalysis.SetPreprocessorSummary;
import com.milaboratory.mixcr.postanalysis.plots.*;
import com.milaboratory.mixcr.postanalysis.ui.CharacteristicGroup;
import com.milaboratory.mixcr.postanalysis.ui.CharacteristicGroupOutputExtractor;
import com.milaboratory.mixcr.postanalysis.ui.CharacteristicGroupResult;
import com.milaboratory.mixcr.postanalysis.ui.OutputTable;
import com.milaboratory.util.GlobalObjectMappers;
import io.repseq.core.Chains;
import jetbrains.letsPlot.intern.Plot;
import org.jetbrains.kotlinx.dataframe.DataFrame;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 *
 */
public abstract class CommandPaExport extends ACommandWithOutputMiXCR {
    @Option(names = {"-m", "--meta", "--metadata"}, description = "Metadata file")
    public String metadata;
    @Parameters(index = "0", description = "pa_result.json")
    public String in;
    @Option(names = {"--chains"}, description = "Export for specific chains only")
    public List<String> chains;

    /**
     * Cached PA result
     */
    private PaResult paResult = null;

    /**
     * Get full PA result
     */
    PaResult getPaResult() {
        try {
            if (paResult != null)
                return paResult;
            return paResult = GlobalObjectMappers.PRETTY.readValue(new File(in), PaResult.class);
        } catch (IOException e) {
            throwValidationException("Broken input file: " + in);
            return null;
        }
    }

    @Override
    public void run0() throws Exception {
        Set<Chains.NamedChains> set = chains == null
                ? null
                : chains.stream().map(Chains::getNamedChains).collect(Collectors.toSet());

        for (Map.Entry<Chains.NamedChains, PaResultByChain> r : getPaResult().results.entrySet()) {
            if (set == null || set.stream().anyMatch(c -> c.chains.intersects(r.getKey().chains)))
                run(r.getKey(), r.getValue());
        }
    }

    abstract void run(Chains.NamedChains chains, PaResultByChain result);

    private abstract static class ExportTablesBase extends CommandPaExport {
        @Parameters(index = "1", description = "Output path")
        public String out;

        @Override
        public void validate() {
            super.validate();
            if (!out.endsWith(".tsv") && !out.endsWith(".csv"))
                throwValidationException("Output file must have .tsv or .csv extension");
        }

        Path outDir() {
            return Path.of(out).toAbsolutePath().getParent();
        }

        String outPrefix() {
            String fName = Path.of(out).getFileName().toString();
            return fName.substring(0, fName.length() - 4);
        }

        String outExtension(Chains.NamedChains chains) {
            return "." + chains.name + "." + out.substring(out.length() - 3);
        }

        String separator() {
            return out.endsWith("tsv") ? "\t" : ",";
        }

        @Override
        void run(Chains.NamedChains chains, PaResultByChain result) {
            try {
                Files.createDirectories(outDir());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            run1(chains, result);
        }

        abstract void run1(Chains.NamedChains chains, PaResultByChain result);
    }

    @CommandLine.Command(name = "tables",
            sortOptions = false,
            separator = " ",
            description = "Biophysics, Diversity, V/J/VJ-Usage, CDR3/V-Spectratype")
    public static final class ExportTables extends ExportTablesBase {
        @Override
        void run1(Chains.NamedChains chains, PaResultByChain result) {
            for (CharacteristicGroup<?, ?> table : result.schema.tables) {
                writeTables(chains, result.result.getTable(table));
            }
        }

        <K> void writeTables(Chains.NamedChains chain, CharacteristicGroupResult<K> tableResult) {
            for (CharacteristicGroupOutputExtractor<K> view : tableResult.group.views)
                for (OutputTable t : view.getTables(tableResult).values()) {
                    t.writeCSV(outDir(), outPrefix() + ".", separator(), outExtension(chain));
                }
        }
    }

    @CommandLine.Command(name = "preprocSummary",
            sortOptions = false,
            separator = " ",
            description = "Export preprocessing summary tables.")
    public static final class ExportPreprocessingSummary extends ExportTablesBase {
        @Override
        void run1(Chains.NamedChains chains, PaResultByChain result) {
            SetPreprocessorSummary.writeToCSV(outDir().resolve(outPrefix() + outExtension(chains)), result.result.preprocSummary, "\t");
        }
    }

    @CommandLine.Command(name = "listMetrics",
            sortOptions = false,
            separator = " ",
            description = "List available metrics")
    static class ListMetrics extends ACommandMiXCR {
        @Parameters(index = "0", description = "pa_result.json")
        public String in;

        @Override
        public void run0() {
            PaResult paResult;
            try {
                paResult = GlobalObjectMappers.PRETTY.readValue(new File(in), PaResult.class);
            } catch (IOException e) {
                throwValidationException("Corrupted PA file.");
                throw new RuntimeException();
            }

            PaResultByChain result = paResult.results.values().stream().findAny().orElseThrow();
            CharacteristicGroup<Clone, ?>
                    biophys = result.schema.getGroup(CommandPa.Biophysics),
                    diversity = result.schema.getGroup(CommandPa.Diversity);

            for (int i = 0; i < 2; i++) {
                System.out.println();
                CharacteristicGroup<Clone, ?> gr = i == 0 ? biophys : diversity;
                if (i == 0)
                    System.out.println("Biophysics metrics:");
                else
                    System.out.println("Diversity metrics:");
                result.result.forGroup(gr)
                        .data.values().stream()
                        .flatMap(d -> d.data.values()
                                .stream().flatMap(ma -> Arrays.stream(ma.data)))
                        .map(m -> m.key)
                        .distinct()
                        .forEach(metric -> System.out.println("    " + metric));
            }
        }
    }

    static abstract class CommandPaExportPlots extends CommandPaExport {
        @Option(names = {"--width"}, description = "Plot width")
        public int width = 0;
        @Option(names = {"--height"}, description = "Plot height")
        public int height = 0;
        @Option(names = {"--filter"}, description = "Filter by metadata. Possible filters column=value, column>=value etc.")
        public String filterByMetadata;

        @Parameters(index = "1", description = "Output PDF file name", defaultValue = "plot.pdf")
        public String out;

        <T> DataFrame<T> filter(DataFrame<T> df) {
            if (filterByMetadata != null) {
                return MetadataKt.parseFilter(metadata(), filterByMetadata).apply(df);
            } else
                return df;
        }

        @Override
        public void validate() {
            super.validate();
            if (!out.endsWith(".pdf"))
                throwValidationException("Output file must ends with .pdf extension");

            if (filterByMetadata != null && metadata == null)
                throwValidationException("Filter is specified by metadata is not.");
        }

        private DataFrame<?> metadataDf;

        DataFrame<?> metadata() {
            return metadataDf != null
                    ? metadataDf
                    : (metadata == null ? null : (metadataDf = MetadataKt.readMetadata(metadata)));
        }

        void writePlots(Chains.NamedChains chains, List<Plot> plots) {
            ExportKt.writePDFFigure(Path.of(out.substring(0, out.length() - 3) + chains.name + ".pdf"), plots);
        }

        void writePlots(Chains.NamedChains chains, Plot plot) {
            ExportKt.writePDF(Path.of(out.substring(0, out.length() - 3) + chains.name + ".pdf"), plot);
        }
    }

    static abstract class ExportBasicStatistics extends CommandPaExportPlots {
        abstract String group();

        @Option(names = {"-p", "--primary-group"}, description = "Primary group")
        public String primaryGroup;
        @Option(names = {"-s", "--secondary-group"}, description = "Secondary group")
        public String secondaryGroup;
        @Option(names = {"--facet-by"}, description = "Facet by")
        public String facetBy;
        @Option(names = {"--metric"}, description = "Select specific metrics to export.")
        public List<String> metrics;

        @Override
        void run(Chains.NamedChains chains, PaResultByChain result) {
            CharacteristicGroup<Clone, ?> ch = result.schema.getGroup(group());

            DataFrame<?> metadata = metadata();

            DataFrame<BasicStatRow> df = BasicStatistics.INSTANCE.dataFrame(
                    result.result.forGroup(ch),
                    metrics,
                    metadata
            );

            df = filter(df);

            List<Plot> plots = BasicStatistics.INSTANCE.plots(df,
                    new BasicStatistics.PlotParameters(
                            primaryGroup,
                            secondaryGroup,
                            facetBy,
                            true,
                            true,
                            null,
                            false,
                            null,
                            null,
                            null,
                            false,
                            TestMethod.Wilcoxon,
                            TestMethod.KruskalWallis,
                            null,
                            CorrelationMethod.Pearson
                    ));

            writePlots(chains, plots);
        }
    }

    @CommandLine.Command(name = "biophysics",
            sortOptions = false,
            separator = " ",
            description = "Export biophysical characteristics")
    public static class ExportBiophysics extends ExportBasicStatistics {
        @Override
        String group() {
            return CommandPa.Biophysics;
        }
    }

    @CommandLine.Command(name = "diversity",
            sortOptions = false,
            separator = " ",
            description = "Export diversity characteristics")
    public static class ExportDiversity extends ExportBasicStatistics {
        @Override
        String group() {
            return CommandPa.Diversity;
        }
    }

    static abstract class ExportHeatmap extends CommandPaExportPlots {
        @Option(names = {"--h-labels-size"}, description = "Width of horizontal labels. One unit corresponds to the width of one tile.")
        public double hLabelsSize = -1.0;
        @Option(names = {"--v-labels-size"}, description = "Height of vertical labels. One unit corresponds to the height of one tile.")
        public double vLabelsSize = -1.0;
    }

    static abstract class ExportGeneUsage extends ExportHeatmap {
        abstract String group();

        @Option(names = {"--no-samples-dendro"}, description = "Do not plot dendrogram for hierarchical clusterization of samples.")
        public boolean noSamplesDendro;
        @Option(names = {"--no-genes-dendro"}, description = "Do not plot dendrogram for hierarchical clusterization of genes.")
        public boolean noGenesDendro;
        @Option(names = {"--color-key"}, description = "Add color key layer.")
        public List<String> colorKey = new ArrayList<>();

        @Override
        void run(Chains.NamedChains chains, PaResultByChain result) {
            CharacteristicGroup<Clone, ?> ch = result.schema.getGroup(group());

            DataFrame<?> metadata = metadata();
            DataFrame<GeneUsageRow> df = GeneUsage.INSTANCE.dataFrame(
                    result.result.forGroup(ch),
                    metadata
            );
            df = filter(df);

            if (df.rowsCount() == 0)
                return;

            Plot plot = GeneUsage.INSTANCE.plot(df,
                    new HeatmapParameters(
                            !noSamplesDendro,
                            !noGenesDendro,
                            colorKey.stream().map(it -> new ColorKey(it, Position.Bottom)).collect(Collectors.toList()),
                            hLabelsSize,
                            vLabelsSize,
                            false,
                            width,
                            height
                    ));

            writePlots(chains, plot);
        }
    }

    @CommandLine.Command(name = "vUsage",
            sortOptions = false,
            separator = " ",
            description = "Export V gene usage heatmap")
    public static class ExportVUsage extends ExportGeneUsage {
        @Override
        String group() {
            return CommandPa.VUsage;
        }
    }

    @CommandLine.Command(name = "jUsage",
            sortOptions = false,
            separator = " ",
            description = "Export J gene usage heatmap")
    public static class ExportJUsage extends ExportGeneUsage {
        @Override
        String group() {
            return CommandPa.JUsage;
        }
    }

    @CommandLine.Command(name = "isotypeUsage",
            sortOptions = false,
            separator = " ",
            description = "Export isotype usage heatmap")
    public static class ExportIsotypeUsage extends ExportGeneUsage {
        @Override
        String group() {
            return CommandPa.IsotypeUsage;
        }
    }

    @CommandLine.Command(name = "vjUsage",
            sortOptions = false,
            separator = " ",
            description = "Export V-J usage heatmap")
    static class ExportVJUsage extends ExportHeatmap {
        @Option(names = {"--no-v-dendro"}, description = "Plot dendrogram for hierarchical clusterization of V genes.")
        public boolean noVDendro;
        @Option(names = {"--no-j-dendro"}, description = "Plot dendrogram for hierarchical clusterization of genes.")
        public boolean noJDendro;

        @Override
        void run(Chains.NamedChains chains, PaResultByChain result) {
            CharacteristicGroup<Clone, ?> ch = result.schema.getGroup(CommandPa.VJUsage);

            DataFrame<?> metadata = metadata();
            DataFrame<VJUsageRow> df = VJUsage.INSTANCE.dataFrame(
                    result.result.forGroup(ch),
                    metadata
            );
            df = filter(df);

            if (df.rowsCount() == 0)
                return;

            List<Plot> plots = VJUsage.INSTANCE.plots(df,
                    new HeatmapParameters(
                            !noJDendro,
                            !noVDendro,
                            Collections.emptyList(),
                            hLabelsSize,
                            vLabelsSize,
                            false,
                            width,
                            height
                    ));

            writePlots(chains, plots);
        }
    }

    @CommandLine.Command(name = "overlap",
            sortOptions = false,
            separator = " ",
            description = "Export overlap heatmap")
    static class ExportOverlap extends ExportHeatmap {
        @Option(names = {"--no-dendro"}, description = "Plot dendrogram for hierarchical clusterization of V genes.")
        public boolean noDendro;
        @Option(names = {"--color-key"}, description = "Add color key layer.")
        public List<String> colorKey = new ArrayList<>();
        @Option(names = {"--metric"}, description = "Select specific metrics to export.")
        public List<String> metrics;

        DataFrame<OverlapRow> filterOverlap(DataFrame<OverlapRow> df) {
            if (filterByMetadata != null) {
                Filter filter = MetadataKt.parseFilter(metadata(), filterByMetadata);
                return Overlap.INSTANCE.filterOverlap(filter, df);
            } else
                return df;
        }

        @Override
        void run(Chains.NamedChains chains, PaResultByChain result) {
            CharacteristicGroup<Clone, ?> ch = result.schema.getGroup(CommandPa.Overlap);

            DataFrame<?> metadata = metadata();
            DataFrame<OverlapRow> df = Overlap.INSTANCE.dataFrame(
                    result.result.forGroup(ch),
                    metrics,
                    metadata
            );
            df = filterOverlap(df);

            if (df.rowsCount() == 0)
                return;

            if (df.get("weight").distinct().toList().size() <= 1)
                return;

            List<Plot> plots = Overlap.INSTANCE.plots(df,
                    new HeatmapParameters(
                            !noDendro,
                            !noDendro,
                            colorKey.stream()
                                    .map(it -> new ColorKey(it, it.startsWith("x") ? Position.Bottom : Position.Left))
                                    .collect(Collectors.toList()),
                            hLabelsSize,
                            vLabelsSize,
                            true,
                            width,
                            height
                    ));

            writePlots(chains, plots);
        }
    }

    @CommandLine.Command(name = "exportPa",
            separator = " ",
            description = "Export postanalysis results.",
            subcommands = {
                    CommandLine.HelpCommand.class
            })
    public static class CommandExportPaMain {
    }
}
