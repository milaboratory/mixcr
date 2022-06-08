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

import cc.redberry.pipe.CUtils
import cc.redberry.pipe.OutputPort
import com.milaboratory.core.sequence.AminoAcidSequence
import com.milaboratory.miplots.plusAssign
import com.milaboratory.miplots.stat.xcontinious.CorrelationMethod
import com.milaboratory.miplots.stat.xcontinious.GGScatter
import com.milaboratory.miplots.stat.xcontinious.plusAssign
import com.milaboratory.miplots.stat.xcontinious.statCor
import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.postanalysis.overlap.OverlapGroup
import io.repseq.core.GeneFeature
import jetbrains.letsPlot.label.xlab
import jetbrains.letsPlot.label.ylab
import jetbrains.letsPlot.scale.scaleXContinuous
import jetbrains.letsPlot.scale.scaleYContinuous
import jetbrains.letsPlot.scale.xlim
import jetbrains.letsPlot.scale.ylim
import jetbrains.letsPlot.theme
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.annotations.DataSchema
import org.jetbrains.kotlinx.dataframe.api.*
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

@DataSchema
interface OverlapScatterRow {
    val cdr3: AminoAcidSequence?
    val count1: Double
    val count2: Double
    val frac1: Double
    val frac2: Double
    val geomMean: Double
}

private data class OverlapScatterRowImpl(
    override val cdr3: AminoAcidSequence?,
    override val count1: Double,
    override val count2: Double
) : OverlapScatterRow {
    constructor(gr: OverlapGroup<Clone>) : this(
        gr.firstOrNull { it.size > 0 }?.get(0)?.getAAFeature(GeneFeature.CDR3),
        gr.getBySample(0).sumOf { it.count },
        gr.getBySample(1).sumOf { it.count }
    )

    override val frac1 = Double.NaN
    override val frac2 = Double.NaN
    override val geomMean = Double.NaN
}


object OverlapScatter {
    /**
     * Imports data into DataFrame
     **/
    fun dataFrame(overlapData: OutputPort<OverlapGroup<Clone>>) =
        run {
            val rows = mutableListOf<OverlapScatterRow>()
            for (gr in CUtils.it(overlapData)) {
                if (gr.size() != 2)
                    throw IllegalArgumentException("Expected pair of samples got " + gr.size())
                rows.add(OverlapScatterRowImpl(gr))
            }

            var df = rows.toDataFrame()
            val sum1 = df.count1.sum()
            val sum2 = df.count2.sum()

            df = df.update { frac1 }.with { count1 / sum1 }
            df = df.update { frac2 }.with { count2 / sum2 }
            df = df.update { geomMean }.with { sqrt(frac1 * frac2) }
            df
        }

    data class PlotParameters(
        val xTitle: String,
        val yTitle: String,
        val method: CorrelationMethod,
        val log10: Boolean
    )

    fun plot(
        _df: DataFrame<OverlapScatterRow>,
        par: PlotParameters,
    ) = run {
        var df = _df
        if (par.log10) {
            df = df.update { frac1 }.with { log10(frac1) }
            df = df.update { frac2 }.with { log10(frac2) }
        }
        val plt = GGScatter(
            df.drop { count1 == 0.0 || count2 == 0.0 },
            x = OverlapScatterRow::frac1.name,
            y = OverlapScatterRow::frac2.name,
            alpha = 0.5
        ) {
            size = OverlapScatterRow::geomMean.name
        }
        val min = min(df.frac1.min(), df.frac2.min())
        val max = max(df.frac1.max(), df.frac2.max())
        plt += xlim(min to max)
        plt += ylim(min to max)

        plt += statCor(method = par.method)
        plt += xlab(par.xTitle)
        plt += ylab(par.yTitle)

        val xScale = scaleXContinuous(
            limits = min to max,
//            format = ".0f"
        )

        val yScale = scaleYContinuous(
            limits = min to max,
//            format = ".0f"
        )

        plt += theme().legendPositionNone()
        plt += xScale
        plt += yScale

        plt.plot
    }
}