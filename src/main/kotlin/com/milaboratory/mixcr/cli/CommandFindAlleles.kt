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
@file:Suppress("LocalVariableName")

package com.milaboratory.mixcr.cli

import cc.redberry.pipe.util.buffered
import cc.redberry.pipe.util.drain
import com.milaboratory.app.ApplicationException
import com.milaboratory.app.InputFileType
import com.milaboratory.app.ValidationException
import com.milaboratory.app.matches
import com.milaboratory.core.Range
import com.milaboratory.core.io.sequence.fasta.FastaRecord
import com.milaboratory.core.io.sequence.fasta.FastaWriter
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.alleles.AllelesBuilder
import com.milaboratory.mixcr.alleles.CloneRebuild
import com.milaboratory.mixcr.alleles.CommandFindAllelesParams
import com.milaboratory.mixcr.alleles.FindAllelesReport
import com.milaboratory.mixcr.alleles.OverallAllelesStatistics
import com.milaboratory.mixcr.basictypes.ClnsWriter
import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.basictypes.CloneReader
import com.milaboratory.mixcr.basictypes.CloneSet
import com.milaboratory.mixcr.basictypes.CloneSetIO
import com.milaboratory.mixcr.basictypes.MiXCRHeader
import com.milaboratory.mixcr.basictypes.tag.TagType
import com.milaboratory.mixcr.basictypes.tag.TagsInfo
import com.milaboratory.mixcr.cli.CommonDescriptions.Labels
import com.milaboratory.mixcr.presets.AssembleContigsMixins
import com.milaboratory.mixcr.presets.MiXCRCommandDescriptor
import com.milaboratory.mixcr.util.VJPair
import com.milaboratory.util.GlobalObjectMappers
import com.milaboratory.util.JsonOverrider
import com.milaboratory.util.K_OM
import com.milaboratory.util.ProgressAndStage
import com.milaboratory.util.ReportUtil
import com.milaboratory.util.SmartProgressReporter
import com.milaboratory.util.TempFileDest
import com.milaboratory.util.TempFileManager
import com.milaboratory.util.XSV.chooseDelimiter
import com.milaboratory.util.XSV.writeXSV
import io.repseq.core.GeneFeature
import io.repseq.core.GeneFeature.CDR3
import io.repseq.core.GeneType.Joining
import io.repseq.core.GeneType.VJ_REFERENCE
import io.repseq.core.GeneType.Variable
import io.repseq.core.ReferencePoint.FR1Begin
import io.repseq.core.VDJCGene
import io.repseq.core.VDJCLibrary
import io.repseq.core.VDJCLibraryRegistry
import io.repseq.dto.VDJCGeneData
import io.repseq.dto.VDJCLibraryData
import picocli.CommandLine.ArgGroup
import picocli.CommandLine.Command
import picocli.CommandLine.Mixin
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.io.File
import java.io.PrintStream
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set
import kotlin.io.path.createDirectories
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension

@Command(
    description = [
        "Find allele variants in clnx.",
        "All inputs must be fully covered by the same feature, have the same align library, the same scoring of V and J genes and the same features to align."
    ]
)
class CommandFindAlleles : MiXCRCommandWithOutputs() {

    @Parameters(
        arity = "1..*",
        paramLabel = "(input_file.(clns|clna)|directory)",
        description = [
            "Input files or directory with files for allele search.",
            "In case of directory no filter by file type will be applied."
        ]
    )
    private val input: List<Path> = mutableListOf()

    override val inputFiles: List<Path>
        get() = input.flatMap { path ->
            when {
                path.isDirectory() -> path.listDirectoryEntries()
                else -> listOf(path)
            }
        }

