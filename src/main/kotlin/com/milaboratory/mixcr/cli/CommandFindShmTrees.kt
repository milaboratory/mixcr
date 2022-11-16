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

import cc.redberry.pipe.InputPort
import cc.redberry.pipe.OutputPort
import com.milaboratory.mitool.exhaustive
import com.milaboratory.mixcr.AssembleContigsMixins
import com.milaboratory.mixcr.MiXCRCommandDescriptor
import com.milaboratory.mixcr.MiXCRParams
import com.milaboratory.mixcr.MiXCRParamsSpec
import com.milaboratory.mixcr.MiXCRStepParams
import com.milaboratory.mixcr.basictypes.ClnsReader
import com.milaboratory.mixcr.basictypes.GeneFeatures
import com.milaboratory.mixcr.basictypes.MiXCRFooterMerger
import com.milaboratory.mixcr.basictypes.MiXCRHeader
import com.milaboratory.mixcr.basictypes.tag.TagType
import com.milaboratory.mixcr.basictypes.tag.TagsInfo
import com.milaboratory.mixcr.cli.CommonDescriptions.Labels
import com.milaboratory.mixcr.trees.BuildSHMTreeReport
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
import com.milaboratory.mixcr.util.toHexString
import com.milaboratory.primitivio.forEach
import com.milaboratory.util.JsonOverrider
import com.milaboratory.util.ProgressAndStage
import com.milaboratory.util.ReportUtil
import com.milaboratory.util.SmartProgressReporter
import com.milaboratory.util.TempFileDest
import com.milaboratory.util.TempFileManager
import io.repseq.core.GeneFeature
import io.repseq.core.GeneType
import io.repseq.core.VDJCLibraryRegistry
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Mixin
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.io.File
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.createDirectories
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries


@Command(
    description = [
        "Builds SHM trees.",
        "All inputs must be fully covered by the same feature, have the same library produced by `findAlleles`, the same scoring of V and J genes and the same features to align."
    ]
)
class CommandFindShmTrees : MiXCRCommandWithOutputs() {
    companion object {
        const val COMMAND_NAME = "findShmTrees"

        private const val inputsLabel = "(input_file.clns|directory)..."

        private const val outputLabel = "output_file.$shmFileExtension"


        fun mkCommandSpec(): CommandSpec = CommandSpec.forAnnotatedObject(CommandFindShmTrees::class.java)
            .addPositional(
                CommandLine.Model.PositionalParamSpec.builder()
                    .index("0")
                    .required(false)
                    .arity("0..*")
                    .type(Path::class.java)
                    .paramLabel(inputsLabel)
                    .hideParamSyntax(true)
                    .description(
                        "Paths to clns files or to directory with clns files that was processed by '${CommandFindAlleles.COMMAND_NAME}' command.",
                        "In case of directory no filter by file type will be applied."
                    )
                    .build()
            )
            .addPositional(
                CommandLine.Model.PositionalParamSpec.builder()
                    .index("1")
                    .required(false)
                    .arity("0..*")
                    .type(Path::class.java)
                    .paramLabel(outputLabel)
                    .hideParamSyntax(true)
                    .description("Path where to write output trees")
                    .build()
            )
    }

    data class Params(val dummy: Boolean = true) : MiXCRParams {
        override val command get() = MiXCRCommandDescriptor.findShmTrees
    }

    @Parameters(
        index = "0",
        arity = "2..*",
        paramLabel = "$inputsLabel $outputLabel",
        hideParamSyntax = true,
        //help is covered by mkCommandSpec
        hidden = true
    )
    lateinit var inOut: List<Path>

    @Mixin
    lateinit var threads: ThreadsOption

    private val outputTreesPath: Path
        get() = inOut.last()

    override val inputFiles
        get() = inOut.dropLast(1)
            .flatMap { path ->
                when {
                    path.isDirectory() -> path.listDirectoryEntries()
                    else -> listOf(path)
                }
            }

    override val outputFiles
        get() = listOf(outputTreesPath)

