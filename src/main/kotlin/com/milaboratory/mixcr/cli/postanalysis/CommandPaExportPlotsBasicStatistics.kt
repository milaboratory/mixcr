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

import com.milaboratory.miplots.stat.util.PValueCorrection
import com.milaboratory.miplots.stat.util.RefGroup
import com.milaboratory.miplots.stat.util.TestMethod
import com.milaboratory.miplots.stat.xcontinious.CorrelationMethod
import com.milaboratory.miplots.stat.xdiscrete.LabelFormat.Companion.Formatted
import com.milaboratory.miplots.stat.xdiscrete.LabelFormat.Companion.Significance
import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.postanalysis.diversity.DiversityMeasure
import com.milaboratory.mixcr.postanalysis.plots.BasicStatistics
import com.milaboratory.mixcr.postanalysis.plots.BasicStatistics.dataFrame
import com.milaboratory.mixcr.postanalysis.plots.BasicStatistics.parsePlotType
import com.milaboratory.mixcr.postanalysis.plots.BasicStatistics.plots
import com.milaboratory.mixcr.postanalysis.ui.PostanalysisParametersIndividual
import picocli.CommandLine
import java.util.*

abstract class CommandPaExportPlotsBasicStatistics : CommandPaExportPlots() {
    @CommandLine.Option(
        description = ["Plot type. Possible values: boxplot, boxplot-bindot, boxplot-jitter, " +
                "lineplot, lineplot-bindot, lineplot-jitter, " +
                "violin, violin-bindot, barplot, barplot-stacked, scatter"],
        names = ["--plot-type"]
    )
    var plotType: String? = null

    @CommandLine.Option(description = ["Primary group"], names = ["-p", "--primary-group"])
    var primaryGroup: String? = null

    @CommandLine.Option(
        description = ["List of comma separated primary group values"],
        names = ["-pv", "--primary-group-values"],
        split = ","
    )
    var primaryGroupValues: List<String>? = null

    @CommandLine.Option(description = ["Secondary group"], names = ["-s", "--secondary-group"])
    var secondaryGroup: String? = null

    @CommandLine.Option(
        description = ["List of comma separated secondary group values"],
        names = ["-sv", "--secondary-group-values"],
        split = ","
    )
    var secondaryGroupValues: List<String>? = null

    @CommandLine.Option(description = ["Facet by"], names = ["--facet-by"])
    var facetBy: String? = null

    @CommandLine.Option(description = ["Select specific metrics to export."], names = ["--metric"], split = ",")
    var metrics: List<String>? = null

    @CommandLine.Option(description = ["Hide overall p-value"], names = ["--hide-overall-p-value"])
    var hideOverallPValue = false

    @CommandLine.Option(description = ["Show pairwise p-value comparisons"], names = ["--pairwise-comparisons"])
    var pairwiseComparisons = false

    @CommandLine.Option(
        description = ["Reference group. Can be \"all\" or some specific value."],
        names = ["--ref-group"],
        paramLabel = "refGroup"
    )
    var refGroupParam: String? = null

    @CommandLine.Option(description = ["Hide non-significant observations"], names = ["--hide-non-significant"])
    var hideNS = false

    @CommandLine.Option(description = ["Do paired analysis"], names = ["--paired"])
    var paired = false

    @CommandLine.Option(
        description = ["Test method. Default is Wilcoxon. Available methods: Wilcoxon, ANOVA, TTest, KruskalWallis, KolmogorovSmirnov"],
        names = ["--method"]
    )
    var method: String = "Wilcoxon"

    @CommandLine.Option(
        description = ["Test method for multiple groups comparison. Default is KruskalWallis. Available methods: ANOVA, KruskalWallis, KolmogorovSmirnov"],
        names = ["--method-multiple-groups"]
    )
    var methodForMultipleGroups: String = "KruskalWallis"

    @CommandLine.Option(
        description = ["Method used to adjust p-values. Default is Holm. Available methods: none, BenjaminiHochberg, BenjaminiYekutieli, Bonferroni, Hochberg, Holm, Hommel"],
        names = ["--p-adjust-method"]
    )
    var pAdjustMethod: String = "Holm"

    @CommandLine.Option(description = ["Show significance level instead of p-values"], names = ["--show-significance"])
    var showSignificance = false

