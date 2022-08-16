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
@file:Suppress("PrivatePropertyName", "LocalVariableName")

package com.milaboratory.mixcr.alleles

import cc.redberry.pipe.OutputPort
import com.milaboratory.core.Range
import com.milaboratory.core.alignment.AlignmentScoring
import com.milaboratory.core.mutations.Mutation
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mitool.helpers.get
import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.basictypes.CloneReader
import com.milaboratory.mixcr.basictypes.VDJCHit
import com.milaboratory.mixcr.trees.constructStateBuilder
import com.milaboratory.mixcr.util.VJPair
import com.milaboratory.mixcr.util.alignmentsCover
import com.milaboratory.mixcr.util.asMutations
import com.milaboratory.mixcr.util.asSequence
import com.milaboratory.primitivio.GroupingCriteria
import com.milaboratory.primitivio.filter
import com.milaboratory.primitivio.flatten
import com.milaboratory.primitivio.groupBy
import com.milaboratory.primitivio.mapInParallel
import com.milaboratory.primitivio.toList
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
import io.repseq.core.ReferencePoint.JBegin
import io.repseq.core.ReferencePoint.VEnd
import io.repseq.core.VDJCGene
import io.repseq.dto.VDJCGeneData
import java.util.*
import kotlin.collections.set