    @Option(
        names = ["-O"],
        description = ["Overrides default build SHM parameter values"],
        paramLabel = Labels.OVERRIDES,
        order = OptionsOrder.overrides
    )
    var overrides: Map<String, String> = mutableMapOf()

    @Mixin
    lateinit var reportOptions: ReportOptions

    @Option(
        description = ["List of VGene names to filter clones"],
        names = ["--v-gene-names"],
        paramLabel = "<gene_name>",
        order = OptionsOrder.main + 10_100
    )
    var VGenesToFilter: Set<String> = mutableSetOf()

    @Option(
        description = ["List of JGene names to filter clones"],
        names = ["--j-gene-names"],
        paramLabel = "<gene_name>",
        order = OptionsOrder.main + 10_200
    )
    var JGenesToFilter: Set<String> = mutableSetOf()

    @Option(
        description = ["List of CDR3 nucleotide sequence lengths to filter clones"],
        names = ["--cdr3-lengths"],
        paramLabel = "<n>",
        order = OptionsOrder.main + 10_300
    )
    var CDR3LengthToFilter: Set<Int> = mutableSetOf()

    @Option(
        description = ["Filter clones with counts great or equal to that parameter"],
        names = ["--min-count"],
        paramLabel = "<n>",
        order = OptionsOrder.main + 10_400
    )
    var minCountForClone: Int? = null

    @Option(description = ["Path to directory to store debug info"], names = ["--debugDir"], hidden = true)
    var debugDir: Path? = null

    private val debugDirectoryPath: Path by lazy {
        val result = debugDir ?: tempDest.resolvePath("trees_debug")
        result.createDirectories()
        result
    }

    @set:Option(
        description = [
            "If specified, trees will be build from data in the file. Main logic of command will be omitted.",
            "File must be formatted as tsv and have 3 columns: treeId, fileName, cloneId",
            "V and J genes will be chosen by majority of clones in a clonal group. CDR3 length must be the same in all clones.",
            "treeId - uniq id for clonal group,",
            "fileName - file name as was used in command line to search for clone,",
            "cloneId - clone id in the specified file"
        ],
        names = ["-bf", "--build-from"],
        paramLabel = "<path>",
        order = OptionsOrder.main + 10_500
    )
    var buildFrom: Path? = null
        set(value) {
            ValidationException.requireFileType(value, InputFileType.TSV)
            ValidationException.requireFileExists(value)
            field = value
        }

    @Mixin
    lateinit var useLocalTemp: UseLocalTempOption

    private val shmTreeBuilderParameters: SHMTreeBuilderParameters by lazy {
        val shmTreeBuilderParametersName = "default"
        var result: SHMTreeBuilderParameters = SHMTreeBuilderParameters.presets.getByName(shmTreeBuilderParametersName)
            ?: throw ValidationException("Unknown parameters: $shmTreeBuilderParametersName")
        if (overrides.isNotEmpty()) {
            result = JsonOverrider.override(result, SHMTreeBuilderParameters::class.java, overrides)
                ?: throw ValidationException("Failed to override some parameter: $overrides")
        }
        result
    }


    override fun initialize() {
        shmTreeBuilderParameters
        debugDirectoryPath
    }

    override fun validate() {
        ValidationException.require(inputFiles.isNotEmpty()) { "there is no files to process" }
        inputFiles.forEach { input ->
            ValidationException.requireFileType(input, InputFileType.CLNS)
        }
        ValidationException.requireFileType(outputTreesPath, InputFileType.SHMT)
        if (shmTreeBuilderParameters.steps.first() !is BuildingInitialTrees) {
            throw ValidationException("First step must be BuildingInitialTrees")
        }
        if (buildFrom != null) {
            if (VGenesToFilter.isNotEmpty()) {
                throw ValidationException("--v-gene-names must be empty if --build-from is specified")
            }
            if (JGenesToFilter.isNotEmpty()) {
                throw ValidationException("--j-gene-names must be empty if --build-from is specified")
            }
            if (CDR3LengthToFilter.isNotEmpty()) {
                throw ValidationException("--cdr3-lengths must be empty if --build-from is specified")
            }
            if (minCountForClone != null) {
                throw ValidationException("--min-count must be empty if --build-from is specified")
            }
            if (debugDir != null) {
                logger.warn("argument --debugDir will not be used with --build-from")
            }
        }
    }

