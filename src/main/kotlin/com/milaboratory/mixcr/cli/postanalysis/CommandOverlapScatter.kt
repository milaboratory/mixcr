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
import com.milaboratory.mixcr.cli.CommonDescriptions
import com.milaboratory.mixcr.cli.MiXCRCommand
import com.milaboratory.mixcr.postanalysis.SetPreprocessor
import com.milaboratory.mixcr.postanalysis.overlap.OverlapUtil
import com.milaboratory.mixcr.postanalysis.plots.OverlapScatter
import com.milaboratory.mixcr.postanalysis.plots.OverlapScatter.dataFrame
import com.milaboratory.mixcr.postanalysis.plots.OverlapScatter.plot
import com.milaboratory.mixcr.postanalysis.preproc.ElementPredicate.IncludeChains
import com.milaboratory.mixcr.postanalysis.preproc.OverlapPreprocessorAdapter
import com.milaboratory.mixcr.postanalysis.ui.DownsamplingParameters
import com.milaboratory.util.SmartProgressReporter
import io.repseq.core.Chains
import io.repseq.core.Chains.NamedChains
import picocli.CommandLine
import java.nio.file.Path
import java.nio.file.Paths

@CommandLine.Command(name = "overlapScatterPlot", separator = " ", description = ["Plot overlap scatter-plot."])
class CommandOverlapScatter : MiXCRCommand() {
    @CommandLine.Parameters(description = ["cloneset_1.{clns|clna}..."], index = "0")
    lateinit var in1: String

    @CommandLine.Parameters(description = ["cloneset_2.{clns|clna}..."], index = "1")
    lateinit var in2: String

    @CommandLine.Parameters(description = ["output.[pdf|eps|png|jpeg]"], index = "2")
    lateinit var out: String

    @CommandLine.Option(description = ["Chains to export"], names = ["--chains"])
    var chains: List<String>? = null

    @CommandLine.Option(description = [CommonDescriptions.ONLY_PRODUCTIVE], names = ["--only-productive"])
    var onlyProductive = false

    @CommandLine.Option(description = [CommonDescriptions.DOWNSAMPLING], names = ["--downsampling"], required = true)
    lateinit var downsampling: String

    @CommandLine.Option(description = [CommonDescriptions.OVERLAP_CRITERIA], names = ["--criteria"])
    var overlapCriteria = "CDR3|AA|V|J"

    @CommandLine.Option(
        description = ["Correlation method to use. Possible value: pearson, kendal, spearman"],
        names = ["--method"]
    )
    var method = "pearson"

    @CommandLine.Option(description = ["Do not apply log10 to clonotype frequencies"], names = ["--no-log"])
    var noLog = false

    override fun getInputFiles(): List<String> = listOf(in1, in2)

    override fun getOutputFiles(): List<String> = listOf(out)

    private fun outputPath(chains: NamedChains): Path {
        if (chains == Chains.ALL_NAMED) return Paths.get(out)
        val fName = fName(out)
        val fileNameWithoutExtension = fName.substring(0, fName.length - 3)
        return Paths.get(out).toAbsolutePath().parent.resolve(
            "$fileNameWithoutExtension${chains.name}.pdf"
        )
    }

    override fun run0() {
        val parameters = DownsamplingParameters.parse(
            downsampling,
            CommandPa.extractTagsInfo(inputFiles, !downsampling.equals("none", ignoreCase = true)),
            false, onlyProductive
        )
        for (curChains in chains?.map { Chains.getNamedChains(it) } ?: Chains.DEFAULT_EXPORT_CHAINS_LIST) {
            val downsampling = OverlapPreprocessorAdapter.Factory(parameters.getPreprocessor(curChains.chains))
            val dataset = SetPreprocessor.processDatasets(
                downsampling.newInstance(),
                OverlapUtil.overlap(
                    listOf(in1, in2),
                    IncludeChains(curChains.chains),
                    OverlapUtil.parseCriteria(overlapCriteria).ordering()
                )
            ).first()
            val plotParameters = OverlapScatter.PlotParameters(
                fName(in1),
                fName(in2),
                parse(method),
                !noLog
            )
            dataset.mkElementsPort().use { port ->
                SmartProgressReporter.startProgressReport("Processing ${curChains.name}", port)
                val df = dataFrame(port)
                if (df.rowsCount() == 0) return@use
                val plot = plot(df, plotParameters)
                writeFile(outputPath(curChains), plot)
            }
        }
    }

    private fun fName(file: String): String = Paths.get(file).toAbsolutePath().fileName.toString()
}