    class OutputClnsOptions {
        @Option(
            description = [
                "Output template may contain {file_name} and {file_dir_path},",
                "outputs for '-o /output/folder/{file_name}_with_alleles.clns input_file.clns input_file2.clns' will be /output/folder/input_file_with_alleles.clns and /output/folder/input_file2_with_alleles.clns,",
                "outputs for '-o {file_dir_path}/{file_name}_with_alleles.clns /some/folder1/input_file.clns /some/folder2/input_file2.clns' will be /seme/folder1/input_file_with_alleles.clns and /some/folder2/input_file2_with_alleles.clns",
                "Resulted outputs must be uniq"
            ],
            names = ["--output-template"],
            paramLabel = "<template.clns>",
            required = true,
            order = 1
        )
        var outputTemplate: String? = null

        @Suppress("unused")
        @Option(
            description = ["Command will not realign input clns files. Must be specified if `--output-template` is omitted."],
            names = ["--no-clns-output"],
            required = true,
            arity = "0",
            order = 2
        )
        var noClnsOutput: Boolean = false
    }

    @ArgGroup(
        exclusive = true, multiplicity = "1",
        order = OptionsOrder.main + 10_000
    )
    lateinit var outputClnsOptions: OutputClnsOptions

    @Option(
        description = [
            "Paths where to write library with found alleles and other genes that exits in inputs.",
            "For `.json` library will be written in reqpseqio format.",
            "For `.fasta` library will be written in FASTA format with gene name and reliable range in description. " +
                    "There will be several records for one gene if clnx were assembled by composite gene feature.",
        ],
        names = ["--export-library"],
        paramLabel = "<path.(json|fasta)>",
        order = OptionsOrder.main + 10_100
    )
    var libraryOutputs: List<Path> = mutableListOf()

    @set:Option(
        description = ["Path where to write descriptions and stats for all result alleles, existed and new."],
        names = ["--export-alleles-mutations"],
        paramLabel = "<path>",
        order = OptionsOrder.main + 10_200
    )
    var allelesMutationsOutput: Path? = null
        set(value) {
            ValidationException.requireFileType(value, InputFileType.XSV)
            field = value
        }

    @Mixin
    lateinit var useLocalTemp: UseLocalTempOption

    @Mixin
    lateinit var threadsOptions: ThreadsOption

    @Option(
        names = ["-O"],
        description = ["Overrides default build SHM parameter values"],
        paramLabel = Labels.OVERRIDES,
        order = OptionsOrder.overrides
    )
    var overrides: Map<String, String> = mutableMapOf()

    @Mixin
    lateinit var reportOptions: ReportOptions

    @Mixin
    lateinit var debugDir: DebugDirOption

    private val outputClnsFiles: List<Path> by lazy {
        val template = outputClnsOptions.outputTemplate ?: return@lazy emptyList()
        if (!template.endsWith(".clns")) {
            throw ValidationException("Wrong template: command produces only clns, got $template")
        }
        val clnsFiles = inputFiles
            .map { it.toAbsolutePath() }
            .map { path ->
                template
                    .replace(Regex("\\{file_name}"), path.nameWithoutExtension)
                    .replace(Regex("\\{file_dir_path}"), path.parent.toString())
            }
            .map { Paths.get(it) }
            .toList()
        if (clnsFiles.distinct().count() < clnsFiles.size) {
            var message = "Output clns files are not uniq: $clnsFiles"
            message += "\nTry to use `{file_name}` and/or `{file_dir_path}` in template to get different output paths for every input. See help for more details"
            throw ValidationException(message)
        }
        clnsFiles
    }

    public override val outputFiles get() = outputClnsFiles + listOfNotNull(allelesMutationsOutput) + libraryOutputs

    private val tempDest: TempFileDest by lazy {
        val path = outputFiles.first()
        if (useLocalTemp.value) path.toAbsolutePath().parent.createDirectories()
        TempFileManager.smartTempDestination(path, ".find_alleles", !useLocalTemp.value)
    }

