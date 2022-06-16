/*
 * Copyright (c) 2014-2019, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
 * (here and after addressed as Inventors)
 * All Rights Reserved
 *
 * Permission to use, copy, modify and distribute any part of this program for
 * educational, research and non-profit purposes, by non-profit institutions
 * only, without fee, and without a written agreement is hereby granted,
 * provided that the above copyright notice, this paragraph and the following
 * three paragraphs appear in all copies.
 *
 * Those desiring to incorporate this work into commercial products or use for
 * commercial purposes should contact MiLaboratory LLC, which owns exclusive
 * rights for distribution of this program for commercial purposes, using the
 * following email address: licensing@milaboratory.com.
 *
 * IN NO EVENT SHALL THE INVENTORS BE LIABLE TO ANY PARTY FOR DIRECT, INDIRECT,
 * SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING LOST PROFITS,
 * ARISING OUT OF THE USE OF THIS SOFTWARE, EVEN IF THE INVENTORS HAS BEEN
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * THE SOFTWARE PROVIDED HEREIN IS ON AN "AS IS" BASIS, AND THE INVENTORS HAS
 * NO OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR
 * MODIFICATIONS. THE INVENTORS MAKES NO REPRESENTATIONS AND EXTENDS NO
 * WARRANTIES OF ANY KIND, EITHER IMPLIED OR EXPRESS, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY OR FITNESS FOR A
 * PARTICULAR PURPOSE, OR THAT THE USE OF THE SOFTWARE WILL NOT INFRINGE ANY
 * PATENT, TRADEMARK OR OTHER RIGHTS.
 */
@file:Suppress("LocalVariableName")

package com.milaboratory.mixcr.cli

import cc.redberry.pipe.CUtils
import cc.redberry.pipe.util.CountingOutputPort
import com.milaboratory.mixcr.basictypes.CloneSetIO
import com.milaboratory.mixcr.trees.ClusteringCriteria.DefaultClusteringCriteria
import com.milaboratory.mixcr.trees.DebugInfo
import com.milaboratory.mixcr.trees.SHMTreeBuilder
import com.milaboratory.mixcr.trees.SHMTreeBuilderParameters
import com.milaboratory.mixcr.trees.SHMTreeBuilderParametersPresets
import com.milaboratory.mixcr.trees.SHMTreesWriter
import com.milaboratory.mixcr.util.XSV
import com.milaboratory.util.ReportUtil
import com.milaboratory.util.SmartProgressReporter
import com.milaboratory.util.TempFileDest
import com.milaboratory.util.TempFileManager
import io.repseq.core.VDJCLibraryRegistry
import org.apache.commons.io.FilenameUtils
import picocli.CommandLine
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths


@CommandLine.Command(
    name = CommandBuildSHMTrees.COMMAND_NAME,
    sortOptions = false,
    separator = " ",
    description = ["Builds SHM trees."]
)
class CommandBuildSHMTrees : ACommandWithOutputMiXCR() {
    @CommandLine.Parameters(arity = "2..*", description = ["input_file.clns [input_file2.clns ....] output_file.tree"])
    private val inOut: List<String> = ArrayList()

    @CommandLine.Option(description = ["Processing threads"], names = ["-t", "--threads"])
    var threads = Runtime.getRuntime().availableProcessors()
        set(value) {
            if (value <= 0) throwValidationException("-t / --threads must be positive")
            field = value
        }

    public override fun getInputFiles(): List<String> = inOut.subList(0, inOut.size - 1)

    override fun getOutputFiles(): List<String> = inOut.subList(inOut.size - 1, inOut.size)

    private val clnsFileNames: List<String>
        get() = inputFiles
    private val outputTreesPath: String
        get() = inOut[inOut.size - 1]

    @CommandLine.Option(description = ["SHM tree builder parameters preset."], names = ["-p", "--preset"])
    var shmTreeBuilderParametersName = "default"

    @CommandLine.Option(names = ["-r", "--report"], description = ["Report file path"])
    var report: String? = null

    @CommandLine.Option(description = ["List of read clone ids to build trees with"], names = ["-i", "--clone-ids"])
    var cloneIds: Set<Int> = HashSet()