class AllelesBuilder(
    private val parameters: FindAllelesParameters,
    private val tempDest: TempFileDest,
    private val datasets: List<CloneReader>,
    private val geneFeatureToMatch: VJPair<GeneFeature>
) {
    private val scoring: VJPair<AlignmentScoring<NucleotideSequence>> = VJPair(
        V = datasets[0].assemblerParameters.cloneFactoryParameters.vParameters.scoring,
        J = datasets[0].assemblerParameters.cloneFactoryParameters.jParameters.scoring
    )

    fun searchForAlleles(
        geneType: GeneType,
        progress: ProgressAndStage,
        threads: Int
    ): List<Pair<String, List<VDJCGeneData>>> {
        val totalClonesCount = datasets.sumOf { it.numberOfClones() }.toLong()
        val stateBuilder = datasets.constructStateBuilder()

        //assumption: there are no allele genes in library
        //TODO how to check assumption?
        return filteredClones { filteredClones ->
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
                        geneId to findAlleles(cluster, geneType).toGeneData()
                    }
                        .toList()
                }
        }
    }


    private fun <R> filteredClones(function: (OutputPort<Clone>) -> R): R = datasets.map { it.readClones() }
        .flatten()
        .filter { c ->
            // filter non-productive clonotypes
            // todo CDR3?
            !parameters.productiveOnly || (!c.containsStopsOrAbsent(CDR3) && !c.isOutOfFrameOrAbsent(CDR3))
        }
        .filter { c ->
            c.count > parameters.useClonesWithCountMoreThen
        }
        .use(function)

    private fun findAlleles(clusterByTheSameGene: List<Clone>, geneType: GeneType): List<Allele> {
        require(clusterByTheSameGene.isNotEmpty())
        val bestHit = clusterByTheSameGene[0].getBestHit(geneType)
        //TODO remove after moving cut logic to contig (left filter of CDR3 mutations)
        val partitioning = bestHit.gene.partitioning.getRelativeReferencePoints(bestHit.alignedFeature)
        val rangesToMatch = partitioning.getRanges(geneFeatureToMatch[geneType])

        val cloneDescriptors = clusterByTheSameGene.asSequence()
            //TODO remove after moving cut logic to contig
            .filter { clone ->
                clone.getBestHit(geneType).alignmentsCover(rangesToMatch)
            }
            .map { clone ->
                CloneDescription(
                    clone.getBestHit(geneType).mutationsWithoutCDR3(rangesToMatch),
                    clone.clusterIdentity(geneType)
                )
            }
            .toList()
        val sequence1 = clusterByTheSameGene.first().getBestHit(geneType).alignments.filterNotNull().first().sequence1
        val allelesSearcher: AllelesSearcher = TIgGERAllelesSearcher(
            scoring[geneType],
            sequence1,
            parameters,
            rangesToMatch
        )

        val foundAlleles = allelesSearcher.search(cloneDescriptors)

        val withMutationsInCDR3 = foundAlleles.map { foundAllele ->
            foundAllele.withMutationsInCDR3(clusterByTheSameGene, rangesToMatch, geneType, sequence1)
        }

        return withMutationsInCDR3.map {
            Allele(
                bestHit.gene,
                it.allele,
                bestHit.alignedFeature,
                rangesToMatch
            )
        }
    }

    private fun Clone.clusterIdentity(geneType: GeneType) = CloneDescription.ClusterIdentity(
        ntLengthOf(CDR3),
        getBestHit(complimentaryGeneType(geneType)).gene.geneName
    )

    private fun VDJCHit.mutationsWithoutCDR3(rangesToMatch: Array<out Range>) =
        alignments.asSequence()
            .filterNotNull()
            .flatMap { it.absoluteMutations.asSequence() }
            .filter { mutation ->
                val position = Mutation.getPosition(mutation)
                rangesToMatch.any { toMatch -> position in toMatch }
            }
            .asMutations(NucleotideSequence.ALPHABET)

    /**
     * Test positions in CDR3 of naive clones.
     * Diversity of naive clones must be more than `parameters.minDiversityForAllele`
     * Register allele mutation if letter is the same in all naive clones in this position (stop on found difference)
     */
    private fun AllelesSearcher.Result.withMutationsInCDR3(
        clones: List<Clone>,
        rangesToMatch: Array<Range>,
        geneType: GeneType,
        sequence1: NucleotideSequence
    ): AllelesSearcher.Result {
        val naiveClones = clones.asSequence()
            //TODO remove after moving cut logic to contig
            .filter { clone ->
                clone.getBestHit(geneType).alignmentsCover(rangesToMatch)
            }
            .filter { clone ->
                clone.getBestHit(geneType).mutationsWithoutCDR3(rangesToMatch) == allele
            }
            .toList()
        if (naiveClones.size < parameters.searchMutationsInCDR3.minClonesCount) {
            return this
        }
        val CDR3OfNaiveClones = naiveClones.map { it.getFeature(CDR3).sequence to it }.toMutableList()
        val minCDR3Size = CDR3OfNaiveClones.minOf { it.first.size() }
        // char by shift from CDR3 border.
        val foundLettersInCDR3 = mutableListOf<Pair<Int, Byte>>()
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
            if (diversityOfMostFrequentLetter < parameters.searchMutationsInCDR3.minDiversity) break
            if (lettersInPosition.size != 1) {
                val countOfPretender = lettersInPosition[mostFrequent]!!.size
                if (countOfPretender < parameters.searchMutationsInCDR3.minClonesCount) break
                if (countOfPretender <= CDR3OfNaiveClones.size * parameters.searchMutationsInCDR3.minPartOfTheSameLetter) break
            }
            val clonesToExclude = (lettersInPosition - mostFrequent).values
                .flatMap { it.map { (_, clone) -> clone } }
                .toSet()
            CDR3OfNaiveClones.removeIf { (_, clone) -> clone in clonesToExclude }
            foundLettersInCDR3 += i to mostFrequent
        }
        if (foundLettersInCDR3.isEmpty()) return this
        val bestHit = clones.first().getBestHit(geneType)
        val partitioning = bestHit.gene.partitioning.getRelativeReferencePoints(bestHit.alignedFeature)
        val foundMutations = mutableListOf<Int>()
        var lastKnownShift = 0
        for ((i, letter) in foundLettersInCDR3) {
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
                else -> {
                    val CDR3EndPosition = partitioning.getPosition(CDR3End)
                    val shifterPosition = CDR3EndPosition - i - 1
                    if (shifterPosition < partitioning.getPosition(JBegin)) {
                        //TODO
                        break
                    }
                    shifterPosition
                }
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
        val resultMutations = when (geneType) {
            Variable -> allele.concat(result)
            else -> result.concat(allele)
        }
        val resultKnownRanges = when (geneType) {
            Variable -> when (knownRanges.last().upper) {
                partitioning.getPosition(CDR3Begin) -> knownRanges.clone().also { copy ->
                    copy[knownRanges.size - 1] = knownRanges.last()
                        .setUpper(partitioning.getPosition(CDR3Begin) + lastKnownShift)
                }
                else -> knownRanges + Range(
                    partitioning.getPosition(CDR3Begin),
                    partitioning.getPosition(CDR3Begin) + lastKnownShift
                )
            }
            else -> when (knownRanges.first().lower) {
                partitioning.getPosition(CDR3End) -> knownRanges.clone().also { copy ->
                    copy[0] = knownRanges.first()
                        .setLower(partitioning.getPosition(CDR3End) - lastKnownShift)
                }
                else -> knownRanges + Range(
                    partitioning.getPosition(CDR3End) - lastKnownShift,
                    partitioning.getPosition(CDR3End)
                )
            }
        }
        return AllelesSearcher.Result(
            resultMutations,
            resultKnownRanges
        )
    }

    private fun complimentaryGeneType(geneType: GeneType): GeneType = when (geneType) {
        Variable -> Joining
        Joining -> Variable
        else -> throw IllegalArgumentException()
    }

    private fun List<Allele>.toGeneData() =
        map { allele ->
            when {
                allele.mutations != Mutations.EMPTY_NUCLEOTIDE_MUTATIONS -> buildGene(allele)
                else -> allele.gene.data
            }
        }
            .sortedBy { it.name }

    private fun buildGene(allele: Allele): VDJCGeneData = VDJCGeneData(
        BaseSequence(
            allele.gene.data.baseSequence.origin,
            allele.gene.partitioning.getRanges(allele.alignedFeature),
            allele.mutations
        ),
        generateGeneName(allele),
        allele.gene.data.geneType,
        allele.gene.data.isFunctional,
        allele.gene.data.chains,
        metaForGeneratedGene(allele),
        recalculatedAnchorPoints(allele)
    )

    private fun generateGeneName(allele: Allele): String =
        allele.gene.name + "-M" + allele.mutations.size() + "-" + allele.mutations.hashCode()

    private fun metaForGeneratedGene(allele: Allele): SortedMap<String, SortedSet<String>> {
        val meta: SortedMap<String, SortedSet<String>> = TreeMap(allele.gene.data.meta)
        meta["alleleMutationsReliableRanges"] = allele.knownRanges
            .map { it.toString() }
            .toSortedSet()
        meta["alleleVariantOf"] = sortedSetOf(allele.gene.name)
        return meta
    }

    private fun recalculatedAnchorPoints(allele: Allele): TreeMap<ReferencePoint, Long> {
        val mappedReferencePoints = allele.gene.partitioning
            .getRelativeReferencePoints(allele.alignedFeature)
            .applyMutations(allele.mutations)
        return (0 until mappedReferencePoints.pointsCount()).asSequence()
            .map { index -> mappedReferencePoints.referencePointFromIndex(index) }
            .associateByTo(TreeMap(), { it }, { mappedReferencePoints.getPosition(it).toLong() })
    }

    private class Allele(
        val gene: VDJCGene,
        val mutations: Mutations<NucleotideSequence>,
        val alignedFeature: GeneFeature,
        val knownRanges: Array<Range>
    ) {
        override fun toString(): String = "Allele{" +
                "id=" + gene.name +
                ", mutations=" + mutations +
                '}'
    }
}
