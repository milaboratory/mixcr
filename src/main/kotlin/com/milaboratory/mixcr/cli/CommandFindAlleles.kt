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
package com.milaboratory.mixcr.cli

import cc.redberry.pipe.CUtils
import cc.redberry.pipe.blocks.Buffer
import com.milaboratory.core.Range
import com.milaboratory.core.alignment.Alignment
import com.milaboratory.core.alignment.AlignmentScoring
import com.milaboratory.core.alignment.AlignmentUtils
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.alleles.AllelesBuilder
import com.milaboratory.mixcr.alleles.AllelesBuilder.SortedClonotypes
import com.milaboratory.mixcr.alleles.FindAllelesParameters
import com.milaboratory.mixcr.assembler.CloneFactory
import com.milaboratory.mixcr.basictypes.ClnsWriter
import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.basictypes.CloneReader
import com.milaboratory.mixcr.basictypes.CloneSet
import com.milaboratory.mixcr.basictypes.CloneSetIO
import com.milaboratory.mixcr.basictypes.GeneAndScore
import com.milaboratory.mixcr.trees.MutationsUtils.positionIfNucleotideWasDeleted
import com.milaboratory.mixcr.util.XSV.writeXSVBody
import com.milaboratory.mixcr.util.XSV.writeXSVHeaders
import com.milaboratory.util.GlobalObjectMappers
import com.milaboratory.util.JsonOverrider
import com.milaboratory.util.TempFileDest
import com.milaboratory.util.TempFileManager
import io.repseq.core.GeneType
import io.repseq.core.GeneType.Constant
import io.repseq.core.GeneType.Diversity
import io.repseq.core.GeneType.Joining
import io.repseq.core.GeneType.VDJC_REFERENCE
import io.repseq.core.GeneType.Variable
import io.repseq.core.VDJCGene
import io.repseq.core.VDJCGeneId
import io.repseq.core.VDJCLibrary
import io.repseq.core.VDJCLibraryRegistry
import io.repseq.dto.VDJCGeneData
import io.repseq.dto.VDJCLibraryData
import org.apache.commons.io.FilenameUtils
import picocli.CommandLine
import java.io.File
import java.io.PrintStream
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

@CommandLine.Command(
    name = CommandFindAlleles.FIND_ALLELES_COMMAND_NAME,
    sortOptions = false,
    separator = " ",
    description = ["Find allele variants in clns."]
)
class CommandFindAlleles : ACommandWithOutputMiXCR() {
    @CommandLine.Parameters(
        arity = "2..*", description = ["""input_file.clns [input_file2.clns ....] output_template.clns
output_template may contain {file_name} and {file_dir_path},
outputs for 'input_file.clns input_file2.clns /output/folder/{file_name}_with_alleles.clns' will be /output/folder/input_file_with_alleles.clns and /output/folder/input_file2_with_alleles.clns,
outputs for '/some/folder1/input_file.clns /some/folder2/input_file2.clns {file_dir_path}/{file_name}_with_alleles.clns' will be /seme/folder1/input_file_with_alleles.clns and /some/folder2/input_file2_with_alleles.clns
Resulted outputs must be uniq"""]
    )
    private val inOut: List<String> = ArrayList()

    @CommandLine.Option(
        description = ["Use system temp folder for temporary files, the output folder will be used if this option is omitted."],
        names = ["--use-system-temp"]
    )
    var useSystemTemp = false

    private val outputClnsFiles: List<String> by lazy {
        val template = inOut[inOut.size - 1]
        if (!template.endsWith(".clns")) {
            throwValidationException("Wrong template: command produces only clns $template")
        }
        val clnsFiles = inputFiles
            .map { Paths.get(it).toAbsolutePath() }
            .map { path: Path ->
                template
                    .replace(Regex("\\{file_name}"), FilenameUtils.removeExtension(path.fileName.toString()))
                    .replace(Regex("\\{file_dir_path}"), path.parent.toString())
            }
            .toList()
        if (clnsFiles.distinct().count() < clnsFiles.size) {
            throwValidationException("Output clns files are not uniq: $clnsFiles")
        }
        clnsFiles
    }

    public override fun getInputFiles(): List<String> {
        return inOut.subList(0, inOut.size - 1)
    }

    public override fun getOutputFiles(): List<String> = when {
        libraryOutput != null -> outputClnsFiles + libraryOutput!!
        else -> outputClnsFiles
    }

    @CommandLine.Option(description = ["Processing threads"], names = ["-t", "--threads"])
    var threads = Runtime.getRuntime().availableProcessors()
        set(value) {
            if (value <= 0) throwValidationException("-t / --threads must be positive")
            field = value
        }

    @CommandLine.Option(description = ["File to write library with found alleles."], names = ["--export-library"])
    var libraryOutput: String? = null

    @CommandLine.Option(description = ["File to description of each allele."], names = ["--export-alleles-mutations"])
    var allelesMutationsOutput: String? = null

    @CommandLine.Option(description = ["Find alleles parameters preset."], names = ["-p", "--preset"])
    var findAllelesParametersName = "default"

