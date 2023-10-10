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
import com.milaboratory.app.logger
import com.milaboratory.app.matches
import com.milaboratory.core.io.sequence.fasta.FastaRecord
import com.milaboratory.core.io.sequence.fasta.FastaWriter
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.alleles.AlleleSearchResult
import com.milaboratory.mixcr.alleles.AlleleSearchResult.Status.DE_NOVO
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
import com.milaboratory.mixcr.presets.AssembleContigsMixins.SetContigAssemblingFeatures
import com.milaboratory.mixcr.presets.MiXCRCommandDescriptor
import com.milaboratory.mixcr.util.VJPair
import com.milaboratory.util.GlobalObjectMappers
import com.milaboratory.util.JsonOverrider
import com.milaboratory.util.K_OM
import com.milaboratory.util.ReportUtil
import com.milaboratory.util.TempFileDest
import com.milaboratory.util.TempFileManager
import com.milaboratory.util.XSV
import io.repseq.core.GeneFeature
import io.repseq.core.GeneFeature.CDR3
import io.repseq.core.GeneFeature.CRegion
import io.repseq.core.GeneFeature.DRegion
import io.repseq.core.GeneFeature.Exon1
import io.repseq.core.GeneFeature.JRegion
import io.repseq.core.GeneFeature.L2
import io.repseq.core.GeneFeature.V5UTRGermline
import io.repseq.core.GeneFeature.VRegion
import io.repseq.core.GeneFeatures
import io.repseq.core.GeneType.Constant
import io.repseq.core.GeneType.Diversity
import io.repseq.core.GeneType.Joining
import io.repseq.core.GeneType.VJ_REFERENCE
import io.repseq.core.GeneType.Variable
import io.repseq.core.ReferencePoint.FR1Begin
import io.repseq.core.VDJCLibrary
import io.repseq.core.VDJCLibraryRegistry
import io.repseq.dto.VDJCGeneData.metaKey
import picocli.CommandLine.ArgGroup
import picocli.CommandLine.Command
import picocli.CommandLine.Mixin
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
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
            "For `.fasta` library will be written in FASTA format with gene name and feature name in description.",
        ],
        names = ["--export-library"],
        paramLabel = "<path.(json|fasta)>",
        order = OptionsOrder.main + 10_100
    )
    var libraryOutputs: List<Path> = mutableListOf()

    @set:Option(
        description = [
            "Path where to write descriptions and stats for all result alleles, existed and new.",
            "Available columns:",
            "alleleName - result allele name. If it's de nova, then before `-M` is nearest known variant, M{n} is how many mutations in it and CDR3-{n} is how many mutations in CDR3, at the end uniq hash.",
            "geneName - gene name, the same for zigotes",
            "type - gene type (Variable or Joining)",
            "status - ${AlleleSearchResult.Status.helpDescription}.",
            "enoughInfo - is there enough clones to search by the main algorithm.",
            "mutations - list of mutations for de nova alleles.",
            "varianceOf - name of nearest known gene variant for de nova allele.",
            "naivesCount - how many naive clones aligned to this allele.",
            "nonProductiveCount - how many non productive clones aligned to this allele.",
            "lowerDiversityBound - diversity of CDR3 length and different genes (different J for V allele and J for V allele).",
            "clonesCount - how many clones aligned to this allele.",
            "totalClonesCountForGene - how many clones in all alleles of this gene (after realigning).",
            "clonesCountWithNegativeScoreChange - how many clones get lower score after aligned to this allele.",
            "filteredForAlleleSearchNaivesCount - how many naive clones aligned to this allele and was used in searching.",
            "filteredForAlleleSearchClonesCount - how many clones aligned to this allele and was used in searching.",
            "filteredForAlleleSearchClonesCountWithNegativeScoreChange - how many clones get lower score after aligned to this allele and was used in searching before",
            "scoreDelta - stats for score change of clones afeter aligning to this allele."
        ],
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

    @Suppress("unused")
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
        ValidationException.requireNotEmpty(inputFiles) { "there is no files to process" }
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
            val withoutFullyCovered = datasets.withIndex()
                .filter { it.value.header.allFullyCoveredBy == null }
                .map { inputFiles[it.index] }
                .joinToString(", ")
            "Some of the inputs were processed by ${CommandAssembleContigs.COMMAND_NAME} without ${SetContigAssemblingFeatures.CMD_OPTION} option: $withoutFullyCovered"
        }
        val allFullyCoveredBy = ValidationException.requireTheSame(datasets.map { it.header.allFullyCoveredBy!! }) {
            "Input files must be cut by the same geneFeature"
        }
        logger.debug { "Feature for search alleles: $allFullyCoveredBy" }
        ValidationException.require(allFullyCoveredBy != GeneFeatures(CDR3)) {
            "Assemble feature must cover more than CDR3"
        }
        ValidationException.require(allFullyCoveredBy.intersection(GeneFeature(V5UTRGermline, Exon1, L2)) == null) {
            "Can't build alleles by regions broader then VRegion, got $allFullyCoveredBy"
        }

        ValidationException.requireTheSame(datasets.flatMap { it.usedGenes }.map { it.id.libraryId }) {
            "input files must be aligned on the same library"
        }
        val originalLibrary = datasets.first().usedGenes.first().parentLibrary
        logger.debug { "Name of the original library: ${originalLibrary.libraryId.libraryName}" }

        for (geneType in VJ_REFERENCE) {
            val scores = datasets.map {
                it.assemblerParameters.cloneFactoryParameters.getVJCParameters(geneType).scoring
            }
            ValidationException.requireTheSame(scores) {
                "Require the same ${geneType.letter} scoring for all input files"
            }
        }
        val scoring = VJPair(
            V = datasets.first().assemblerParameters.cloneFactoryParameters.vParameters.scoring,
            J = datasets.first().assemblerParameters.cloneFactoryParameters.jParameters.scoring
        )

        ValidationException.requireTheSame(datasets.map { it.header.featuresToAlignMap }) {
            "Require the same features to align for all input files"
        }
        val featureToAlign = datasets.first().header
        ValidationException.require(featureToAlign.getFeatureToAlign(Variable)!!.contains(FR1Begin)) {
            "Input files must be aligned by V feature containing FR1Begin"
        }

        val baseGenes = originalLibrary.allGenes
            .mapNotNull { it.data.alleleInfo?.parent }
            .distinct()
        val absentBaseGenes = baseGenes.filter { it !in originalLibrary }
        ValidationException.requireEmpty(absentBaseGenes) {
            "All base genes must be presented in the library"
        }

        val recursiveAllelesInLibrary = baseGenes
            .map { originalLibrary[it] }
            .filter { it.data.alleleInfo != null }
        ValidationException.requireEmpty(recursiveAllelesInLibrary) {
            "Recursive allele variants are not supported."
        }

        val stepsCount = 7
        val allelesBuilder = AllelesBuilder.create(
            "Step 1 of $stepsCount",
            clonesFilter,
            originalLibrary,
            tempDest,
            datasets,
            scoring,
            featureToAlign,
            allFullyCoveredBy,
            threadsOptions.value
        )

        val JAllelesFromFirstRound = allelesBuilder.searchForAlleles(
            "Step 2 of $stepsCount",
            findAllelesParameters.searchAlleleParameterForFirstRound,
            null,
            Joining,
            emptyMap()
        )
        val VAllelesFromFirstRound = allelesBuilder.searchForAlleles(
            "Step 3 of $stepsCount",
            findAllelesParameters.searchAlleleParameterForFirstRound,
            null,
            Variable,
            JAllelesFromFirstRound
        )
        reportBuilder.reportFirstRound((JAllelesFromFirstRound.values + VAllelesFromFirstRound.values).flatten())
        val JAllelesFromSecondRound = allelesBuilder.searchForAlleles(
            "Step 4 of $stepsCount",
            findAllelesParameters.searchAlleleParameterForSecondRound,
            findAllelesParameters.searchMutationsInCDR3,
            Joining,
            VAllelesFromFirstRound
        )
        val VAllelesFromSecondRound = allelesBuilder.searchForAlleles(
            "Step 5 of $stepsCount",
            findAllelesParameters.searchAlleleParameterForSecondRound,
            findAllelesParameters.searchMutationsInCDR3,
            Variable,
            JAllelesFromSecondRound
        )
        // cleanup results
        var allelesAfterRemoval = allelesBuilder.removeAllelesIfPossible(
            "Step 6 of $stepsCount",
            VAllelesFromSecondRound + JAllelesFromSecondRound
        )
        allelesAfterRemoval = allelesBuilder.removeDuplicatedDeNovoAlleles(allelesAfterRemoval)
        // what variants will be used to replace genes in hits (key is base gene of original hit).
        val allelesMapping = allelesAfterRemoval
            .filter { it.status.exist }
            // Duplicates will be grouped by several key
            .groupBy { it.searchedOn }
        // There are maybe case of the same allele found on different genes if the actual difference outside of gene feature to search
        ApplicationException.checkDistinct(allelesAfterRemoval.map { allele -> "${allele.result.name} found on ${allele.searchedOn}" }) {
            "There are duplicates of found alleles"
        }
        // without differentiability of searched on
        val result = allelesAfterRemoval.distinctBy { it.result.name }
        reportBuilder.reportResults(result)

        val resultLibrary = buildLibrary(libraryRegistry, originalLibrary, result)
        libraryOutputs.forEach { libraryOutput ->
            libraryOutput.toAbsolutePath().parent.createDirectories()
            if (libraryOutput.matches(InputFileType.JSON)) {
                K_OM.writeValue(libraryOutput.toFile(), arrayOf(resultLibrary.data))
            } else if (libraryOutput.matches(InputFileType.FASTA)) {
                resultLibrary.writeToFASTA(libraryOutput)
            } else {
                throw ApplicationException("Unsupported file type for export library, $libraryOutput")
            }
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
                val reportPrefix = "Step 7 of $stepsCount"
                val message = when (outputClnsOptions.outputTemplate) {
                    null -> "$reportPrefix: recalculating scores ${inputFiles[i]}"
                    else -> "$reportPrefix: realigning ${inputFiles[i]}"
                }
                val withRecalculatedScores = cloneRebuild.recalculateScores(
                    port.reportProgress(message),
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
        allelesMutationsOutput?.let { allelesMutationsOutput ->
            allelesMutationsOutput.toAbsolutePath().parent.createDirectories()
            printAllelesMutationsOutput(
                result,
                reportBuilder.overallAllelesStatistics,
                allelesMutationsOutput
            )
        }
        ReportUtil.writeReportToStdout(report)
        reportOptions.appendToFiles(report)
    }

    private fun VDJCLibrary.writeToFASTA(libraryOutput: Path) {
        FastaWriter<NucleotideSequence>(libraryOutput.toFile()).use { writer ->
            var id = 0L
            primaryGenes
                .sortedBy { it.name }
                .forEach { gene ->
                    val feature = when (gene.geneType) {
                        Variable -> VRegion
                        Joining -> JRegion
                        Diversity -> DRegion
                        Constant -> CRegion
                    }
                    val range = gene.referencePoints.getRange(feature)
                    // allow empty region only for C
                    if (range != null || gene.geneType != Constant) {
                        val sequence = gene.getSequence(range)
                        writer.write(FastaRecord(id++, "${gene.name}|region=${GeneFeature.encode(feature)}", sequence))
                    }
                }
        }
    }

    private fun printAllelesMutationsOutput(
        result: Collection<AlleleSearchResult>,
        allelesStatistics: OverallAllelesStatistics,
        allelesMutationsOutput: Path
    ) {
        val columns = buildMap<String, (allele: AlleleSearchResult) -> Any?> {
            this["alleleName"] = { it.result.name }
            this["geneName"] = { it.result.geneName }
            this["type"] = { it.result.geneType }
            this["status"] = { it.status }
            this["enoughInfo"] = { allele ->
                allele.enoughInfo
            }
            this["reliableRegion"] = { allele ->
                allele.reliableRegion?.encode() ?: ""
            }
            this["mutations"] = { allele ->
                if (allele.status == DE_NOVO) {
                    allele.result.meta[metaKey.alleleMutations]?.first() ?: ""
                } else ""
            }
            this["varianceOf"] = { allele ->
                if (allele.status == DE_NOVO) {
                    allele.result.meta[metaKey.alleleVariantOf]?.first() ?: ""
                } else ""
            }
            this["naivesCount"] = { allele ->
                allelesStatistics.stats(allele.result.name).naives(filtered = false)
            }
            this["nonProductiveCount"] = { allele ->
                allelesStatistics.stats(allele.result.name).nonProductive()
            }
            this["lowerDiversityBound"] = { allele ->
                allelesStatistics.stats(allele.result.name).diversity()
            }
            this["clonesCount"] = { allele ->
                allelesStatistics.stats(allele.result.name).count(filtered = false)
            }
            this["totalClonesCountForGene"] = { allele ->
                allelesStatistics.baseGeneCount(allele.result.geneName)
            }
            this["clonesCountWithNegativeScoreChange"] = { allele ->
                allelesStatistics.stats(allele.result.name).withNegativeScoreChange(filtered = false)
            }
            this["filteredForAlleleSearchNaivesCount"] = { allele ->
                allelesStatistics.stats(allele.result.name).naives(filtered = true)
            }
            this["filteredForAlleleSearchClonesCount"] = { allele ->
                allelesStatistics.stats(allele.result.name).count(filtered = true)
            }
            this["filteredForAlleleSearchClonesCountWithNegativeScoreChange"] = { allele ->
                allelesStatistics.stats(allele.result.name).withNegativeScoreChange(filtered = true)
            }
            this["scoreDelta"] = { allele ->
                val summaryStatistics = allelesStatistics.stats(allele.result.name).scoreDelta
                if (summaryStatistics.n == 0L) "" else
                    GlobalObjectMappers.toOneLine(MiXCRCommandReport.StandardStats.from(summaryStatistics))
            }
        }
        val records = result.sortedWith(
            Comparator.comparing { allele: AlleleSearchResult -> allele.result.geneType }
                .thenComparing { allele: AlleleSearchResult -> allele.result.name }
        )
        XSV.writeXSV(
            allelesMutationsOutput,
            records,
            columns
        )
    }

    private fun File.writeMappedClones(
        clones: List<Clone>,
        resultLibrary: VDJCLibrary,
        cloneReader: CloneReader
    ): (FindAllelesReport) -> Unit {
        toPath().toAbsolutePath().parent.toFile().mkdirs()
        val cloneSet = CloneSet.Builder(
            clones,
            resultLibrary.primaryGenes,
            cloneReader.header
                .copy(foundAlleles = MiXCRHeader.FoundAlleles(resultLibrary.name, resultLibrary.data))
                .addStepParams(MiXCRCommandDescriptor.findAlleles, findAllelesParameters)
        )
            .sort(cloneReader.ordering)
            .withTotalCounts(cloneReader.cloneSetInfo.counts)
            .build()
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
        results: Collection<AlleleSearchResult>
    ): VDJCLibrary {
        val vAndJ = results.filter { it.status.exist }.map { it.result }
        val restGenes = originalLibrary.primaryGenes.filter { it.geneType !in VJ_REFERENCE }.map { it.data }
        val genesToAdd = vAndJ + restGenes
        val resultLibrary = VDJCLibrary(
            originalLibrary.data.useAsTemplate(genesToAdd),
            originalLibrary.name + "_with_found_alleles",
            libraryRegistry,
            originalLibrary.context
        )
        genesToAdd.forEach { VDJCLibrary.addGene(resultLibrary, it) }
        return resultLibrary
    }

    companion object {
        const val COMMAND_NAME = MiXCRCommandDescriptor.findAlleles.name
    }
}
