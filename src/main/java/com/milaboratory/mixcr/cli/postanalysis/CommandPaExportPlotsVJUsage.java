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
    @Option(description = "Don't add V genes dendrogram",
            names = {"--no-v-dendro"})
    public boolean noVDendro;
    @Option(description = "Don't add J genes dendrogram",
            names = {"--no-j-dendro"})
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
                        false,
                        width,
                        height
                ));

        writePlots(result.group, plots);
    }
}
