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
package com.milaboratory.mixcr.cli.postanalysis

import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.postanalysis.plots.HeatmapParameters
import com.milaboratory.mixcr.postanalysis.plots.VJUsage.dataFrame
import com.milaboratory.mixcr.postanalysis.plots.VJUsage.plots
import com.milaboratory.mixcr.postanalysis.ui.PostanalysisParametersIndividual
import picocli.CommandLine

@CommandLine.Command(
    name = "vjUsage",
    sortOptions = false,
    separator = " ",
    description = ["Export V-J usage heatmap"],
    hidden = true
)
class CommandPaExportPlotsVJUsage : CommandPaExportPlotsHeatmap() {
    @CommandLine.Option(description = ["Don't add V genes dendrogram"], names = ["--no-v-dendro"])
    var noVDendro = false

    @CommandLine.Option(description = ["Don't add J genes dendrogram"], names = ["--no-j-dendro"])
    var noJDendro = false

    override fun run(result: PaResultByGroup) {
        val ch = result.schema.getGroup<Clone>(PostanalysisParametersIndividual.VJUsage)
        val df = dataFrame(result.result.forGroup(ch), metadataDf)
            .filterByMetadata()
        if (df.rowsCount() == 0) return
        val plots = plots(
            df,
            HeatmapParameters(
                !noJDendro,
                !noVDendro, emptyList(),
                null,
                hLabelsSize,
                vLabelsSize,
                false,
                parsePallete(),
                width,
                height
            )
        )
        writePlots(result.group, plots)
    }
}