    private val tempDest: TempFileDest by lazy {
        if (useLocalTemp.value) outputTreesPath.toAbsolutePath().parent.createDirectories()
        TempFileManager.smartTempDestination(outputTreesPath, ".build_trees", !useLocalTemp.value)
    }

    override fun run0() {
        val reportBuilder = BuildSHMTreeReport.Builder()
            .setCommandLine(commandLineArguments)
            .setInputFiles(inputFiles)
            .setOutputFiles(outputFiles)
            .setStartMillis(System.currentTimeMillis())

        val vdjcLibraryRegistry = VDJCLibraryRegistry.getDefault()
        val datasets = inputFiles.map { path ->
            ClnsReader(path, vdjcLibraryRegistry)
        }

        ValidationException.requireDistinct(datasets.map { it.header.featuresToAlignMap }) {
            "Require the same features to align for all input files"
        }
        val featureToAlign = datasets.first().header.featuresToAlign

        for (geneType in GeneType.VJ_REFERENCE) {
            val scores = datasets
                .map { it.assemblerParameters.cloneFactoryParameters.getVJCParameters(geneType).scoring }
            ValidationException.requireDistinct(scores) {
                "Input files must have the same $geneType scoring"
            }
        }
        val scoringSet = ScoringSet(
            datasets.first().assemblerParameters.cloneFactoryParameters.vParameters.scoring,
            MutationsUtils.NDNScoring(),
            datasets.first().assemblerParameters.cloneFactoryParameters.jParameters.scoring
        )

        ValidationException.require(datasets.all { it.header.foundAlleles != null }) {
            "Input files must be processed by ${CommandFindAlleles.COMMAND_NAME}"
        }
        ValidationException.requireDistinct(datasets.map { it.header.foundAlleles }) {
            "All input files must be assembled with the same alleles"
        }

        ValidationException.require(datasets.all { it.header.allFullyCoveredBy != null }) {
            "Input files must not be processed by ${CommandAssembleContigs.COMMAND_NAME} without ${AssembleContigsMixins.SetContigAssemblingFeatures.CMD_OPTION} option"
        }
        ValidationException.requireDistinct(datasets.map { it.header.allFullyCoveredBy }) {
            "Input files must be cut by the same geneFeature"
        }
        val allFullyCoveredBy = datasets.first().header.allFullyCoveredBy!!

        val shmTreeBuilderOrchestrator = SHMTreeBuilderOrchestrator(
            shmTreeBuilderParameters,
            scoringSet,
            datasets,
            featureToAlign,
            datasets.flatMap { it.usedGenes }.distinct(),
            allFullyCoveredBy,
            tempDest,
            debugDirectoryPath,
            VGenesToFilter,
            JGenesToFilter,
            CDR3LengthToFilter,
            minCountForClone
        )
        val report: BuildSHMTreeReport
        outputTreesPath.toAbsolutePath().parent.createDirectories()
        SHMTreesWriter(outputTreesPath).use { shmTreesWriter ->
            shmTreesWriter.writeHeader(datasets, Params())

            val writer = shmTreesWriter.treesWriter()

            buildFrom?.let { buildFrom ->
                val result =
                    shmTreeBuilderOrchestrator.buildByUserData(readUserInput(buildFrom.toFile()), threads.value)
                writeResults(writer, result, scoringSet, generateGlobalTreeIds = false)
                return
            }
            val allDatasetsHasCellTags = datasets.all { reader -> reader.tagsInfo.any { it.type == TagType.Cell } }
            if (allDatasetsHasCellTags) {
                when (val singleCellParams = shmTreeBuilderParameters.singleCell) {
                    is SHMTreeBuilderParameters.SingleCell.NoOP -> {
//                    warn("Single cell tags will not be used, but it's possible on this data")
                    }
                    is SHMTreeBuilderParameters.SingleCell.SimpleClustering -> {
                        shmTreeBuilderOrchestrator.buildTreesByCellTags(singleCellParams, threads.value) {
                            writeResults(writer, it, scoringSet, generateGlobalTreeIds = true)
                        }
                        return
                    }
                }.exhaustive
            }
            val progressAndStage = ProgressAndStage("Search for clones with the same targets", 0.0)
            SmartProgressReporter.startProgressReport(progressAndStage)
            shmTreeBuilderOrchestrator.buildTreesBySteps(progressAndStage, reportBuilder, threads.value) {
                writeResults(writer, it, scoringSet, generateGlobalTreeIds = true)
            }
            progressAndStage.finish()
            reportBuilder.setFinishMillis(System.currentTimeMillis())
            report = reportBuilder.buildReport()
            shmTreesWriter.setFooter(
                datasets.fold(MiXCRFooterMerger()) { m, f ->
                    m.addReportsFromInput(f.footer)
                }
                    .addStepReport(MiXCRCommandDescriptor.findShmTrees, report)
                    .build()
            )
        }
        ReportUtil.writeReportToStdout(report)
        reportOptions.appendToFiles(report)
    }

