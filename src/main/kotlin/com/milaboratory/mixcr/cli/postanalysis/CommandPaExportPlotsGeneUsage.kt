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

import com.milaboratory.miplots.Position
import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.postanalysis.plots.ColorKey
import com.milaboratory.mixcr.postanalysis.plots.GeneUsage.dataFrame
import com.milaboratory.mixcr.postanalysis.plots.GeneUsage.plot
import com.milaboratory.mixcr.postanalysis.plots.HeatmapParameters
import com.milaboratory.mixcr.postanalysis.ui.PostanalysisParametersIndividual
import picocli.CommandLine
import java.util.stream.Collectors

abstract class CommandPaExportPlotsGeneUsage : CommandPaExportPlotsHeatmapWithGroupBy() {
    abstract fun group(): String

    @CommandLine.Option(description = ["Don't add samples dendrogram."], names = ["--no-samples-dendro"])
    var noSamplesDendro = false

    @CommandLine.Option(description = ["Don't add genes dendrogram."], names = ["--no-genes-dendro"])
    var noGenesDendro = false

    @CommandLine.Option(description = ["Add color key layer."], names = ["--color-key"])
    var colorKey: List<String> = mutableListOf()

    override fun run(result: PaResultByGroup) {
        val ch = result.schema.getGroup<Clone>(group())
        val df = dataFrame(result.result.forGroup(ch), metadataDf)
            .filterByMetadata()
        if (df.rowsCount() == 0) return
        val plot = plot(
            df,
            HeatmapParameters(
                !noSamplesDendro,
                !noGenesDendro,
                colorKey.stream().map { it: String? ->
                    ColorKey(
                        it!!, Position.Bottom
                    )
                }.collect(Collectors.toList()),
                groupBy,
                hLabelsSize,
                vLabelsSize,
                false,
                parsePallete(),
                width,
                height
            )
        )
        writePlots(result.group, plot)
    }

    @CommandLine.Command(
        name = "vUsage",
        sortOptions = false,
        separator = " ",
        description = ["Export V gene usage heatmap"]
    )
    class ExportVUsage : CommandPaExportPlotsGeneUsage() {
        override fun group(): String = PostanalysisParametersIndividual.VUsage
    }

    @CommandLine.Command(
        name = "jUsage",
        sortOptions = false,
        separator = " ",
        description = ["Export J gene usage heatmap"]
    )
    class ExportJUsage : CommandPaExportPlotsGeneUsage() {
        override fun group(): String = PostanalysisParametersIndividual.JUsage
    }

    @CommandLine.Command(
        name = "isotypeUsage",
        sortOptions = false,
        separator = " ",
        description = ["Export isotype usage heatmap"]
    )
    class ExportIsotypeUsage : CommandPaExportPlotsGeneUsage() {
        override fun group(): String = PostanalysisParametersIndividual.IsotypeUsage
    }
}
