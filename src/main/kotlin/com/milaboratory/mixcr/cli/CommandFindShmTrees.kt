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
import com.milaboratory.mitool.exhaustive
import com.milaboratory.mixcr.basictypes.CloneReader
import com.milaboratory.mixcr.basictypes.CloneSetIO
import com.milaboratory.mixcr.basictypes.tag.TagType
import com.milaboratory.mixcr.trees.BuildSHMTreeStep.BuildingInitialTrees
import com.milaboratory.mixcr.trees.CloneWithDatasetId
import com.milaboratory.mixcr.trees.MutationsUtils
import com.milaboratory.mixcr.trees.SHMTreeBuilder
import com.milaboratory.mixcr.trees.SHMTreeBuilderOrchestrator
import com.milaboratory.mixcr.trees.SHMTreeBuilderParameters
import com.milaboratory.mixcr.trees.SHMTreeResult
import com.milaboratory.mixcr.trees.SHMTreesWriter
import com.milaboratory.mixcr.trees.SHMTreesWriter.Companion.shmFileExtension
import com.milaboratory.mixcr.trees.ScoringSet
import com.milaboratory.mixcr.trees.TreeWithMetaBuilder
import com.milaboratory.mixcr.util.XSV
import com.milaboratory.primitivio.forEach
import com.milaboratory.util.JsonOverrider
import com.milaboratory.util.ProgressAndStage
import com.milaboratory.util.ReportUtil
import com.milaboratory.util.SmartProgressReporter
import com.milaboratory.util.TempFileDest
import com.milaboratory.util.TempFileManager
import io.repseq.core.GeneType
import io.repseq.core.VDJCLibraryRegistry
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.pathString


@Command(
    name = CommandFindShmTrees.COMMAND_NAME,
    sortOptions = false,
    separator = " ",
    description = ["Builds SHM trees."]
)
class CommandFindShmTrees : MiXCRCommand() {
    @Parameters(
        arity = "2..*",
        description = ["Paths to clns files that was processed by command ${CommandFindAlleles.FIND_ALLELES_COMMAND_NAME} and path to output file"],
        paramLabel = "input_file.clns [input_file2.clns ....] output_file.$shmFileExtension",
        hideParamSyntax = true
    )
    lateinit var inOut: List<Path>

    @Option(description = ["Processing threads"], names = ["-t", "--threads"])
    var threads = Runtime.getRuntime().availableProcessors()
        set(value) {
            if (value <= 0) throwValidationExceptionKotlin("-t / --threads must be positive")
            field = value
        }

    public override fun getInputFiles(): List<String> = inOut.dropLast(1).map { it.toString() }

    override fun getOutputFiles(): List<String> = inOut.takeLast(1).map { it.toString() }

    private val clnsFileNames: List<Path>
        get() = inOut.dropLast(1)
    private val outputTreesPath: Path
        get() = inOut.last()

    @Option(names = ["-O"], description = ["Overrides default build SHM parameter values"])
    var overrides: Map<String, String> = mutableMapOf()

    @Option(
        description = ["SHM tree builder parameters preset."],
        names = ["-p", "--preset"],
        defaultValue = "default",
        paramLabel = "preset",
        showDefaultValue = CommandLine.Help.Visibility.ALWAYS
    )
    lateinit var shmTreeBuilderParametersName: String

    @Option(names = ["-r", "--report"], description = ["Report file path"])
    var report: Path? = null

    @Option(description = ["List of VGene names to filter clones"], names = ["-v", "--v-gene-names"])
    var VGenesToFilter: Set<String> = mutableSetOf()

    @Option(description = ["List of JGene names to filter clones"], names = ["-j", "--j-gene-names"])
    var JGenesToFilter: Set<String> = mutableSetOf()

    @Option(
        description = ["List of CDR3 nucleotide sequence lengths to filter clones"],
        names = ["-cdr3", "--cdr3-lengths"]
    )
    var CDR3LengthToFilter: Set<Int> = mutableSetOf()

    @Option(
        description = ["Filter clones with counts great or equal to that parameter"],
        names = ["--min-count"]
    )
    var minCountForClone: Int? = null

    @Option(description = ["Path to directory to store debug info"], names = ["-d", "--debug"])
    var debugDir: Path? = null

