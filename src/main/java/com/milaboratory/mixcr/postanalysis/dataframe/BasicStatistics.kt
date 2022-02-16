package com.milaboratory.mixcr.postanalysis.dataframe

import com.milaboratory.miplots.stat.util.PValueCorrection
import com.milaboratory.miplots.stat.util.RefGroup
import com.milaboratory.miplots.stat.util.TestMethod
import com.milaboratory.miplots.stat.xcontinious.CorrelationMethod
import com.milaboratory.miplots.stat.xcontinious.GGScatter
import com.milaboratory.miplots.stat.xcontinious.plusAssign
import com.milaboratory.miplots.stat.xcontinious.statCor
import com.milaboratory.miplots.stat.xdiscrete.GGBoxPlot
import com.milaboratory.miplots.stat.xdiscrete.LabelFormat
import com.milaboratory.miplots.stat.xdiscrete.plusAssign
import com.milaboratory.miplots.stat.xdiscrete.statCompareMeans
import com.milaboratory.miplots.writePDF
import com.milaboratory.mixcr.postanalysis.PostanalysisResult
import jetbrains.letsPlot.facet.facetWrap
import jetbrains.letsPlot.geom.geomBoxplot
import jetbrains.letsPlot.intern.Plot
import jetbrains.letsPlot.label.ggtitle
import jetbrains.letsPlot.label.xlab
import jetbrains.letsPlot.letsPlot
import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.annotations.DataSchema
import org.jetbrains.kotlinx.dataframe.api.*
import org.jetbrains.kotlinx.dataframe.io.read
import java.nio.file.Path

/**
 * DataFrame row for single statistical char group
 */
@DataSchema
@Suppress("UNCHECKED_CAST")
data class BasicStatRow(
    /** Sample ID */
    val sample: String,

    /** Metric name */
    val metric: String,

    /** Value */
    val value: Double,
)

object BasicStatistics {
    /**
     * Imports data into DataFrame
     **/
    fun dataFrame(
        paResult: PostanalysisResult,
        metricsFilter: List<String>?,
    ): DataFrame<BasicStatRow> = run {
        val data = mutableListOf<BasicStatRow>()

        val mf = metricsFilter?.toSet()
        for ((_, charData) in paResult.data) {
            for ((sampleId, keys) in charData.data) {
                for (metric in keys.data) {
                    val key = metric.key.toString()
                    if (mf != null && !mf.contains(key)) {
                        continue
                    }
                    data += BasicStatRow(sampleId, key, metric.value)
                }
            }
        }

        data.toDataFrame()
    }

    /**
     * Imports data into DataFrame
     **/
    fun dataFrame(
        paResult: PostanalysisResult,
        metricsFilter: List<String>?,
        metadataPath: String?,
    ) = run {
        var df = dataFrame(paResult, metricsFilter)
        if (metadataPath != null)
            df = df.withMetadata(DataFrame.read(metadataPath))
        df
    }

    /**
     * Attaches metadata to statistics
     **/
    fun DataFrame<BasicStatRow>.withMetadata(metadata: AnyFrame) = run {
        attachMetadata(this, "sample", metadata, "sample").cast<BasicStatRow>()
    }

    data class PlotParameters(
        val primaryGroup: String? = null,
        val secondaryGroup: String? = null,
        val facetBy: String? = null,
        val showOverallPValue: Boolean = true,
        val showPairwisePValue: Boolean = true,
        val refGroup: RefGroup? = null,
        val hideNS: Boolean = false,
        val overallPValueFormat: LabelFormat? = null,
        val refPValueFormat: LabelFormat? = null,
        val comparisonsPValueFormat: LabelFormat? = null,
        val paired: Boolean = false,
        val method: TestMethod = TestMethod.Wilcoxon,
        val multipleGroupsMethod: TestMethod = TestMethod.KruskalWallis,
        val pAdjustMethod: PValueCorrection.Method? = PValueCorrection.Method.Bonferroni,
        val correlationMethod: CorrelationMethod = CorrelationMethod.Pearson,
    )

    fun plots(
        df: DataFrame<BasicStatRow>,
        pp: PlotParameters,
    ) = df.metric.distinct().toList()
        .map { mt -> plot(df.filter { metric == mt }, pp) + ggtitle(mt) }

    fun write(path: String, plots: List<Plot>) {
        writePDF(Path.of(path), plots)
    }

    fun plot(
        df: DataFrame<BasicStatRow>,
        pp: PlotParameters,
    ) =
        if (pp.primaryGroup == null) {
            val data = df.add(List(df.rowsCount()) { "" }.toColumn("__x__")).toMap()
            var plt = letsPlot(data) {
                x = "__x__"
                y = BasicStatRow::value.name
            }
            plt += geomBoxplot()
            if (pp.facetBy != null)
                plt += facetWrap(pp.facetBy)

            plt += xlab("")
            plt
        } else if (df.isNumeric(pp.primaryGroup)) {
            val plt = GGScatter(
                df,
                x = pp.primaryGroup,
                y = BasicStatRow::value.name,
                facetBy = pp.facetBy,
                facetNRow = 1,
            ) {
                shape = pp.secondaryGroup
                color = pp.secondaryGroup
                linetype = pp.secondaryGroup
            }

            plt += statCor(method = pp.correlationMethod)

            plt.plot
        } else {
            val plt = GGBoxPlot(
                df,
                x = pp.primaryGroup,
                y = BasicStatRow::value.name,
                facetBy = pp.facetBy,
                facetNRow = 1,
            ) {
                fill = pp.secondaryGroup ?: pp.primaryGroup
            }

            if (pp.showPairwisePValue)
                plt += statCompareMeans(
                    method = pp.method,
                    multipleGroupsMethod = pp.multipleGroupsMethod,
                    pAdjustMethod = pp.pAdjustMethod,
                    paired = pp.paired,
                    hideNS = pp.hideNS,
                    labelFormat = pp.comparisonsPValueFormat,
                    allComparisons = true
                )

            if (pp.refGroup != null)
                plt += statCompareMeans(
                    method = pp.method,
                    multipleGroupsMethod = pp.multipleGroupsMethod,
                    pAdjustMethod = pp.pAdjustMethod,
                    paired = pp.paired,
                    hideNS = pp.hideNS,
                    labelFormat = pp.refPValueFormat,
                    refGroup = pp.refGroup
                )

            if (pp.showOverallPValue)
                plt += statCompareMeans(
                    method = pp.method,
                    multipleGroupsMethod = pp.multipleGroupsMethod,
                    labelFormat = pp.overallPValueFormat,
                )

            plt.plot
        }
}
