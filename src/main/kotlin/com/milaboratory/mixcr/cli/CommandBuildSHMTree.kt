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
import com.google.common.collect.ImmutableMap
import com.milaboratory.core.mutations.MutationsUtil.MutationNt2AADescriptor
import com.milaboratory.core.sequence.AminoAcidSequence
import com.milaboratory.mixcr.basictypes.CloneReader
import com.milaboratory.mixcr.basictypes.CloneSetIO
import com.milaboratory.mixcr.trees.CloneOrFoundAncestor
import com.milaboratory.mixcr.trees.ClusteringCriteria.DefaultClusteringCriteria
import com.milaboratory.mixcr.trees.DebugInfo
import com.milaboratory.mixcr.trees.NewickTreePrinter
import com.milaboratory.mixcr.trees.SHMTreeBuilder
import com.milaboratory.mixcr.trees.SHMTreeBuilderParameters
import com.milaboratory.mixcr.trees.SHMTreeBuilderParametersPresets
import com.milaboratory.mixcr.trees.Tree.NodeWithParent
import com.milaboratory.mixcr.trees.TreeWithMeta
import com.milaboratory.mixcr.util.XSV
import com.milaboratory.util.ReportUtil
import com.milaboratory.util.SmartProgressReporter
import io.repseq.core.VDJCLibraryRegistry
import org.apache.commons.io.FilenameUtils
import picocli.CommandLine
import java.io.File
import java.io.IOException
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.function.Function
import java.util.stream.Collectors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream


@CommandLine.Command(
    name = CommandBuildSHMTree.BUILD_SHM_TREE_COMMAND_NAME,
    sortOptions = false,
    separator = " ",
    description = ["Builds SHM trees."]
)
class CommandBuildSHMTree : ACommandWithOutputMiXCR() {
    @CommandLine.Parameters(arity = "2..*", description = ["input_file.clns [input_file2.clns ....] output_files.zip"])
    private val inOut: List<String> = ArrayList()

    @CommandLine.Option(description = ["Processing threads"], names = ["-t", "--threads"])
    var threads = Runtime.getRuntime().availableProcessors()
        set(value) {
            if (value <= 0) throwValidationException("-t / --threads must be positive")
            field = value
        }

    public override fun getInputFiles(): List<String> {
        return inOut.subList(0, inOut.size - 1)
    }

    override fun getOutputFiles(): List<String> {
        return inOut.subList(inOut.size - 1, inOut.size)
    }

    private val clnsFiles: List<String>
        get() = inputFiles
    private val outputZipPath: String
        get() = inOut[inOut.size - 1]

    @CommandLine.Option(description = ["SHM tree builder parameters preset."], names = ["-p", "--preset"])
    var shmTreeBuilderParametersName = "default"

    @CommandLine.Option(names = ["-r", "--report"], description = ["Report file path"])
    var report: String? = null

    @CommandLine.Option(names = ["-rp", "--report-pdf"], description = ["Pdf report file path"])
    var reportPdf: String? = null

    @CommandLine.Option(description = ["Path to directory to store debug info"], names = ["-d", "--debug"])
    var debugDirectoryPath: String? = null
    var debugDirectory: Path? = null
    private var shmTreeBuilderParameters: SHMTreeBuilderParameters? = null

    @Throws(IOException::class)
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

    private fun getReportFileName(): String? {
        return Objects.requireNonNullElseGet(report) {
            FilenameUtils.removeExtension(
                outputZipPath
            ) + ".report"
        }
    }

