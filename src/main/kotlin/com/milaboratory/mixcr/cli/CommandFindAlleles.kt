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
@file:Suppress("LocalVariableName")

package com.milaboratory.mixcr.cli

import com.milaboratory.mixcr.AssembleContigsMixins
import com.milaboratory.mixcr.MiXCRCommandDescriptor
import com.milaboratory.mixcr.MiXCRParams
import com.milaboratory.mixcr.alleles.AllelesBuilder
import com.milaboratory.mixcr.alleles.AllelesBuilder.Companion.metaKeyForAlleleMutationsReliableGeneFeatures
import com.milaboratory.mixcr.alleles.AllelesBuilder.Companion.metaKeyForAlleleMutationsReliableRanges
import com.milaboratory.mixcr.alleles.CloneRebuild
import com.milaboratory.mixcr.alleles.FindAllelesParameters
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
import com.milaboratory.mixcr.util.XSV.chooseDelimiter
import com.milaboratory.mixcr.util.XSV.writeXSV
import com.milaboratory.primitivio.forEach
import com.milaboratory.primitivio.port
import com.milaboratory.primitivio.withProgress
import com.milaboratory.util.GlobalObjectMappers
import com.milaboratory.util.JsonOverrider
import com.milaboratory.util.ProgressAndStage
import com.milaboratory.util.ReportUtil
import com.milaboratory.util.SmartProgressReporter
import com.milaboratory.util.TempFileDest
import com.milaboratory.util.TempFileManager
import io.repseq.core.GeneFeature.CDR3
import io.repseq.core.GeneType.Joining
import io.repseq.core.GeneType.VDJC_REFERENCE
import io.repseq.core.GeneType.VJ_REFERENCE
import io.repseq.core.GeneType.Variable
import io.repseq.core.VDJCGene
import io.repseq.core.VDJCLibrary
import io.repseq.core.VDJCLibraryRegistry
import io.repseq.dto.VDJCGeneData
import io.repseq.dto.VDJCLibraryData
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
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

@Command(
    description = ["Find allele variants in clnx."]
)
class CommandFindAlleles : MiXCRCommandWithOutputs() {
    data class Params(val dummy: Boolean = true) : MiXCRParams {
        override val command get() = MiXCRCommandDescriptor.findAlleles
    }

    @Parameters(
        arity = "1..*",
        paramLabel = "input_file.clns [input_file2.clns ...]",
        hideParamSyntax = true,
        description = ["Input files for allele search"]
    )
    override val inputFiles: List<Path> = mutableListOf()

    @Option(
        description = [
            "Output template may contain {file_name} and {file_dir_path},",
            "outputs for '-o /output/folder/{file_name}_with_alleles.clns input_file.clns input_file2.clns' will be /output/folder/input_file_with_alleles.clns and /output/folder/input_file2_with_alleles.clns,",
            "outputs for '-o {file_dir_path}/{file_name}_with_alleles.clns /some/folder1/input_file.clns /some/folder2/input_file2.clns' will be /seme/folder1/input_file_with_alleles.clns and /some/folder2/input_file2_with_alleles.clns",
            "Resulted outputs must be uniq"
        ],
        names = ["--output-template", "-o"],
        paramLabel = "<template.clns>"
    )
    var outputTemplate: String? = null

    @Option(
        description = ["Path to write library with found alleles."],
        names = ["--export-library"],
        paramLabel = "<path>"
    )
    var libraryOutput: Path? = null

    @Option(
        description = ["Path to write descriptions and stats for all result alleles, existed and new."],
        names = ["--export-alleles-mutations"],
        paramLabel = "<path>"
    )
    var allelesMutationsOutput: Path? = null

    @Option(
        description = ["Put temporary files in the same folder as the output files."],
        names = ["--use-local-temp"],
        order = 1_000_000 - 5
    )
    var useLocalTemp = false

    @Mixin
    lateinit var threadsOptions: ThreadsOption

    @Option(
        names = ["-O"],
        description = ["Overrides default build SHM parameter values"],
        paramLabel = Labels.OVERRIDES
    )
    var overrides: Map<String, String> = mutableMapOf()

    @Mixin
    lateinit var reportOptions: ReportOptions

    @Option(names = ["--debugDir"], hidden = true)
    var debugDir: Path? = null

    private val outputClnsFiles: List<Path> by lazy {
        val template = outputTemplate ?: return@lazy emptyList()
        if (!template.endsWith(".clns")) {
            throw ValidationException("Wrong template: command produces only clns $template")
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
            throw ValidationException("Output clns files are not uniq: $clnsFiles")
        }
        clnsFiles
    }

