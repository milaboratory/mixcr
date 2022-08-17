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

import cc.redberry.pipe.OutputPort
import com.milaboratory.core.Range
import com.milaboratory.core.alignment.Alignment
import com.milaboratory.core.alignment.AlignmentScoring
import com.milaboratory.core.alignment.AlignmentUtils
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.alleles.AllelesBuilder
import com.milaboratory.mixcr.alleles.FindAllelesParameters
import com.milaboratory.mixcr.assembler.CloneAssemblerParameters
import com.milaboratory.mixcr.assembler.CloneFactory
import com.milaboratory.mixcr.basictypes.ClnsWriter
import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.basictypes.CloneReader
import com.milaboratory.mixcr.basictypes.CloneSet
import com.milaboratory.mixcr.basictypes.CloneSetIO
import com.milaboratory.mixcr.basictypes.GeneAndScore
import com.milaboratory.mixcr.basictypes.VDJCHit
import com.milaboratory.mixcr.basictypes.tag.TagCountAggregator
import com.milaboratory.mixcr.trees.MutationsUtils.positionIfNucleotideWasDeleted
import com.milaboratory.mixcr.util.VJPair
import com.milaboratory.mixcr.util.XSV.writeXSV
import com.milaboratory.mixcr.util.alignmentsCover
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters
import com.milaboratory.primitivio.filter
import com.milaboratory.primitivio.forEach
import com.milaboratory.primitivio.mapInParallel
import com.milaboratory.primitivio.toList
import com.milaboratory.primitivio.withProgress
import com.milaboratory.util.GlobalObjectMappers
import com.milaboratory.util.JsonOverrider
import com.milaboratory.util.ProgressAndStage
import com.milaboratory.util.SmartProgressReporter
import com.milaboratory.util.TempFileDest
import com.milaboratory.util.TempFileManager
import io.repseq.core.GeneFeature
import io.repseq.core.GeneType
import io.repseq.core.GeneType.Constant
import io.repseq.core.GeneType.Diversity
import io.repseq.core.GeneType.Joining
import io.repseq.core.GeneType.VDJC_REFERENCE
import io.repseq.core.GeneType.VJ_REFERENCE
import io.repseq.core.GeneType.Variable
import io.repseq.core.ReferencePoint.CDR3Begin
import io.repseq.core.ReferencePoint.CDR3End
import io.repseq.core.ReferencePoint.FR4End
import io.repseq.core.ReferencePoint.UTR5Begin
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
import kotlin.math.min

