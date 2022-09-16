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
import com.milaboratory.mixcr.postanalysis.plots.GeneUsage.plotBarPlot
import com.milaboratory.mixcr.postanalysis.plots.HeatmapParameters
import com.milaboratory.mixcr.postanalysis.ui.PostanalysisParametersIndividual
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.util.stream.Collectors

abstract class CommandPaExportPlotsGeneUsage : CommandPaExportPlotsHeatmapWithGroupBy() {
    abstract fun group(): String

    @Option(
        description = ["Show gene family usage instead."],
        names = ["--family-usage"]
    )
    var familyUsage: Boolean = false

    @Option(
        description = ["Don't add samples dendrogram on heatmap."],
        names = ["--no-samples-dendro"]
    )
    var noSamplesDendro = false

    @Option(
        description = ["Don't add genes dendrogram on heatmap."],
        names = ["--no-genes-dendro"]
    )
    var noGenesDendro = false

    @Option(
        description = ["Add color key layer to heatmap."],
        names = ["--color-key"]
    )
    var colorKey: List<String> = mutableListOf()

    @Option(
        description = ["Export bar plot instead of heatmap."],
        names = ["--bar-plot"]
    )
    var barPlot = false

    @Option(
        description = ["Facet barplot."],
        names = ["--facet-by"]
    )
    var facetBy: String? = null

    override fun run(result: PaResultByGroup) {
        val ch = result.schema.getGroup<Clone>(group())
        val df = dataFrame(result.result.forGroup(ch), metadataDf).filterByMetadata()
        if (df.rowsCount() == 0) return

        val plot = if (barPlot)
            plotBarPlot(df, facetBy)
        else
            plot(
                df,
                HeatmapParameters(
                    !noSamplesDendro,
                    !noGenesDendro,
                    colorKey.stream().map {
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

    @Command(
        name = "vUsage",
        sortOptions = false,
        separator = " ",
        description = ["Export V gene usage"]
    )
    class VUsage : CommandPaExportPlotsGeneUsage() {
        override fun group(): String =
            if (familyUsage)
                PostanalysisParametersIndividual.VFamilyUsage
            else
                PostanalysisParametersIndividual.VUsage
    }

    @Command(
        name = "jUsage",
        sortOptions = false,
        separator = " ",
        description = ["Export J gene usage"]
    )
    class JUsage : CommandPaExportPlotsGeneUsage() {
        override fun group(): String =
            if (familyUsage)
                PostanalysisParametersIndividual.JFamilyUsage
            else
                PostanalysisParametersIndividual.JUsage
    }

    @Command(
        name = "isotypeUsage",
        sortOptions = false,
        separator = " ",
        description = ["Export isotype usage heatmap"]
    )
    class IsotypeUsage : CommandPaExportPlotsGeneUsage() {
        override fun group(): String = PostanalysisParametersIndividual.IsotypeUsage
    }
}
