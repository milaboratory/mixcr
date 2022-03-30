package com.milaboratory.mixcr.cli.postanalysis;

import com.milaboratory.miplots.stat.util.TestMethod;
import com.milaboratory.miplots.stat.xcontinious.CorrelationMethod;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.postanalysis.plots.BasicStatRow;
import com.milaboratory.mixcr.postanalysis.plots.BasicStatistics;
import com.milaboratory.mixcr.postanalysis.ui.CharacteristicGroup;
import jetbrains.letsPlot.intern.Plot;
import org.jetbrains.kotlinx.dataframe.DataFrame;
import picocli.CommandLine;
import picocli.CommandLine.Option;

import java.util.List;

public abstract class CommandPaExportPlotsBasicStatistics extends CommandPaExportPlots {
    abstract String group();

    @Option(description = "Primary group", names = {"-p", "--primary-group"})
    public String primaryGroup;
    @Option(description = "Secondary group", names = {"-s", "--secondary-group"})
    public String secondaryGroup;
    @Option(description = "Facet by", names = {"--facet-by"})
    public String facetBy;
    @Option(description = "Select specific metrics to export.", names = {"--metric"})
    public List<String> metrics;

    @Override
    void run(PaResultByGroup result) {
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

        writePlots(result.group, plots);
    }

    @CommandLine.Command(name = "biophysics",
            sortOptions = false,
            separator = " ",
            description = "Export biophysical characteristics")
    public static class ExportBiophysics extends CommandPaExportPlotsBasicStatistics {
        @Override
        String group() {
            return CommandPaIndividual.Biophysics;
        }
    }

    @CommandLine.Command(name = "diversity",
            sortOptions = false,
            separator = " ",
            description = "Export diversity characteristics")
    public static class ExportDiversity extends CommandPaExportPlotsBasicStatistics {
        @Override
        String group() {
            return CommandPaIndividual.Diversity;
        }
    }
}
