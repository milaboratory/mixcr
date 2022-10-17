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

import com.milaboratory.miplots.StandardPlots.PlotType
import com.milaboratory.miplots.stat.util.PValueCorrection
import com.milaboratory.miplots.stat.util.RefGroup
import com.milaboratory.miplots.stat.util.TestMethod
import com.milaboratory.miplots.stat.xcontinious.CorrelationMethod
import com.milaboratory.miplots.stat.xdiscrete.LabelFormat.Companion.Formatted
import com.milaboratory.miplots.stat.xdiscrete.LabelFormat.Companion.Significance
import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.cli.EnumTypes
import com.milaboratory.mixcr.cli.EnumTypes.PValueCorrectionMethodCandidatesWithNone
import com.milaboratory.mixcr.cli.EnumTypes.PValueCorrectionMethodConverterWithNone
import com.milaboratory.mixcr.cli.EnumTypes.PlotTypeCandidates
import com.milaboratory.mixcr.cli.MultipleMetricsInOneFile
import com.milaboratory.mixcr.postanalysis.diversity.DiversityMeasure
import com.milaboratory.mixcr.postanalysis.plots.BasicStatistics
import com.milaboratory.mixcr.postanalysis.plots.BasicStatistics.dataFrame
import com.milaboratory.mixcr.postanalysis.plots.BasicStatistics.plots
import com.milaboratory.mixcr.postanalysis.ui.PostanalysisParametersIndividual
import picocli.CommandLine.Command
import picocli.CommandLine.Help.Visibility.ALWAYS
import picocli.CommandLine.Option
import java.util.*

abstract class CommandPaExportPlotsBasicStatistics : MultipleMetricsInOneFile, CommandPaExportPlots() {
    @Option(
        description = ["Plot type. Possible values: \${COMPLETION-CANDIDATES}"],
        names = ["--plot-type"],
        completionCandidates = PlotTypeCandidates::class
    )
    var plotType: PlotType? = null

    @Option(
        description = ["Specify metadata column used to group datasets."],
        names = ["-p", "--primary-group"],
        paramLabel = "<meta>"
    )
    var primaryGroup: String? = null
        get() = field?.lowercase()

    @Option(
        description = ["List of comma separated primary group values."],
        names = ["-pv", "--primary-group-values"],
        split = ",",
        paramLabel = "<value>"
    )
    var primaryGroupValues: List<String>? = null

    @Option(
        description = ["Secondary group."],
        names = ["-s", "--secondary-group"],
        paramLabel = "<meta>"
    )
    var secondaryGroup: String? = null
        get() = field?.lowercase()

    @Option(
        description = ["List of comma separated secondary group values."],
        names = ["-sv", "--secondary-group-values"],
        split = ",",
        paramLabel = "<value>"
    )
    var secondaryGroupValues: List<String>? = null

    @Option(
        description = ["Specify metadata column to use for plotting facets."],
        names = ["--facet-by"],
        paramLabel = "<meta>"
    )
    var facetBy: String? = null
        get() = field?.lowercase()

    abstract var metrics: List<String>?

    @Option(description = ["Hide overall p-value."], names = ["--hide-overall-p-value"])
    var hideOverallPValue = false

    @Option(description = ["Show pairwise p-value comparisons."], names = ["--pairwise-comparisons"])
    var pairwiseComparisons = false

    @Option(
        description = ["Reference group for compare means statistics. Can be 'all' or some specific value."],
        names = ["--ref-group"],
        paramLabel = "<refGroup>"
    )
    var refGroupParam: String? = null

    @Option(description = ["Hide non-significant observations."], names = ["--hide-non-significant"])
    var hideNS = false

    @Option(description = ["Do paired analysis"], names = ["--paired"])
    var paired = false

    @Option(
        description = ["Statistical test method. Available methods: \${COMPLETION-CANDIDATES}."],
        names = ["--method"],
        showDefaultValue = ALWAYS,
        paramLabel = "<method>",
        completionCandidates = EnumTypes.TestMethodCandidates::class
    )
    var method: TestMethod = TestMethod.Wilcoxon