    @CommandLine.Option(names = ["-rp", "--report-pdf"], description = ["Pdf report file path"])
    var reportPdf: String? = null

    @CommandLine.Option(description = ["Path to directory to store debug info"], names = ["-d", "--debug"])
    var debugDirectoryPath: String? = null
    var debugDirectory: Path? = null
    private var shmTreeBuilderParameters: SHMTreeBuilderParameters? = null

    @CommandLine.Option(
        description = ["Use system temp folder for temporary files, the output folder will be used if this option is omitted."],
        names = ["--use-system-temp"]
    )
    var useSystemTemp = false

    private fun ensureParametersInitialized() {
        if (shmTreeBuilderParameters == null) {
            shmTreeBuilderParameters = SHMTreeBuilderParametersPresets.getByName(shmTreeBuilderParametersName)
            if (shmTreeBuilderParameters == null) throwValidationException("Unknown parameters: $shmTreeBuilderParametersName")
        }
        if (debugDirectory == null) {
            debugDirectory = when (debugDirectoryPath) {
                null -> Files.createTempDirectory("debug")
                else -> Paths.get(debugDirectoryPath!!)
            }
        }
        debugDirectory!!.toFile().mkdirs()
    }

    override fun validate() {
        super.validate()
        if (report == null) warn("NOTE: report file is not specified, using " + getReportFileName() + " to write report.")
    }

    private fun getReportFileName(): String {
        return report ?: (FilenameUtils.removeExtension(outputTreesPath) + ".report")
    }

