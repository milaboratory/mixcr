package com.milaboratory.mixcr.cli;

import com.milaboratory.miplots.ExportKt;
import com.milaboratory.miplots.stat.util.TestMethod;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.cli.CommandPa.PaResult;
import com.milaboratory.mixcr.cli.CommandPa.PaResultByChain;
import com.milaboratory.mixcr.postanalysis.dataframe.BasicStatRow;
import com.milaboratory.mixcr.postanalysis.dataframe.BasicStatistics;
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
        @Option(names = {"--metric"}, description = "Metrics to export")
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
                            null
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

    @CommandLine.Command(name = "exportPostanalysis",
            separator = " ",
            description = "Export postanalysis results.",
            subcommands = {
                    CommandLine.HelpCommand.class
            })
    public static class CommandExportPostanalysisMain {
    }
}