    private val findAllelesParameters: CommandFindAllelesParams by lazy {
        val findAllelesParametersName = "default"
        var result: CommandFindAllelesParams = CommandFindAllelesParams.presets.getByName(findAllelesParametersName)
            ?: throw ValidationException("Unknown parameters: $findAllelesParametersName")
        if (overrides.isNotEmpty()) {
            result = JsonOverrider.override(result, CommandFindAllelesParams::class.java, overrides)
                ?: throw ValidationException("Failed to override some parameter: $overrides")
        }
        result
    }

    override fun validate() {
        ValidationException.require(inputFiles.isNotEmpty()) { "there is no files to process" }
        inputFiles.forEach { input ->
            ValidationException.requireFileType(input, InputFileType.CLNX)
        }
        libraryOutputs.forEach { output ->
            ValidationException.requireFileType(output, InputFileType.JSON, InputFileType.FASTA)
        }
        if ((listOfNotNull(outputClnsOptions.outputTemplate, allelesMutationsOutput) + libraryOutputs).isEmpty()) {
            throw ValidationException("--output-template, --export-library or --export-alleles-mutations must be set")
        }
    }

    override fun initialize() {
        findAllelesParameters
    }

    override fun run1() {
        val clonesFilter: AllelesBuilder.ClonesFilter = object : AllelesBuilder.ClonesFilter {
            override fun match(clone: Clone, tagsInfo: TagsInfo): Boolean {
                if (findAllelesParameters.productiveOnly) {
                    if (clone.containsStopsOrAbsent(CDR3) || clone.isOutOfFrameOrAbsent(CDR3)) {
                        return false
                    }
                }
                val hasUmi = tagsInfo.any { it.type == TagType.Molecule }
                val useClonesWithCountGreaterThen = when {
                    hasUmi -> findAllelesParameters.filterForDataWithUmi.useClonesWithCountGreaterThen
                    else -> findAllelesParameters.filterForDataWithoutUmi.useClonesWithCountGreaterThen
                }
                return clone.count > useClonesWithCountGreaterThen
            }
        }

        val reportBuilder = FindAllelesReport.Builder(
            OverallAllelesStatistics(clonesFilter)
        )
            .setCommandLine(commandLineArguments)
            .setInputFiles(inputFiles)
            .setOutputFiles(outputFiles)
            .setStartMillis(System.currentTimeMillis())
        val libraryRegistry = VDJCLibraryRegistry.getDefault()
        val datasets = inputFiles.map { CloneSetIO.mkReader(it, libraryRegistry) }
        ValidationException.require(datasets.all { it.header.allFullyCoveredBy != null }) {
            "Input files must not be processed by ${CommandAssembleContigs.COMMAND_NAME} without ${AssembleContigsMixins.SetContigAssemblingFeatures.CMD_OPTION} option"
        }
        ValidationException.requireDistinct(datasets.map { it.header.allFullyCoveredBy }) {
            "Input files must be cut by the same geneFeature"
        }
        val allFullyCoveredBy = datasets.first().header.allFullyCoveredBy!!

        ValidationException.requireDistinct(datasets.flatMap { it.usedGenes }.map { it.id.libraryId }) {
            "input files must be aligned on the same library"
        }
        val originalLibrary = datasets.first().usedGenes.first().parentLibrary

        for (geneType in VJ_REFERENCE) {
            val scores =
                datasets.map { it.assemblerParameters.cloneFactoryParameters.getVJCParameters(geneType).scoring }
            ValidationException.requireDistinct(scores) {
                "Require the same ${geneType.letter} scoring for all input files"
            }
        }
        val scoring = VJPair(
            V = datasets.first().assemblerParameters.cloneFactoryParameters.vParameters.scoring,
            J = datasets.first().assemblerParameters.cloneFactoryParameters.jParameters.scoring
        )

        ValidationException.requireDistinct(datasets.map { it.header.featuresToAlignMap }) {
            "Require the same features to align for all input files"
        }
        val featureToAlign = datasets.first().header.featuresToAlign
        ValidationException.require(featureToAlign.getFeatureToAlign(Variable)!!.contains(FR1Begin)) {
            "Input files must be aligned by V feature containing FR1Begin"
        }

        val allelesBuilder = AllelesBuilder.create(
            findAllelesParameters,
            clonesFilter,
            originalLibrary.allGenes,
            tempDest,
            datasets,
            scoring,
            datasets.flatMap { it.usedGenes }.distinct(),
            featureToAlign,
            allFullyCoveredBy,
            reportBuilder
        )

        val progressAndStage = ProgressAndStage("Grouping by the same J gene", 0.0)
        SmartProgressReporter.startProgressReport(progressAndStage)
        val JAlleles = allelesBuilder.searchForAlleles(Joining, emptyMap(), progressAndStage, threadsOptions.value)
        val VAlleles = allelesBuilder.searchForAlleles(Variable, JAlleles, progressAndStage, threadsOptions.value)

        val alleles = (VAlleles + JAlleles).toMutableMap()
        val usedGenes = collectUsedGenes(datasets, alleles)
        registerNotProcessedVJ(alleles, usedGenes)
        val resultLibrary = buildLibrary(libraryRegistry, originalLibrary, usedGenes.values.flatten())
        libraryOutputs.forEach { libraryOutput ->
            libraryOutput.toAbsolutePath().parent.createDirectories()
            if (libraryOutput.matches(InputFileType.JSON)) {
                K_OM.writeValue(libraryOutput.toFile(), arrayOf(resultLibrary.data))
            } else if (libraryOutput.matches(InputFileType.FASTA)) {
                resultLibrary.writeToFASTA(libraryOutput, originalLibrary)
            } else {
                throw ApplicationException("Unsupported file type for export library, $libraryOutput")
            }
        }
        val allelesMapping = alleles.mapValues { (_, geneDatum) ->
            geneDatum.map { resultLibrary[it.name].id }
        }
        val writerCloseCallbacks = mutableListOf<(FindAllelesReport) -> Unit>()
        datasets.forEachIndexed { i, cloneReader ->
            val cloneRebuild = CloneRebuild(
                resultLibrary,
                allelesMapping,
                allFullyCoveredBy,
                threadsOptions.value,
                cloneReader.assemblerParameters.cloneFactoryParameters,
                cloneReader.header.featuresToAlignMap
            )
            cloneReader.readClones().use { port ->
                val message = when (outputClnsOptions.outputTemplate) {
                    null -> "Recalculating scores ${inputFiles[i]}"
                    else -> "Realigning ${inputFiles[i]}"
                }
                val withRecalculatedScores = cloneRebuild.recalculateScores(
                    port.reportProgress(progressAndStage, message),
                    cloneReader.tagsInfo,
                    reportBuilder
                )
                if (outputClnsOptions.outputTemplate != null) {
                    val mapperClones = cloneRebuild.rebuildClones(withRecalculatedScores.buffered(threadsOptions.value))
                    outputClnsFiles[i].toAbsolutePath().parent.createDirectories()
                    val callback = outputClnsFiles[i].toFile()
                        .writeMappedClones(mapperClones, resultLibrary, cloneReader)
                    writerCloseCallbacks += callback
                } else {
                    withRecalculatedScores.drain()
                }
            }
        }

        reportBuilder.setFinishMillis(System.currentTimeMillis())
        val report = reportBuilder.buildReport()
        writerCloseCallbacks.forEach {
            it(report)
        }
        progressAndStage.finish()
        allelesMutationsOutput?.let { allelesMutationsOutput ->
            allelesMutationsOutput.toAbsolutePath().parent.createDirectories()
            printAllelesMutationsOutput(
                originalLibrary,
                resultLibrary,
                reportBuilder.overallAllelesStatistics,
                report,
                allelesMutationsOutput
            )
        }
        ReportUtil.writeReportToStdout(report)
        reportOptions.appendToFiles(report)
    }

