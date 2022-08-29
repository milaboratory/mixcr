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
package com.milaboratory.mixcr.cli.postanalysis;

import com.milaboratory.miplots.Position;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.postanalysis.PostanalysisResult;
import com.milaboratory.mixcr.postanalysis.overlap.OverlapType;
import com.milaboratory.mixcr.postanalysis.plots.*;
import com.milaboratory.mixcr.postanalysis.ui.CharacteristicGroup;
import com.milaboratory.mixcr.postanalysis.ui.PostanalysisParametersOverlap;
import jetbrains.letsPlot.intern.Plot;
import org.jetbrains.kotlinx.dataframe.DataFrame;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Command(name = "overlap",
        sortOptions = false,
        separator = " ",
        description = "Export overlap heatmaps")
public class CommandPaExportPlotsOverlap extends CommandPaExportPlotsHeatmapWithGroupBy {
    @Option(description = "Don't add dendrograms",
            names = {"--no-dendro"})
    public boolean noDendro;

    @Option(description = "Add color key layer; prefix 'x_' (add to the bottom) or 'y_' (add to the left) should be used.",
            names = {"--color-key"})
    public List<String> colorKey = new ArrayList<>();

    @Option(description = "Fill diagonal line",
            names = {"--fill-diagonal"}
    )
    public boolean fillDiagonal = false;

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

    private List<OverlapType> metricsFilter() {
        if (metrics == null || metrics.isEmpty())
            return null;
        return metrics.stream()
                .map(OverlapType::byNameOrThrow)
                .collect(Collectors.toList());
    }

    @Override
    void run(PaResultByGroup result) {
        CharacteristicGroup<Clone, ?> ch = result.schema.getGroup(PostanalysisParametersOverlap.Overlap);
        PostanalysisResult paResult = result.result.forGroup(ch);
        DataFrame<?> metadata = metadata();

        List<OverlapType> metrics = metricsFilter();
        DataFrame<OverlapRow> df = Overlap.INSTANCE.dataFrame(
                paResult,
                metrics,
                fillDiagonal,
                metadata
        );
        df = filterOverlap(df);

        if (df.rowsCount() == 0)
            return;

        if (df.get("weight").distinct().toList().size() <= 1)
            return;

        List<String> colorKey = this.colorKey.stream()
                .map(it -> it.startsWith("x_") || it.startsWith("y_") ? it : "x_" + it)
                .collect(Collectors.toList());

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
                parsePallete(),
                width,
                height
        );

        List<Plot> plots = Overlap.INSTANCE.plots(df, par);
        writePlots(result.group, plots);
    }
}