    override val outputFiles get() = outputClnsFiles + listOfNotNull(libraryOutput, allelesMutationsOutput)

    private val tempDest: TempFileDest by lazy {
        val path = outputFiles.first()
        if (useLocalTemp) path.toAbsolutePath().parent.createDirectories()
        TempFileManager.smartTempDestination(path, ".find_alleles", !useLocalTemp)
    }

    private val findAllelesParameters: FindAllelesParameters by lazy {
        val findAllelesParametersName = "default"
        var result: FindAllelesParameters = FindAllelesParameters.presets.getByName(findAllelesParametersName)
            ?: throw ValidationException("Unknown parameters: $findAllelesParametersName")
        if (overrides.isNotEmpty()) {
            result = JsonOverrider.override(result, FindAllelesParameters::class.java, overrides)
                ?: throw ValidationException("Failed to override some parameter: $overrides")
        }
        result
    }

    override fun validate() {
        libraryOutput?.let { libraryOutput ->
            if (libraryOutput.extension != "json") {
                throw ValidationException("--export-library must be json: $libraryOutput")
            }
        }
        allelesMutationsOutput?.let { allelesMutationsOutput ->
            if (allelesMutationsOutput.extension !in arrayOf("tsv", "csv")) {
                throw ValidationException("--export-alleles-mutations must be csv: $allelesMutationsOutput")
            }
        }
        if (listOfNotNull(outputTemplate, libraryOutput, allelesMutationsOutput).isEmpty()) {
            throw ValidationException("--output-template, --export-library or --export-alleles-mutations must be set")
        }
    }

    private fun ensureParametersInitialized() {
        findAllelesParameters
        if (debugDir != null) {
            debugDir?.toFile()?.mkdirs()
        }
    }