    private fun VDJCLibrary.writeToFASTA(libraryOutput: Path, originalLibrary: VDJCLibrary) {
        FastaWriter<NucleotideSequence>(libraryOutput.toFile()).use { writer ->
            var id = 0L
            primaryGenes
                .sortedBy { it.name }
                .forEach { gene ->
                    val geneFeaturesForFoundAllele =
                        gene.data.meta[AllelesBuilder.metaKey.alleleMutationsReliableRegion]
                            ?.map { GeneFeature.parse(it) }
                            ?.sorted()
                    when {
                        geneFeaturesForFoundAllele != null -> {
                            geneFeaturesForFoundAllele.forEach { geneFeature ->
                                val varianceOf =
                                    originalLibrary[gene.data.meta[AllelesBuilder.metaKey.alleleVariantOf]!!.first()]
                                val range = varianceOf.referencePoints.getRange(geneFeature)
                                val sequence = gene.getFeature(geneFeature)
                                writer.write(FastaRecord(id++, "${gene.name} $range $geneFeature", sequence))
                            }
                        }

                        else -> {
                            val range = Range(
                                gene.referencePoints.firstAvailablePosition,
                                gene.referencePoints.lastAvailablePosition
                            )
                            val sequence = gene.getSequence(range)
                            writer.write(FastaRecord(id++, "${gene.name} $range", sequence))
                        }
                    }
                }
        }
    }

