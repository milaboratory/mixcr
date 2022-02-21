package com.milaboratory.mixcr.cli;

import com.milaboratory.miplots.ExportKt;
import com.milaboratory.miplots.stat.util.TestMethod;
import com.milaboratory.miplots.stat.xcontinious.CorrelationMethod;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.cli.CommandPa.PaResult;
import com.milaboratory.mixcr.cli.CommandPa.PaResultByChain;
import com.milaboratory.mixcr.postanalysis.dataframe.*;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    @CommandLine.Command(name = "tables",
            sortOptions = false,
            separator = " ",
            description = "Biophysics, Diversity, V/J/VJ-Usage, CDR3/V-Spectratype")
    public static final class ExportTables extends CommandPaExport {
        @Parameters(index = "1", description = "Output directory")
        public String out;

        @Override
        void run(Chains.NamedChains chains, PaResultByChain result) {
            Path p = Path.of(out).toAbsolutePath();
            try {
                Files.createDirectories(p);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            for (CharacteristicGroup<?, ?> table : result.schema.tables) {
                writeTables(chains, result.result.getTable(table));
            }
        }

        <K> void writeTables(Chains.NamedChains chain, CharacteristicGroupResult<K> tableResult) {
            for (CharacteristicGroupOutputExtractor<K> view : tableResult.group.views)
                for (OutputTable t : view.getTables(tableResult).values())
                    t.writeCSV(Path.of(out).toAbsolutePath(), "", "\t", "." + chain.name + ".tsv");
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

    static abstract class ExportBasicStatistics extends CommandPaExport {
        abstract String group();

        @Parameters(index = "1", description = "Output PDF file name", defaultValue = "plot.pdf")
        public String out;

        @Option(names = {"-p", "--primary-group"}, description = "Primary group")
        public String primaryGroup;
        @Option(names = {"-s", "--secondary-group"}, description = "Secondary group")
        public String secondaryGroup;
        @Option(names = {"--facet-by"}, description = "Facet by")
        public String facetBy;
        @Option(names = {"--metric"}, description = "Select specific metrics to export.")
        public List<String> metrics;

        @Override
        public void validate() {
            super.validate();
            if (!out.endsWith(".pdf"))
                throwValidationException("Output file must ends with .pdf extension");
        }

        @Override
        void run(Chains.NamedChains chains, PaResultByChain result) {
            CharacteristicGroup<Clone, ?> ch = result.schema.getGroup(group());

            DataFrame<BasicStatRow> df = BasicStatistics.INSTANCE.dataFrame(
                    result.result.forGroup(ch),
                    metrics,
                    metadata
            );

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

            ExportKt.writePDFFigure(Path.of(out.substring(0, out.length() - 3) + chains.name + ".pdf"), plots);
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

    static abstract class ExportGeneUsage extends CommandPaExport {
        abstract String group();

        @Parameters(index = "1", description = "Output PDF file name", defaultValue = "plot.pdf")
        public String out;
        @Option(names = {"--no-samples-dendro"}, description = "Do not plot dendrogram for hierarchical clusterization of samples.")
        public boolean noSamplesDendro;
        @Option(names = {"--no-genes-dendro"}, description = "Do not plot dendrogram for hierarchical clusterization of genes.")
        public boolean noGenesDendro;
        @Option(names = {"--color-key"}, description = "Add color key layer.")
        public List<String> colorKey;

        @Override
        public void validate() {
            super.validate();
            if (!out.endsWith(".pdf"))
                throwValidationException("Output file must ends with .pdf extension");
        }

        @Override
        void run(Chains.NamedChains chains, PaResultByChain result) {
            CharacteristicGroup<Clone, ?> ch = result.schema.getGroup(group());

            DataFrame<GeneUsageRow> df = GeneUsage.INSTANCE.dataFrame(
                    result.result.forGroup(ch),
                    metadata
            );

            if (df.rowsCount() == 0)
                return;

            Plot plots = GeneUsage.INSTANCE.plot(df,
                    new GeneUsage.PlotParameters(
                            colorKey,
                            !noSamplesDendro,
                            !noGenesDendro
                    ));

            ExportKt.writePDF(Path.of(out.substring(0, out.length() - 3) + chains.name + ".pdf"), plots);
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
    static class ExportVJUsage extends CommandPaExport {
        @Parameters(index = "1", description = "Output PDF file name", defaultValue = "plot.pdf")
        public String out;
        @Option(names = {"--no-v-dendro"}, description = "Plot dendrogram for hierarchical clusterization of V genes.")
        public boolean noVDendro;
        @Option(names = {"--no-j-dendro"}, description = "Plot dendrogram for hierarchical clusterization of genes.")
        public boolean noJDendro;
        @Option(names = {"--color-key"}, description = "Add color key layer.")
        public List<String> colorKey;

        @Override
        public void validate() {
            super.validate();
            if (!out.endsWith(".pdf"))
                throwValidationException("Output file must ends with .pdf extension");
        }

        @Override
        void run(Chains.NamedChains chains, PaResultByChain result) {
            CharacteristicGroup<Clone, ?> ch = result.schema.getGroup(CommandPa.VJUsage);

            DataFrame<VJUsageRow> df = VJUsage.INSTANCE.dataFrame(
                    result.result.forGroup(ch),
                    metadata
            );

            if (df.rowsCount() == 0)
                return;

            Plot plots = VJUsage.INSTANCE.plot(df,
                    new VJUsage.PlotParameters(
                            colorKey,
                            !noVDendro,
                            !noJDendro
                    ));

            ExportKt.writePDF(Path.of(out.substring(0, out.length() - 3) + chains.name + ".pdf"), plots);
        }
    }

    @CommandLine.Command(name = "overlap",
            sortOptions = false,
            separator = " ",
            description = "Export overlap heatmap")
    static class ExportOverlap extends CommandPaExport {
        @Parameters(index = "1", description = "Output PDF file name", defaultValue = "plot.pdf")
        public String out;
        @Option(names = {"--no-dendro"}, description = "Plot dendrogram for hierarchical clusterization of V genes.")
        public boolean noDendro;
        @Option(names = {"--color-key"}, description = "Add color key layer.")
        public List<String> colorKey;
        @Option(names = {"--metric"}, description = "Select specific metrics to export.")
        public List<String> metrics;

        @Override
        public void validate() {
            super.validate();
            if (!out.endsWith(".pdf"))
                throwValidationException("Output file must ends with .pdf extension");
        }

        @Override
        void run(Chains.NamedChains chains, PaResultByChain result) {
            CharacteristicGroup<Clone, ?> ch = result.schema.getGroup(CommandPa.Overlap);

            DataFrame<OverlapRow> df = Overlap.INSTANCE.dataFrame(
                    result.result.forGroup(ch),
                    metadata
            );

            if (df.rowsCount() == 0)
                return;

            Plot plots = Overlap.INSTANCE.plot(df,
                    new Overlap.PlotParameters(
                            colorKey,
                            !noDendro,
                    ));

            ExportKt.writePDF(Path.of(out.substring(0, out.length() - 3) + chains.name + ".pdf"), plots);
        }
    }

    @CommandLine.Command(name = "exportPa",
            separator = " ",
            description = "Export postanalysis results.",
            subcommands = {
                    CommandLine.HelpCommand.class
            })
    public static class CommandExportPostanalysisMain {
    }
}
