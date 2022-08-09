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
@file:Suppress("LocalVariableName", "PropertyName")

package com.milaboratory.mixcr.cli

import cc.redberry.pipe.OutputPort
import com.milaboratory.mixcr.basictypes.CloneReader
import com.milaboratory.mixcr.basictypes.CloneSetIO
import com.milaboratory.mixcr.basictypes.tag.TagType
import com.milaboratory.mixcr.trees.BuildSHMTreeStep.BuildingInitialTrees
import com.milaboratory.mixcr.trees.CloneWithDatasetId
import com.milaboratory.mixcr.trees.DebugInfo
import com.milaboratory.mixcr.trees.MutationsUtils
import com.milaboratory.mixcr.trees.SHMTreeBuilder
import com.milaboratory.mixcr.trees.SHMTreeBuilderOrchestrator
import com.milaboratory.mixcr.trees.SHMTreeBuilderParameters
import com.milaboratory.mixcr.trees.SHMTreeResult
import com.milaboratory.mixcr.trees.SHMTreesWriter
import com.milaboratory.mixcr.trees.SHMTreesWriter.Companion.shmFileExtension
import com.milaboratory.mixcr.trees.ScoringSet
import com.milaboratory.mixcr.trees.TreeWithMetaBuilder
import com.milaboratory.mixcr.trees.constructStateBuilder
import com.milaboratory.mixcr.trees.forPrint
import com.milaboratory.mixcr.util.XSV
import com.milaboratory.primitivio.cached
import com.milaboratory.primitivio.count
import com.milaboratory.primitivio.flatten
import com.milaboratory.primitivio.forEach
import com.milaboratory.primitivio.forEachInParallel
import com.milaboratory.primitivio.mapInParallel
import com.milaboratory.primitivio.withProgress
import com.milaboratory.util.JsonOverrider
import com.milaboratory.util.ProgressAndStage
import com.milaboratory.util.ReportUtil
import com.milaboratory.util.SmartProgressReporter
import com.milaboratory.util.TempFileDest
import com.milaboratory.util.TempFileManager
import io.repseq.core.VDJCLibraryRegistry
import org.apache.commons.io.FilenameUtils
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.io.File
import java.io.PrintStream
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.Path


@Command(
    name = CommandFindShmTrees.COMMAND_NAME,
    sortOptions = false,
    separator = " ",
    description = ["Builds SHM trees."]
)
class CommandFindShmTrees : MiXCRCommand() {
    @Parameters(arity = "2..*", description = ["input_file.clns [input_file2.clns ....] output_file.$shmFileExtension"])
    private val inOut: List<String> = ArrayList()

    @Option(description = ["Processing threads"], names = ["-t", "--threads"])
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

    @Option(names = ["-O"], description = ["Overrides default build SHM parameter values"])
    var overrides: Map<String, String> = HashMap()

    @Option(
        description = ["SHM tree builder parameters preset."],
        names = ["-p", "--preset"],
        defaultValue = "default"
    )
    lateinit var shmTreeBuilderParametersName: String

    @Option(names = ["-r", "--report"], description = ["Report file path"])
    var report: String? = null

    @Option(description = ["List of VGene names to filter clones"], names = ["-v", "--v-gene-names"])
    var VGenesToSearch: Set<String> = HashSet()

    @Option(description = ["List of JGene names to filter clones"], names = ["-j", "--j-gene-names"])
    var JGenesToSearch: Set<String> = HashSet()

    @Option(
        description = ["List of CDR3 nucleotide sequence lengths to filter clones"],
        names = ["-cdr3", "--cdr3-lengths"]
    )
    var CDR3LengthToSearch: Set<Int> = HashSet()

    @Option(
        description = ["Filter clones with counts great or equal to that parameter"],
        names = ["--min-count"]
    )
    var minCountForClone: Int? = null

    @Option(names = ["-rp", "--report-pdf"], description = ["Pdf report file path"])
    var reportPdf: String? = null

