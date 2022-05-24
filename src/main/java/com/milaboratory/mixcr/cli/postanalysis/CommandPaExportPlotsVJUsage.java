package com.milaboratory.mixcr.cli.postanalysis;

import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.postanalysis.plots.HeatmapParameters;
import com.milaboratory.mixcr.postanalysis.plots.VJUsage;
import com.milaboratory.mixcr.postanalysis.plots.VJUsageRow;
import com.milaboratory.mixcr.postanalysis.ui.CharacteristicGroup;
import com.milaboratory.mixcr.postanalysis.ui.PostanalysisParametersIndividual;
import jetbrains.letsPlot.intern.Plot;
import org.jetbrains.kotlinx.dataframe.DataFrame;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.Collections;
import java.util.List;

@Command(name = "vjUsage",
        sortOptions = false,
        separator = " ",
        description = "Export V-J usage heatmap",
        hidden = true)
public class CommandPaExportPlotsVJUsage extends CommandPaExportPlotsHeatmap {
    @Option(description = "Plot dendrogram for hierarchical clusterization of V genes.", names = {"--no-v-dendro"})
    public boolean noVDendro;
    @Option(description = "Plot dendrogram for hierarchical clusterization of genes.", names = {"--no-j-dendro"})
    public boolean noJDendro;

    @Override
    void run(PaResultByGroup result) {
        CharacteristicGroup<Clone, ?> ch = result.schema.getGroup(PostanalysisParametersIndividual.VJUsage);

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
                        null,
                        hLabelsSize,
                        vLabelsSize,
                        true,
                        width,
                        height
                ));

        writePlots(result.group, plots);
    }
}
