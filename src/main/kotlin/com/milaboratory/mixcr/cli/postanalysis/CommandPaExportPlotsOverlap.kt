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

import com.milaboratory.miplots.Position.Bottom
import com.milaboratory.miplots.Position.Left
import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.cli.MultipleMetricsInOneFile
import com.milaboratory.mixcr.postanalysis.overlap.OverlapType
import com.milaboratory.mixcr.postanalysis.plots.ColorKey
import com.milaboratory.mixcr.postanalysis.plots.HeatmapParameters
import com.milaboratory.mixcr.postanalysis.plots.Overlap.dataFrame
import com.milaboratory.mixcr.postanalysis.plots.Overlap.filterOverlap
import com.milaboratory.mixcr.postanalysis.plots.Overlap.plots
import com.milaboratory.mixcr.postanalysis.plots.OverlapRow
import com.milaboratory.mixcr.postanalysis.plots.parseFilter
import com.milaboratory.mixcr.postanalysis.ui.PostanalysisParametersOverlap
import org.jetbrains.kotlinx.dataframe.DataFrame
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.util.stream.Collectors

@Command(description = ["Export overlap heatmaps"])
class CommandPaExportPlotsOverlap : MultipleMetricsInOneFile, CommandPaExportPlotsHeatmapWithGroupBy() {
    @Option(description = ["Don't add dendrograms"], names = ["--no-dendro"])
    var noDendro = false

    @Option(
        description = ["Add color key layer; prefix 'x_' (add to the bottom) or 'y_' (add to the left) should be used."],
        names = ["--color-key"],
        paramLabel = "<key>"
    )
    var colorKeysParam: List<String> = mutableListOf()

    @Option(description = ["Fill diagonal line"], names = ["--fill-diagonal"])
    var fillDiagonal = false

    @Option(
        description = ["Select specific metrics to export."],
        names = ["--metric"],
        paramLabel = "<metric>"
    )
    var metrics: List<String>? = null

    override fun validate() {
        super.validate()
        validateNonPdf(out, metrics)
    }

    private fun DataFrame<OverlapRow>.filterOverlapByMetadata(): DataFrame<OverlapRow> {
        var result = this
        filterByMetadata?.let { filterByMetadata ->
            for (f in filterByMetadata) {
                val filter = metadataDf!!.parseFilter(f)
                result = filterOverlap(filter, result)
            }
        }
        return result
    }

    private fun metricsFilter(): List<OverlapType>? {
        return if (metrics == null || metrics!!.isEmpty()) null else metrics!!.stream()
            .map { name: String? -> OverlapType.byNameOrThrow(name) }
            .collect(Collectors.toList())
    }

    override fun run(result: PaResultByGroup) {
        val ch = result.schema.getGroup<Clone>(PostanalysisParametersOverlap.Overlap)
        val df: DataFrame<OverlapRow> = dataFrame(
            result.result.forGroup(ch),
            metricsFilter(),
            fillDiagonal,
            metadataDf
        ).filterOverlapByMetadata()
        if (df.rowsCount() == 0) return
        if (df["weight"].distinct().toList().size <= 1) return
        val colorKeys = colorKeysParam.map { key ->
            when {
                key.startsWith("x_") -> ColorKey(key, Bottom)
                key.startsWith("y_") -> ColorKey(key, Left)
                else -> ColorKey("x_$key".lowercase(), Bottom)
            }
        }
        val par = HeatmapParameters(
            !noDendro,
            !noDendro,
            colorKeys,
            groupBy,
            hLabelsSize,
            vLabelsSize,
            false,
            parsePalette(),
            width,
            height
        )
        val plots = plots(df, par)
        writePlots(result.group, plots)
    }
}