    @CommandLine.Option(names = ["-O"], description = ["Overrides default build SHM parameter values"])
    var overrides: Map<String, String> = HashMap()

    private val findAllelesParameters: FindAllelesParameters by lazy {
        var result = FindAllelesParameters.presets.getByName(findAllelesParametersName)
        if (result == null) throwValidationException("Unknown parameters: $findAllelesParametersName")
        if (overrides.isNotEmpty()) {
            result = JsonOverrider.override(result!!, FindAllelesParameters::class.java, overrides)
            if (result == null) throwValidationException("Failed to override some parameter: $overrides")
        }
        result!!
    }

    override fun validate() {
        if (libraryOutput != null) {
            if (!libraryOutput!!.endsWith(".json")) {
                throwValidationException("--export-library must be json: $libraryOutput")
            }
        }
        if (allelesMutationsOutput != null) {
            if (!allelesMutationsOutput!!.endsWith(".csv")) {
                throwValidationException("--export-alleles-mutations must be csv: $allelesMutationsOutput")
            }
        }
    }

    private fun ensureParametersInitialized() {
        findAllelesParameters
    }

    //TODO report
    @Throws(Exception::class)
    override fun run0() {
        ensureParametersInitialized()
        val libraryRegistry = VDJCLibraryRegistry.getDefault()
        val cloneReaders = inputFiles.map { CloneSetIO.mkReader(Paths.get(it), libraryRegistry) }
        require(cloneReaders.isNotEmpty()) { "there is no files to process" }
        require(cloneReaders.map { it.assemblerParameters }.distinct().count() == 1) {
            "input files must have the same assembler parameters"
        }

        val tempDest: TempFileDest = TempFileManager.smartTempDestination(outputClnsFiles.first(), "", useSystemTemp)
        val allelesBuilder = AllelesBuilder(findAllelesParameters!!, cloneReaders, tempDest)
        val sortedClonotypes = allelesBuilder.sortClonotypes()
        val alleles = (
                buildAlleles(allelesBuilder, sortedClonotypes, Variable) +
                        buildAlleles(allelesBuilder, sortedClonotypes, Joining)
                ).toMap().toMutableMap()
        val usedGenes = collectUsedGenes(cloneReaders, alleles)
        registerNotProcessedVJ(alleles, usedGenes)
        val resultLibrary = buildLibrary(libraryRegistry, cloneReaders, usedGenes)
        if (libraryOutput != null) {
            val libraryOutputFile = Paths.get(libraryOutput!!).toAbsolutePath()
            libraryOutputFile.parent.toFile().mkdirs()
            GlobalObjectMappers.getOneLine().writeValue(libraryOutputFile.toFile(), resultLibrary.data)
        }
        val allelesMapping = alleles.mapValues { (_, geneDatum) ->
            geneDatum.map { resultLibrary[it.name].id }
        }
        val allelesStatistics = mutableMapOf<String, Int>()
        cloneReaders.forEachIndexed { i, cloneReader ->
            val mapperClones = rebuildClones(resultLibrary, allelesMapping, cloneReader)
            mapperClones.forEach { clone ->
                for (geneType in arrayOf(Variable, Joining)) {
                    allelesStatistics.increment(clone.getBestHit(geneType).gene.name)
                    allelesStatistics.increment(clone.getBestHit(geneType).gene.geneName)
                }
            }
            File(outputClnsFiles[i]).writeMappedClones(mapperClones, resultLibrary, cloneReader)
        }
        if (allelesMutationsOutput != null) {
            Paths.get(allelesMutationsOutput!!).toAbsolutePath().parent.toFile().mkdirs()
            printAllelesMutationsOutput(resultLibrary, allelesStatistics)
        }
    }

    private fun <T> MutableMap<T, Int>.increment(key: T) {
        merge(key, 0) { old, _ -> old + 1 }
    }

    private fun printAllelesMutationsOutput(resultLibrary: VDJCLibrary, allelesStatistics: Map<String, Int>) {
        PrintStream(allelesMutationsOutput!!).use { output ->
            val columns = mapOf<String, (VDJCGene) -> Any?>(
                "geneName" to { it.name },
                "type" to { it.geneType },
                "regions" to { gene ->
                    gene.data.baseSequence.regions?.joinToString { it.toString() }
                },
                "mutations" to { gene ->
                    gene.data.baseSequence.mutations?.encode() ?: ""
                },
                "count" to { gene ->
                    allelesStatistics[gene.name]
                },
                "totalCount" to { gene ->
                    allelesStatistics[gene.geneName]
                }
            )
            writeXSVHeaders(output, columns.keys, ";")
            val genes = resultLibrary.genes
                .sortedWith(Comparator.comparing { it: VDJCGene -> it.geneType }
                    .thenComparing { it: VDJCGene -> it.name })
            writeXSVBody(output, genes, columns, ";")
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
            cloneReader.alignerParameters,
            cloneReader.assemblerParameters,
            cloneReader.tagsInfo,
            cloneReader.ordering()
        )
        ClnsWriter(this).use { clnsWriter ->
            clnsWriter.writeCloneSet(
                cloneReader.pipelineConfiguration,
                cloneSet,
                listOf(resultLibrary)
            )
        }
    }