    override fun run0() {
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
        ensureParametersInitialized()
        val libraryRegistry = VDJCLibraryRegistry.getDefault()
        val cloneReaders = inputFiles.map { CloneSetIO.mkReader(it, libraryRegistry) }
        require(cloneReaders.map { it.alignerParameters }.distinct().count() == 1) {
            "input files must have the same aligner parameters"
        }
        require(cloneReaders.all { it.header.allFullyCoveredBy != null }) {
            "Input files must not be processed by ${CommandAssembleContigs.COMMAND_NAME} without ${AssembleContigsMixins.SetContigAssemblingFeatures.CMD_OPTION} option"
        }
        require(cloneReaders.map { it.header.allFullyCoveredBy }.distinct().count() == 1) {
            "Input files must be cut by the same geneFeature"
        }

        val allFullyCoveredBy = cloneReaders.first().header.allFullyCoveredBy!!

        val allelesBuilder = AllelesBuilder.create(
            findAllelesParameters,
            clonesFilter,
            tempDest,
            cloneReaders,
            allFullyCoveredBy,
            debugDir
        )

        val progressAndStage = ProgressAndStage("Grouping by the same J gene", 0.0)
        SmartProgressReporter.startProgressReport(progressAndStage)
        val JAlleles =
            allelesBuilder.searchForAlleles(Joining, emptyMap(), progressAndStage, reportBuilder, threadsOptions.value)
        val VAlleles =
            allelesBuilder.searchForAlleles(Variable, JAlleles, progressAndStage, reportBuilder, threadsOptions.value)

        val alleles = (VAlleles + JAlleles).toMutableMap()
        val usedGenes = collectUsedGenes(cloneReaders, alleles)
        registerNotProcessedVJ(alleles, usedGenes)
        val resultLibrary = buildLibrary(libraryRegistry, cloneReaders, usedGenes)
        libraryOutput?.let { libraryOutput ->
            libraryOutput.toAbsolutePath().parent.createDirectories()
            GlobalObjectMappers.getOneLine().writeValue(libraryOutput.toFile(), arrayOf(resultLibrary.data))
        }
        val allelesMapping = alleles.mapValues { (_, geneDatum) ->
            geneDatum.map { resultLibrary[it.name].id }
        }
        val writerCloseCallbacks = mutableListOf<(FindAllelesReport) -> Unit>()
        cloneReaders.forEachIndexed { i, cloneReader ->
            val cloneRebuild = CloneRebuild(
                resultLibrary,
                allelesMapping,
                allFullyCoveredBy,
                threadsOptions.value,
                cloneReader.assemblerParameters,
                cloneReader.alignerParameters
            )
            cloneReader.readClones().use { port ->
                val withRecalculatedScores = port.withProgress(
                    cloneReader.numberOfClones().toLong(),
                    progressAndStage,
                    "Recalculating scores ${inputFiles[i]}"
                ) { clones ->
                    cloneRebuild.recalculateScores(clones, cloneReader.tagsInfo, reportBuilder)
                }
                if (outputTemplate != null) {
                    withRecalculatedScores.port.withProgress(
                        cloneReader.numberOfClones().toLong(),
                        progressAndStage,
                        "Realigning ${inputFiles[i]}"
                    ) { clonesWithScores ->
                        val mapperClones = cloneRebuild.rebuildClones(clonesWithScores)
                        outputClnsFiles[i].toAbsolutePath().parent.createDirectories()
                        val callback = outputClnsFiles[i].toFile()
                            .writeMappedClones(mapperClones, resultLibrary, cloneReader)
                        writerCloseCallbacks += callback
                    }
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
                resultLibrary,
                reportBuilder.overallAllelesStatistics,
                allelesMutationsOutput
            )
        }
        ReportUtil.writeReportToStdout(report)
        reportOptions.appendToFiles(report)
    }

    private fun printAllelesMutationsOutput(
        resultLibrary: VDJCLibrary,
        allelesStatistics: OverallAllelesStatistics,
        allelesMutationsOutput: Path
    ) {
        PrintStream(allelesMutationsOutput.toFile()).use { output ->
            val columns = buildMap<String, (VDJCGene) -> Any?> {
                this["alleleName"] = { it.name }
                this["geneName"] = { it.geneName }
                this["type"] = { it.geneType }
                this[metaKeyForAlleleMutationsReliableRanges] = { gene ->
                    gene.data.meta[metaKeyForAlleleMutationsReliableRanges]
                }
                this[metaKeyForAlleleMutationsReliableGeneFeatures] = { gene ->
                    gene.data.meta[metaKeyForAlleleMutationsReliableGeneFeatures]
                }
                this["mutations"] = { gene ->
                    gene.data.baseSequence.mutations?.encode() ?: ""
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
            val genes = resultLibrary.genes
                .filter { it.geneType in VJ_REFERENCE }
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
            resultLibrary.genes,
            cloneReader.header.copy(
                foundAlleles = MiXCRHeader.FoundAlleles(
                    resultLibrary.name,
                    resultLibrary.data
                )
            ).addStepParams(MiXCRCommandDescriptor.findAlleles, Params()),
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
        cloneReaders: List<CloneReader>,
        usedGenes: Map<String, VDJCGeneData>
    ): VDJCLibrary {
        val originalLibrary = cloneReaders.first().usedGenes.first().parentLibrary
        val resultLibrary = VDJCLibrary(
            VDJCLibraryData(originalLibrary.data, ArrayList(usedGenes.values)),
            originalLibrary.name + "_with_found_alleles",
            libraryRegistry,
            null
        )
        usedGenes.values.forEach { VDJCLibrary.addGene(resultLibrary, it) }
        return resultLibrary
    }

    private fun registerNotProcessedVJ(
        alleles: MutableMap<String, List<VDJCGeneData>>,
        usedGenes: Map<String, VDJCGeneData>
    ) {
        usedGenes.forEach { (name, geneData) ->
            if (geneData.geneType in VJ_REFERENCE) {
                // if gene wasn't processed in alleles search, then register it as a single allele
                if (!alleles.containsKey(name)) {
                    alleles[geneData.name] = listOf(geneData)
                }
            }
        }
    }

    private fun collectUsedGenes(
        cloneReaders: List<CloneReader>,
        alleles: Map<String, List<VDJCGeneData>>
    ): Map<String, VDJCGeneData> {
        val usedGenes = mutableMapOf<String, VDJCGeneData>()
        alleles.values
            .flatten()
            .forEach { usedGenes[it.name] = it }
        for (cloneReader in cloneReaders) {
            cloneReader.readClones().use { port ->
                port.forEach { clone ->
                    for (gt in VDJC_REFERENCE) {
                        for (hit in clone.getHits(gt)) {
                            val geneName = hit.gene.name
                            if (geneName !in alleles && geneName !in usedGenes) {
                                usedGenes[geneName] = hit.gene.data
                            }
                        }
                    }
                }
            }
        }
        return usedGenes
    }

    companion object {
        const val COMMAND_NAME = "findAlleles"
    }
}
