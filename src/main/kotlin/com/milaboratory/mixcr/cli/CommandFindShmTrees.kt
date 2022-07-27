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

import com.milaboratory.mixcr.basictypes.CloneReader
import com.milaboratory.mixcr.basictypes.CloneSetIO
import com.milaboratory.mixcr.trees.*
import com.milaboratory.mixcr.trees.SHMTreesWriter.Companion.shmFileExtension
import com.milaboratory.mixcr.util.XSV
import com.milaboratory.primitivio.GroupingCriteria
import com.milaboratory.primitivio.forEach
import com.milaboratory.primitivio.forEachInParallel
import com.milaboratory.primitivio.sortAndGroupWithProgress
import com.milaboratory.util.*
import io.repseq.core.VDJCLibraryRegistry
import org.apache.commons.io.FilenameUtils
import picocli.CommandLine.*
import java.io.File
import java.io.PrintStream
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.Path


private val groupingCriteria: GroupingCriteria<CloneWrapper> = object : GroupingCriteria<CloneWrapper> {
    override fun hashCodeForGroup(entity: CloneWrapper): Int = entity.VJBase.hashCode()

    override val comparator: Comparator<CloneWrapper> = Comparator
        .comparing({ c -> c.VJBase }, VJBase.comparator)
}

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
    var vGenesToSearch: Set<String> = HashSet()

    @Option(description = ["List of JGene names to filter clones"], names = ["-j", "--j-gene-names"])
    var jGenesToSearch: Set<String> = HashSet()

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

    @Option(
        description = ["Search alleles within GeneFeature."],
        names = ["--region"],
        defaultValue = "VDJRegion"
    )
    lateinit var geneFeatureToSearchParameter: String

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
        if (buildFrom != null) {
            if (!buildFrom!!.endsWith(".tsv")) {
                throwValidationException("--build-from must be .tsv, got $buildFrom")
            }
            if (vGenesToSearch.isNotEmpty()) {
                throwValidationException("--v-gene-names must be empty if --build-from is specified")
            }
            if (jGenesToSearch.isNotEmpty()) {
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
        if (!useSystemTemp) {
            Paths.get(outputTreesPath).toAbsolutePath().parent.toFile().mkdirs()
        }
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
        val shmTreeBuilder = SHMTreeBuilder(
            shmTreeBuilderParameters,
            cloneReaders,
            //TODO check that clones are strictly aligned by assemblingFeatures
            cloneReaders.first().assemblerParameters.assemblingFeatures,
            tempDest,
            threads,
            vGenesToSearch,
            jGenesToSearch,
            CDR3LengthToSearch,
            minCountForClone
        )
        if (buildFrom != null) {
            buildFromUserInput(shmTreeBuilder, cloneReaders)
            return
        }
        val cloneWrappersCount = shmTreeBuilder.cloneWrappersCount().toLong()
        val report = BuildSHMTreeReport()
        val stepsCount = shmTreeBuilderParameters.stepsOrder.size + 1
        var stepNumber = 1
        val (clustersForZeroStep, progressOfZeroStep) = shmTreeBuilder.unsortedClonotypes()
            .sortAndGroupWithProgress(
                groupingCriteria,
                cloneReaders.constructStateBuilder(),
                tempDest.addSuffix("tree.builder.init"),
                cloneWrappersCount
            )
        var stepDescription = "Step $stepNumber/$stepsCount, ${BuildSHMTreeStep.BuildingInitialTrees.forPrint}"
        SmartProgressReporter.startProgressReport(stepDescription, progressOfZeroStep)
        var currentStepDebug = createDebug(stepNumber)
        val relatedAllelesMutations = shmTreeBuilder.relatedAllelesMutations()
        //TODO process big clusters first for max CPU utilization, for other steps too
        clustersForZeroStep.forEachInParallel(threads) { cluster ->
            shmTreeBuilder.zeroStep(
                cluster,
                currentStepDebug.treesBeforeDecisionsWriter,
                relatedAllelesMutations
            )
        }
        val clonesWasAddedOnInit = shmTreeBuilder.makeDecisions()

        //TODO check that all trees has minimum common mutations in VJ
        report.onStepEnd(BuildSHMTreeStep.BuildingInitialTrees, clonesWasAddedOnInit, shmTreeBuilder.treesCount())
        var previousStepDebug = currentStepDebug
        for (step in shmTreeBuilderParameters.stepsOrder) {
            stepNumber++
            currentStepDebug = createDebug(stepNumber)
            val treesCountBefore = shmTreeBuilder.treesCount()
            val (clustersForStep, progressOfStep) = shmTreeBuilder.unsortedClonotypes()
                .sortAndGroupWithProgress(
                    groupingCriteria,
                    cloneReaders.constructStateBuilder(),
                    tempDest.addSuffix("tree.builder.$stepNumber"),
                    cloneWrappersCount
                )
            stepDescription = "Step $stepNumber/$stepsCount, ${step.forPrint}"
            SmartProgressReporter.startProgressReport(stepDescription, progressOfStep)
            val allClonesInTress = shmTreeBuilder.allClonesInTress()
            clustersForStep.forEachInParallel(threads) { cluster ->
                shmTreeBuilder.applyStep(
                    cluster,
                    step,
                    allClonesInTress,
                    previousStepDebug.treesAfterDecisionsWriter,
                    currentStepDebug.treesBeforeDecisionsWriter
                )
            }
            val clonesWasAdded = shmTreeBuilder.makeDecisions()
            report.onStepEnd(step, clonesWasAdded, shmTreeBuilder.treesCount() - treesCountBefore)
            previousStepDebug = currentStepDebug
        }
        val (clustersForBuildResult, progressOfBuildResult) = shmTreeBuilder.unsortedClonotypes()
            .sortAndGroupWithProgress(
                groupingCriteria,
                cloneReaders.constructStateBuilder(),
                tempDest.addSuffix("tree.builder.result"),
                cloneWrappersCount
            )
        SmartProgressReporter.startProgressReport("Building results", progressOfBuildResult)

        SHMTreesWriter(outputTreesPath).use { shmTreesWriter ->
            val usedGenes = cloneReaders.flatMap { it.usedGenes }.distinct()
            shmTreesWriter.writeHeader(
                cloneReaders.first().assemblerParameters,
                cloneReaders.first().alignerParameters,
                clnsFileNames,
                usedGenes,
                //TODO summarize tagsInfo
                cloneReaders.first().tagsInfo,
                usedGenes.map { it.parentLibrary }.distinct()
            )

            val writer = shmTreesWriter.treesWriter()
            val idGenerator = AtomicInteger()
            clustersForBuildResult.forEach { cluster ->
                shmTreeBuilder
                    .getResult(cluster, previousStepDebug.treesAfterDecisionsWriter, idGenerator)
                    .forEach { writer.put(it) }
            }
            writer.put(null)
        }
        for (i in 0..shmTreeBuilderParameters.stepsOrder.size) {
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
        ReportUtil.writeJsonReport(reportFileName, report)
    }

    private fun buildFromUserInput(
        shmTreeBuilder: SHMTreeBuilder,
        cloneReaders: List<CloneReader>
    ) {
        val fileNameToDatasetId = clnsFileNames.withIndex().associate { it.value to it.index }
        val rows = XSV.readXSV(Path(buildFrom!!).toFile(), listOf("treeId", "fileName", "cloneId"), "\t")
        val userInput: Map<CloneWrapper.ID, Int> = rows.associate { row ->
            val datasetId = (fileNameToDatasetId[row["fileName"]!!]
                ?: throw IllegalArgumentException("No such file ${row["fileName"]} in arguments"))
            val datasetIdWithCloneId = CloneWrapper.ID(
                datasetId = datasetId,
                cloneId = row["cloneId"]!!.toInt()
            )
            val treeId = row["treeId"]!!.toInt()
            datasetIdWithCloneId to treeId
        }
        val result = shmTreeBuilder.buildByUserData(userInput)
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
            result.forEach { tree ->
                writer.put(tree)
            }
            writer.put(null)
        }
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