    private fun buildLibrary(
        libraryRegistry: VDJCLibraryRegistry,
        cloneReaders: List<CloneReader>,
        usedGenes: Map<String, VDJCGeneData>
    ): VDJCLibrary {
        val originalLibrary = anyClone(cloneReaders).getBestHit(Variable).gene.parentLibrary
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
                CUtils.it(port).forEach { clone ->
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

    private fun rebuildClones(
        resultLibrary: VDJCLibrary,
        allelesMapping: Map<String, List<VDJCGeneId>>,
        cloneReader: CloneReader
    ): List<Clone> {
        val cloneFactory = CloneFactory(
            cloneReader.assemblerParameters.cloneFactoryParameters,
            cloneReader.assemblerParameters.assemblingFeatures,
            resultLibrary.genes,
            cloneReader.alignerParameters.featuresToAlignMap
        )
        val result = CUtils.orderedParallelProcessor(
            cloneReader.readClones(),
            { clone -> rebuildClone(resultLibrary, allelesMapping, cloneFactory, clone) },
            Buffer.DEFAULT_SIZE,
            threads
        )
        return CUtils.it(result).toList()
    }

    private fun rebuildClone(
        resultLibrary: VDJCLibrary,
        allelesMapping: Map<String, List<VDJCGeneId>>,
        cloneFactory: CloneFactory,
        clone: Clone
    ): Clone {
        val originalGeneScores = EnumMap<GeneType, List<GeneAndScore>>(
            GeneType::class.java
        )
        //copy D and C
        for (gt in listOf(Diversity, Constant)) {
            originalGeneScores[gt] = clone.getHits(gt).map { hit ->
                val mappedGeneId = VDJCGeneId(resultLibrary.libraryId, hit.gene.name)
                GeneAndScore(mappedGeneId, hit.score)
            }
        }
        for (gt in listOf(Variable, Joining)) {
            originalGeneScores[gt] = clone.getHits(gt).flatMap { hit ->
                allelesMapping[hit.gene.name]!!.map { foundAlleleId ->
                    if (foundAlleleId.name != hit.gene.name) {
                        val scoreDelta = scoreDelta(
                            resultLibrary[foundAlleleId.name],
                            cloneFactory.parameters.getVJCParameters(gt).scoring,
                            hit.alignments
                        )
                        GeneAndScore(foundAlleleId, hit.score + scoreDelta)
                    } else {
                        GeneAndScore(foundAlleleId, hit.score)
                    }
                }
            }
        }
        return cloneFactory.create(
            clone.id,
            clone.count,
            originalGeneScores,
            clone.tagCount,
            clone.targets,
            clone.group
        )
    }

    private fun buildAlleles(
        allelesBuilder: AllelesBuilder,
        sortedClonotypes: SortedClonotypes,
        geneType: GeneType
    ): List<Pair<String, List<VDJCGeneData>>> {
        val sortedClones = sortedClonotypes.getSortedBy(geneType)
        val clusters = allelesBuilder.buildClusters(sortedClones, geneType)
        val result = CUtils.orderedParallelProcessor(
            clusters,
            { cluster ->
                val geneId = cluster.cluster[0].getBestHit(geneType).gene.name
                val resultGenes = allelesBuilder.allelesGeneData(cluster, geneType)
                geneId to resultGenes
            },
            Buffer.DEFAULT_SIZE,
            threads
        )
        return CUtils.it(result).toList()
    }

    private fun scoreDelta(
        foundAllele: VDJCGene,
        scoring: AlignmentScoring<NucleotideSequence>,
        alignments: Array<Alignment<NucleotideSequence>>
    ): Float {
        var scoreDelta = 0.0f
        //recalculate score for every alignment based on found allele
        for (alignment in alignments) {
            val alleleMutations = foundAllele.data.baseSequence.mutations
            if (alleleMutations != null) {
                val seq1RangeAfterAlleleMutations = Range(
                    positionIfNucleotideWasDeleted(alleleMutations.convertToSeq2Position(alignment.sequence1Range.lower)),
                    positionIfNucleotideWasDeleted(alleleMutations.convertToSeq2Position(alignment.sequence1Range.upper))
                )
                val mutationsFromAllele = alignment.absoluteMutations.invert().combineWith(alleleMutations).invert()
                val recalculatedScore = AlignmentUtils.calculateScore(
                    alleleMutations.mutate(alignment.sequence1),
                    seq1RangeAfterAlleleMutations,
                    mutationsFromAllele.extractAbsoluteMutationsForRange(seq1RangeAfterAlleleMutations),
                    scoring
                )
                scoreDelta += recalculatedScore - alignment.score
            }
        }
        return scoreDelta
    }

    private fun anyClone(cloneReaders: List<CloneReader>): Clone {
        cloneReaders[0].readClones().use { port -> return port.take() }
    }

    companion object {
        const val FIND_ALLELES_COMMAND_NAME = "findAlleles"
    }
}