    @Option(description = ["Path to directory to store debug info"], names = ["-d", "--debug"])
    var debugDirectoryPath: String? = null

    private val debugDirectory: Path by lazy {
        when (debugDirectoryPath) {
            null -> tempDest.resolvePath("trees_debug")
            else -> Paths.get(debugDirectoryPath!!)
        }.also { it.toFile().mkdirs() }
    }

    @Option(
        description = [
            "If specified, trees will be build from data in the file. Main logic of command will be omitted.",
            "File must be formatted as tsv and have 3 columns: treeId, fileName, cloneId",
            "V and J genes will be chosen by majority of clones in a clonal group. CDR3 length must be the same in all clones.",
            "treeId - uniq id for clonal group,",
            "fileName - file name as was used in command line to search for clone,",
            "cloneId - clone id in the specified file"
        ],
        names = ["-bf", "--build-from"]
    )
    var buildFrom: String? = null

    @Option(
        description = ["Use system temp folder for temporary files, the output folder will be used if this option is omitted."],
        names = ["--use-system-temp"]
    )
    var useSystemTemp = false

    private val shmTreeBuilderParameters: SHMTreeBuilderParameters by lazy {
        var result = SHMTreeBuilderParameters.presets.getByName(shmTreeBuilderParametersName)
        if (result == null) throwValidationException("Unknown parameters: $shmTreeBuilderParametersName")
        if (overrides.isNotEmpty()) {
            result = JsonOverrider.override(result!!, SHMTreeBuilderParameters::class.java, overrides)
            if (result == null) throwValidationException("Failed to override some parameter: $overrides")
        }
        result!!
    }

    private fun ensureParametersInitialized() {
        shmTreeBuilderParameters
        debugDirectory
    }

    override fun validate() {
        super.validate()
        if (report == null && buildFrom == null) {
            warn("NOTE: report file is not specified, using $reportFileName to write report.")
        }
        if (!outputTreesPath.endsWith(".$shmFileExtension")) {
            throwValidationException("Output file should have extension $shmFileExtension. Given $outputTreesPath")
        }
        if (shmTreeBuilderParameters.steps.first() !is BuildingInitialTrees) {
            throwValidationException("First step must be BuildingInitialTrees")
        }
        if (buildFrom != null) {
            if (!buildFrom!!.endsWith(".tsv")) {
                throwValidationException("--build-from must be .tsv, got $buildFrom")
            }
            if (VGenesToSearch.isNotEmpty()) {
                throwValidationException("--v-gene-names must be empty if --build-from is specified")
            }
            if (JGenesToSearch.isNotEmpty()) {
                throwValidationException("--j-gene-names must be empty if --build-from is specified")
            }
            if (CDR3LengthToSearch.isNotEmpty()) {
                throwValidationException("--cdr3-lengths must be empty if --build-from is specified")
            }
            if (minCountForClone != null) {
                throwValidationException("--min-count must be empty if --build-from is specified")
            }
            if (report != null) {
                println("WARN: argument --report will not be used with --build-from")
            }
            if (reportPdf != null) {
                println("WARN: argument --report-pdf will not be used with --build-from")
            }
            if (debugDirectoryPath != null) {
                println("WARN: argument --debug will not be used with --build-from")
            }
        }
    }

    private val reportFileName: String get() = report ?: (FilenameUtils.removeExtension(outputTreesPath) + ".report")

    private val tempDest: TempFileDest by lazy {
        TempFileManager.smartTempDestination(outputTreesPath, "", useSystemTemp)
    }

