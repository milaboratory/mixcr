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
@file:Suppress("PrivatePropertyName", "LocalVariableName", "FunctionName")

package com.milaboratory.mixcr.alleles

import cc.redberry.pipe.OutputPort
import com.milaboratory.core.alignment.AlignmentScoring
import com.milaboratory.core.mutations.Mutation
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.mutations.Mutations.EMPTY_NUCLEOTIDE_MUTATIONS
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mitool.helpers.get
import com.milaboratory.mixcr.alleles.AlleleUtil.complimentaryGeneType
import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.basictypes.ClonesSupplier
import com.milaboratory.mixcr.basictypes.GeneFeatures
import com.milaboratory.mixcr.basictypes.HasFeatureToAlign
import com.milaboratory.mixcr.basictypes.VDJCHit
import com.milaboratory.mixcr.basictypes.tag.TagsInfo
import com.milaboratory.mixcr.trees.constructStateBuilder
import com.milaboratory.mixcr.util.VJPair
import com.milaboratory.mixcr.util.XSV
import com.milaboratory.mixcr.util.asMutations
import com.milaboratory.mixcr.util.asSequence
import com.milaboratory.primitivio.GroupingCriteria
import com.milaboratory.primitivio.asSequence
import com.milaboratory.primitivio.filter
import com.milaboratory.primitivio.flatMap
import com.milaboratory.primitivio.forEach
import com.milaboratory.primitivio.groupBy
import com.milaboratory.primitivio.mapInParallel
import com.milaboratory.primitivio.port
import com.milaboratory.primitivio.withProgress
import com.milaboratory.util.ProgressAndStage
import com.milaboratory.util.TempFileDest
import io.repseq.core.BaseSequence
import io.repseq.core.GeneFeature
import io.repseq.core.GeneFeature.CDR3
import io.repseq.core.GeneType
import io.repseq.core.GeneType.Joining
import io.repseq.core.GeneType.Variable
import io.repseq.core.ReferencePoint
import io.repseq.core.ReferencePoint.CDR3Begin
import io.repseq.core.ReferencePoint.CDR3End
import io.repseq.core.ReferencePoint.FR4End
import io.repseq.core.ReferencePoint.JBegin
import io.repseq.core.ReferencePoint.UTR5Begin
import io.repseq.core.ReferencePoint.VEnd
import io.repseq.core.ReferencePoints
import io.repseq.core.VDJCGene
import io.repseq.dto.VDJCGeneData
import java.io.PrintStream
import java.nio.file.Path
import java.util.*
import kotlin.collections.set
import kotlin.math.absoluteValue
import kotlin.math.ceil
import kotlin.math.floor

