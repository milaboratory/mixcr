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
package com.milaboratory.mixcr.postanalysis.plots

import com.milaboratory.miplots.Position
import com.milaboratory.miplots.Position.*
import com.milaboratory.miplots.color.Palettes
import com.milaboratory.miplots.heatmap.*
import com.milaboratory.mixcr.postanalysis.plots.HeatmapParameters.Companion.hLabelSize
import com.milaboratory.mixcr.postanalysis.plots.HeatmapParameters.Companion.vLabelSize
import jetbrains.letsPlot.ggsize
import org.jetbrains.kotlinx.dataframe.AnyFrame
import kotlin.math.sqrt

data class ColorKey(
    val key: String,
    val pos: Position
)

data class HeatmapParameters(
    val clusterX: Boolean,
    val clusterY: Boolean,
    val colorKey: List<ColorKey>? = null,
    val groupBy: List<String>? = null,
    val hLabelsSize: Double,
    val vLabelsSize: Double,
    val fillNaZeroes: Boolean,
    override val width: Int,
    override val height: Int,
) : WithPlotSize {

    companion object {
        fun defaultHLabelSize(labels: List<String>) = run {
            val l = labels.maxOfOrNull { it.length } ?: 0
            (0.9 * l / 3)
        }

        fun defaultVLabelSize(labels: List<String>) = defaultHLabelSize(labels)

        private fun AnyFrame.labels(col: String) = this[col].distinct().toList().map { it?.toString() }.filterNotNull()

        fun AnyFrame.hLabelSize(w: Double, col: String) = if (w < 0.0) defaultHLabelSize(labels(col)) else w
        fun AnyFrame.vLabelSize(h: Double, col: String) = if (h < 0.0) defaultVLabelSize(labels(col)) else h
    }
}

fun mkHeatmap(
    df: AnyFrame,
    x: String, y: String, z: String,
    params: HeatmapParameters,
) = run {
    var plt = Heatmap(
        df,
        x = x,
        y = y,
        z = z,
        xOrder = if (params.clusterX) Hierarchical() else null,
        yOrder = if (params.clusterY) Hierarchical() else null,
        fillNoValue = params.fillNaZeroes,
        noValue = 0.0,
        fillPalette = Palettes.Diverging.limeRose15
    )

    plt = plt.withBorder()

    val ncats = params.colorKey?.map { df[it.key].distinct().size() }?.sum() ?: 0
    var pallete = Palettes.Categorical.auto(ncats)
    params.colorKey?.let {
        val first = Position.values().map { p -> p to true }.toMap().toMutableMap()
        for (key in it) {
            val keyCol = key.key
            val nColors = df[keyCol].distinct().size()
            plt.withColorKey(
                keyCol,
                pos = key.pos,
                sep = if (first[key.pos]!!) 0.1 else 0.0,
                labelPos = when (key.pos) {
                    Bottom -> Left
                    Left -> Top
                    Top -> Right
                    Right -> Bottom
                },
                labelAngle = when (key.pos) {
                    Bottom, Top -> 0
                    Left, Right -> 90
                },
                label = if (keyCol.startsWith("x_") || keyCol.startsWith("y_"))
                    keyCol
                        .replaceFirst("_x", "")
                        .replaceFirst("_y", "")
                else
                    null,
                labelSep = 0.1,
                pallete = pallete
            )
            pallete = pallete.shift(nColors)
            first[key.pos] = false
        }
    }

    if (params.clusterX)
        plt = plt.withDendrogram(pos = Position.Top, 0.1)
    if (params.clusterY)
        plt = plt.withDendrogram(pos = Position.Right, 0.1)

    plt = plt.withLabels(
        Bottom,
        angle = 45,
        sep = 0.1,
        height = df.vLabelSize(params.vLabelsSize, x) * sqrt(2.0) / 2
    )

    plt = plt.withLabels(
        Left,
        sep = 0.1,
        width = df.hLabelSize(params.hLabelsSize, y)
    )

    plt = plt.withFillLegend(Left, size = 0.5)
    plt = plt.withColorKeyLegend(Left)

    if (params.width > 0 && params.height > 0)
        plt.plusAssign(ggsize(params.width, params.height))

    plt.plot
}