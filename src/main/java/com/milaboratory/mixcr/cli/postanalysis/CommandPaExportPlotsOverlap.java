package com.milaboratory.mixcr.cli.postanalysis;

import com.milaboratory.miplots.Position;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.postanalysis.PostanalysisResult;
import com.milaboratory.mixcr.postanalysis.SetPreprocessorSummary;
import com.milaboratory.mixcr.postanalysis.plots.*;
import com.milaboratory.mixcr.postanalysis.ui.CharacteristicGroup;
import org.jetbrains.kotlinx.dataframe.DataFrame;
import picocli.CommandLine;
import picocli.CommandLine.Option;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@CommandLine.Command(name = "overlap",
        sortOptions = false,
        separator = " ",
        description = "Export overlap heatmap")
public class CommandPaExportPlotsOverlap extends CommandPaExportPlotsHeatmapWithGroupBy {
    @Option(description = "Plot dendrogram for hierarchical clusterization of V genes.",
            names = {"--no-dendro"})
    public boolean noDendro;
    @Option(description = "Add color key layer.",
            names = {"--color-key"})
    public List<String> colorKey = new ArrayList<>();
    @Option(description = "Select specific metrics to export.",
            names = {"--metric"})
    public List<String> metrics;

    DataFrame<OverlapRow> filterOverlap(DataFrame<OverlapRow> df) {
        if (filterByMetadata != null) {
            for (String f : filterByMetadata) {
                Filter filter = MetadataKt.parseFilter(metadata(), f);
                df = Overlap.INSTANCE.filterOverlap(filter, df);
            }
        }
        return df;
    }

    @Override
    void run(PaResultByGroup result) {
        CharacteristicGroup<Clone, ?> ch = result.schema.getGroup(CommandPaOverlap.Overlap);
        PostanalysisResult paResult = result.result.forGroup(ch);
        Map<String, SetPreprocessorSummary> preprocSummary = paResult.preprocSummary;
        DataFrame<?> metadata = metadata();

        DataFrame<OverlapRow> df = Overlap.INSTANCE.dataFrame(
                paResult,
                metrics,
                metadata
        );
        df = filterOverlap(df);

        if (df.rowsCount() == 0)
            return;

        if (df.get("weight").distinct().toList().size() <= 1)
            return;

        HeatmapParameters par = new HeatmapParameters(
                !noDendro,
                !noDendro,
                colorKey.stream()
                        .map(it -> new ColorKey(it, it.startsWith("x") ? Position.Bottom : Position.Left))
                        .collect(Collectors.toList()),
                groupBy,
                hLabelsSize,
                vLabelsSize,
                false,
                width,
                height
        );

        List<byte[]> plotsAndSummary = Overlap.INSTANCE.plotsAndSummary(df, par);
        writePlotsAndSummary(ch, result.group, plotsAndSummary, preprocSummary);
    }
}