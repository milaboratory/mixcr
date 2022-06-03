package com.milaboratory.mixcr.alleles

import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.util.asMutations
import com.milaboratory.mixcr.util.asSequence
import org.apache.commons.math3.stat.inference.TTest
import org.apache.commons.math3.stat.regression.SimpleRegression

private const val window = 10

class TIgGERAllelesSearcher : AllelesSearcher {
    override fun search(clones: List<CloneDescription>): List<AllelesSearcher.Result> {
        val mutations = clones.flatMap { it.mutations.asSequence() }.distinct()
        val countByMutationsCount = clones.groupingBy { it.mutations.size() }.eachCount()

        val mutationsInAlleles = mutations.filter { mutation ->
            val countByMutationsCountWithTheMutation =
                clones.filter { clone -> clone.mutations.asSequence().any { it == mutation } }
                    .groupingBy { it.mutations.size() }
                    .eachCount()
            val mutationCountWithMaxFrequency = countByMutationsCountWithTheMutation.entries
                .maxByOrNull { it.value / countByMutationsCount[it.key]!!.toDouble() }!!
                .key

            val regression = SimpleRegression()

            val data = DoubleArray(window)
            (0 until window).forEach { i ->
                //                val x = i + mutationCountWithMaxFrequency
                val x = i + 1
                val result = (countByMutationsCountWithTheMutation[x] ?: 0) /
                    (countByMutationsCount[x]?.toDouble() ?: 1.0)
                regression.addData(x.toDouble(), result)
                data[i] = result
            }

            val regressionResults = regression.regress()

            val a = regressionResults.getParameterEstimate(0)
            val b = regressionResults.getParameterEstimate(1)

            val estimate = DoubleArray(window)
            (0 until window).forEach { i ->
                //                val x = i + mutationCountWithMaxFrequency
                val x = i + 1
                estimate[i] = a + b * x
            }

            val pValue = TTest().tTest(estimate, data)

            pValue >= 0.95 && a >= 0.125

            //            if (pValue >= 0.95 && a >= 0.125) {
            //                println(Mutation.toString(NucleotideSequence.ALPHABET, mutation))
            //                println(countByMutationsCountWithTheMutation.toSortedMap())
            //                println(countByMutationsCountWithTheMutation.toSortedMap().mapValues { (it.value * 100.0 / countByMutationsCount[it.key]!!).toInt() })
            //                println(data.map { (it * 100).toInt() })
            //                println(estimate.map { (it * 100).toInt() })
            //                println("$pValue a: $a, b: $b")
            //                println()
            //            }
        }

        return groupMutationsByAlleles(mutationsInAlleles.toSet(), clones)
            .map { AllelesSearcher.Result(it) }
    }

    private fun groupMutationsByAlleles(
        mutations: Set<Int>, clones: List<CloneDescription>
    ): Collection<Mutations<NucleotideSequence>> {
        val mutationSubsetsWithDiversity = clones.map { clone ->
            val subsetOfAlleleMutations = clone.mutations.asSequence()
                .filter { it in mutations }
                .asMutations(NucleotideSequence.ALPHABET)
            subsetOfAlleleMutations to clone.clusterIdentity
        }
            .groupBy({ it.first }) { it.second }
            .mapValues { it.value.distinct().size }
        val bound = mutationSubsetsWithDiversity.values.maxOrNull()!! * 3 / 4
        return mutationSubsetsWithDiversity
            .filterValues { it >= bound }
            .keys
    }
}