    abstract fun group(): String

    abstract fun metricsFilter(): (String) -> Boolean

    override fun validate() {
        super.validate()
        if (!out.endsWith("pdf") && metrics.isNullOrEmpty()) {
            val ext = out.takeLast(3).uppercase(Locale.getDefault())
            throwValidationExceptionKotlin(
                "For export in $ext Use --metrics option to specify only one metric to export. Or use PDF format for export."
            )
        }
    }

    override fun run(result: PaResultByGroup) {
        val ch = result.schema.getGroup<Clone>(group())
        val dataFrame = dataFrame(result.result.forGroup(ch), metadataDf, metricsFilter())
            .filterByMetadata()
        if (dataFrame.rowsCount() == 0) return
        val refGroup: RefGroup? = when {
            refGroupParam == "all" -> RefGroup.all
            refGroupParam != null -> RefGroup.of(refGroupParam!!)
            else -> null
        }
        val plotType = parsePlotType(plotType)
        val labelFormat = if (showSignificance) Significance else Formatted()
        val par = BasicStatistics.PlotParameters(
            plotType,
            primaryGroup,
            secondaryGroup,
            primaryGroupValues,
            secondaryGroupValues,
            facetBy,
            !hideOverallPValue,
            pairwiseComparisons,
            refGroup,
            hideNS,
            null,
            labelFormat,
            labelFormat,
            paired,
            TestMethod.valueOf(method),
            TestMethod.valueOf(methodForMultipleGroups),
            if (pAdjustMethod == "none") null else PValueCorrection.Method.valueOf(pAdjustMethod),
            CorrelationMethod.Pearson
        )
        val plots = plots(dataFrame, par)
        writePlots(result.group, plots)
    }

    @CommandLine.Command(
        name = "cdr3metrics",
        sortOptions = false,
        separator = " ",
        description = ["Export CDR3 metrics"]
    )
    class ExportCDR3Metrics : CommandPaExportPlotsBasicStatistics() {
        override fun group(): String = PostanalysisParametersIndividual.CDR3Metrics

        override fun metricsFilter(): (String) -> Boolean {
            val metrics = metrics
            if (metrics.isNullOrEmpty()) return { true }
            val set = setOf(*PostanalysisParametersIndividual.SUPPORTED_CDR3_METRICS)
            metrics.forEach {
                require(it.lowercase(Locale.getDefault()) in set) { "Unknown metric: $it" }
            }
            val metricsAsSet = metrics.toSet()
            return { it in metricsAsSet }
        }
    }

    @CommandLine.Command(
        name = "diversity",
        sortOptions = false,
        separator = " ",
        description = ["Export diversity metrics"]
    )
    class ExportDiversity : CommandPaExportPlotsBasicStatistics() {
        override fun group(): String = PostanalysisParametersIndividual.Diversity

        override fun metricsFilter(): (String) -> Boolean {
            val metrics = metrics
            if (metrics.isNullOrEmpty()) return { true }
            val map = buildMap {
                put("chao1".lowercase(Locale.getDefault()), DiversityMeasure.Chao1.name)
                put("efronThisted".lowercase(Locale.getDefault()), DiversityMeasure.EfronThisted.name)
                put("inverseSimpsonIndex".lowercase(Locale.getDefault()), DiversityMeasure.InverseSimpsonIndex.name)
                put("giniIndex".lowercase(Locale.getDefault()), DiversityMeasure.GiniIndex.name)
                put("observed".lowercase(Locale.getDefault()), DiversityMeasure.Observed.name)
                put("shannonWiener".lowercase(Locale.getDefault()), DiversityMeasure.ShannonWiener.name)
                put(
                    "normalizedShannonWienerIndex".lowercase(Locale.getDefault()),
                    DiversityMeasure.NormalizedShannonWienerIndex.name
                )
                put("d50", "d50")
            }
            for (m in metrics) {
                require(m.lowercase(Locale.getDefault()) in map) { "Unknown metric: $m" }
            }
            val metricsAsSet = metrics
                .map { it.lowercase(Locale.getDefault()) }
                .map { key -> map[key] }
                .toSet()
            return { it in metricsAsSet }
        }
    }
}
