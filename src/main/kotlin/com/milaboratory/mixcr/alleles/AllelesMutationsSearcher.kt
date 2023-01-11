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
package com.milaboratory.mixcr.alleles

import com.milaboratory.core.alignment.Aligner
import com.milaboratory.core.alignment.AlignmentScoring
import com.milaboratory.core.alignment.AlignmentUtils
import com.milaboratory.core.mutations.Mutation
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.mutations.Mutations.EMPTY_NUCLEOTIDE_MUTATIONS
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.alleles.AllelesMutationsSearcher.Allele.Companion.ZERO_ALLELE
import com.milaboratory.mixcr.alleles.CloneDescription.MutationGroup
import com.milaboratory.mixcr.util.asMutations
import com.milaboratory.mixcr.util.asSequence
import io.repseq.core.VDJCGeneId
import kotlin.math.ceil
import kotlin.math.floor


class AllelesMutationsSearcher(
    private val reportBuilder: FindAllelesReport.Builder,
    private val scoring: AlignmentScoring<NucleotideSequence>,
    private val sequence1: NucleotideSequence,
    private val parameters: FindAllelesParameters.AlleleMutationsSearchParameters,
    maxPossibleDiversity: Int
) : AllelesSearcher {
    private val maxScore = scoring.maximalMatchScore * sequence1.size()

    private val minDiversityForAllele =
        ceil(parameters.diversityThresholds.minDiversityForAllele * maxPossibleDiversity).toInt()
    private val minDiversityForMutation =
        ceil(parameters.diversityThresholds.minDiversityForMutation * maxPossibleDiversity)
    private val diversityForSkipTestForRatioForZeroAllele =
        ceil(parameters.diversityThresholds.diversityForSkipTestForRatioForZeroAllele * maxPossibleDiversity)

    override fun search(geneId: VDJCGeneId, clones: List<CloneDescription>): List<AllelesSearcher.Result> {
        val searchHistory = reportBuilder.historyForBCells(
            geneId.name,
            clones.size,
            clones.diversity(),
            minDiversityForAllele
        )
        val allMutations = clones.flatMap { it.mutationGroups }.distinct()
        searchHistory.differentMutationsCount = allMutations.size

        val mutationsFilteredByDiversity = allMutations
            // other mutations definitely are not allele mutations
            .filter { mutation ->
                clones
                    .filter { mutation in it.mutationGroups }
                    .diversity() > minDiversityForMutation
            }
        searchHistory.mutationsWithEnoughDiversityCount = mutationsFilteredByDiversity.size

        val foundAlleles = chooseAndGroupMutationsByAlleles(mutationsFilteredByDiversity.toSet(), clones)

        val withZeroAllele = addZeroAlleleIfNeeded(foundAlleles, clones, searchHistory)
        // TODO try remove (or may be only for zero allele)
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
        if (diversityOfAll < minDiversityForAllele) return EMPTY_NUCLEOTIDE_MUTATIONS
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
        // check if there are enough clones that more close to zero allele
        else -> {
            val clonesByAlleles = alignClonesOnAlleles(clones, foundAlleles + ZERO_ALLELE)
            val alleleDiversitiesAndScores = clonesByAlleles
                .associate { alleleWithClones ->
                    val filteredClones = alleleWithClones.clonesWithPenalties
                        .filter {
                            it.relativePenalty >= parameters.minRelativePenaltyBetweenAllelesForCloneAlign
                        }
                        .map { it.clone }
                    alleleWithClones.allele to DiversityAndScore(
                        filteredClones.diversity(),
                        filteredClones.score()
                    )
                }
            val diversityOfZeroAllele = alleleDiversitiesAndScores[ZERO_ALLELE]?.diversity ?: 0
            when {
                // TODO try remove
                // Zero allele is not represented enough
                diversityOfZeroAllele < minDiversityForAllele -> foundAlleles
                else -> {
                    val boundary =
                        alleleDiversitiesAndScores.values.maxOf { it.score } * (1.0 - parameters.topByScore)
                    val result = alleleDiversitiesAndScores
                        .filter { it.value.score >= boundary || it.value.diversity >= diversityForSkipTestForRatioForZeroAllele }
                        .keys
                    if (ZERO_ALLELE in result) {
                        searchHistory.alleles.addedKnownAllele = ZERO_ALLELE.mutationGroups
                    }
                    result
                }
            }
        }
    }

    private fun List<CloneDescription>.diversity() = map { it.clusterIdentity }.distinct().size

    private fun List<CloneDescription>.score(): Double =
        diversity() + parameters.coefficientForNaiveClonesInScore * filter { it.naiveByComplimentaryGeneMutations }.diversity()

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
                        // prefer to align on not zero allele
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

    private fun chooseAndGroupMutationsByAlleles(
        mutations: Set<MutationGroup>,
        clones: List<CloneDescription>
    ): Collection<Allele> {
        val mutationSubsetsWithClones = clones.map { clone ->
            // subsetOfAlleleMutations may be empty (zero allele)
            val subsetOfAlleleMutations = clone.mutationGroups.filter { it in mutations }
            Allele(subsetOfAlleleMutations) to clone
        }.groupBy({ it.first }) { it.second }

        val mutationSubsetsWithDiversity = mutationSubsetsWithClones
            .filterValues { it.count { clone -> clone.naiveByComplimentaryGeneMutations } >= parameters.minCountOfNaiveClonesToAddAllele }
            .mapValues { it.value.diversity() }
            .filterValues { it >= minDiversityForAllele }

        val filteredMutationSubsets = mutationSubsetsWithDiversity.keys
        if (filteredMutationSubsets.isEmpty()) return emptyList()

        val alignedClones = alignClonesOnAlleles(clones, filteredMutationSubsets)

        val allelesWithScores = alignedClones.associate { alleleWithClones ->
            val filteredClones = alleleWithClones.clonesWithPenalties
                .filter {
                    it.relativePenalty >= parameters.minRelativePenaltyBetweenAllelesForCloneAlign
                }
                .map { it.clone }
            alleleWithClones.allele to filteredClones.score()
        }

        // filter candidates with too low diversity in comparison to others
        val boundary = allelesWithScores.values.maxOrNull()!! * (1.0 - parameters.topByScore)
        return allelesWithScores
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

        override fun hashCode(): Int = mutationGroups.hashCode()

        override fun toString(): String = mutationGroups.toString()
    }

    private data class DiversityAndScore(
        val diversity: Int,
        val score: Double
    )
}
