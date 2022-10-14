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

import com.milaboratory.miplots.stat.xcontinious.CorrelationMethod.Companion.parse
import com.milaboratory.miplots.writeFile
import com.milaboratory.mixcr.cli.ChainsUtil
import com.milaboratory.mixcr.cli.ChainsUtil.name
import com.milaboratory.mixcr.cli.ChainsUtil.toPath
import com.milaboratory.mixcr.cli.CommonDescriptions
import com.milaboratory.mixcr.cli.MiXCRCommandWithOutputs
import com.milaboratory.mixcr.postanalysis.SetPreprocessor
import com.milaboratory.mixcr.postanalysis.overlap.OverlapUtil
import com.milaboratory.mixcr.postanalysis.plots.OverlapScatter
import com.milaboratory.mixcr.postanalysis.plots.OverlapScatter.dataFrame
import com.milaboratory.mixcr.postanalysis.plots.OverlapScatter.plot
import com.milaboratory.mixcr.postanalysis.preproc.ChainsFilter
import com.milaboratory.mixcr.postanalysis.preproc.ElementPredicate.IncludeChains
import com.milaboratory.mixcr.postanalysis.preproc.OverlapPreprocessorAdapter
import com.milaboratory.mixcr.postanalysis.ui.DownsamplingParameters
import com.milaboratory.util.SmartProgressReporter
import picocli.CommandLine.Command
import picocli.CommandLine.Help.Visibility.ALWAYS
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.nio.file.Path
import kotlin.io.path.nameWithoutExtension

@Command(description = ["Plot overlap scatter-plot."])
class CommandOverlapScatter : MiXCRCommandWithOutputs() {
    @Parameters(description = ["cloneset_1.(clns|clna)"], index = "0")
    lateinit var in1: Path

    @Parameters(description = ["cloneset_2.(clns|clna)"], index = "1")
    lateinit var in2: Path

    @Parameters(description = ["output.(pdf|eps|png|jpeg)"], index = "2")
    lateinit var out: Path

    @Option(description = ["Chains to export"], names = ["--chains"], split = ",")
    var chains: Set<String>? = null

    @Option(description = [CommonDescriptions.ONLY_PRODUCTIVE], names = ["--only-productive"])
    var onlyProductive = false

    @Option(description = [CommonDescriptions.DOWNSAMPLING], names = ["--downsampling"], required = true)
    lateinit var downsampling: String

    @Option(
        description = ["Overlap criteria."],
        names = ["--criteria"],
        showDefaultValue = ALWAYS,
        paramLabel = "<s>"
    )
    var overlapCriteria = "CDR3|AA|V|J"

    @Option(
        description = ["Correlation method to use. Possible value: pearson, kendal, spearman"],
        names = ["--method"]
    )
    var method = "pearson"

    @Option(description = ["Do not apply log10 to clonotype frequencies"], names = ["--no-log"])
    var noLog = false

    override val inputFiles
        get() = listOf(in1, in2)

    override val outputFiles
        get() = listOf(out)

    override fun run0() {
        val parameters = DownsamplingParameters.parse(
            downsampling,
            CommandPa.extractTagsInfo(inputFiles, !downsampling.equals("none", ignoreCase = true)),
            false, onlyProductive
        )

        var chainsToProcess = ChainsUtil.allChainsFromClnx(listOf(in1, in2))
        chainsToProcess = chains?.let { ChainsFilter.parseChainsList(it) } ?: chainsToProcess

        for (chain in chainsToProcess) {
            val downsampling = OverlapPreprocessorAdapter.Factory(parameters.getPreprocessor(chain))
            val dataset = SetPreprocessor.processDatasets(
                downsampling.newInstance(),
                OverlapUtil.overlap(
                    listOf(in1, in2).map { it.toString() },
                    IncludeChains(setOf(chain), false),
                    OverlapUtil.parseCriteria(overlapCriteria).ordering()
                )
            ).first()
            val plotParameters = OverlapScatter.PlotParameters(
                in1.nameWithoutExtension,
                in2.nameWithoutExtension,
                parse(method),
                !noLog
            )
            dataset.mkElementsPort().use { port ->
                SmartProgressReporter.startProgressReport("Processing ${chain.name}", port)
                val df = dataFrame(port)
                if (df.rowsCount() == 0) return@use
                val plot = plot(df, plotParameters)
                writeFile(chain.toPath(out), plot)
            }
        }
    }
}