@CommandLine.Command(
    name = CommandFindAlleles.FIND_ALLELES_COMMAND_NAME,
    sortOptions = false,
    separator = " ",
    description = ["Find allele variants in clns."]
)
class CommandFindAlleles : MiXCRCommand() {
    @CommandLine.Parameters(
        arity = "2..*",
        description = [
            "input_file.clns [input_file2.clns ....] output_template.clns",
            "output_template may contain {file_name} and {file_dir_path},",
            "outputs for 'input_file.clns input_file2.clns /output/folder/{file_name}_with_alleles.clns' will be /output/folder/input_file_with_alleles.clns and /output/folder/input_file2_with_alleles.clns,",
            "outputs for '/some/folder1/input_file.clns /some/folder2/input_file2.clns {file_dir_path}/{file_name}_with_alleles.clns' will be /seme/folder1/input_file_with_alleles.clns and /some/folder2/input_file2_with_alleles.clns",
            "Resulted outputs must be uniq"
        ]
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

    public override fun getInputFiles(): List<String> = inOut.subList(0, inOut.size - 1)

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

    @CommandLine.Option(
        description = ["Find alleles parameters preset."],
        names = ["-p", "--preset"],
        defaultValue = "default"
    )
    lateinit var findAllelesParametersName: String

    @CommandLine.Option(
        description = ["Search alleles within GeneFeature."],
        names = ["--region"],
        defaultValue = "VDJRegion"
    )
    lateinit var geneFeatureToSearchParameter: String

    private val geneFeatureToSearch: GeneFeature by lazy {
        GeneFeature.parse(geneFeatureToSearchParameter)
    }

    @CommandLine.Option(names = ["-O"], description = ["Overrides default build SHM parameter values"])
    var overrides: Map<String, String> = HashMap()

    private val assembleFeatures: Array<GeneFeature> by lazy {
        geneFeatureToSearch
            .map { GeneFeature(it.begin, it.end) }
            .toTypedArray()
    }

    private val geneFeatureToMatch: VJPair<GeneFeature> by lazy {
        VJPair(
            V = GeneFeature.intersection(geneFeatureToSearch, GeneFeature(UTR5Begin, CDR3Begin)),
            J = GeneFeature.intersection(geneFeatureToSearch, GeneFeature(CDR3End, FR4End))
        )
    }

    private val tempDest: TempFileDest by lazy {
        if (!useSystemTemp) {
            Paths.get(outputClnsFiles.first()).toAbsolutePath().parent.toFile().mkdirs()
        }
        TempFileManager.smartTempDestination(outputClnsFiles.first(), "", useSystemTemp)
    }

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
    override fun run0() {
        ensureParametersInitialized()
        val libraryRegistry = VDJCLibraryRegistry.getDefault()
        val cloneReaders = inputFiles.map { CloneSetIO.mkReader(Paths.get(it), libraryRegistry) }
        require(cloneReaders.isNotEmpty()) { "there is no files to process" }
        require(cloneReaders.map { it.assemblerParameters }.distinct().count() == 1) {
            "input files must have the same assembler parameters"
        }
        require(cloneReaders.map { it.alignerParameters }.distinct().count() == 1) {
            "input files must have the same aligner parameters"
        }
        require(cloneReaders.all { it.info.allClonesCutBy != null }) {
            "Input files must not be processed by ${CommandAssembleContigs.ASSEMBLE_CONTIGS_COMMAND_NAME} without ${CommandAssembleContigs.CUT_BY_FEATURE_OPTION_NAME} option"
        }
        require(cloneReaders.map { it.info.allClonesCutBy }.distinct().count() == 1) {
            "Input files must not be cut by the same geneFeature"
        }

        val allelesBuilder = AllelesBuilder(
            findAllelesParameters,
            tempDest,
            cloneReaders,
            geneFeatureToMatch
        )

        val progressAndStage = ProgressAndStage("Grouping by the same V gene", 0.0)
        SmartProgressReporter.startProgressReport(progressAndStage)
        val VAlleles = allelesBuilder.searchForAlleles(Variable, progressAndStage, threads)
        val JAlleles = allelesBuilder.searchForAlleles(Joining, progressAndStage, threads)

        val alleles: MutableMap<String, List<VDJCGeneData>> = (VAlleles + JAlleles).toMap(HashMap())
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
        val cloneRebuild = CloneRebuild(
            resultLibrary,
            allelesMapping,
            assembleFeatures,
            geneFeatureToMatch,
            threads,
            cloneReaders.first().assemblerParameters,
            cloneReaders.first().alignerParameters
        )
        cloneReaders.forEachIndexed { i, cloneReader ->
            cloneReader.readClones().use { port ->
                port.withProgress(
                    cloneReader.numberOfClones().toLong(),
                    progressAndStage,
                    "Realigning ${inputFiles[i]}"
                ) { clones ->
                    val mapperClones = cloneRebuild.rebuildClones(clones)
                    mapperClones.forEach { clone ->
                        for (geneType in VJ_REFERENCE) {
                            allelesStatistics.increment(clone.getBestHit(geneType).gene.name)
                            allelesStatistics.increment(clone.getBestHit(geneType).gene.geneName)
                        }
                    }
                    File(outputClnsFiles[i]).writeMappedClones(mapperClones, resultLibrary, cloneReader)
                }
            }
        }
        progressAndStage.finish()
        if (allelesMutationsOutput != null) {
            Paths.get(allelesMutationsOutput!!).toAbsolutePath().parent.toFile().mkdirs()
            printAllelesMutationsOutput(resultLibrary, allelesStatistics)
        }
    }

    private fun <T> MutableMap<T, Int>.increment(key: T) {
        merge(key, 1) { old, _ -> old + 1 }
    }

    private fun printAllelesMutationsOutput(resultLibrary: VDJCLibrary, allelesStatistics: Map<String, Int>) {
        PrintStream(allelesMutationsOutput!!).use { output ->
            val columns = mapOf<String, (VDJCGene) -> Any?>(
                "alleleName" to { it.name },
                "geneName" to { it.geneName },
                "type" to { it.geneType },
                "regions" to { gene ->
                    gene.data.baseSequence.regions?.joinToString { it.toString() }
                },
                "alleleMutationsReliableRanges" to { gene ->
                    gene.data.meta["alleleMutationsReliableRanges"]
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
            val genes = resultLibrary.genes
                .filter { it.geneType in VJ_REFERENCE }
                .sortedWith(Comparator.comparing { it: VDJCGene -> it.geneType }
                    .thenComparing { it: VDJCGene -> it.name })
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
            cloneReader.info.copy(foundAlleles = resultLibrary),
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

    private fun anyClone(cloneReaders: List<CloneReader>): Clone {
        cloneReaders[0].readClones().use { port -> return port.take() }
    }

    companion object {
        const val FIND_ALLELES_COMMAND_NAME = "findAlleles"
    }
}

private class CloneRebuild(
    private val resultLibrary: VDJCLibrary,
    private val allelesMapping: Map<String, List<VDJCGeneId>>,
    private val assemblingFeatures: Array<GeneFeature>,
    /**
     * correspond with assemblingFeatures
     */
    private val geneFeatureToMatch: VJPair<GeneFeature>,
    private val threads: Int,
    assemblerParameters: CloneAssemblerParameters,
    alignerParameters: VDJCAlignerParameters
) {
    private val cloneFactory = CloneFactory(
        assemblerParameters.cloneFactoryParameters,
        assemblingFeatures,
        resultLibrary.genes,
        alignerParameters.featuresToAlignMap
    )

    //TODO move this logic to contig command
    /**
     * Every clone will be assembled by assemblingFeatures.
     * For every clone will be added hits aligned to found alleles.
     *
     * Cutting targets by assemblingFeatures may produce identical clones, their will be merged
     */
    fun rebuildClones(input: OutputPort<Clone>): List<Clone> {
        val remappedClones = input
            //map only clones that fully covered by geneFeatureToSearch
            .filter { clone ->
                //TODO remove other hits
                clone.getHits(Variable).any { hit -> hit.alignmentsCover(geneFeatureToMatch) } &&
                        clone.getHits(Joining).any { hit -> hit.alignmentsCover(geneFeatureToMatch) }
            }
            .mapInParallel(threads) { clone ->
                //remove targets and alignments that don't cover geneFeatureToSearch
                val cloneCutByGeneFeature = clone.removeTargetsNotCoveredBy(geneFeatureToMatch)

                //cut targets by assemblingFeatures
                val cutTargets = assemblingFeatures
                    .map { cloneCutByGeneFeature.getFeature(it) }
                    .toTypedArray()
                cloneFactory.create(
                    cloneCutByGeneFeature.id,
                    cloneCutByGeneFeature.count,
                    cloneCutByGeneFeature.scoresWithAddedAlleles(),
                    cloneCutByGeneFeature.tagCount,
                    cutTargets,
                    cloneCutByGeneFeature.group
                )
            }
            .toList()
        return remappedClones
            //TODO add V and J names of best hits
            //TODO use meta split by V, split by J
            .groupBy { clone -> clone.targets.map { it.sequence } to clone.getBestHit(Constant)?.gene?.id }
            .values
            .map { clones ->
                clones.reduce { a, b ->
                    //TODO choose base by max count
                    Clone(
                        a.targets,
                        a.hits,
                        TagCountAggregator.merge(a.tagCount, b.tagCount),
                        a.count + b.count,
                        min(a.id, b.id),
                        null
                    )
                }
            }
    }

    private fun Clone.scoresWithAddedAlleles(): EnumMap<GeneType, List<GeneAndScore>> {
        val originalGeneScores = EnumMap<GeneType, List<GeneAndScore>>(GeneType::class.java)
        //copy D and C
        for (gt in arrayOf(Diversity, Constant)) {
            originalGeneScores[gt] = getHits(gt).map { hit ->
                val mappedGeneId = VDJCGeneId(resultLibrary.libraryId, hit.gene.name)
                GeneAndScore(mappedGeneId, hit.score)
            }
        }
        //add hits with alleles and add delta score
        for (gt in VJ_REFERENCE) {
            originalGeneScores[gt] = getHits(gt).flatMap { hit ->
                allelesMapping[hit.gene.name]!!.map { foundAlleleId ->
                    if (foundAlleleId.name != hit.gene.name) {
                        //TODO use only one allele if they are too different (use relative min score from assemble parameters)
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
        return originalGeneScores
    }

    private fun Clone.removeTargetsNotCoveredBy(geneFeatureToMatch: VJPair<GeneFeature>): Clone {
        val resultHits: EnumMap<GeneType, Array<VDJCHit>?> = EnumMap(hits)
        resultHits[Variable] = hits[Variable]?.removeAlignmentsNotCoveredBy(geneFeatureToMatch)
        resultHits[Joining] = hits[Joining]?.removeAlignmentsNotCoveredBy(geneFeatureToMatch)

        val targetIndexesWithAlignments = resultHits.values
            .filterNotNull()
            .flatMap { it.toList() }
            .flatMap { hit ->
                (0 until hit.numberOfTargets())
                    .filter { i -> hit.getAlignment(i) != null }
            }
            .distinct()
            .sorted()

        resultHits[Variable] = resultHits[Variable]?.map { hit ->
            hit.filterAlignmentsByTargetIndexes(targetIndexesWithAlignments)
        }?.toTypedArray()
        resultHits[Joining] = resultHits[Joining]?.map { hit ->
            hit.filterAlignmentsByTargetIndexes(targetIndexesWithAlignments)
        }?.toTypedArray()
        return Clone(
            targetIndexesWithAlignments.map { getTarget(it) }.toTypedArray(),
            resultHits,
            tagCount,
            count,
            id,
            group
        )
    }

    private fun Array<out VDJCHit>.removeAlignmentsNotCoveredBy(geneFeatureToMatch: VJPair<GeneFeature>) =
        filter { it.alignmentsCover(geneFeatureToMatch) }
            .map { hit ->
                var result = hit
                val partitioning = hit.gene.partitioning.getRelativeReferencePoints(hit.alignedFeature)
                val rangesToMatch = partitioning.getRanges(geneFeatureToMatch[hit.geneType])

                for (i in 0 until hit.numberOfTargets()) {
                    val alignment: Alignment<NucleotideSequence>? = hit.getAlignment(i)
                    if (alignment != null && rangesToMatch.none { toMatch -> toMatch in alignment.sequence1Range }) {
                        result = hit.setAlignment(i, null)
                    }
                }
                result
            }
            .toTypedArray()

    private fun VDJCHit.filterAlignmentsByTargetIndexes(targetIndexesWithAlignments: List<Int>): VDJCHit {
        val filteredAlignments: Array<Alignment<NucleotideSequence>?> =
            Array(targetIndexesWithAlignments.size) { i ->
                getAlignment(targetIndexesWithAlignments[i])
            }
        return VDJCHit(gene, filteredAlignments, alignedFeature)
    }

    private fun scoreDelta(
        foundAllele: VDJCGene,
        scoring: AlignmentScoring<NucleotideSequence>,
        alignments: Array<Alignment<NucleotideSequence>?>
    ): Float {
        var scoreDelta = 0.0f
        //recalculate score for every alignment based on found allele
        for (alignment in alignments) {
            if (alignment == null) continue
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
}
