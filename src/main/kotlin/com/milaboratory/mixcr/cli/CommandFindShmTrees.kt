/*
 * Copyright (c) 2014-2023, MiLaboratories Inc. All Rights Reserved
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
import cc.redberry.pipe.util.asOutputPort
import cc.redberry.pipe.util.asSequence
import cc.redberry.pipe.util.drainToAndClose
import cc.redberry.pipe.util.flatMap
import cc.redberry.pipe.util.map
import cc.redberry.pipe.util.toList
import com.milaboratory.app.InputFileType
import com.milaboratory.app.ValidationException
import com.milaboratory.app.logger
import com.milaboratory.mixcr.basictypes.ClnsReader
import com.milaboratory.mixcr.basictypes.CloneRanks
import com.milaboratory.mixcr.basictypes.HasFeatureToAlign
import com.milaboratory.mixcr.basictypes.MiXCRFooterMerger
import com.milaboratory.mixcr.basictypes.MiXCRHeader
import com.milaboratory.mixcr.basictypes.tag.TagsInfo
import com.milaboratory.mixcr.basictypes.validateCompositeFeatures
import com.milaboratory.mixcr.cli.CommonDescriptions.Labels
import com.milaboratory.mixcr.presets.AssembleContigsMixins
import com.milaboratory.mixcr.presets.MiXCRCommandDescriptor
import com.milaboratory.mixcr.presets.MiXCRParamsSpec
import com.milaboratory.mixcr.presets.MiXCRStepParams
import com.milaboratory.mixcr.trees.BuildSHMTreeReport
import com.milaboratory.mixcr.trees.BuildSHMTreeStep.BuildingInitialTrees
import com.milaboratory.mixcr.trees.CloneWithDatasetId
import com.milaboratory.mixcr.trees.CommandFindShmTreesParams
import com.milaboratory.mixcr.trees.MultiRootTree
import com.milaboratory.mixcr.trees.MutationsUtils
import com.milaboratory.mixcr.trees.SHMTreeBuilder
import com.milaboratory.mixcr.trees.SHMTreeBuilderOrchestrator
import com.milaboratory.mixcr.trees.SHMTreeResult
import com.milaboratory.mixcr.trees.SHMTreesCombiner
import com.milaboratory.mixcr.trees.SHMTreesWriter
import com.milaboratory.mixcr.trees.SHMTreesWriter.Companion.shmFileExtension
import com.milaboratory.mixcr.trees.ScoringSet
import com.milaboratory.mixcr.trees.constructStateBuilder
import com.milaboratory.mixcr.util.DebugDir
import com.milaboratory.mixcr.util.VJPair
import com.milaboratory.mixcr.util.toHexString
import com.milaboratory.util.ComparatorWithHash
import com.milaboratory.util.JsonOverrider
import com.milaboratory.util.ReportUtil
import com.milaboratory.util.TempFileDest
import com.milaboratory.util.TempFileManager
import com.milaboratory.util.asOutputPortWithProgress
import com.milaboratory.util.cached
import com.milaboratory.util.groupByOnDisk
import io.repseq.core.GeneFeature
import io.repseq.core.GeneFeature.CDR3
import io.repseq.core.GeneFeatures
import io.repseq.core.GeneType
import io.repseq.core.GeneVariantName
import io.repseq.core.VDJCLibraryRegistry
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Mixin
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
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
        const val COMMAND_NAME = MiXCRCommandDescriptor.findShmTrees.name

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

    @Parameters(
        index = "0",
        arity = "2..*",
        paramLabel = "$inputsLabel $outputLabel",
        hideParamSyntax = true,
        // help is covered by mkCommandSpec
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
    var VGenesToFilter: Set<GeneVariantName> = mutableSetOf()

    @Option(
        description = ["List of JGene names to filter clones"],
        names = ["--j-gene-names"],
        paramLabel = "<gene_name>",
        order = OptionsOrder.main + 10_200
    )
    var JGenesToFilter: Set<GeneVariantName> = mutableSetOf()

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

    @Mixin
    lateinit var debugDir: DebugDirOption

    private val debugDirectoryPath: Path by lazy {
        var result: Path? = null
        DebugDir { path ->
            result = path
        }
        result ?: tempDest.resolvePath("trees_debug").also {
            it.createDirectories()
        }
    }

    @Mixin
    lateinit var useLocalTemp: UseLocalTempOption

    private val shmTreeBuilderParameters: CommandFindShmTreesParams by lazy {
        val presetNAme = "default"
        var result = CommandFindShmTreesParams.presets.getByName(presetNAme)
            ?: throw ValidationException("Unknown parameters: $presetNAme")
        if (overrides.isNotEmpty()) {
            result = JsonOverrider.override(result, CommandFindShmTreesParams::class.java, overrides)
                ?: throw ValidationException("Failed to override some parameter: $overrides")
        }
        result
    }


    override fun initialize() {
        shmTreeBuilderParameters
        debugDirectoryPath
    }

    override fun validate() {
        ValidationException.requireNotEmpty(inputFiles) { "there is no files to process" }
        inputFiles.forEach { input ->
            ValidationException.requireFileType(input, InputFileType.CLNS)
        }
        ValidationException.requireFileType(outputTreesPath, InputFileType.SHMT)
        if (shmTreeBuilderParameters.steps.first() !is BuildingInitialTrees) {
            throw ValidationException("First step must be BuildingInitialTrees")
        }
    }

    private val tempDest: TempFileDest by lazy {
        if (useLocalTemp.value) outputTreesPath.toAbsolutePath().parent.createDirectories()
        TempFileManager.smartTempDestination(outputTreesPath, ".build_trees", !useLocalTemp.value)
    }

    override fun run1() {
        val reportBuilder = BuildSHMTreeReport.Builder()
            .setCommandLine(commandLineArguments)
            .setInputFiles(inputFiles)
            .setOutputFiles(outputFiles)
            .setStartMillis(System.currentTimeMillis())

        val datasets = inputFiles.map { path ->
            ClnsReader(path, VDJCLibraryRegistry.getDefault())
        }

        reportBuilder.totalClonesProcessed = datasets.sumOf { it.numberOfClones() }

        ValidationException.requireTheSame(datasets.map { it.header.featuresToAlignMap }) {
            "Require the same features to align for all input files"
        }
        val featureToAlign = datasets.first().header

        for (geneType in GeneType.VJ_REFERENCE) {
            val scores = datasets
                .map { it.assemblerParameters.cloneFactoryParameters.getVJCParameters(geneType).scoring }
            ValidationException.requireTheSame(scores) {
                "Input files must have the same $geneType scoring"
            }
        }
        val scoringSet = ScoringSet(
            datasets.first().assemblerParameters.cloneFactoryParameters.vParameters.scoring,
            MutationsUtils.NDNScoring(),
            datasets.first().assemblerParameters.cloneFactoryParameters.jParameters.scoring,
            shmTreeBuilderParameters.topologyBuilder.multiplierForNDNScore
        )

        ValidationException.require(datasets.all { it.header.foundAlleles != null }) {
            "Input files must be processed by ${CommandFindAlleles.COMMAND_NAME}"
        }
        val foundAlleles = ValidationException.requireTheSame(datasets.map { it.header.foundAlleles }) {
            "All input files must be assembled with the same alleles"
        }!!

        ValidationException.require(datasets.all { it.header.allFullyCoveredBy != null }) {
            "Some of the inputs were processed by ${CommandAssembleContigs.COMMAND_NAME} without ${AssembleContigsMixins.SetContigAssemblingFeatures.CMD_OPTION} option"
        }
        val allFullyCoveredBy = ValidationException.requireTheSame(datasets.map { it.header.allFullyCoveredBy!! }) {
            "Input files must be cut by the same geneFeature"
        }
        ValidationException.require(allFullyCoveredBy != GeneFeatures(CDR3)) {
            "Assemble feature must cover more than CDR3"
        }

        val featuresWithMutations: VJPair<GeneFeatures> = VJPair(
            allFullyCoveredBy.intersection(featureToAlign.getFeatureToAlign(GeneType.Variable)!!)!!,
            allFullyCoveredBy.intersection(featureToAlign.getFeatureToAlign(GeneType.Joining)!!)!!
        )
        val shmTreeBuilder = SHMTreeBuilder(
            shmTreeBuilderParameters.topologyBuilder,
            featuresWithMutations,
            scoringSet
        )

        val shmTreeBuilderOrchestrator = SHMTreeBuilderOrchestrator(
            shmTreeBuilderParameters,
            scoringSet,
            datasets,
            featureToAlign,
            datasets.flatMap { it.usedGenes }.distinct(),
            featuresWithMutations,
            shmTreeBuilder,
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
            shmTreesWriter.writeHeader(datasets, shmTreeBuilderParameters, foundAlleles)

            val writer = shmTreesWriter.treesWriter()

            shmTreeBuilderOrchestrator.buildTreesBySteps(reportBuilder, threads.value) { result ->
                val shmTreesCombiner = SHMTreesCombiner(
                    datasets,
                    featuresWithMutations,
                    shmTreeBuilder
                )
                if (shmTreesCombiner.datasetsThatShouldHaveGroups.isNotEmpty()) {
                    logger.warn {
                        val filesToGroup = inputFiles
                            .filterIndexed { index, _ -> index in shmTreesCombiner.datasetsThatShouldHaveGroups }
                            .joinToString(", ")
                        "Can't use information about cell, run `${MiXCRCommandDescriptor.groupClones}` for $filesToGroup"
                    }
                }
                val singleCellTrees = shmTreesCombiner.groupByChains(result.toList())
                writeResults(writer, singleCellTrees, datasets, shmTreeBuilder)
            }
            reportBuilder.setFinishMillis(System.currentTimeMillis())
            report = reportBuilder.buildReport()
            shmTreesWriter.setFooter(
                datasets.foldIndexed(MiXCRFooterMerger()) { index, m, f ->
                    m.addReportsFromInput(inputFiles[index].toString(), f.footer)
                }
                    .addStepReport(MiXCRCommandDescriptor.findShmTrees, report)
                    .build()
            )
        }
        ReportUtil.writeReportToStdout(report)
        reportOptions.appendToFiles(report)
    }

    private fun writeResults(
        writer: InputPort<SHMTreeResult>,
        result: List<MultiRootTree>,
        datasets: List<ClnsReader>,
        shmTreeBuilder: SHMTreeBuilder
    ) {
        var treeIdGenerator = 1
        val stateBuilder = datasets.first().header.constructStateBuilder(datasets.first().usedGenes)
        result
            .asOutputPortWithProgress()
            .map { shmTreeBuilder.rebuildFromMRCA(it) }
            .map { tree ->
                SHMTreeResult(
                    tree.buildResult(),
                    tree.roots,
                    treeIdGenerator++
                )
            }
            .cached(tempDest, stateBuilder, blockSize = 100)
            .use { cache ->
                // recalculating ranks for clones that left in trees
                val recalculatedRanks = cache.createPort()
                    .reportProgress("Writing results 1/1")
                    .flatMap { result ->
                        result.tree.allNodes()
                            .flatMap { it.node.content.clones }
                            .iterator().asOutputPort()
                    }
                    .groupByOnDisk(
                        comparator = ComparatorWithHash.compareBy { it.datasetId },
                        tempDest,
                        stateBuilder = stateBuilder,
                        suffixForTempDest = "sort_for_divide_by_tags",
                    )
                    .asSequence()
                    .associate { group ->
                        val datasetId = group.key
                        val clones = group.map { it.clone }.toList()
                        val ranks = CloneRanks.calculate(clones, datasets[datasetId].header)
                        datasetId to clones.indices.associate { clones[it].id to ranks[it] }
                    }

                cache.createPort()
                    .reportProgress("Writing results 1/2")
                    .map { shmTreeResult ->
                        val withReplacedClones = shmTreeResult.tree
                            .map { _, content ->
                                content.copy(
                                    chains = content.chains.map { reconstructedChain ->
                                        reconstructedChain.copy(
                                            clones = reconstructedChain.clones.map { (clone, datasetId) ->
                                                val newRanks = recalculatedRanks[datasetId]!![clone.id]!!
                                                CloneWithDatasetId(
                                                    datasetId = datasetId,
                                                    clone = clone.withRanks(newRanks)
                                                )
                                            }
                                        )
                                    }
                                )
                            }
                        SHMTreeResult(withReplacedClones, shmTreeResult.rootInfos, shmTreeResult.treeId)
                    }
                    .drainToAndClose(writer)
            }
    }

    private fun SHMTreesWriter.writeHeader(
        cloneReaders: List<ClnsReader>,
        params: CommandFindShmTreesParams,
        library: MiXCRHeader.FoundAlleles
    ) {
        val usedGenes = cloneReaders.flatMap { it.usedGenes }.distinct()
        val headers = cloneReaders.map { it.cloneSetInfo }
        writeHeader(
            headers
                .foldIndexed(MiXCRHeaderMerger()) { index, m, cloneSetInfo ->
                    m.add(inputFiles[index].toString(), cloneSetInfo.header)
                }
                .build()
                .addStepParams(MiXCRCommandDescriptor.findShmTrees, params),
            inputFiles.map { it.toString() },
            headers,
            usedGenes,
            library
        )
    }
}

private class MiXCRHeaderMerger {
    private var inputHashAccumulator: MessageDigest? = MessageDigest.getInstance("MD5")
    private var upstreamParams = mutableListOf<Pair<String, MiXCRStepParams>>()
    private var featuresToAlignMap: Map<GeneType, GeneFeature?>? = null
    private var foundAlleles: MiXCRHeader.FoundAlleles? = null
    private var allFullyCoveredBy: GeneFeatures? = null

    fun add(fileName: String, header: MiXCRHeader) = run {
        if (header.inputHash == null)
            inputHashAccumulator = null
        inputHashAccumulator?.update(header.inputHash!!.encodeToByteArray())
        upstreamParams += fileName to header.stepParams
        if (allFullyCoveredBy == null) {
            featuresToAlignMap = header.featuresToAlignMap
            foundAlleles = header.foundAlleles!!
            allFullyCoveredBy = header.allFullyCoveredBy!!
            HasFeatureToAlign(featuresToAlignMap!!).validateCompositeFeatures(allFullyCoveredBy!!)
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
