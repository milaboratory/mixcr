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

import com.milaboratory.core.alignment.AlignmentScoring
import com.milaboratory.core.alignment.AlignmentUtils
import com.milaboratory.core.mutations.Mutation
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.mutations.Mutations.EMPTY_NUCLEOTIDE_MUTATIONS
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.util.asMutations
import com.milaboratory.mixcr.util.asSequence
import org.apache.commons.math3.stat.inference.TTest
import org.apache.commons.math3.stat.regression.SimpleRegression
import kotlin.math.ceil


class TIgGERAllelesSearcher(
    private val scoring: AlignmentScoring<NucleotideSequence>,
    private val sequence1: NucleotideSequence,
    private val parameters: FindAllelesParameters
) : AllelesSearcher {
    override fun search(clones: List<CloneDescription>): List<AllelesSearcher.Result> {
        val mutations = clones.flatMap { it.mutations.asSequence() }.distinct()
            //other mutations definitely are not allele mutations
            .filter { mutation ->
                clones
                    .filter { mutation in it.mutations.asSequence() }
                    .diversity() > parameters.minDiversityForMutation
            }
        val countByMutationsCount = clones.groupingBy { it.mutations.size() }.eachCount()

        val possibleAlleleMutations = mutations.filter { mutation ->
            isItCandidateToAlleleMutation(clones, mutation, countByMutationsCount)
        }

        val foundAlleles = chooseAndGroupMutationsByAlleles(possibleAlleleMutations.toSet(), clones)
        val withZeroAllele = addZeroAlleleIfNeeded(foundAlleles, clones)
        val enriched = enrichAllelesWithMutationsThatExistsInAlmostAllClones(withZeroAllele, clones)
        return enriched.map { AllelesSearcher.Result(it) }
    }

    /**
     * Find and add mutations that exist in almost all clones of an allele
     */
    private fun enrichAllelesWithMutationsThatExistsInAlmostAllClones(
        alleles: Collection<Mutations<NucleotideSequence>>,
        clones: List<CloneDescription>
    ) = alignClonesOnAlleles(clones, alleles, addZeroAllele = false)
        .map { (allele, clones) ->
            if (clones.diversity() < parameters.minDiversityForMutation) {
                return@map allele
            }
            val boundary = ceil(clones.size * parameters.portionOfClonesToSearchCommonMutationsInAnAllele).toInt()
            val mutationsThatExistsInAlmostAllClones = clones
                .flatMap { allele.invert().combineWith(it.mutations).asSequence() }
                .groupingBy { it }.eachCount()
                .filterValues { it >= boundary }
                .keys.asSequence()
                .sortedBy { Mutation.getPosition(it) }
                .asMutations(NucleotideSequence.ALPHABET)
            allele.combineWith(mutationsThatExistsInAlmostAllClones)
        }

    /**
     * If there are no zero allele in candidates, test if it there
     */
    private fun addZeroAlleleIfNeeded(
        foundAlleles: Collection<Mutations<NucleotideSequence>>,
        clones: List<CloneDescription>
    ): Collection<Mutations<NucleotideSequence>> = when {
        foundAlleles.isEmpty() -> listOf(EMPTY_NUCLEOTIDE_MUTATIONS)
        EMPTY_NUCLEOTIDE_MUTATIONS in foundAlleles -> foundAlleles
        //check if there are enough clones that more close to zero allele
        else -> {
            val alleleDiversities = alignClonesOnAlleles(clones, foundAlleles, addZeroAllele = true)
                .mapValues { it.value.diversity() }
            val diversityOfZeroAllele = alleleDiversities[EMPTY_NUCLEOTIDE_MUTATIONS]!!
            val minDiversityOfNotZeroAllele =
                alleleDiversities.filterKeys { it != EMPTY_NUCLEOTIDE_MUTATIONS }.values.minOrNull()!!
            when {
                // Zero allele is not represented enough
                diversityOfZeroAllele < parameters.minDiversityForAllele -> foundAlleles
                // There are maybe some not zero alleles that are less represented than zero allele.
                // Try to filter them by comparison with top represented
                diversityOfZeroAllele > minDiversityOfNotZeroAllele -> {
                    val boundary = alleleDiversities.values.maxOrNull()!! * parameters.minDiversityRatioBetweenAlleles
                    foundAlleles.filter { alleleDiversities[it]!! >= boundary } + EMPTY_NUCLEOTIDE_MUTATIONS
                }
                else -> foundAlleles + EMPTY_NUCLEOTIDE_MUTATIONS
            }
        }
    }

    private fun List<CloneDescription>.diversity() = map { it.clusterIdentity }.distinct().size

    private fun alignClonesOnAlleles(
        clones: List<CloneDescription>,
        alleles: Collection<Mutations<NucleotideSequence>>,
        addZeroAllele: Boolean
    ): Map<Mutations<NucleotideSequence>, List<CloneDescription>> {
        val alleleClones = when {
            addZeroAllele -> alleles + EMPTY_NUCLEOTIDE_MUTATIONS
            else -> alleles
        }.associateWith { mutableListOf<CloneDescription>() }
        clones.forEach { clone ->
            val tryToAlignOn = when {
                addZeroAllele && clone.mutations.size() > parameters.minClonesCountToTestForPossibleZeroAllele -> alleles + EMPTY_NUCLEOTIDE_MUTATIONS
                else -> alleles
            }
            val alignedOn = tryToAlignOn.maxWithOrNull(
                Comparator.comparing<Mutations<NucleotideSequence>, Int> { allele ->
                    AlignmentUtils.calculateScore(
                        sequence1,
                        allele.invert().combineWith(clone.mutations),
                        scoring
                    )
                }
                    //prefer to align on not zero allele
                    .then(Comparator.comparing { allele -> if (allele == EMPTY_NUCLEOTIDE_MUTATIONS) 0 else 1 })
            )!!
            alleleClones.getValue(alignedOn) += clone
        }
        return alleleClones
    }

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
        clones: List<CloneDescription>,
        mutation: Int,
        countByMutationsCount: Map<Int, Int>
    ): Boolean {
        val countByMutationsCountWithTheMutation =
            clones
                .filter { clone ->
                    //TODO try to replace with binary search
                    clone.mutations.asSequence().any { it == mutation }
                }
                .groupingBy { it.mutations.size() }.eachCount()
        val mutationCountWithMaxClonesCount = countByMutationsCountWithTheMutation.entries
            .maxByOrNull { it.value }!!
            .key

        val regression = SimpleRegression()

        val xPoints = (0 until parameters.windowSizeForRegression)
            .map { i -> i + mutationCountWithMaxClonesCount }
            .filter { x -> countByMutationsCountWithTheMutation[x] != 0 }
        if (parameters.windowSizeForRegression - xPoints.size > parameters.allowedSkippedPointsInRegression) {
            return false
        }

        val y = DoubleArray(xPoints.size)
        xPoints.forEachIndexed { i, x ->
            val result = (countByMutationsCountWithTheMutation[x] ?: 0) /
                    (countByMutationsCount[x]?.toDouble() ?: 1.0)
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

        return pValue >= parameters.minPValueForRegression && a >= parameters.minYIntersect
    }

    private fun chooseAndGroupMutationsByAlleles(
        mutations: Set<Int>, clones: List<CloneDescription>
    ): Collection<Mutations<NucleotideSequence>> {
        //count diversity of coexisted variants of candidates and filter by minDiversityForAllele
        val mutationSubsetsWithDiversity = clones.map { clone ->
            //subsetOfAlleleMutations may be empty (zero allele)
            val subsetOfAlleleMutations = clone.mutations.asSequence()
                .filter { it in mutations }
                .asMutations(NucleotideSequence.ALPHABET)
            subsetOfAlleleMutations to clone
        }
            .groupBy({ it.first }) { it.second }
            //TODO plus diversity of all clones that include this subset and more
            .mapValues { it.value.diversity() }
            .filterValues { it >= parameters.minDiversityForAllele }
        return when {
            mutationSubsetsWithDiversity.isEmpty() -> emptyList()
            else -> {
                //filter candidates with too low diversity in comparison to others
                val boundary =
                    mutationSubsetsWithDiversity.values.maxOrNull()!! * parameters.minDiversityRatioBetweenAlleles
                mutationSubsetsWithDiversity
                    .filterValues { it >= boundary }
                    .keys
            }
        }
    }
}
