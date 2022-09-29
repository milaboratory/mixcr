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
package com.milaboratory.mixcr.alleles

import com.milaboratory.core.alignment.Aligner
import com.milaboratory.core.alignment.AlignmentScoring
import com.milaboratory.core.alignment.AlignmentUtils
import com.milaboratory.core.mutations.Mutation
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.mutations.Mutations.EMPTY_NUCLEOTIDE_MUTATIONS
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.alleles.BCellsAllelesSearcher.Allele.Companion.ZERO_ALLELE
import com.milaboratory.mixcr.alleles.CloneDescription.MutationGroup
import com.milaboratory.mixcr.alleles.FindAllelesParameters.BCellsAlleleSearchParameters.RegressionFilter
import com.milaboratory.mixcr.util.asMutations
import com.milaboratory.mixcr.util.asSequence
import io.repseq.core.VDJCGeneId
import org.apache.commons.math3.stat.inference.TTest
import org.apache.commons.math3.stat.regression.SimpleRegression
import kotlin.math.absoluteValue
import kotlin.math.floor


class BCellsAllelesSearcher(
    private val reportBuilder: FindAllelesReport.Builder,
    private val scoring: AlignmentScoring<NucleotideSequence>,
    private val sequence1: NucleotideSequence,
    private val parameters: FindAllelesParameters.BCellsAlleleSearchParameters,
    private val diversityThresholds: FindAllelesParameters.BCellsAlleleSearchParameters.DiversityThresholds
) : AllelesSearcher {
    private val maxScore = scoring.maximalMatchScore * sequence1.size()

    override fun search(geneId: VDJCGeneId, clones: List<CloneDescription>): List<AllelesSearcher.Result> {
        val searchHistory = reportBuilder.historyForBCells(
            geneId.name,
            clones.size,
            clones.diversity(),
            diversityThresholds.minDiversityForAllele
        )
        val allMutations = clones.flatMap { it.mutationGroups }.distinct()
        searchHistory.differentMutationsCount = allMutations.size

        val mutationsFilteredByDiversity = allMutations
            //other mutations definitely are not allele mutations
            .filter { mutation ->
                clones
                    .filter { mutation in it.mutationGroups }
                    .diversity() > diversityThresholds.minDiversityForMutation
            }
        searchHistory.mutationsWithEnoughDiversityCount = mutationsFilteredByDiversity.size

        val possibleAlleleMutations = filterMutationsCandidatesByRegression(clones, mutationsFilteredByDiversity)
        val foundAlleles = chooseAndGroupMutationsByAlleles(possibleAlleleMutations.toSet(), clones)

        val withZeroAllele = addZeroAlleleIfNeeded(foundAlleles, clones, searchHistory)
        val enriched = enrichAllelesWithMutationsThatExistsInAlmostAllClones(withZeroAllele, clones)
        enriched
            .map { it.allele }
            .filter { it.enrichedMutations.isNotEmpty() }
            .forEach {
                searchHistory.alleles.enrichedMutations += it.mutationGroups to it.enrichedMutations.asMutations()
            }

        var (withEnoughNaives, filteredOutAlleleCandidates) = enriched
            .partition { allele -> allele.naives >= parameters.minCountOfNaiveClonesToAddAllele }
        filteredOutAlleleCandidates
            .filter { it.allele.enrichedMutations.isNotEmpty() }
            .forEach { filteredOut ->
                val withoutEnrichedMutations = filteredOut.copy(
                    allele = Allele(
                        filteredOut.allele.mutationGroups - filteredOut.allele.enrichedMutations.toSet()
                    )
                )
                if (withoutEnrichedMutations.naives >= parameters.minRelativePenaltyBetweenAllelesForCloneAlign) {
                    withEnoughNaives += withoutEnrichedMutations
                    filteredOutAlleleCandidates -= filteredOut
                    searchHistory.alleles.discardedEnrichedMutations += filteredOut.allele.mutationGroups
                }
            }

        searchHistory.alleles.filteredOutByNaiveCount = filteredOutAlleleCandidates.map { candidate ->
            FindAllelesReport.AlleleCandidate(
                candidate.allele.mutations.encode(","),
                candidate.clones.size,
                candidate.clonesWithPenalties
                    .count { it.relativePenalty >= parameters.minRelativePenaltyBetweenAllelesForCloneAlign },
                candidate.naives
            )
        }
        searchHistory.alleles.result = withEnoughNaives.map { it.allele.mutationGroups }
        return when {
            withEnoughNaives.isNotEmpty() -> withEnoughNaives.map { it.allele }
            else -> listOf(ZERO_ALLELE)
        }.map { AllelesSearcher.Result(it.mutations) }
    }

    private fun filterMutationsCandidatesByRegression(
        clones: List<CloneDescription>,
        candidates: List<MutationGroup>
    ): List<MutationGroup> = when (parameters.regressionFilter) {
        is RegressionFilter.NoOP -> candidates
        is RegressionFilter.Filter -> {
            val pointSupplier: List<CloneDescription>.() -> Map<Int, Int> = when (parameters.regressionFilter) {
                is RegressionFilter.ByCount -> {
                    {
                        groupingBy { it.mutationGroups.size }.eachCount()
                    }
                }
                is RegressionFilter.ByDiversity -> {
                    {
                        groupBy { it.mutationGroups.size }
                            .mapValues { it.value.diversity() }
                    }
                }
            }

            val byMutationsCount = clones.pointSupplier()

            candidates.filter { mutation ->
                isItCandidateToAlleleMutation(
                    byMutationsCount,
                    clones
                        .filter { clone -> mutation in clone.mutationGroups }
                        .pointSupplier(),
                    parameters.regressionFilter
                )
            }
        }
    }

    /**
     * Find and add mutations that exist in almost all clones of an allele
     */
    private fun enrichAllelesWithMutationsThatExistsInAlmostAllClones(
        alleles: Collection<Allele>,
        clones: List<CloneDescription>
    ): List<AlleleWithClones> = alignClonesOnAlleles(clones, alleles)
        .map { alleleWithClones ->
            val mutationsToAdd = alleleWithClones.mutationsThatExistsInAlmostAllClones()
            val result = MutationGroup.groupMutationsByPositions(
                alleleWithClones.allele.mutations.combineWith(mutationsToAdd)
            ).toList()
            alleleWithClones.copy(
                allele = Allele(
                    mutationGroups = result,
                    enrichedMutations = MutationGroup.groupMutationsByPositions(mutationsToAdd).toList()
                )
            )
        }

    private fun AlleleWithClones.mutationsThatExistsInAlmostAllClones(): Mutations<NucleotideSequence> {
        val diversityOfAll = clones.diversity()
        if (diversityOfAll < diversityThresholds.minDiversityForAllele) return EMPTY_NUCLEOTIDE_MUTATIONS
        val boundary = floor(diversityOfAll * parameters.diversityRatioToSearchCommonMutationsInAnAllele).toInt()
        val diversityOfMutations = clones
            .flatMap { clone ->
                val alleleHasIndels = allele.mutations.asSequence().any { Mutation.isInDel(it) }
                val mutationsFromAllele = when {
                    allele == ZERO_ALLELE -> clone.mutations
                    alleleHasIndels -> Aligner.alignGlobal(
                        scoring,
                        allele.mutations.mutate(sequence1),
                        clone.mutations.mutate(sequence1)
                    ).absoluteMutations
                    else -> allele.mutations.invert().combineWith(clone.mutations)
                }
                MutationGroup.groupMutationsByPositions(mutationsFromAllele).map { it to clone }
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.diversity() }
        return diversityOfMutations
            .filterValues { it >= boundary }
            .keys.asSequence()
            .flatMap { it.mutations }
            .sortedBy { Mutation.getPosition(it) }
            .asMutations(NucleotideSequence.ALPHABET)
    }

    /**
     * If there are no zero allele, test if it can be added.
     * If allele was added, test all alleles by diversity ratio.
     */
    private fun addZeroAlleleIfNeeded(
        foundAlleles: Collection<Allele>,
        clones: List<CloneDescription>,
        searchHistory: FindAllelesReport.Builder.SearchHistoryForBCellsBuilder
    ): Collection<Allele> = when {
        foundAlleles.isEmpty() -> listOf(ZERO_ALLELE)
        foundAlleles.any { it == ZERO_ALLELE } -> foundAlleles
        //check if there are enough clones that more close to zero allele
        else -> {
            val clonesByAlleles = alignClonesOnAlleles(clones, foundAlleles + ZERO_ALLELE)
            val alleleDiversities = clonesByAlleles
                .associate { alleleWithClones ->
                    val filteredClones = alleleWithClones.clonesWithPenalties
                        .filter {
                            it.relativePenalty >= parameters.minRelativePenaltyBetweenAllelesForCloneAlign
                        }
                        .map { it.clone }
                    alleleWithClones.allele to filteredClones.diversity()
                }
            val diversityOfZeroAllele = alleleDiversities[ZERO_ALLELE] ?: 0
            when {
                // Zero allele is not represented enough
                diversityOfZeroAllele < diversityThresholds.minDiversityForAllele -> foundAlleles
                else -> {
                    searchHistory.alleles.addedKnownAllele = ZERO_ALLELE.mutationGroups
                    val boundary = alleleDiversities.values.maxOrNull()!! * parameters.minDiversityRatioBetweenAlleles
                    alleleDiversities
                        .filter { it.value >= boundary || it.value >= diversityThresholds.diversityForSkipTestForRatioForZeroAllele }
                        .keys
                }
            }
        }
    }

    private fun List<CloneDescription>.diversity() = map { it.clusterIdentity }.distinct().size

    private fun alignClonesOnAlleles(
        clones: List<CloneDescription>,
        alleles: Collection<Allele>
    ): List<AlleleWithClones> = clones
        .map { clone ->
            val allelesWithScore = alleles
                .map { allele ->
                    val alleleHasIndels = allele.mutations.asSequence().any { Mutation.isInDel(it) }
                    val score = if (alleleHasIndels) {
                        Aligner.alignGlobal(
                            scoring,
                            allele.mutations.mutate(sequence1),
                            clone.mutations.mutate(sequence1)
                        ).score.toDouble()
                    } else {
                        AlignmentUtils.calculateScore(
                            sequence1,
                            allele.mutations.invert().combineWith(clone.mutations),
                            scoring
                        ).toDouble()
                    }
                    allele to maxScore - score
                }
                .sortedWith(
                    Comparator.comparingDouble { (_, score): Pair<Allele, Double> -> score }
                        //prefer to align on not zero allele
                        .then(Comparator.comparingInt { (allele, _) -> if (allele == ZERO_ALLELE) 1 else 0 })
                )
            val (alignedOn, penalty) = allelesWithScore.first()
            alignedOn to AlleleWithClones.CloneWithPenalties(
                clone,
                penalty,
                allelesWithScore.getOrNull(1)?.second
            )
        }
        .groupBy({ it.first }, { it.second })
        .map { AlleleWithClones(it.key, it.value) }

    /**
     * Build regression for data:
     * x = mutations count in a sequence
     * y = (count of clones with `mutation` in mutations and mutations.size() == x) / (count of clones mutations.size() == x)
     * i.e. y - frequency of `mutation` in clones that have x mutations
     *
     * Regression starts from x point with max count of clones that contains `mutation`.
     * If there are sufficient data and regression is statistical significant, compare y-intersect with parameter.
     *
     * For allele mutations this regression is horizontal or points downwards. For hotspots it points upwards.
     * That's because in SHM process there are more and more different mutations in more mutated sequences, but
     * allele mutations remains more or less constant (apart from point with allele clones without mutations)
     */
    private fun isItCandidateToAlleleMutation(
        byMutationsCount: Map<Int, Int>,
        byMutationsCountWithTheMutation: Map<Int, Int>,
        filter: RegressionFilter.Filter
    ): Boolean {
        val mutationCountWithMaxClonesCount = byMutationsCountWithTheMutation.entries
            .maxByOrNull { it.value }!!
            .key

        val regression = SimpleRegression()

        val xPoints = (0 until filter.windowSizeForRegression)
            .map { i -> i + mutationCountWithMaxClonesCount }
            .filter { x ->
                val pointValue = byMutationsCountWithTheMutation[x]
                pointValue != null && pointValue != 0
            }
        if (filter.windowSizeForRegression - xPoints.size > filter.allowedSkippedPointsInRegression) {
            return false
        }

        val y = DoubleArray(xPoints.size)
        xPoints.forEachIndexed { i, x ->
            val result = (byMutationsCountWithTheMutation[x] ?: 0) /
                    (byMutationsCount[x]?.toDouble() ?: 1.0)
            regression.addData(x.toDouble(), result)
            y[i] = result
        }

        val regressionResults = regression.regress()

        val a = regressionResults.getParameterEstimate(0)
        val b = regressionResults.getParameterEstimate(1)

        val estimate = DoubleArray(xPoints.size)
        xPoints.forEachIndexed { i, x ->
            estimate[i] = a + b * x
        }

        val pValue = TTest().tTest(estimate, y)

        val intercept = a + b * (xPoints[0] - 1)
        val isHomozygousAlleleMutation =
            intercept > filter.minYInterceptForHomozygous && b.absoluteValue < filter.maxSlopeForHomozygous
        val isHeterozygousAlleleMutation =
            pValue >= filter.minPValue && intercept >= filter.minYInterceptForHeterozygous
        return isHomozygousAlleleMutation || isHeterozygousAlleleMutation
    }

    private fun chooseAndGroupMutationsByAlleles(
        mutations: Set<MutationGroup>,
        clones: List<CloneDescription>
    ): Collection<Allele> {
        //count diversity of coexisted variants of candidates and filter by minDiversityForAllele
        val mutationSubsetsWithDiversity = clones.map { clone ->
            //subsetOfAlleleMutations may be empty (zero allele)
            val subsetOfAlleleMutations = clone.mutationGroups.filter { it in mutations }
            Allele(subsetOfAlleleMutations) to clone
        }
            .groupBy({ it.first }) { it.second }
            .filterValues { it.count { clone -> clone.naiveByComplimentaryGeneMutations } >= parameters.minCountOfNaiveClonesToAddAllele }
            .mapValues { it.value.diversity() }
        val filteredMutationSubsets = mutationSubsetsWithDiversity
            .filterValues { it >= diversityThresholds.minDiversityForAllele }
            .keys
        if (filteredMutationSubsets.isEmpty()) return emptyList()

        val alignedClones = alignClonesOnAlleles(clones, filteredMutationSubsets)

        val alleleDiversities = alignedClones.associate { alleleWithClones ->
            val filteredClones = alleleWithClones.clonesWithPenalties
                .filter {
                    it.relativePenalty >= parameters.minRelativePenaltyBetweenAllelesForCloneAlign
                }
                .map { it.clone }
            alleleWithClones.allele to filteredClones.diversity()
        }

        //filter candidates with too low diversity in comparison to others
        val boundary = alleleDiversities.values.maxOrNull()!! * parameters.minDiversityRatioBetweenAlleles
        return alleleDiversities
            .filter { it.value >= boundary }
            .keys
    }

    private data class AlleleWithClones(
        val allele: Allele,
        val clonesWithPenalties: List<CloneWithPenalties>
    ) {
        val clones get() = clonesWithPenalties.map { it.clone }
        val naives: Int
            get() = clones.count { it.mutations == allele.mutations && it.naiveByComplimentaryGeneMutations }

        data class CloneWithPenalties(
            val clone: CloneDescription,
            private val penaltyForBestAllele: Double,
            private val penaltyForNextAllele: Double?
        ) {
            val relativePenalty: Double
                get() = when {
                    penaltyForNextAllele != null -> (penaltyForNextAllele - penaltyForBestAllele) / penaltyForNextAllele
                    else -> 1.0
                }
        }
    }

    class Allele(
        val mutationGroups: List<MutationGroup>,
        val enrichedMutations: List<MutationGroup> = emptyList()
    ) {
        val mutations: Mutations<NucleotideSequence> = mutationGroups.asMutations()

        companion object {
            val ZERO_ALLELE = Allele(emptyList())
        }


        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Allele

            if (mutationGroups != other.mutationGroups) return false

            return true
        }

        override fun hashCode(): Int {
            return mutationGroups.hashCode()
        }

        override fun toString(): String {
            return mutationGroups.toString()
        }
    }
}