    override fun run0() {
        ensureParametersInitialized()
        val cloneReaders = clnsFileNames.map { path ->
            CloneSetIO.mkReader(Paths.get(path), VDJCLibraryRegistry.getDefault())
        }
        require(cloneReaders.isNotEmpty()) { "there is no files to process" }
        require(cloneReaders.map { it.assemblerParameters }.distinct().count() == 1) {
            "input files must have the same assembler parameters"
        }
        require(cloneReaders.map { it.alignerParameters }.distinct().count() == 1) {
            "input files must have the same aligner parameters"
        }
        val assemblerParameters = cloneReaders.first().assemblerParameters
        val scoringSet = ScoringSet(
            assemblerParameters.cloneFactoryParameters.vParameters.scoring,
            MutationsUtils.NDNScoring(),
            assemblerParameters.cloneFactoryParameters.jParameters.scoring
        )
        val shmTreeBuilderOrchestrator = SHMTreeBuilderOrchestrator(
            shmTreeBuilderParameters,
            scoringSet,
            cloneReaders,
            //TODO check that clones are strictly aligned by assemblingFeatures
            assemblerParameters.assemblingFeatures,
            tempDest,
            VGenesToSearch,
            JGenesToSearch,
            CDR3LengthToSearch,
            minCountForClone
        )
        if (buildFrom != null) {
            val result = shmTreeBuilderOrchestrator.buildByUserData(readUserInput(Path(buildFrom!!).toFile()), threads)
            writeResults(result, cloneReaders, scoringSet, generateGlobalTreeIds = false)
            return
        }
        val allDatasetsHasCellTags = cloneReaders.all { reader -> reader.tagsInfo.any { it.type == TagType.Cell } }
        if (allDatasetsHasCellTags) {
            when (val singleCellParams = shmTreeBuilderParameters.singleCell) {
                is SHMTreeBuilderParameters.SingleCell.NoOP ->
                    warn("Single cell tags will not be used but it's possible on this data")
                is SHMTreeBuilderParameters.SingleCell.SimpleClustering -> {
                    val result = shmTreeBuilderOrchestrator.buildTreesByCellTags(singleCellParams, threads)
                    writeResults(result, cloneReaders, scoringSet, generateGlobalTreeIds = true)
                    return
                }
            }
        }
        shmTreeBuilderOrchestrator.buildTreesAndPrintReport {
            writeResults(it, cloneReaders, scoringSet, generateGlobalTreeIds = true)
        }
    }

    private fun SHMTreeBuilderOrchestrator.buildTreesAndPrintReport(
        resultWriter: (OutputPort<TreeWithMetaBuilder>) -> Unit
    ) {
        val report = BuildSHMTreeReport()
        val stateBuilder = datasets.constructStateBuilder()
        val progressAndStage = ProgressAndStage("Search for clones with the same targets", 0.0)
        SmartProgressReporter.startProgressReport(progressAndStage)
        clonesWithTheSameVJAndCDR3Length(progressAndStage)
            .cached(
                tempDest.addSuffix("tree.builder.grouping.by.the.same.VJ.CDR3Length"),
                stateBuilder,
                blockSize = 100,
                concurrencyToRead = threads / 2,
                concurrencyToWrite = threads / 2
            ) { clustersCache ->
                val cloneWrappersCount = clustersCache().count().toLong()

                val stepsCount = shmTreeBuilderParameters.steps.size
                var stepNumber = 0
                var previousStepDebug = createDebug(stepNumber)
                for (step in shmTreeBuilderParameters.steps) {
                    stepNumber++
                    val currentStepDebug = createDebug(stepNumber)
                    val treesCountBefore = treesCount()
                    val allClonesInTress = allClonesInTress()
                    clustersCache().withProgress(
                        cloneWrappersCount,
                        progressAndStage,
                        "Step $stepNumber/$stepsCount, ${step.forPrint}"
                    ) { clusters ->
                        clusters.forEachInParallel(threads) { cluster ->
                            applyStep(
                                cluster.clones,
                                step,
                                allClonesInTress,
                                previousStepDebug.treesAfterDecisionsWriter,
                                currentStepDebug.treesBeforeDecisionsWriter
                            )
                        }
                    }
                    val clonesWasAdded = makeDecisions()
                    report.onStepEnd(step, clonesWasAdded, treesCount() - treesCountBefore)
                    previousStepDebug = currentStepDebug
                }
                clustersCache().withProgress(
                    cloneWrappersCount,
                    progressAndStage,
                    "Building results"
                ) { clusters ->
                    val result = clusters
                        .mapInParallel(threads) { cluster ->
                            getResult(cluster.clones, previousStepDebug.treesAfterDecisionsWriter)
                        }
                        .flatten()

                    resultWriter(result)
                }
            }

        for (i in 0 until shmTreeBuilderParameters.steps.size) {
            val stepNumber = i + 1
            val treesBeforeDecisions = debugFile(stepNumber, Debug.BEFORE_DECISIONS_SUFFIX)
            val treesAfterDecisions = debugFile(stepNumber, Debug.AFTER_DECISIONS_SUFFIX)
            report.addStatsForStep(i, treesBeforeDecisions, treesAfterDecisions)
        }
        if (reportPdf != null) {
            report.writePdfReport(Paths.get(reportPdf!!))
        }
        println("============= Report ==============")
        ReportUtil.writeReportToStdout(report)
        ReportUtil.writeJsonReport(reportFileName, report)
    }