    override fun run0() {
        ensureParametersInitialized()
        val cloneReaders = clnsFileNames.map { path ->
            CloneSetIO.mkReader(Paths.get(path), VDJCLibraryRegistry.getDefault())
        }
        require(cloneReaders.isNotEmpty()) { "there is no files to process" }
        //TODO check other common things
        require(
            cloneReaders.map { it.assemblerParameters }.distinct().count() == 1
        ) { "input files must have the same assembler parameters" }
        val tempDest: TempFileDest = TempFileManager.smartTempDestination(outputTreesPath, "", useSystemTemp)
        val shmTreeBuilder = SHMTreeBuilder(
            shmTreeBuilderParameters!!,
            DefaultClusteringCriteria(),
            cloneReaders,
            tempDest,
            cloneIds
        )
        val cloneWrappersCount = shmTreeBuilder.cloneWrappersCount()
        val report = BuildSHMTreeReport()
        val stepsCount = shmTreeBuilderParameters!!.stepsOrder.size + 1
        var sortedClones = CountingOutputPort(shmTreeBuilder.sortedClones())
        var stepNumber = 1
        var stepDescription =
            "Step " + stepNumber + "/" + stepsCount + ", " + BuildSHMTreeStep.BuildingInitialTrees.forPrint
        SmartProgressReporter.startProgressReport(
            stepDescription,
            SmartProgressReporter.extractProgress(sortedClones, cloneWrappersCount.toLong())
        )
        var currentStepDebug = createDebug(stepNumber)
        val relatedAllelesMutations = shmTreeBuilder.relatedAllelesMutations()
        CUtils.processAllInParallel(
            shmTreeBuilder.buildClusters(sortedClones),
            { cluster ->
                shmTreeBuilder.zeroStep(
                    cluster,
                    currentStepDebug.treesBeforeDecisionsWriter,
                    relatedAllelesMutations
                )
            },
            threads
        )
        val clonesWasAddedOnInit = shmTreeBuilder.makeDecisions()
        shmTreeBuilder.makeDecisions()

        //TODO check that all trees has minimum common mutations in VJ
        report.onStepEnd(BuildSHMTreeStep.BuildingInitialTrees, clonesWasAddedOnInit, shmTreeBuilder.treesCount())
        var previousStepDebug = currentStepDebug
        for (step in shmTreeBuilderParameters!!.stepsOrder) {
            stepNumber++
            currentStepDebug = createDebug(stepNumber)
            val treesCountBefore = shmTreeBuilder.treesCount()
            sortedClones = CountingOutputPort(shmTreeBuilder.sortedClones())
            stepDescription = "Step " + stepNumber + "/" + stepsCount + ", " + step.forPrint
            SmartProgressReporter.startProgressReport(
                stepDescription,
                SmartProgressReporter.extractProgress(sortedClones, cloneWrappersCount.toLong())
            )
            CUtils.processAllInParallel(
                shmTreeBuilder.buildClusters(sortedClones),
                { cluster ->
                    shmTreeBuilder.applyStep(
                        cluster,
                        step,
                        previousStepDebug.treesAfterDecisionsWriter,
                        currentStepDebug.treesBeforeDecisionsWriter
                    )
                },
                threads
            )
            val clonesWasAdded = shmTreeBuilder.makeDecisions()
            report.onStepEnd(step, clonesWasAdded, shmTreeBuilder.treesCount() - treesCountBefore)
            previousStepDebug = currentStepDebug
        }
        sortedClones = CountingOutputPort(shmTreeBuilder.sortedClones())
        SmartProgressReporter.startProgressReport(
            "Building results",
            SmartProgressReporter.extractProgress(sortedClones, cloneWrappersCount.toLong())
        )
        SHMTreesWriter(outputTreesPath).use { shmTreesWriter ->
            val usedGenes = cloneReaders.flatMap { it.usedGenes }.distinct()
            shmTreesWriter.writeHeader(
                cloneReaders.first().assemblerParameters,
                cloneReaders.first().alignerParameters,
                clnsFileNames,
                usedGenes,
                cloneReaders.first().tagsInfo,
                usedGenes.map { it.parentLibrary }.distinct()
            )

            val writer = shmTreesWriter.treesWriter()
            for (cluster in CUtils.it(shmTreeBuilder.buildClusters(sortedClones))) {
                shmTreeBuilder
                    .getResult(cluster, previousStepDebug.treesAfterDecisionsWriter)
                    .filter { treeWithMeta ->
                        cloneIds.isEmpty() || treeWithMeta.tree.allNodes()
                            .mapNotNull { it.node.content.clone?.id }
                            .any { it in cloneIds }
                    }
                    .forEach { writer.put(it) }
            }
            writer.put(null)
        }
        for (i in 0..shmTreeBuilderParameters!!.stepsOrder.size) {
            stepNumber = i + 1
            val treesBeforeDecisions = debugFile(stepNumber, Debug.BEFORE_DECISIONS_SUFFIX)
            val treesAfterDecisions = debugFile(stepNumber, Debug.AFTER_DECISIONS_SUFFIX)
            report.addStatsForStep(i, treesBeforeDecisions, treesAfterDecisions)
        }
        if (reportPdf != null) {
            report.writePdfReport(Paths.get(reportPdf!!))
        }
        println("============= Report ==============")
        ReportUtil.writeReportToStdout(report)
        ReportUtil.writeJsonReport(getReportFileName(), report)
    }

    private fun createDebug(stepNumber: Int) = Debug(
        prepareDebugFile(stepNumber, Debug.BEFORE_DECISIONS_SUFFIX),
        prepareDebugFile(stepNumber, Debug.AFTER_DECISIONS_SUFFIX)
    )

    private fun prepareDebugFile(stepNumber: Int, suffix: String): PrintStream {
        val debugFile = debugFile(stepNumber, suffix)
        debugFile.delete()
        debugFile.createNewFile()
        val debugWriter = PrintStream(debugFile)
        XSV.writeXSVHeaders(debugWriter, DebugInfo.COLUMNS_FOR_XSV.keys, ";")
        return debugWriter
    }

    private fun debugFile(stepNumber: Int, suffix: String): File =
        debugDirectory!!.resolve("step_" + stepNumber + "_" + suffix + ".csv").toFile()

    class Debug(val treesBeforeDecisionsWriter: PrintStream, val treesAfterDecisionsWriter: PrintStream) {
        companion object {
            const val BEFORE_DECISIONS_SUFFIX = "before_decisions"
            const val AFTER_DECISIONS_SUFFIX = "after_decisions"
        }
    }

    companion object {
        const val COMMAND_NAME = "shm_tree"
    }
}

