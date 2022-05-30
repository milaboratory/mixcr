/*
 * Copyright (c) 2014-2019, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
 * (here and after addressed as Inventors)
 * All Rights Reserved
 *
 * Permission to use, copy, modify and distribute any part of this program for
 * educational, research and non-profit purposes, by non-profit institutions
 * only, without fee, and without a written agreement is hereby granted,
 * provided that the above copyright notice, this paragraph and the following
 * three paragraphs appear in all copies.
 *
 * Those desiring to incorporate this work into commercial products or use for
 * commercial purposes should contact MiLaboratory LLC, which owns exclusive
 * rights for distribution of this program for commercial purposes, using the
 * following email address: licensing@milaboratory.com.
 *
 * IN NO EVENT SHALL THE INVENTORS BE LIABLE TO ANY PARTY FOR DIRECT, INDIRECT,
 * SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING LOST PROFITS,
 * ARISING OUT OF THE USE OF THIS SOFTWARE, EVEN IF THE INVENTORS HAS BEEN
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * THE SOFTWARE PROVIDED HEREIN IS ON AN "AS IS" BASIS, AND THE INVENTORS HAS
 * NO OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR
 * MODIFICATIONS. THE INVENTORS MAKES NO REPRESENTATIONS AND EXTENDS NO
 * WARRANTIES OF ANY KIND, EITHER IMPLIED OR EXPRESS, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY OR FITNESS FOR A
 * PARTICULAR PURPOSE, OR THAT THE USE OF THE SOFTWARE WILL NOT INFRINGE ANY
 * PATENT, TRADEMARK OR OTHER RIGHTS.
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
import com.milaboratory.mixcr.alleles.FindAllelesParametersPresets.getByName
import com.milaboratory.mixcr.assembler.CloneFactory
import com.milaboratory.mixcr.basictypes.ClnsWriter
import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.basictypes.CloneReader
import com.milaboratory.mixcr.basictypes.CloneSet
import com.milaboratory.mixcr.basictypes.CloneSetIO
import com.milaboratory.mixcr.trees.MutationsUtils.positionIfNucleotideWasDeleted
import com.milaboratory.mixcr.util.XSV.writeXSVBody
import com.milaboratory.mixcr.util.XSV.writeXSVHeaders
import com.milaboratory.util.GlobalObjectMappers
import gnu.trove.map.hash.TObjectFloatHashMap
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
import java.io.FileNotFoundException
import java.io.IOException
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
    public override fun getInputFiles(): List<String> {
        return inOut.subList(0, inOut.size - 1)
    }

    public override fun getOutputFiles(): List<String> = when {
        libraryOutput != null -> outputClnsFiles() + libraryOutput!!
        else -> outputClnsFiles()
    }

    @CommandLine.Option(description = ["Processing threads"], names = ["-t", "--threads"])
    var threads = Runtime.getRuntime().availableProcessors()
        set(value) {
            if (value <= 0) throwValidationException("-t / --threads must be positive")
            field = value
        }

    private fun outputClnsFiles(): List<String> {
        val template = inOut[inOut.size - 1]
        if (!template.endsWith(".clns")) {
            throwValidationException("Wrong template: command produces only clns $template")
        }
        val clnsFiles = inputFiles
            .map { Paths.get(it).toAbsolutePath() }
            .map { path: Path ->
                template
                    .replace("\\{file_name}".toRegex(), FilenameUtils.removeExtension(path.fileName.toString()))
                    .replace("\\{file_dir_path}".toRegex(), path.parent.toString())
            }
            .toList()
        if (clnsFiles.distinct().count() < clnsFiles.size) {
            throwValidationException("Output clns files are not uniq: $clnsFiles")
        }
        return clnsFiles
    }

    @CommandLine.Option(description = ["File to write library with found alleles."], names = ["--export-library"])
    var libraryOutput: String? = null

    @CommandLine.Option(description = ["File to description of each allele."], names = ["--export-alleles-mutations"])
    var allelesMutationsOutput: String? = null

    @CommandLine.Option(description = ["Find alleles parameters preset."], names = ["-p", "--preset"])
    var findAllelesParametersName = "default"
    private var findAllelesParameters: FindAllelesParameters? = null
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
        if (findAllelesParameters != null) return
        findAllelesParameters = getByName(findAllelesParametersName)
        if (findAllelesParameters == null) throwValidationException("Unknown parameters: $findAllelesParametersName")
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
        val allelesBuilder = AllelesBuilder(findAllelesParameters!!, cloneReaders)
        val sortedClonotypes = allelesBuilder.sortClonotypes()
        val alleles = (
            buildAlleles(allelesBuilder, sortedClonotypes, Variable) +
                buildAlleles(allelesBuilder, sortedClonotypes, Joining)
            ).toMap().toMutableMap()
        val usedGenes = collectUsedGenes(cloneReaders, alleles)
        registerNotProcessedVJ(alleles, usedGenes)
        val resultLibrary = buildLibrary(libraryRegistry, cloneReaders, usedGenes)
        if (libraryOutput != null) {
            val libraryOutputFile = File(libraryOutput!!)
            libraryOutputFile.parentFile.mkdirs()
            GlobalObjectMappers.getOneLine().writeValue(libraryOutputFile, resultLibrary.data)
        }
        if (allelesMutationsOutput != null) {
            File(allelesMutationsOutput!!).parentFile.mkdirs()
            printAllelesMutationsOutput(resultLibrary)
        }
        writeResultClnsFiles(cloneReaders, alleles, resultLibrary)
    }

    @Throws(FileNotFoundException::class)
    private fun printAllelesMutationsOutput(resultLibrary: VDJCLibrary) {
        PrintStream(allelesMutationsOutput!!).use { output ->
            val columns = mapOf<String, (VDJCGene) -> Any?>(
                "geneName" to { it.name },
                "type" to { it.geneType },
                "regions" to { gene ->
                    gene.data.baseSequence.regions?.joinToString { it.toString() }
                },
                "mutations" to { gene ->
                    gene.data.baseSequence.mutations?.encode() ?: ""
                }
            )
            writeXSVHeaders(output, columns.keys, ";")
            val genes = resultLibrary.genes
                .sortedWith(Comparator.comparing { it: VDJCGene -> it.geneType }
                    .thenComparing { it: VDJCGene -> it.name })
            writeXSVBody(output, genes, columns, ";")
        }
    }

    @Throws(IOException::class)
    private fun writeResultClnsFiles(
        cloneReaders: List<CloneReader>,
        alleles: Map<String, List<VDJCGeneData>>,
        resultLibrary: VDJCLibrary
    ) {
        val allelesMapping = alleles.mapValues { (_, value) ->
            value.map { resultLibrary[it.name].id }
        }
        for (i in cloneReaders.indices) {
            val cloneReader = cloneReaders[i]
            val outputFile = outputClnsFiles()[i]
            File(outputFile).parentFile.mkdirs()
            val mapperClones = rebuildClones(resultLibrary, allelesMapping, cloneReader)
            val cloneSet = CloneSet(
                mapperClones,
                resultLibrary.genes,
                cloneReader.alignerParameters,
                cloneReader.assemblerParameters,
                cloneReader.ordering()
            )
            ClnsWriter(outputFile).use { clnsWriter ->
                clnsWriter.writeCloneSet(
                    cloneReader.pipelineConfiguration,
                    cloneSet,
                    listOf(resultLibrary)
                )
            }
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
            { clone: Clone -> rebuildClone(resultLibrary, allelesMapping, cloneFactory, clone) },
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
        val originalGeneScores = EnumMap<GeneType, TObjectFloatHashMap<VDJCGeneId>>(
            GeneType::class.java
        )
        //copy D and C
        for (gt in listOf(Diversity, Constant)) {
            val scores = TObjectFloatHashMap<VDJCGeneId>()
            for (hit in clone.getHits(gt)) {
                val mappedGeneId = VDJCGeneId(resultLibrary.libraryId, hit.gene.name)
                scores.put(mappedGeneId, hit.score)
            }
            originalGeneScores[gt] = scores
        }
        for (gt in listOf(Variable, Joining)) {
            val scores = TObjectFloatHashMap<VDJCGeneId>()
            for (hit in clone.getHits(gt)) {
                for (foundAlleleId in allelesMapping[hit.gene.name]!!) {
                    if (foundAlleleId.name != hit.gene.name) {
                        val scoreDelta = scoreDelta(
                            resultLibrary[foundAlleleId.name],
                            cloneFactory.parameters.getVJCParameters(gt).scoring,
                            hit.alignments
                        )
                        scores.put(foundAlleleId, hit.score + scoreDelta)
                    } else {
                        scores.put(foundAlleleId, hit.score)
                    }
                }
            }
            originalGeneScores[gt] = scores
        }
        return cloneFactory.create(
            clone.id,
            clone.count,
            originalGeneScores,
            clone.tagCounter,
            clone.targets
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
        const val FIND_ALLELES_COMMAND_NAME = "find_alleles"
    }
}