class AllelesBuilder(
    private val searchAlleleParameters: FindAllelesParameters.AlleleMutationsSearchParameters,
    private val searchMutationsInCDR3Parameters: FindAllelesParameters.SearchMutationsInCDR3Params?,
    private val clonesFilter: ClonesFilter,
    private val tempDest: TempFileDest,
    private val datasets: List<ClonesSupplier>,
    private val featureToAlign: HasFeatureToAlign,
    private val usedGenes: Collection<VDJCGene>,
    private val allClonesCutBy: GeneFeatures,
    private val debugDir: Path?,
    private val scoring: VJPair<AlignmentScoring<NucleotideSequence>>,
    private val CDR3Diversity: Int,
    private val geneDiversity: VJPair<Int>
) {
    fun searchForAlleles(
        geneType: GeneType,
        complementaryAlleles: Map<String, List<VDJCGeneData>>,
        progress: ProgressAndStage,
        reportBuilder: FindAllelesReport.Builder,
        threads: Int
    ): Map<String, List<VDJCGeneData>> {
        val totalClonesCount = datasets.sumOf { it.numberOfClones() }.toLong()
        val stateBuilder = featureToAlign.constructStateBuilder(usedGenes)

        //assumption: there are no allele genes in library
        //TODO how to check assumption?
        return datasets.filteredClones(clonesFilter) { filteredClones ->
            filteredClones.withProgress(
                totalClonesCount,
                progress,
                "Grouping by the same ${geneType.letter} gene"
            ) { clones ->
                clones.groupBy(
                    stateBuilder,
                    tempDest.addSuffix("alleles.searcher.${geneType.letterLowerCase}"),
                    GroupingCriteria.groupBy { it.getBestHit(geneType).gene }
                )
            }
                .withProgress(
                    totalClonesCount,
                    progress,
                    "Searching for ${geneType.letter} alleles",
                    countPerElement = { it.size.toLong() }
                ) { clustersWithTheSameV ->
                    clustersWithTheSameV.mapInParallel(threads) { cluster ->
                        val geneId = cluster[0].getBestHit(geneType).gene.name
                        geneId to findAlleles(
                            cluster,
                            complementaryAlleles,
                            geneType,
                            reportBuilder
                        ).sortedBy { it.name }
                    }
                        .asSequence()
                        .toMap()
                }
        }
    }

    private fun findAlleles(
        clusterByTheSameGene: List<Clone>,
        complementaryAlleles: Map<String, List<VDJCGeneData>>,
        geneType: GeneType,
        reportBuilder: FindAllelesReport.Builder
    ): List<VDJCGeneData> {
        require(clusterByTheSameGene.isNotEmpty())
        val bestHit = clusterByTheSameGene[0].getBestHit(geneType)

        val cloneDescriptors = clusterByTheSameGene.asSequence()
            .map { clone ->
                val alleles = complementaryAlleles[clone.getBestHit(complimentaryGeneType(geneType)).gene.name]
                val naiveByComplimentaryGeneMutations = when {
                    alleles.isNullOrEmpty() -> clone.getBestHit(complimentaryGeneType(geneType))
                        .alignments
                        .filterNotNull()
                        .all { it.absoluteMutations == EMPTY_NUCLEOTIDE_MUTATIONS }
                    else -> alleles.any {
                        CloneRebuild.alignmentsChange(clone, it, scoring[complimentaryGeneType(geneType)]).naive
                    }
                }
                CloneDescription(
                    clone,
                    clone.getBestHit(geneType).mutationsWithoutCDR3(),
                    naiveByComplimentaryGeneMutations,
                    clone.clusterIdentity(complimentaryGeneType(geneType))
                )
            }
            .toList()

        val sequence1 = clusterByTheSameGene.first().getBestHit(geneType).alignments.filterNotNull().first().sequence1
        val maxDiversity = CDR3Diversity * geneDiversity[complimentaryGeneType(geneType)]
        val allelesSearcher: AllelesSearcher = AllelesMutationsSearcher(
            reportBuilder,
            scoring[geneType],
            sequence1,
            searchAlleleParameters,
            AllelesMutationsSearcher.DiversityThresholds(
                ceil(searchAlleleParameters.diversityThresholds.minDiversityForMutation * maxDiversity).toInt(),
                ceil(searchAlleleParameters.diversityThresholds.minDiversityForAllele * maxDiversity).toInt(),
                ceil(searchAlleleParameters.diversityThresholds.diversityForSkipTestForRatioForZeroAllele * maxDiversity).toInt()
            )
        )

        val foundAlleles = allelesSearcher.search(bestHit.gene.id, cloneDescriptors)

        reportBuilder.foundAlleles(foundAlleles.count { it.allele != EMPTY_NUCLEOTIDE_MUTATIONS })
        reportBuilder.zygote(foundAlleles.size)

        val result = foundAlleles.map { foundAllele ->
            val naiveClones = clusterByTheSameGene.asSequence()
                .filter { clone -> clone.getBestHit(geneType).mutationsWithoutCDR3() == foundAllele.allele }
                .filter { clone -> clone.getBestHit(complimentaryGeneType(geneType)).mutationsWithoutCDR3().isEmpty }
                .toList()
            //TODO calculate mutationsInCDR3 as separate action to get more precise filter by clone.getBestHit(complimentaryGeneType(geneType)).mutations
            val mutationsInCDR3 = when {
                searchMutationsInCDR3Parameters != null -> mutationsInCDR3(
                    geneType,
                    sequence1,
                    naiveClones,
                    searchMutationsInCDR3Parameters,
                    geneDiversity[complimentaryGeneType(geneType)]
                )
                else -> MutationsInCDR3.empty
            }
            buildAllele(
                bestHit,
                foundAllele.allele,
                mutationsInCDR3.mutations,
                mutationsInCDR3.knownCDR3RangeLength
            )
        }
        if (debugDir != null) {
            val rows = cloneDescriptors
                .groupBy { it.mutationGroups }
                .entries
                .sortedBy { it.key.size }
            PrintStream(debugDir.resolve("${bestHit.gene.name}.tsv").toFile()).use { debugFile ->
                XSV.writeXSV(
                    debugFile,
                    rows,
                    buildMap {
                        this["diversity"] = { (_, clones) -> clones.map { it.clusterIdentity }.distinct().size }
                        this["count"] = { (_, clones) -> clones.size }
                        this["noMutationsInComplimentaryGene"] =
                            { (_, clones) -> clones.count { it.naiveByComplimentaryGeneMutations } }
                        this["mutations"] = { (mutations, _) -> mutations }
                        result.forEach { allele ->
                            this[allele.name] = { (_, clonesDescriptors) ->
                                clonesDescriptors
                                    .map { it.clone }
                                    .minOf {
                                        CloneRebuild
                                            .alignmentsChange(it, allele, scoring[geneType])
                                            .penalty
                                    }
                            }
                        }
                    },
                    "\t"
                )
            }
        }

        return result
    }

    private fun buildAllele(
        bestHit: VDJCHit,
        mutationsWithoutCDR3: Mutations<NucleotideSequence>,
        mutationsInCDR3: Mutations<NucleotideSequence>,
        knownCDR3RangeLength: Int
    ): VDJCGeneData {
        val gene = bestHit.gene
        val mutationsWithCDR3 = when (gene.geneType) {
            Variable -> mutationsWithoutCDR3.concat(mutationsInCDR3)
            Joining -> mutationsInCDR3.concat(mutationsWithoutCDR3)
            else -> throw IllegalArgumentException()
        }
        return when (mutationsWithCDR3) {
            EMPTY_NUCLEOTIDE_MUTATIONS -> gene.data
            else -> {
                val knownFeatures = calculateKnownFeatures(gene.geneType, knownCDR3RangeLength)
                val mappedReferencePoints = bestHit.gene.partitioning
                    .getRelativeReferencePoints(bestHit.alignedFeature)
                    .applyMutations(mutationsWithCDR3)
                VDJCGeneData(
                    BaseSequence(
                        gene.data.baseSequence.origin,
                        gene.partitioning.getRanges(bestHit.alignedFeature),
                        mutationsWithCDR3
                    ),
                    generateAlleleName(gene, mutationsWithoutCDR3, mutationsInCDR3, mutationsWithCDR3),
                    gene.data.geneType,
                    gene.data.isFunctional,
                    gene.data.chains,
                    metaForGeneratedGene(gene, knownFeatures),
                    recalculatedAnchorPoints(mappedReferencePoints)
                )
            }
        }
    }

    private fun generateAlleleName(
        gene: VDJCGene,
        mutationsWithoutCDR3: Mutations<NucleotideSequence>,
        mutationsInCDR3: Mutations<NucleotideSequence>,
        mutationsWithCDR3: Mutations<NucleotideSequence>
    ): String {
        val inCDR3 = when {
            mutationsInCDR3 != EMPTY_NUCLEOTIDE_MUTATIONS -> "-CDR3-" + mutationsInCDR3.size()
            else -> ""
        }
        return gene.name + "-M" + mutationsWithoutCDR3.size() + inCDR3 + "-" + mutationsWithCDR3.hashCode().absoluteValue
    }

    private fun Clone.clusterIdentity(geneType: GeneType) = CloneDescription.ClusterIdentity(
        ntLengthOf(CDR3),
        getBestHit(geneType).gene.geneName
    )

    private fun VDJCHit.mutationsWithoutCDR3(): Mutations<NucleotideSequence> {
        val referencePoints = gene.partitioning.getRelativeReferencePoints(alignedFeature)
        return alignments.asSequence()
            .filterNotNull()
            .flatMap { it.absoluteMutations.asSequence() }
            .filter { mutation ->
                val position = Mutation.getPosition(mutation)
                when (gene.geneType) {
                    Variable -> position < referencePoints.getPosition(CDR3Begin)
                    Joining -> position >= referencePoints.getPosition(CDR3End)
                    else -> throw UnsupportedOperationException()
                }
            }
            .asMutations(NucleotideSequence.ALPHABET)
    }

    /**
     * Test positions in CDR3 of naive clones.
     * Diversity of naive clones must be more than `parameters.minDiversityForAllele`
     * Register allele mutation if letter is the same in all naive clones in this position (stop on found difference)
     */
    private fun mutationsInCDR3(
        geneType: GeneType,
        sequence1: NucleotideSequence,
        naiveClones: List<Clone>,
        searchMutationsInCDR3Params: FindAllelesParameters.SearchMutationsInCDR3Params,
        diversityOfComlementaryGene: Int
    ): MutationsInCDR3 {
        if (naiveClones.size < searchMutationsInCDR3Params.minClonesCount) {
            return MutationsInCDR3.empty
        }
        val CDR3OfNaiveClones = naiveClones.map { it.getFeature(CDR3).sequence to it }.toMutableList()
        val minCDR3Size = CDR3OfNaiveClones.minOf { it.first.size() }
        // char by shift from CDR3 border.
        val foundLettersInCDR3 = mutableListOf<Byte>()
        for (i in 0 until minCDR3Size) {
            val lettersInPosition = CDR3OfNaiveClones
                .map { (CDR3, clone) ->
                    val position = if (geneType == Variable) i else CDR3.size() - 1 - i
                    CDR3.codeAt(position) to clone
                }
                .groupBy { it.first }
            val mostFrequent = lettersInPosition.maxByOrNull { it.value.size }!!.key
            val diversityOfMostFrequentLetter = lettersInPosition[mostFrequent]!!
                .map { it.second.getBestHit(complimentaryGeneType(geneType)).gene.id }
                .distinct().size
            if (diversityOfMostFrequentLetter < floor(diversityOfComlementaryGene * searchMutationsInCDR3Params.minDiversity)) break
            if (lettersInPosition.size != 1) {
                val countOfPretender = lettersInPosition[mostFrequent]!!.size
                if (countOfPretender < searchMutationsInCDR3Params.minClonesCount) break
                if (countOfPretender <= CDR3OfNaiveClones.size * searchMutationsInCDR3Params.minPartOfTheSameLetter) break
            }
            val clonesToExclude = (lettersInPosition - mostFrequent).values
                .flatMap { it.map { (_, clone) -> clone } }
                .toSet()
            CDR3OfNaiveClones.removeIf { (_, clone) -> clone in clonesToExclude }
            foundLettersInCDR3 += mostFrequent
        }
        if (foundLettersInCDR3.isEmpty()) return MutationsInCDR3.empty
        val bestHit = naiveClones.first().getBestHit(geneType)
        val partitioning = bestHit.gene.partitioning.getRelativeReferencePoints(bestHit.alignedFeature)
        val foundMutations = mutableListOf<Int>()
        var lastKnownShift = 0
        for ((i, letter) in foundLettersInCDR3.withIndex()) {
            val position = when (geneType) {
                Variable -> {
                    val CDR3BeginPosition = partitioning.getPosition(CDR3Begin)
                    val shiftedPosition = CDR3BeginPosition + i
                    if (partitioning.getPosition(VEnd) <= shiftedPosition) {
                        //TODO
                        break
                    }
                    shiftedPosition
                }
                Joining -> {
                    val CDR3EndPosition = partitioning.getPosition(CDR3End)
                    val shifterPosition = CDR3EndPosition - i - 1
                    if (shifterPosition < partitioning.getPosition(JBegin)) {
                        //TODO
                        break
                    }
                    shifterPosition
                }
                else -> throw UnsupportedOperationException()
            }
            lastKnownShift = i
            val letterInGermline = sequence1[position]
            if (letter != letterInGermline) {
                foundMutations += Mutation.createSubstitution(position, letterInGermline.toInt(), letter.toInt())
            }
        }
        val result = foundMutations
            .asSequence()
            .sortedBy { Mutation.getPosition(it) }
            .asMutations(NucleotideSequence.ALPHABET)
        return MutationsInCDR3(result, lastKnownShift)
    }

    private fun metaForGeneratedGene(
        gene: VDJCGene,
        knownFeatures: GeneFeatures
    ): SortedMap<String, SortedSet<String>> {
        val meta: SortedMap<String, SortedSet<String>> = TreeMap(gene.data.meta)
        meta[metaKeyForAlleleMutationsReliableGeneFeatures] =
            knownFeatures.features.map { GeneFeature.encode(it) }.toSortedSet()
        meta[metaKeyForAlleleMutationsReliableRanges] =
            knownFeatures.features
                .flatMap { gene.partitioning.getRanges(it).asSequence() }
                .map { it.toString() }
                .toSortedSet()
        meta["alleleVariantOf"] = sortedSetOf(gene.name)
        return meta
    }

    private fun calculateKnownFeatures(geneType: GeneType, knownCDR3RangeLength: Int) = when (geneType) {
        Variable -> {
            val toAdd = GeneFeature(CDR3Begin, 0, knownCDR3RangeLength)
            val intersection = allClonesCutBy.intersection(GeneFeature(UTR5Begin, CDR3Begin))
            if (intersection != null) {
                intersection + toAdd
            } else {
                GeneFeatures(toAdd)
            }
        }
        Joining -> {
            val toAdd = GeneFeatures(GeneFeature(CDR3End, -knownCDR3RangeLength, 0))
            val intersection = allClonesCutBy.intersection(GeneFeature(CDR3End, FR4End))
            if (intersection != null) {
                toAdd + intersection
            } else {
                toAdd
            }
        }
        else -> throw UnsupportedOperationException()
    }

    private fun recalculatedAnchorPoints(mappedReferencePoints: ReferencePoints): SortedMap<ReferencePoint, Long> =
        (0 until mappedReferencePoints.pointsCount()).asSequence()
            .map { index -> mappedReferencePoints.referencePointFromIndex(index) }
            .associateByTo(TreeMap(), { it }, { mappedReferencePoints.getPosition(it).toLong() })

    data class MutationsInCDR3(
        val mutations: Mutations<NucleotideSequence>,
        val knownCDR3RangeLength: Int
    ) {
        companion object {
            val empty = MutationsInCDR3(EMPTY_NUCLEOTIDE_MUTATIONS, 0)
        }

    }

    interface ClonesFilter {
        fun match(clone: Clone, tagsInfo: TagsInfo): Boolean
    }

    companion object {
        const val metaKeyForAlleleMutationsReliableGeneFeatures = "alleleMutationsReliableGeneFeatures"
        const val metaKeyForAlleleMutationsReliableRanges = "alleleMutationsReliableRanges"

        fun create(
            parameters: FindAllelesParameters,
            clonesFilter: ClonesFilter,
            tempDest: TempFileDest,
            datasets: List<ClonesSupplier>,
            scoring: VJPair<AlignmentScoring<NucleotideSequence>>,
            usedGenes: Collection<VDJCGene>,
            featureToAlign: HasFeatureToAlign,
            allClonesCutBy: GeneFeatures,
            debugDir: Path?
        ): AllelesBuilder {
            val CDR3Variants = mutableSetOf<Int>()
            val VVariants = mutableSetOf<String>()
            val JVariants = mutableSetOf<String>()
            datasets.filteredClones(clonesFilter) { clones ->
                clones.forEach { clone ->
                    CDR3Variants += clone.ntLengthOf(CDR3)
                    VVariants += clone.getBestHit(Variable).gene.name
                    JVariants += clone.getBestHit(Joining).gene.name
                }
            }
            return AllelesBuilder(
                parameters.searchAlleleParameter,
                parameters.searchMutationsInCDR3,
                clonesFilter,
                tempDest,
                datasets,
                featureToAlign,
                usedGenes,
                allClonesCutBy,
                debugDir,
                scoring,
                CDR3Variants.size,
                VJPair(
                    VVariants.size,
                    JVariants.size
                )
            )
        }

        private fun <R> List<ClonesSupplier>.filteredClones(
            filter: ClonesFilter,
            function: (OutputPort<Clone>) -> R
        ): R = port
            .flatMap { cloneReader ->
                cloneReader.readClones().filter { clone ->
                    filter.match(clone, cloneReader.tagsInfo)
                }
            }
            .use(function)
    }
}
