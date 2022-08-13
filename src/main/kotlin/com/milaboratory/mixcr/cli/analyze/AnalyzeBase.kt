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
package com.milaboratory.mixcr.cli.analyze

import com.milaboratory.mixcr.cli.CommandAlign
import com.milaboratory.mixcr.cli.CommandAssemble
import com.milaboratory.mixcr.cli.CommandExport
import com.milaboratory.mixcr.cli.MiXCRCommand
import com.milaboratory.mixcr.cli.analyze.AnalyzeUtil.runCommand
import io.repseq.core.Chains
import jetbrains.datalore.plot.config.asMutable
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters

abstract class AnalyzeBase : MiXCRCommand() {
    @Parameters(description = ["input_file1 [input_file2] analysisOutputName"])
    var inOut: List<String> = ArrayList()

    @Option(
        description = ["Processing threads"],
        names = ["-t", "--threads"]
    )
    var threads: Int = Runtime.getRuntime().availableProcessors()

    @Option(
        names = ["-r", "--report"],
        description = ["Report file path"]
    )
    var report: String? = null

    @Option(
        names = ["-j", "--json-report"],
        description = ["Output json reports."]
    )
    var jsonReport: String? = null

    @Option(
        names = ["-b", "--library"],
        description = ["V/D/J/C gene library"]
    )
    var library = "default"

    /** output prefix */
    val prefix get() = inOut.last()

    /** report file */
    private val reportFile get() = report ?: "$prefix.report"

    /** json report file */
    private val jsonReportFile get() = jsonReport ?: "$prefix.report.jsonl"

    fun inheritOptions(options: MutableList<String>) {
        // add report file
        options += "--report $reportFile"

        // add json report file
        if (jsonReport != null)
            options += "--json-report $jsonReportFile"

        // add force overwrite
        if (forceOverwrite)
            options += "--force-overwrite"
    }

    ///////////////////////////////// common commands /////////////////////////////////

    @Option(
        names = ["--align"],
        description = ["Additional parameters for align step specified with double quotes (e.g --align \"--limit 1000\" --align \"-OminSumScore=100\" etc."],
        arity = "1"
    )
    var alignOverrides: List<String> = emptyList()
    val alignOps = mutableListOf<String>()

    /** run align */
    fun runAlign(output: String): String {
        alignOps += """
            --threads $threads
            --library $library
        """.trimIndent()

        // reports & commons
        inheritOptions(alignOps)
        // additional parameters
        alignOps += alignOverrides
        // add input output
        alignOps += inOut.subList(0, inOut.size - 1)
        alignOps += output

        runCommand(CommandAlign(), spec, alignOps)

        return output
    }

    @Option(
        names = ["--assemble"],
        description = ["Additional parameters for assemble step specified with double quotes (e.g --assemble \"-OassemblingFeatures=[V5UTR+L1+L2+FR1,FR3+CDR3]\" --assemble \"-ObadQualityThreshold=0\" etc."],
        arity = "1"
    )
    var assembleOverrides: List<String> = java.util.ArrayList()
    val assembleOps = mutableListOf<String>()

    /** run assemble */
    fun runAssemble(input: String, output: String): String {
        // reports & commons
        inheritOptions(assembleOps)
        // additional parameters
        assembleOps += assembleOverrides
        // add input output
        assembleOps += "$input $output"

        runCommand(CommandAssemble(), spec, assembleOps)

        return output
    }

    @Option(
        names = ["--no-export"],
        description = ["Do not export clonotypes to tab-delimited file."]
    )
    var noExport = false

    @Option(
        names = ["--impute-germline-on-export"],
        description = ["Export germline segments"]
    )
    var imputeGermline = false

    @Option(
        names = ["--only-productive"],
        description = ["Filter out-of-frame sequences and clonotypes with stop-codons in " +
                "clonal sequence export"]
    )
    var onlyProductive = false

    @Option(
        names = ["--export"],
        description = ["Additional parameters for exportClones step specified with double quotes (e.g --export \"-p full\" --export \"-cloneId\" etc."],
        arity = "1"
    )
    var exportOverrides: List<String> = ArrayList()
    val exportOps = mutableListOf<String>()

    /** run export */
    fun runExport(additionalOps: List<String>, input: String, output: String): String {
        // copy ops
        val exportOps = (this.exportOps.toList() + additionalOps).asMutable()
        // always force overwrite
        exportOps += "--force-overwrite"

        if (onlyProductive)
            exportOps += "--filter-out-of-frames --filter-stops"

        exportOps += if (imputeGermline)
            "-p fullImputed"
        else
            "-p full"

        // additional parameters
        exportOps += exportOverrides
        // add input output
        exportOps += "$input $output"

        runCommand(CommandExport.mkClonesSpec(), exportOps)

        return output
    }

    fun runExportPerEachChain(receptorType: Chains, clnx: String){
        for (exportChains in Chains.DEFAULT_EXPORT_CHAINS_LIST) {
            if (exportChains.chains.intersects(receptorType)) {
                runExport(
                    listOf("--chains ${exportChains.name}"),
                    clnx,
                    "$prefix.clonotypes.${exportChains.name}.tsv"
                )
            }
        }
    }
}