    private fun readUserInput(userInputFile: File): Map<CloneWithDatasetId.ID, Int> {
        val fileNameToDatasetId = clnsFileNames.withIndex().associate { it.value to it.index }
        val rows = XSV.readXSV(userInputFile, listOf("treeId", "fileName", "cloneId"), "\t")
        return rows.associate { row ->
            val datasetId = (fileNameToDatasetId[row["fileName"]!!]
                ?: throw IllegalArgumentException("No such file ${row["fileName"]} in arguments"))
            val datasetIdWithCloneId = CloneWithDatasetId.ID(
                datasetId = datasetId,
                cloneId = row["cloneId"]!!.toInt()
            )
            val treeId = row["treeId"]!!.toInt()
            datasetIdWithCloneId to treeId
        }
    }

    private fun writeResults(
        result: OutputPort<TreeWithMetaBuilder>,
        cloneReaders: List<CloneReader>,
        scoringSet: ScoringSet,
        generateGlobalTreeIds: Boolean
    ) {
        var treeIdGenerator = 1
        val shmTreeBuilder = SHMTreeBuilder(shmTreeBuilderParameters.topologyBuilder, scoringSet)
        SHMTreesWriter(outputTreesPath).use { shmTreesWriter ->
            shmTreesWriter.writeHeader(cloneReaders)

            val writer = shmTreesWriter.treesWriter()
            result.forEach { tree ->
                val rebuildFromMRCA = shmTreeBuilder.rebuildFromMRCA(tree)
                writer.put(
                    SHMTreeResult(
                        rebuildFromMRCA.buildResult(),
                        rebuildFromMRCA.rootInfo,
                        if (generateGlobalTreeIds) {
                            treeIdGenerator++
                        } else {
                            rebuildFromMRCA.treeId.id
                        }
                    )
                )
            }
            writer.put(null)
        }
    }

    private fun SHMTreesWriter.writeHeader(cloneReaders: List<CloneReader>) {
        val usedGenes = cloneReaders.flatMap { it.usedGenes }.distinct()
        val anyCloneReader = cloneReaders.first()
        writeHeader(
            anyCloneReader.assemblerParameters,
            anyCloneReader.alignerParameters,
            clnsFileNames,
            usedGenes,
            //TODO summarize tagsInfo
            anyCloneReader.tagsInfo,
            usedGenes.map { it.parentLibrary }.distinct()
        )
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
        debugDirectory.resolve("step_" + stepNumber + "_" + suffix + ".csv").toFile()

    class Debug(val treesBeforeDecisionsWriter: PrintStream, val treesAfterDecisionsWriter: PrintStream) {
        companion object {
            const val BEFORE_DECISIONS_SUFFIX = "before_decisions"
            const val AFTER_DECISIONS_SUFFIX = "after_decisions"
        }
    }

    companion object {
        const val COMMAND_NAME = "findShmTrees"
    }
}