    private fun printAllelesMutationsOutput(
        originalLibrary: VDJCLibrary,
        resultLibrary: VDJCLibrary,
        allelesStatistics: OverallAllelesStatistics,
        report: FindAllelesReport,
        allelesMutationsOutput: Path
    ) {
        PrintStream(allelesMutationsOutput.toFile()).use { output ->
            val columns = buildMap<String, (VDJCGene) -> Any?> {
                this["alleleName"] = { it.name }
                this["geneName"] = { it.geneName }
                this["type"] = { it.geneType }
                this["enoughInfo"] = { gene ->
                    val history = report.searchHistoryForBCells[gene.geneName]
                    history?.alleles?.result?.isNotEmpty() ?: false
                }
                this[AllelesBuilder.metaKey.alleleMutationsReliableRegion] = { gene ->
                    if (gene.name !in originalLibrary) {
                        gene.data.meta[AllelesBuilder.metaKey.alleleMutationsReliableRegion]
                    } else ""
                }
                this["mutations"] = { gene ->
                    if (gene.name !in originalLibrary) {
                        gene.data.meta[AllelesBuilder.metaKey.alleleMutations]?.first() ?: ""
                    } else ""
                }
                this["varianceOf"] = { gene ->
                    if (gene.name !in originalLibrary) {
                        gene.data.meta[AllelesBuilder.metaKey.alleleVariantOf]?.first() ?: ""
                    } else ""
                }
                this["naivesCount"] = { gene ->
                    allelesStatistics.stats(gene.id).naives(filtered = false)
                }
                this["lowerDiversityBound"] = { gene ->
                    allelesStatistics.stats(gene.id).diversity()
                }
                this["clonesCount"] = { gene ->
                    allelesStatistics.stats(gene.id).count(filtered = false)
                }
                this["totalClonesCountForGene"] = { gene ->
                    allelesStatistics.baseGeneCount(gene.id)
                }
                this["clonesCountWithNegativeScoreChange"] = { gene ->
                    allelesStatistics.stats(gene.id).withNegativeScoreChange(filtered = false)
                }
                this["filteredForAlleleSearchNaivesCount"] = { gene ->
                    allelesStatistics.stats(gene.id).naives(filtered = true)
                }
                this["filteredForAlleleSearchClonesCount"] = { gene ->
                    allelesStatistics.stats(gene.id).count(filtered = true)
                }
                this["filteredForAlleleSearchClonesCountWithNegativeScoreChange"] = { gene ->
                    allelesStatistics.stats(gene.id).withNegativeScoreChange(filtered = true)
                }
                this["scoreDelta"] = { gene ->
                    val summaryStatistics = allelesStatistics.stats(gene.id).scoreDelta
                    if (summaryStatistics.n == 0L) "" else
                        GlobalObjectMappers.toOneLine(MiXCRCommandReport.StandardStats.from(summaryStatistics))
                }
            }
            val genes = resultLibrary.primaryGenes
                .filter { it.geneType in VJ_REFERENCE }
                .filter { it.isPrimary() }
                .sortedWith(Comparator.comparing { gene: VDJCGene -> gene.geneType }
                    .thenComparing { gene: VDJCGene -> gene.name })
            writeXSV(output, genes, columns, chooseDelimiter(allelesMutationsOutput))
        }
    }