    private val debugDirectoryPath: Path by lazy {
        val result = debugDir ?: tempDest.resolvePath("trees_debug")
        result.createDirectories()
        result
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
    var buildFrom: Path? = null

    @Option(
        description = ["Put temporary files in the same folder as the output files."],
        names = ["--use-local-temp"]
    )
    var useLocalTemp = false

    private val shmTreeBuilderParameters: SHMTreeBuilderParameters by lazy {
        var result: SHMTreeBuilderParameters = SHMTreeBuilderParameters.presets.getByName(shmTreeBuilderParametersName)
            ?: throwValidationExceptionKotlin("Unknown parameters: $shmTreeBuilderParametersName")
        if (overrides.isNotEmpty()) {
            result = JsonOverrider.override(result, SHMTreeBuilderParameters::class.java, overrides)
                ?: throwValidationExceptionKotlin("Failed to override some parameter: $overrides")
        }
        result
    }

    private fun ensureParametersInitialized() {
        shmTreeBuilderParameters
        debugDirectoryPath
    }

    override fun validate() {
        super.validate()
        if (report == null && buildFrom == null) {
            warn("NOTE: report file is not specified, using $reportFile to write report.")
        }
        if (!outputTreesPath.extension.endsWith(shmFileExtension)) {
            throwValidationExceptionKotlin("Output file should have extension $shmFileExtension. Given $outputTreesPath")
        }
        if (shmTreeBuilderParameters.steps.first() !is BuildingInitialTrees) {
            throwValidationExceptionKotlin("First step must be BuildingInitialTrees")
        }
        if (buildFrom != null) {
            if (!buildFrom!!.endsWith(".tsv")) {
                throwValidationExceptionKotlin("--build-from must be .tsv, got $buildFrom")
            }
            if (VGenesToFilter.isNotEmpty()) {
                throwValidationExceptionKotlin("--v-gene-names must be empty if --build-from is specified")
            }
            if (JGenesToFilter.isNotEmpty()) {
                throwValidationExceptionKotlin("--j-gene-names must be empty if --build-from is specified")
            }
            if (CDR3LengthToFilter.isNotEmpty()) {
                throwValidationExceptionKotlin("--cdr3-lengths must be empty if --build-from is specified")
            }
            if (minCountForClone != null) {
                throwValidationExceptionKotlin("--min-count must be empty if --build-from is specified")
            }
            if (report != null) {
                println("WARN: argument --report will not be used with --build-from")
            }
            if (debugDir != null) {
                println("WARN: argument --debug will not be used with --build-from")
            }
        }
    }

    private val reportFile: Path
        get() = report ?: outputTreesPath.parent.resolve(outputTreesPath.nameWithoutExtension + ".report")

    private val tempDest: TempFileDest by lazy {
        if (useLocalTemp) outputTreesPath.toAbsolutePath().parent.createDirectories()
        TempFileManager.smartTempDestination(outputTreesPath, ".build_trees", !useLocalTemp)
    }

    override fun run0() {
        val reportBuilder = BuildSHMTreeReport.Builder()
            .setCommandLine(commandLineArguments)
            .setInputFiles(inputFiles)
            .setOutputFiles(outputFiles)
            .setStartMillis(System.currentTimeMillis())

        ensureParametersInitialized()
        val vdjcLibraryRegistry = VDJCLibraryRegistry.getDefault()
        val cloneReaders = clnsFileNames.map { path ->
            CloneSetIO.mkReader(path, vdjcLibraryRegistry)
        }
        require(cloneReaders.isNotEmpty()) { "there is no files to process" }
        require(cloneReaders.map { it.alignerParameters }.distinct().count() == 1) {
            "input files must have the same aligner parameters"
        }
        for (geneType in GeneType.VJ_REFERENCE) {
            require(cloneReaders
                .map { it.assemblerParameters.cloneFactoryParameters.getVJCParameters(geneType).scoring }
                .distinct().count() == 1) {
                "input files must have the same $geneType scoring"
            }
        }
        require(cloneReaders.all { it.info.foundAlleles != null }) {
            "Input files must be processed by ${CommandFindAlleles.FIND_ALLELES_COMMAND_NAME}"
        }
        require(cloneReaders.map { it.info.foundAlleles }.distinct().count() == 1) {
            "All input files must be assembled with the same alleles"
        }
        require(cloneReaders.all { it.info.allFullyCoveredBy != null }) {
            "Input files must not be processed by ${CommandAssembleContigs.ASSEMBLE_CONTIGS_COMMAND_NAME} without ${CommandAssembleContigs.CUT_BY_FEATURE_OPTION_NAME} option"
        }
        require(cloneReaders.map { it.info.allFullyCoveredBy }.distinct().count() == 1) {
            "Input files must be cut by the same geneFeature"
        }
        require(cloneReaders.map { it.info.tagsInfo }.distinct().count() == 1) {
            "Input files with different tags are not supported yet"
        }
        val allFullyCoveredBy = cloneReaders.first().info.allFullyCoveredBy!!
        val scoringSet = ScoringSet(
            cloneReaders.first().assemblerParameters.cloneFactoryParameters.vParameters.scoring,
            MutationsUtils.NDNScoring(),
            cloneReaders.first().assemblerParameters.cloneFactoryParameters.jParameters.scoring
        )
        val shmTreeBuilderOrchestrator = SHMTreeBuilderOrchestrator(
            shmTreeBuilderParameters,
            scoringSet,
            cloneReaders,
            allFullyCoveredBy,
            tempDest,
            debugDirectoryPath,
            VGenesToFilter,
            JGenesToFilter,
            CDR3LengthToFilter,
            minCountForClone
        )
        buildFrom?.let { buildFrom ->
            val result = shmTreeBuilderOrchestrator.buildByUserData(readUserInput(buildFrom.toFile()), threads)
            writeResults(reportBuilder, result, cloneReaders, scoringSet, generateGlobalTreeIds = false)
            return
        }
        val allDatasetsHasCellTags = cloneReaders.all { reader -> reader.tagsInfo.any { it.type == TagType.Cell } }
        if (allDatasetsHasCellTags) {
            when (val singleCellParams = shmTreeBuilderParameters.singleCell) {
                is SHMTreeBuilderParameters.SingleCell.NoOP ->
                    warn("Single cell tags will not be used but it's possible on this data")
                is SHMTreeBuilderParameters.SingleCell.SimpleClustering -> {
                    shmTreeBuilderOrchestrator.buildTreesByCellTags(singleCellParams, threads) {
                        writeResults(reportBuilder, it, cloneReaders, scoringSet, generateGlobalTreeIds = true)
                    }
                    return
                }
            }.exhaustive
        }
        val progressAndStage = ProgressAndStage("Search for clones with the same targets", 0.0)
        SmartProgressReporter.startProgressReport(progressAndStage)
        shmTreeBuilderOrchestrator.buildTreesBySteps(progressAndStage, reportBuilder, threads) {
            writeResults(reportBuilder, it, cloneReaders, scoringSet, generateGlobalTreeIds = true)
        }
        progressAndStage.finish()
        val report = reportBuilder.buildReport()
        ReportUtil.writeReportToStdout(report)
        ReportUtil.writeJsonReport(reportFile.pathString, report)
    }

    private fun readUserInput(userInputFile: File): Map<CloneWithDatasetId.ID, Int> {
        val fileNameToDatasetId = clnsFileNames.withIndex().associate { it.value.toString() to it.index }
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
        reportBuilder: BuildSHMTreeReport.Builder,
        result: OutputPort<TreeWithMetaBuilder>,
        cloneReaders: List<CloneReader>,
        scoringSet: ScoringSet,
        generateGlobalTreeIds: Boolean
    ) {
        var treeIdGenerator = 1
        val shmTreeBuilder = SHMTreeBuilder(shmTreeBuilderParameters.topologyBuilder, scoringSet)
        outputTreesPath.toAbsolutePath().parent.createDirectories()
        SHMTreesWriter(outputTreesPath.toString()).use { shmTreesWriter ->
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

            reportBuilder.setFinishMillis(System.currentTimeMillis())
            shmTreesWriter.writeFooter(
                cloneReaders.mapIndexed { i, cloneReader -> clnsFileNames[i].toString() to cloneReader.reports() }
                    .toMap(),
                reportBuilder.buildReport()
            )
        }
    }

    private fun SHMTreesWriter.writeHeader(cloneReaders: List<CloneReader>) {
        val usedGenes = cloneReaders.flatMap { it.usedGenes }.distinct()
        val metaInfos = cloneReaders.map { it.info }
        require(metaInfos.map { it.alignerParameters }.distinct().size == 1) {
            "alignerParameters must be the same"
        }
        writeHeader(
            metaInfos,
            metaInfos.first().alignerParameters,
            clnsFileNames.map { it.toString() },
            usedGenes
        )
    }

    companion object {
        const val COMMAND_NAME = "findShmTrees"
    }
}

