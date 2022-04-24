package com.milaboratory.mixcr.cli.postanalysis;

import com.milaboratory.miplots.stat.util.TestMethod;
import com.milaboratory.miplots.stat.xcontinious.CorrelationMethod;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.postanalysis.PostanalysisResult;
import com.milaboratory.mixcr.postanalysis.plots.BasicStatRow;
import com.milaboratory.mixcr.postanalysis.plots.BasicStatistics;
import com.milaboratory.mixcr.postanalysis.ui.CharacteristicGroup;
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
        PostanalysisResult paResult = result.result.forGroup(ch);
        DataFrame<?> metadata = metadata();
        DataFrame<BasicStatRow> df = BasicStatistics.INSTANCE.dataFrame(
                paResult,
                metrics,
                metadata
        );

        df = filter(df);

        BasicStatistics.PlotParameters par = new BasicStatistics.PlotParameters(
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
        );

        List<byte[]> plots = BasicStatistics.INSTANCE.plotsAndSummary(df, par);
        writePlotsAndSummary(ch, result.group, plots, paResult.preprocSummary);
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