    private fun File.writeMappedClones(
        clones: List<Clone>,
        resultLibrary: VDJCLibrary,
        cloneReader: CloneReader
    ): (FindAllelesReport) -> Unit {
        toPath().toAbsolutePath().parent.toFile().mkdirs()
        val cloneSet = CloneSet(
            clones,
            resultLibrary.primaryGenes,
            cloneReader.header
                .copy(foundAlleles = MiXCRHeader.FoundAlleles(resultLibrary.name, resultLibrary.data))
                .addStepParams(MiXCRCommandDescriptor.findAlleles, findAllelesParameters),
            cloneReader.footer,
            cloneReader.ordering()
        )
        val clnsWriter = ClnsWriter(this)
        clnsWriter.writeCloneSet(cloneSet)
        return { report ->
            clnsWriter.setFooter(cloneReader.footer.addStepReport(MiXCRCommandDescriptor.findAlleles, report))
            clnsWriter.close()
        }
    }

    private fun buildLibrary(
        libraryRegistry: VDJCLibraryRegistry,
        originalLibrary: VDJCLibrary,
        usedGenes: Collection<VDJCGeneData>
    ): VDJCLibrary {
        val resultLibrary = VDJCLibrary(
            VDJCLibraryData(originalLibrary.data, ArrayList(usedGenes)),
            originalLibrary.name + "_with_found_alleles",
            libraryRegistry,
            originalLibrary.context
        )
        usedGenes.forEach { VDJCLibrary.addGene(resultLibrary, it) }
        return resultLibrary
    }

    private fun registerNotProcessedVJ(
        alleles: MutableMap<String, List<VDJCGeneData>>,
        usedGenes: Map<String, List<VDJCGeneData>>
    ) {
        for ((geneName, geneDatum) in usedGenes) {
            // if gene wasn't processed in alleles search, then register it as a single allele
            if (!alleles.containsKey(geneName)) {
                if (geneDatum.first().geneType !in VJ_REFERENCE) continue
                alleles[geneName] = geneDatum
            }
        }
    }

    private fun collectUsedGenes(
        cloneReaders: List<CloneReader>,
        alleles: Map<String, List<VDJCGeneData>>
    ): Map<String, List<VDJCGeneData>> {
        val usedGenes = mutableMapOf<String, MutableList<VDJCGeneData>>()
        alleles.values
            .flatten()
            .forEach { usedGenes.computeIfAbsent(it.geneName) { mutableListOf() }.add(it) }
        for (cloneReader in cloneReaders) {
            for (gene in cloneReader.usedGenes) {
                val geneName = gene.geneName
                if (geneName !in alleles && geneName !in usedGenes) {
                    usedGenes.computeIfAbsent(geneName) { mutableListOf() }.add(gene.data)
                }
            }
        }
        return usedGenes
    }

    companion object {
        const val COMMAND_NAME = MiXCRCommandDescriptor.findAlleles.name
    }
}
