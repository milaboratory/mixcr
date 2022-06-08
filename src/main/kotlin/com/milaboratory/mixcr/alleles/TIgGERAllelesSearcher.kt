package com.milaboratory.mixcr.alleles

import com.milaboratory.core.alignment.AlignmentScoring
import com.milaboratory.core.alignment.AlignmentUtils
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
    private val sequence1: NucleotideSequence
) : AllelesSearcher {
    private val window = 10
    private val allowedSkippedPoints = 3
    private val minDiversityRatioBetweenAlleles = 0.75
    private val minDiversityForMutation = 3
    private val minDiversityForAllele = 5
    private val minPValue = 0.95
    private val minYIntersect = 0.125
    private val portionOfClonesToSearchCommonMutationsInAnAllele = 0.99

    override fun search(clones: List<CloneDescription>): List<AllelesSearcher.Result> {
        val mutations = clones.flatMap { it.mutations.asSequence() }.distinct()
            .filter { mutation ->
                clones
                    .filter { mutation in it.mutations.asSequence() }
                    .diversity() > minDiversityForMutation
            }
        val countByMutationsCount = clones.groupingBy { it.mutations.size() }.eachCount()

        val possibleAlleleMutations = mutations.filter { mutation ->
            isItCandidateToAlleleMutation(clones, mutation, countByMutationsCount)
        }

        val foundAlleles = chooseAndGroupMutationsByAlleles(possibleAlleleMutations.toSet(), clones)
        //TODO add mutations that exists in all clones of allele
        val withZeroAllele = addZeroAlleleIfNeeded(foundAlleles, clones)
        val enriched = enrichAllelesWithMutationsThatExistsInAlmostAllClones(withZeroAllele, clones)
        return enriched.map { AllelesSearcher.Result(it) }
    }

    private fun enrichAllelesWithMutationsThatExistsInAlmostAllClones(
        alleles: Collection<Mutations<NucleotideSequence>>,
        clones: List<CloneDescription>
    ) = alignClonesOnAlleles(clones, alleles)
        .map { (allele, clones) ->
            if (clones.diversity() < minDiversityForMutation) {
                return@map allele
            }
            val boundary = ceil(clones.size * portionOfClonesToSearchCommonMutationsInAnAllele).toInt()
            val mutationsThatExistsInAlmostAllClones =
                clones.flatMap { allele.invert().combineWith(it.mutations).asSequence() }
                    .groupingBy { it }.eachCount()
                    .filterValues { it >= boundary }
                    .keys.asSequence().asMutations(NucleotideSequence.ALPHABET)
            allele.combineWith(mutationsThatExistsInAlmostAllClones)
        }

    private fun addZeroAlleleIfNeeded(
        foundAlleles: Collection<Mutations<NucleotideSequence>>,
        clones: List<CloneDescription>
    ): Collection<Mutations<NucleotideSequence>> = when {
        foundAlleles.isEmpty() -> listOf(EMPTY_NUCLEOTIDE_MUTATIONS)
        EMPTY_NUCLEOTIDE_MUTATIONS in foundAlleles -> foundAlleles
        //check if there are enough clones that more close to zero allele
        else -> {
            val alleleDiversities = alignClonesOnAlleles(clones, foundAlleles + EMPTY_NUCLEOTIDE_MUTATIONS)
                .mapValues { it.value.diversity() }
            val diversityOfZeroAllele = alleleDiversities[EMPTY_NUCLEOTIDE_MUTATIONS]!!
            val minDiversityOfNotZeroAllele =
                alleleDiversities.filterKeys { it != EMPTY_NUCLEOTIDE_MUTATIONS }.values.minOrNull()!!
            when {
                // Zero allele is not represented enough
                diversityOfZeroAllele < minDiversityForAllele -> foundAlleles
                // There are some not zero alleles that are less represented than zero allele.
                // Try to filter them by comparison with top represented
                diversityOfZeroAllele > minDiversityOfNotZeroAllele -> {
                    val boundary = alleleDiversities.values.maxOrNull()!! * minDiversityRatioBetweenAlleles
                    foundAlleles.filter { alleleDiversities[it]!! >= boundary } + EMPTY_NUCLEOTIDE_MUTATIONS
                }
                else -> foundAlleles + EMPTY_NUCLEOTIDE_MUTATIONS
            }
        }
    }

    private fun List<CloneDescription>.diversity() = map { it.clusterIdentity }.distinct().size

    private fun alignClonesOnAlleles(
        clones: List<CloneDescription>,
        alleles: Collection<Mutations<NucleotideSequence>>
    ): Map<Mutations<NucleotideSequence>, List<CloneDescription>> {
        val alleleClones = alleles.associateWith { mutableListOf<CloneDescription>() }
        clones.forEach { clone ->
            val alignedOn = alleles.maxWithOrNull(
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

    private fun isItCandidateToAlleleMutation(
        clones: List<CloneDescription>,
        mutation: Int,
        countByMutationsCount: Map<Int, Int>
    ): Boolean {
        val countByMutationsCountWithTheMutation =
            clones.filter { clone -> clone.mutations.asSequence().any { it == mutation } }
                .groupingBy { it.mutations.size() }.eachCount()
        val mutationCountWithMaxClonesCount = countByMutationsCountWithTheMutation.entries
            .maxByOrNull { it.value }!!
            .key

        val regression = SimpleRegression()

        val xPoints = (0 until window)
            .map { i -> i + mutationCountWithMaxClonesCount }
            .filter { x -> countByMutationsCountWithTheMutation[x] != 0 }
        if (window - xPoints.size > allowedSkippedPoints) {
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

        return pValue >= minPValue && a >= minYIntersect
    }

    private fun chooseAndGroupMutationsByAlleles(
        mutations: Set<Int>, clones: List<CloneDescription>
    ): Collection<Mutations<NucleotideSequence>> {
        val mutationSubsetsWithDiversity = clones.map { clone ->
            //subsetOfAlleleMutations may be empty (zero allele)
            val subsetOfAlleleMutations = clone.mutations.asSequence()
                .filter { it in mutations }
                .asMutations(NucleotideSequence.ALPHABET)
            subsetOfAlleleMutations to clone
        }
            .groupBy({ it.first }) { it.second }
            .mapValues { it.value.diversity() }
            .filterValues { it >= minDiversityForAllele }
        return when {
            mutationSubsetsWithDiversity.isEmpty() -> emptyList()
            else -> {
                val boundary = mutationSubsetsWithDiversity.values.maxOrNull()!! * minDiversityRatioBetweenAlleles
                mutationSubsetsWithDiversity
                    .filterValues { it >= boundary }
                    .keys
            }
        }
    }
}