    private fun readUserInput(userInputFile: File): Map<CloneWithDatasetId.ID, Int> {
        val fileNameToDatasetId = inputFiles.withIndex().associate { it.value.toString() to it.index }
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
        writer: InputPort<SHMTreeResult>,
        result: OutputPort<TreeWithMetaBuilder>,
        scoringSet: ScoringSet,
        generateGlobalTreeIds: Boolean
    ) {
        var treeIdGenerator = 1
        val shmTreeBuilder = SHMTreeBuilder(shmTreeBuilderParameters.topologyBuilder, scoringSet)

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

    private fun SHMTreesWriter.writeHeader(cloneReaders: List<ClnsReader>, params: Params) {
        val usedGenes = cloneReaders.flatMap { it.usedGenes }.distinct()
        val headers = cloneReaders.map { it.readCloneSet().cloneSetInfo }
        writeHeader(
            headers
                .fold(MiXCRHeaderMerger()) { m, cloneSetInfo -> m.add(cloneSetInfo.header) }.build()
                .addStepParams(MiXCRCommandDescriptor.findShmTrees, params),
            inputFiles.map { it.toString() },
            headers,
            usedGenes
        )
    }
}

private class MiXCRHeaderMerger {
    private var inputHashAccumulator: MessageDigest? = MessageDigest.getInstance("MD5")
    private var upstreamParams = mutableListOf<MiXCRStepParams>()
    private var featuresToAlignMap: Map<GeneType, GeneFeature>? = null
    private var foundAlleles: MiXCRHeader.FoundAlleles? = null
    private var allFullyCoveredBy: GeneFeatures? = null

    fun add(header: MiXCRHeader) = run {
        if (header.inputHash == null)
            inputHashAccumulator = null
        inputHashAccumulator?.update(header.inputHash!!.encodeToByteArray())
        upstreamParams += header.stepParams
        if (allFullyCoveredBy == null) {
            featuresToAlignMap = header.featuresToAlignMap
            foundAlleles = header.foundAlleles
            allFullyCoveredBy = header.allFullyCoveredBy
        } else {
            check(featuresToAlignMap == header.featuresToAlignMap) { "Different featuresToAlignMap" }
            check(foundAlleles == header.foundAlleles) { "Different library" }
            check(allFullyCoveredBy == header.allFullyCoveredBy) { "Different covered region" }
        }
        this
    }

    fun build() =
        MiXCRHeader(
            inputHashAccumulator?.digest()?.toHexString(),
            MiXCRParamsSpec("null"),
            MiXCRStepParams.mergeUpstreams(upstreamParams),
            TagsInfo.NO_TAGS,
            null,
            featuresToAlignMap!!,
            null,
            foundAlleles,
            allFullyCoveredBy
        )
}