    @Option(
        description = ["Test method for multiple groups comparison. Available methods: \${COMPLETION-CANDIDATES}."],
        names = ["--method-multiple-groups"],
        showDefaultValue = ALWAYS,
        paramLabel = "<method>",
        completionCandidates = EnumTypes.TestMethodCandidates::class
    )
    var methodForMultipleGroups: TestMethod = TestMethod.KruskalWallis

    @Option(
        description = ["Method used to adjust p-values. Available methods: \${COMPLETION-CANDIDATES}."],
        names = ["--p-adjust-method"],
        showDefaultValue = ALWAYS,
        completionCandidates = PValueCorrectionMethodCandidatesWithNone::class,
        converter = [PValueCorrectionMethodConverterWithNone::class],
        paramLabel = "<method>"
    )
    var pAdjustMethod: PValueCorrection.Method = PValueCorrection.Method.Holm

    @Option(
        description = ["Show significance levels instead of p-values ( `ns` for p-value >= 0.05, `***` for p-value < 0.0001,  `**` for p-value < 0.001, `*` in other case)."],
        names = ["--show-significance"]
    )
    var showSignificance = false

    abstract fun group(): String

    abstract fun metricsFilter(): (String) -> Boolean

    override fun validate() {
        super.validate()
        validateNonPdf(out, metrics)
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
            method,
            methodForMultipleGroups,
            pAdjustMethod,
            CorrelationMethod.Pearson
        )
        val plots = plots(dataFrame, par)
        writePlots(result.group, plots)
    }

    @Command(
        description = ["Export CDR3 metrics"]
    )
    class ExportCDR3Metrics : CommandPaExportPlotsBasicStatistics() {
        class MetricValues @JvmOverloads constructor(
            private val candidates: List<String> = listOf(*PostanalysisParametersIndividual.SUPPORTED_CDR3_METRICS)
        ) : Iterable<String> by candidates

        @Option(
            description = [
                "Output only specified list of metrics.",
                "Possible values are: \${COMPLETION-CANDIDATES}"
            ],
            names = ["--metric"],
            split = ",",
            paramLabel = "<metric>",
            completionCandidates = MetricValues::class
        )
        override var metrics: List<String>? = null

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

    @Command(
        description = ["Export diversity metrics"]
    )
    class ExportDiversity : CommandPaExportPlotsBasicStatistics() {
        class MetricValues @JvmOverloads constructor(
            private val candidates: Collection<String> = possibleMetrics().keys
        ) : Iterable<String> by candidates

        @Option(
            description = [
                "Output only specified list of metrics.",
                "Possible values are: \${COMPLETION-CANDIDATES}"
            ],
            names = ["--metric"],
            split = ",",
            paramLabel = "<metric>",
            completionCandidates = MetricValues::class
        )
        override var metrics: List<String>? = null

        override fun group(): String = PostanalysisParametersIndividual.Diversity

        override fun metricsFilter(): (String) -> Boolean {
            val metrics = metrics
            if (metrics.isNullOrEmpty()) return { true }
            val possibleMetrics = possibleMetrics().mapKeys { it.key.lowercase(Locale.getDefault()) }
            for (m in metrics) {
                require(m.lowercase(Locale.getDefault()) in possibleMetrics) { "Unknown metric: $m" }
            }
            val metricsAsSet = metrics
                .map { it.lowercase(Locale.getDefault()) }
                .map { key -> possibleMetrics[key] }
                .toSet()
            return { it in metricsAsSet }
        }

        companion object {
            private fun possibleMetrics(): Map<String, String> = buildMap {
                put("observed", DiversityMeasure.Observed.name)
                put("shannonWiener", DiversityMeasure.ShannonWiener.name)
                put("chao1", DiversityMeasure.Chao1.name)
                put("normalizedShannonWienerIndex", DiversityMeasure.NormalizedShannonWienerIndex.name)
                put("inverseSimpsonIndex", DiversityMeasure.InverseSimpsonIndex.name)
                put("giniIndex", DiversityMeasure.GiniIndex.name)
                put("d50", "d50")
                put("efronThisted", DiversityMeasure.EfronThisted.name)
            }
        }
    }
}