    @Throws(Exception::class)
    override fun run0() {
        ensureParametersInitialized()
        val cloneReaders =
            clnsFiles.map { path -> CloneSetIO.mkReader(Paths.get(path), VDJCLibraryRegistry.getDefault()) }
        require(cloneReaders.isNotEmpty()) { "there is no files to process" }
        require(
            cloneReaders.stream().map { obj: CloneReader -> obj.assemblerParameters }.distinct().count() == 1L
        ) { "input files must have the same assembler parameters" }
        val shmTreeBuilder = SHMTreeBuilder(
            shmTreeBuilderParameters!!,
            DefaultClusteringCriteria(),
            cloneReaders
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
        val outputDirInTmp = Files.createTempDirectory("tree_outputs").toFile()
        outputDirInTmp.deleteOnExit()
        val columnsThatDependOnTree = buildMap<String, Function<TreeWithMeta, Any?>> {
            put("treeId", Function { it.treeId.encode() })
            put("VGene", Function { it.rootInfo.VJBase.VGeneName })
            put("JGene", Function { it.rootInfo.VJBase.JGeneName })
        }
        val nodesTableFile = outputDirInTmp.toPath().resolve("nodes.tsv").toFile()
        nodesTableFile.createNewFile()
        val nodesTable = PrintStream(nodesTableFile)
        val allColumnNames = columnsThatDependOnNode.keys + columnsThatDependOnTree.keys
        XSV.writeXSVHeaders(nodesTable, allColumnNames, "\t")
        val printer = NewickTreePrinter<CloneOrFoundAncestor>(
            nameExtractor = { it.content.id.toString() },
            printDistances = true,
            printOnlyLeafNames = false
        )
        for (cluster in CUtils.it(shmTreeBuilder.buildClusters(sortedClones))) {
            val result = shmTreeBuilder.getResult(cluster, previousStepDebug.treesAfterDecisionsWriter)
                .sortedBy { it.treeId.encode() }
            for (treeWithMeta in result) {
                val columns = columnsThatDependOnNode + columnsThatDependOnTree
                    .mapValues { (_, function) -> { function.apply(treeWithMeta) } }
                val nodes = treeWithMeta.tree
                    .allNodes()
                    .collect(Collectors.toList())
                XSV.writeXSVBody(nodesTable, nodes, columns, "\t")
                val treeFile = outputDirInTmp.toPath().resolve(treeWithMeta.treeId.encode() + ".tree").toFile()
                Files.writeString(treeFile.toPath(), printer.print(treeWithMeta.tree))
            }
        }
        zip(outputDirInTmp.toPath(), Path.of(outputZipPath))
        for (i in 0..shmTreeBuilderParameters!!.stepsOrder.size) {
            stepNumber = i + 1
            val treesBeforeDecisions = debugFile(stepNumber, Debug.BEFORE_DECISIONS_SUFFIX)
            val treesAfterDecisions = debugFile(stepNumber, Debug.AFTER_DECISIONS_SUFFIX)
            report.addStatsForStep(i, treesBeforeDecisions, treesAfterDecisions)
        }
        println("============= Report ==============")
        ReportUtil.writeReportToStdout(report)
        ReportUtil.writeJsonReport(getReportFileName(), report)
        if (reportPdf != null) {
            report.writePdfReport(Paths.get(reportPdf!!))
        }
    }

    @Throws(IOException::class)
    private fun createDebug(stepNumber: Int): Debug {
        return Debug(
            prepareDebugFile(stepNumber, Debug.BEFORE_DECISIONS_SUFFIX),
            prepareDebugFile(stepNumber, Debug.AFTER_DECISIONS_SUFFIX)
        )
    }

    @Throws(IOException::class)
    private fun prepareDebugFile(stepNumber: Int, suffix: String): PrintStream {
        val debugFile = debugFile(stepNumber, suffix)
        debugFile.delete()
        debugFile.createNewFile()
        val debugWriter = PrintStream(debugFile)
        XSV.writeXSVHeaders(debugWriter, DebugInfo.COLUMNS_FOR_XSV.keys, ";")
        return debugWriter
    }

    private fun debugFile(stepNumber: Int, suffix: String): File {
        return debugDirectory!!.resolve("step_" + stepNumber + "_" + suffix + ".csv").toFile()
    }

    class Debug(val treesBeforeDecisionsWriter: PrintStream, val treesAfterDecisionsWriter: PrintStream) {
        companion object {
            const val BEFORE_DECISIONS_SUFFIX = "before_decisions"
            const val AFTER_DECISIONS_SUFFIX = "after_decisions"
        }
    }

    companion object {
        const val BUILD_SHM_TREE_COMMAND_NAME = "shm_tree"
        private val columnsThatDependOnNode: Map<String, (NodeWithParent<CloneOrFoundAncestor>) -> Any?> =
            ImmutableMap.builder<String, (NodeWithParent<CloneOrFoundAncestor>) -> Any?>()
                .put("id") { it.node.content.id }
                .put("parentId") { it.parent?.content?.id }
                .put("cloneId") { it.node.content.cloneId }
                .put("count") { it.node.content.count }
                .put("distanceFromGermline") { it.node.content.distanceFromGermline }
                .put("distanceFromReconstructedRoot") { it.node.content.distanceFromReconstructedRoot }
                .put("distanceFromParent") { it.distance }
                .put("CDR3") { it.node.content.CDR3 }
                .put("CDR3_AA") {
                    val CDR3 = it.node.content.CDR3
                    return@put when {
                        CDR3.size() % 3 == 0 -> AminoAcidSequence.translate(CDR3)
                        else -> ""
                    }
                }
                .put("CDR3_VMutations_FromGermline") {
                    it.node.content.CDR3_VMutations(CloneOrFoundAncestor.Base.FromGermline)
                }
                .put("CDR3_VMutations_FromParent") {
                    it.node.content.CDR3_VMutations(CloneOrFoundAncestor.Base.FromParent)
                }
                .put("CDR3_VMutations_FromRoot") {
                    it.node.content.CDR3_VMutations(CloneOrFoundAncestor.Base.FromReconstructedRoot)
                }
                .put("CDR3_AA_VMutations_FromGermline") {
                    it.node.content.CDR3_AA_VMutations(CloneOrFoundAncestor.Base.FromGermline).asString()
                }
                .put("CDR3_AA_VMutations_FromParent") {
                    it.node.content.CDR3_AA_VMutations(CloneOrFoundAncestor.Base.FromParent).asString()
                }
                .put("CDR3_AA_VMutations_FromRoot") {
                    it.node.content.CDR3_AA_VMutations(CloneOrFoundAncestor.Base.FromReconstructedRoot).asString()
                }
                .put("CDR3_JMutations_FromGermline") {
                    it.node.content.CDR3_JMutations(CloneOrFoundAncestor.Base.FromGermline)
                }
                .put("CDR3_JMutations_FromParent") {
                    it.node.content.CDR3_JMutations(CloneOrFoundAncestor.Base.FromParent)
                }
                .put("CDR3_JMutations_FromRoot") {
                    it.node.content.CDR3_JMutations(CloneOrFoundAncestor.Base.FromReconstructedRoot)
                }
                .put("CDR3_AA_JMutations_FromGermline") {
                    it.node.content.CDR3_AA_JMutations(CloneOrFoundAncestor.Base.FromGermline).asString()
                }
                .put("CDR3_AA_JMutations_FromParent") {
                    it.node.content.CDR3_AA_JMutations(CloneOrFoundAncestor.Base.FromParent).asString()
                }
                .put("CDR3_AA_JMutations_FromRoot") {
                    it.node.content.CDR3_AA_JMutations(CloneOrFoundAncestor.Base.FromReconstructedRoot).asString()
                }
                .put("CDR3_NDN_FromGermline") {
                    it.node.content.CDR3_NDNMutations(CloneOrFoundAncestor.Base.FromGermline)
                }
                .put("CDR3_NDN_FromParent") {
                    it.node.content.CDR3_NDNMutations(CloneOrFoundAncestor.Base.FromParent)
                }
                .put("CDR3_NDN_FromRoot") {
                    it.node.content.CDR3_NDNMutations(CloneOrFoundAncestor.Base.FromReconstructedRoot)
                }
                .put("CGene") { it.node.content.CGeneName }
                .build()

        private fun Array<MutationNt2AADescriptor>?.asString(): String {
            return when (this) {
                null -> ""
                else -> this.joinToString { obj: MutationNt2AADescriptor -> obj.toString() }
            }
        }

        @Throws(IOException::class)
        private fun zip(sourceDir: Path, destination: Path) {
            ZipOutputStream(Files.newOutputStream(destination)).use { zs ->
                Files.walk(sourceDir)
                    .filter { path -> !Files.isDirectory(path) }
                    .forEach { path ->
                        val zipEntry = ZipEntry(sourceDir.relativize(path).toString())
                        try {
                            zs.putNextEntry(zipEntry)
                            Files.copy(path, zs)
                            zs.closeEntry()
                        } catch (e: IOException) {
                            throw RuntimeException(e)
                        }
                    }
            }
        }
    }
}
