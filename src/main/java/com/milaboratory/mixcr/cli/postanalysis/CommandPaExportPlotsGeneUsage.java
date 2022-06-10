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
import com.milaboratory.mixcr.postanalysis.plots.ColorKey;
import com.milaboratory.mixcr.postanalysis.plots.GeneUsage;
import com.milaboratory.mixcr.postanalysis.plots.GeneUsageRow;
import com.milaboratory.mixcr.postanalysis.plots.HeatmapParameters;
import com.milaboratory.mixcr.postanalysis.ui.CharacteristicGroup;
import com.milaboratory.mixcr.postanalysis.ui.PostanalysisParametersIndividual;
import org.jetbrains.kotlinx.dataframe.DataFrame;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public abstract class CommandPaExportPlotsGeneUsage extends CommandPaExportPlotsHeatmapWithGroupBy {
    abstract String group();

    @Option(description = "Don't add samples dendrogram.",
            names = {"--no-samples-dendro"})
    public boolean noSamplesDendro;

    @Option(description = "Don't add genes dendrogram.",
            names = {"--no-genes-dendro"})
    public boolean noGenesDendro;

    @Option(description = "Add color key layer.",
            names = {"--color-key"})
    public List<String> colorKey = new ArrayList<>();

    @Override
    void run(PaResultByGroup result) {
        CharacteristicGroup<Clone, ?> ch = result.schema.getGroup(group());
        PostanalysisResult paResult = result.result.forGroup(ch);
        DataFrame<?> metadata = metadata();
        DataFrame<GeneUsageRow> df = GeneUsage.INSTANCE.dataFrame(
                paResult,
                metadata
        );
        df = filter(df);

        if (df.rowsCount() == 0)
            return;

        List<byte[]> plot = GeneUsage.INSTANCE.plotAndSummary(df,
                new HeatmapParameters(
                        !noSamplesDendro,
                        !noGenesDendro,
                        colorKey.stream().map(it -> new ColorKey(it, Position.Bottom)).collect(Collectors.toList()),
                        groupBy,
                        hLabelsSize,
                        vLabelsSize,
                        false,
                        width,
                        height
                ));

        writePlotsAndSummary(result.group, plot);
    }

    @Command(name = "vUsage",
            sortOptions = false,
            separator = " ",
            description = "Export V gene usage heatmap")
    public static class ExportVUsage extends CommandPaExportPlotsGeneUsage {
        @Override
        String group() {
            return PostanalysisParametersIndividual.VUsage;
        }
    }

    @Command(name = "jUsage",
            sortOptions = false,
            separator = " ",
            description = "Export J gene usage heatmap")
    public static class ExportJUsage extends CommandPaExportPlotsGeneUsage {
        @Override
        String group() {
            return PostanalysisParametersIndividual.JUsage;
        }
    }

    @Command(name = "isotypeUsage",
            sortOptions = false,
            separator = " ",
            description = "Export isotype usage heatmap")
    public static class ExportIsotypeUsage extends CommandPaExportPlotsGeneUsage {
        @Override
        String group() {
            return PostanalysisParametersIndividual.IsotypeUsage;
        }
    }
}
