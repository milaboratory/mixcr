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
    name = CommandFindAlleles.FIND_ALLELES_COMMAND_NAME,
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

    public override fun getOutputFiles(): List<String> =
        (outputClnsFiles + listOfNotNull(libraryOutput, allelesMutationsOutput))
            .map { it.toString() }

    private val tempDest: TempFileDest by lazy {
        val path = listOfNotNull(outputClnsFiles.firstOrNull(), libraryOutput, allelesMutationsOutput).first()
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
    }

    //TODO report
    override fun run0() {
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
            allFullyCoveredBy
        )

        val progressAndStage = ProgressAndStage("Grouping by the same V gene", 0.0)
        SmartProgressReporter.startProgressReport(progressAndStage)
        val VAlleles = allelesBuilder.searchForAlleles(Variable, progressAndStage, threads)
        val JAlleles = allelesBuilder.searchForAlleles(Joining, progressAndStage, threads)

        val alleles = (VAlleles + JAlleles).toMap(mutableMapOf())
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
        val overallAllelesStatistics = OverallAllelesStatistics()
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
                    cloneRebuild.recalculateScores(clones, overallAllelesStatistics)
                }
                if (outputTemplate != null) {
                    withRecalculatedScores.port.withProgress(
                        cloneReader.numberOfClones().toLong(),
                        progressAndStage,
                        "Realigning ${inputFiles[i]}"
                    ) { clonesWithScores ->
                        val mapperClones = cloneRebuild.rebuildClones(clonesWithScores)
                        outputClnsFiles[i].toAbsolutePath().parent.createDirectories()
                        outputClnsFiles[i].toFile().writeMappedClones(mapperClones, resultLibrary, cloneReader)
                    }
                }
            }
        }
        progressAndStage.finish()
        allelesMutationsOutput?.let { allelesMutationsOutput ->
            allelesMutationsOutput.toAbsolutePath().parent.createDirectories()
            printAllelesMutationsOutput(resultLibrary, overallAllelesStatistics, allelesMutationsOutput.toFile())
        }
    }

    private fun printAllelesMutationsOutput(
        resultLibrary: VDJCLibrary,
        allelesStatistics: OverallAllelesStatistics,
        allelesMutationsOutput: File
    ) {
        PrintStream(allelesMutationsOutput).use { output ->
            val columns = mapOf<String, (VDJCGene) -> Any?>(
                "alleleName" to { it.name },
                "geneName" to { it.geneName },
                "type" to { it.geneType },
                metaKeyForAlleleMutationsReliableRanges to { gene ->
                    gene.data.meta[metaKeyForAlleleMutationsReliableRanges]
                },
                metaKeyForAlleleMutationsReliableGeneFeatures to { gene ->
                    gene.data.meta[metaKeyForAlleleMutationsReliableGeneFeatures]
                },
                "mutations" to { gene ->
                    gene.data.baseSequence.mutations?.encode() ?: ""
                },
                "naivesCount" to { gene ->
                    allelesStatistics.stats(gene.id).naives.get()
                },
                "lowerDiversityBound" to { gene ->
                    allelesStatistics.stats(gene.id).diversity.size
                },
                "count" to { gene ->
                    allelesStatistics.stats(gene.id).count.get()
                },
                "totalCount" to { gene ->
                    allelesStatistics.baseGeneCount(gene.id)
                }
            )
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
    ) {
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
        ClnsWriter(this).use { clnsWriter ->
            clnsWriter.writeCloneSet(cloneSet)
            //TODO make and write search alleles report
            clnsWriter.writeFooter(cloneReader.reports(), null)
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
            if (geneData.geneType == Joining || geneData.geneType == Variable) {
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
        const val FIND_ALLELES_COMMAND_NAME = "findAlleles"
    }
}
