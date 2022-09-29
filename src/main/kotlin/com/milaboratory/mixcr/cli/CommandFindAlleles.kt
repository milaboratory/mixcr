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
import com.milaboratory.mixcr.basictypes.MiXCRMetaInfo
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
    name = CommandFindAlleles.COMMAND_NAME,
    sortOptions = false,
    separator = " ",
    description = ["Find allele variants in clnx."]
)
class CommandFindAlleles : MiXCRCommand() {
    @Parameters(
        arity = "1..*",
        paramLabel = "input_file.clns [input_file2.clns ...]",
        description = ["Input files for allele search"]
    )
    lateinit var `in`: List<Path>

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
        description = ["File to write library with found alleles."],
        names = ["--export-library"],
        paramLabel = "<path>"
    )
    var libraryOutput: Path? = null

    @Option(
        description = ["File to description of each allele."],
        names = ["--export-alleles-mutations"],
        paramLabel = "<path>"
    )
    var allelesMutationsOutput: Path? = null

    @Option(
        description = ["Find alleles parameters preset."],
        names = ["-p", "--preset"],
        defaultValue = "default",
        paramLabel = "<preset>"
    )
    lateinit var findAllelesParametersName: String

    @Option(
        description = ["Put temporary files in the same folder as the output files."],
        names = ["--use-local-temp"]
    )
    var useLocalTemp = false

    @Option(description = ["Processing threads"], names = ["-t", "--threads"])
    var threads = Runtime.getRuntime().availableProcessors()
        set(value) {
            if (value <= 0) throwValidationExceptionKotlin("-t / --threads must be positive")
            field = value
        }

    @Option(names = ["-O"], description = ["Overrides default build SHM parameter values"])
    var overrides: Map<String, String> = mutableMapOf()

    @Option(description = [CommonDescriptions.REPORT], names = ["-r", "--report"])
    var reportFile: String? = null

    @Option(description = [CommonDescriptions.JSON_REPORT], names = ["-j", "--json-report"])
    var jsonReport: String? = null

    @Option(names = ["--debugDir"], hidden = true)
    var debugDir: Path? = null

    private val outputClnsFiles: List<Path> by lazy {
        val template = outputTemplate ?: return@lazy emptyList()
        if (!template.endsWith(".clns")) {
            throwValidationExceptionKotlin("Wrong template: command produces only clns $template")
        }
        val clnsFiles = `in`
            .map { it.toAbsolutePath() }
            .map { path ->
                template
                    .replace(Regex("\\{file_name}"), path.nameWithoutExtension)
                    .replace(Regex("\\{file_dir_path}"), path.parent.toString())
            }
            .map { Paths.get(it) }
            .toList()
        if (clnsFiles.distinct().count() < clnsFiles.size) {
            throwValidationExceptionKotlin("Output clns files are not uniq: $clnsFiles")
        }
        clnsFiles
    }

    public override fun getInputFiles(): List<String> = `in`.map { it.toString() }

    public override fun getOutputFiles(): List<String> = outputPaths.map { it.toString() }

    private val outputPaths get() = outputClnsFiles + listOfNotNull(libraryOutput, allelesMutationsOutput)

    private val tempDest: TempFileDest by lazy {
        val path = outputPaths.first()
        if (useLocalTemp) path.toAbsolutePath().parent.createDirectories()
        TempFileManager.smartTempDestination(path, ".find_alleles", !useLocalTemp)
    }

    private val findAllelesParameters: FindAllelesParameters by lazy {
        var result: FindAllelesParameters = FindAllelesParameters.presets.getByName(findAllelesParametersName)
            ?: throwValidationExceptionKotlin("Unknown parameters: $findAllelesParametersName")
        if (overrides.isNotEmpty()) {
            result = JsonOverrider.override(result, FindAllelesParameters::class.java, overrides)
                ?: throwValidationExceptionKotlin("Failed to override some parameter: $overrides")
        }
        result
    }

    override fun validate() {
        super.validate()
        libraryOutput?.let { libraryOutput ->
            if (!libraryOutput.extension.endsWith("json")) {
                throwValidationExceptionKotlin("--export-library must be json: $libraryOutput")
            }
        }
        allelesMutationsOutput?.let { allelesMutationsOutput ->
            if (!allelesMutationsOutput.extension.endsWith("csv")) {
                throwValidationExceptionKotlin("--export-alleles-mutations must be csv: $allelesMutationsOutput")
            }
        }
        if (listOfNotNull(outputTemplate, libraryOutput, allelesMutationsOutput).isEmpty()) {
            throwValidationExceptionKotlin("--output-template, --export-library or --export-alleles-mutations must be set")
        }
    }

    private fun ensureParametersInitialized() {
        findAllelesParameters
        if (debugDir != null) {
            debugDir?.toFile()?.mkdirs()
        }
    }

    override fun run0() {
        val reportBuilder = FindAllelesReport.Builder(
            OverallAllelesStatistics(findAllelesParameters.useClonesWithCountGreaterThen)
        )
            .setCommandLine(commandLineArguments)
            .setInputFiles(inputFiles)
            .setOutputFiles(outputFiles)
            .setStartMillis(System.currentTimeMillis())
        ensureParametersInitialized()
        val libraryRegistry = VDJCLibraryRegistry.getDefault()
        val cloneReaders = inputFiles.map { CloneSetIO.mkReader(Paths.get(it), libraryRegistry) }
        require(cloneReaders.map { it.alignerParameters }.distinct().count() == 1) {
            "input files must have the same aligner parameters"
        }
        require(cloneReaders.all { it.info.allFullyCoveredBy != null }) {
            "Input files must not be processed by ${CommandAssembleContigs.ASSEMBLE_CONTIGS_COMMAND_NAME} without ${CommandAssembleContigs.CUT_BY_FEATURE_OPTION_NAME} option"
        }
        require(cloneReaders.map { it.info.allFullyCoveredBy }.distinct().count() == 1) {
            "Input files must be cut by the same geneFeature"
        }
        val allFullyCoveredBy = cloneReaders.first().info.allFullyCoveredBy!!

        val allelesBuilder = AllelesBuilder(
            findAllelesParameters,
            tempDest,
            cloneReaders,
            allFullyCoveredBy,
            debugDir
        )

        val progressAndStage = ProgressAndStage("Grouping by the same J gene", 0.0)
        SmartProgressReporter.startProgressReport(progressAndStage)
        val JAlleles = allelesBuilder.searchForAlleles(Joining, emptyMap(), progressAndStage, reportBuilder, threads)
        val VAlleles = allelesBuilder.searchForAlleles(Variable, JAlleles, progressAndStage, reportBuilder, threads)

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
        val writerCloseCallbacks = mutableListOf<(MiXCRCommandReport) -> Unit>()
        cloneReaders.forEachIndexed { i, cloneReader ->
            val cloneRebuild = CloneRebuild(
                resultLibrary,
                allelesMapping,
                allFullyCoveredBy,
                threads,
                cloneReader.assemblerParameters,
                cloneReader.alignerParameters
            )
            cloneReader.readClones().use { port ->
                val withRecalculatedScores = port.withProgress(
                    cloneReader.numberOfClones().toLong(),
                    progressAndStage,
                    "Recalculating scores ${inputFiles[i]}"
                ) { clones ->
                    cloneRebuild.recalculateScores(clones, reportBuilder)
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
                allelesMutationsOutput.toFile()
            )
        }
        ReportUtil.writeReportToStdout(report)
        if (reportFile != null) ReportUtil.appendReport(reportFile, report)
        if (jsonReport != null) ReportUtil.appendJsonReport(jsonReport, report)
    }

    private fun printAllelesMutationsOutput(
        resultLibrary: VDJCLibrary,
        allelesStatistics: OverallAllelesStatistics,
        allelesMutationsOutput: File
    ) {
        PrintStream(allelesMutationsOutput).use { output ->
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
                    allelesStatistics.stats(gene.id).naives(filteredByCount = false)
                }
                this["lowerDiversityBound"] = { gene ->
                    allelesStatistics.stats(gene.id).diversity()
                }
                this["clonesCount"] = { gene ->
                    allelesStatistics.stats(gene.id).count(filteredByCount = false)
                }
                this["totalClonesCountForGene"] = { gene ->
                    allelesStatistics.baseGeneCount(gene.id)
                }
                this["clonesCountWithNegativeScoreChange"] = { gene ->
                    allelesStatistics.stats(gene.id).withNegativeScoreChange(filteredByCount = false)
                }
                if (allelesStatistics.useClonesWithCountGreaterThen != 0) {
                    this["naivesCountWithCountGreaterThen${allelesStatistics.useClonesWithCountGreaterThen}"] =
                        { gene ->
                            allelesStatistics.stats(gene.id).naives(filteredByCount = true)
                        }
                    this["clonesCountWithCountGreaterThen${allelesStatistics.useClonesWithCountGreaterThen}"] =
                        { gene ->
                            allelesStatistics.stats(gene.id).count(filteredByCount = true)
                        }
                    this["clonesCountWithNegativeScoreChangeWithCountGreaterThen${allelesStatistics.useClonesWithCountGreaterThen}"] =
                        { gene ->
                            allelesStatistics.stats(gene.id).withNegativeScoreChange(filteredByCount = true)
                        }
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
            writeXSV(output, genes, columns, ";")
        }
    }

    private fun File.writeMappedClones(
        clones: List<Clone>,
        resultLibrary: VDJCLibrary,
        cloneReader: CloneReader
    ): (MiXCRCommandReport) -> Unit {
        toPath().toAbsolutePath().parent.toFile().mkdirs()
        val cloneSet = CloneSet(
            clones,
            resultLibrary.genes,
            cloneReader.info.copy(
                foundAlleles = MiXCRMetaInfo.FoundAlleles(
                    resultLibrary.name,
                    resultLibrary.data
                )
            ),
            cloneReader.ordering()
        )
        val clnsWriter = ClnsWriter(this)
        clnsWriter.writeCloneSet(cloneSet)
        return { report ->
            clnsWriter.writeFooter(cloneReader.reports(), report)
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
                //if gene wasn't processed in alleles search, then register it as a single allele
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
