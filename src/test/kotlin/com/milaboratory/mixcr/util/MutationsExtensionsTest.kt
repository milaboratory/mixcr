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
package com.milaboratory.mixcr.util

import com.milaboratory.core.mutations.Mutation
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.trees.MutationsUtils
import com.milaboratory.mixcr.trees.generateMutations
import com.milaboratory.test.RandomizedTest
import com.milaboratory.test.generateSequence
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.shouldBe
import org.junit.Test
import kotlin.random.Random

class MutationsExtensionsTest {
    @Test
    fun `intersection for substitutions`() {
        mutations("ST0G,ST1G").intersection(mutations("ST0G,ST2G")) shouldBe mutations("ST0G")
        mutations("ST1G").intersection(mutations("ST0G,ST1G")) shouldBe mutations("ST1G")
        mutations("ST0G,ST1G").intersection(mutations("ST1G")) shouldBe mutations("ST1G")
    }

    @Test
    fun `intersection of three mutations without one in the center`() {
        mutations("ST0G,ST1G,ST2G").intersection(mutations("ST1G")) shouldBe mutations("ST1G")
    }

    @Test
    fun `intersection of three mutations without two in the end`() {
        mutations("ST0G,ST1G,ST2G").intersection(mutations("ST1G,ST2G")) shouldBe mutations("ST1G,ST2G")
    }

    @Test
    fun `intersection of duplicated insertion with itself`() {
        mutations("I0G,I0G").intersection(mutations("I0G")) shouldBe mutations("I0G")
        mutations("I0G").intersection(mutations("I0G,I0G")) shouldBe mutations("I0G")
    }

    @Test
    fun `intersection of two insertions and one insertion`() {
        mutations("I1C,ST2G").intersection(mutations("I1T,I1C,ST2G")) shouldBe mutations("I1C,ST2G")
        mutations("I1T,I1C,ST2G").intersection(mutations("I1C,ST2G")) shouldBe mutations("I1C,ST2G")
    }

    @Test
    fun `intersection of two different insertions`() {
        mutations("I1C,I2C").intersection(mutations("I1C,I2C")) shouldBe mutations("I1C,I2C")
    }

    @Test
    fun `intersection by second insertions`() {
        mutations("I0G,I0T").intersection(mutations("I0T")) shouldBe mutations("I0T")
        mutations("I0T").intersection(mutations("I0T,I0G")) shouldBe mutations("I0T")
    }

    @Test
    fun `intersection by second insertion in the middle of two others`() {
        mutations("I0G").intersection(mutations("I0T,I0G,I0T")) shouldBe mutations("I0G")
        mutations("I0T,I0G,I0T").intersection(mutations("I0G")) shouldBe mutations("I0G")
    }

    @Test
    fun `intersection of substitution after insertion`() {
        mutations("I0G,ST0G").intersection(mutations("ST0G")) shouldBe mutations("ST0G")
        mutations("ST0G").intersection(mutations("I0G,ST0G")) shouldBe mutations("ST0G")
    }

    @Test
    fun `without for substitutions`() {
        mutations("ST0G,ST1G").without(mutations("ST0G")) shouldBe mutations("ST1G")
    }

    @Test
    fun `three mutations without one in the center`() {
        mutations("ST0G,ST1G,ST2G").without(mutations("ST1G")) shouldBe mutations("ST0G,ST2G")
    }

    @Test
    fun `three mutations without two in the end`() {
        mutations("ST0G,ST1G,ST2G").without(mutations("ST1G,ST2G")) shouldBe mutations("ST0G")
    }

    @Test
    fun `without for duplicated insertion`() {
        mutations("I0G,I0G").without(mutations("I0G")) shouldBe mutations("I0G")
    }

    @Test
    fun `without second insertions`() {
        mutations("I0G,I0T").without(mutations("I0T")) shouldBe mutations("I0G")
    }

    @Test
    fun `random test of difference`() {
        RandomizedTest.randomized(::testDifference, numberOfRuns = 1_000_000)
    }

    @Test
    fun `reproduce test of difference`() {
        RandomizedTest.reproduce(
            ::testDifference,
            1395594558168514785L,
            2576371376849600943L,
            1456318475387300261L,
            4025326375754574485L,
            2916762172989578227L,
        )
    }


    private fun testDifference(random: Random, print: Boolean) {
        val sequence1 = random.generateSequence(20)
        val first = random.generateMutations(sequence1)
        val second = random.generateMutations(sequence1)

        val result = MutationsUtils.difference(
            sequence1,
            first,
            second
        )
        if (print) {
            println("     sequence1: $sequence1")
            println("         first: ${first.encode(",")}")
            println("        second: ${second.encode(",")}")
            val intersection = first.intersection(second)
            println("  intersection: ${intersection.encode(",")}")
            println(" first without: ${first.combineWith(intersection.invert()).encode(",")}")
            println("second without: ${second.combineWith(intersection.invert()).encode(",")}")
            println("        result: ${result.mutationsFromParentToThis.encode(",")}")
        }
        result.calculateParent() shouldBe first.mutate(sequence1)
        result.mutationsFromParentToThis.mutate(result.calculateParent()) shouldBe second.mutate(sequence1)
    }

    @Test
    fun `random test of without`() {
        RandomizedTest.randomized(::testWithout, numberOfRuns = 100000)
    }

    @Test
    fun `reproduce test of without`() {
        RandomizedTest.reproduce(
            ::testWithout,
            4750412158561094728L,
            3502722435274504377L,
            -1598204848989057487L,
        )
    }

    @Test
    fun `random test of intersection`() {
        RandomizedTest.randomized(::testIntersection, numberOfRuns = 100000)
    }

    @Test
    fun `reproduce test of intersection`() {
        RandomizedTest.reproduce(
            ::testIntersection,
            5064150615102225317L,
            6920324828711573378L,
            7112278539570627394L,
            6313853897278610290L,
            1436838224206222452L,
            4912652181881843442L,
            7380827987566875796L,
            8956308861977725090L,
            2382159383607284620L,
        )
    }

    private fun testWithout(random: Random, print: Boolean) {
        val parent = random.generateSequence(20)
        val original = random.generateMutations(parent)
        if (original.isEmpty) {
            return
        }
        val subsetOfIndexes = random.sampleOfIndexes(original)
        val mutationsToSubtract = subsetOfIndexes
            .asSequence()
            .map { original.getMutation(it) }
            .asMutations(NucleotideSequence.ALPHABET)
        val mutationsLeft = (0 until original.size())
            .asSequence()
            .filter { it !in subsetOfIndexes }
            .map { original.getMutation(it) }
            .asMutations(NucleotideSequence.ALPHABET)
        val result = original.without(mutationsToSubtract)
        if (print) {
            println("original: ${original.encode(",")}")
            println("subtract: ${mutationsToSubtract.encode(",")}")
            println("    left: ${mutationsLeft.encode(",")}")
            println("  result: ${result.encode(",")}")
        }
        result shouldBe mutationsLeft
    }

    private fun testIntersection(random: Random, print: Boolean) {
        val parent = random.generateSequence(20)
        val original = random.generateMutations(parent)
        if (original.isEmpty) {
            return
        }
        val intersectionIndexes = random.sampleOfIndexes(original)
        val firstSubsetOfIndexes = random.sampleOfIndexes(original) - intersectionIndexes.toSet()
        val secondSubsetOfIndexes =
            random.sampleOfIndexes(original) - intersectionIndexes.toSet() - firstSubsetOfIndexes.toSet()
        val intersection = intersectionIndexes
            .asSequence()
            .map { original.getMutation(it) }
            .asMutations(NucleotideSequence.ALPHABET)
        val firstDiff = firstSubsetOfIndexes.map { original.getMutation(it) }
        val secondDiff = secondSubsetOfIndexes.map { original.getMutation(it) }
        //different indexes yield the same insertion
        if (firstDiff.any { it in secondDiff }) {
            return
        }

        val firstMutations = (intersectionIndexes + firstSubsetOfIndexes)
            .sorted()
            .asSequence()
            .map { original.getMutation(it) }
            .asMutations(NucleotideSequence.ALPHABET)
        val secondMutations = (intersectionIndexes + secondSubsetOfIndexes)
            .sorted()
            .asSequence()
            .map { original.getMutation(it) }
            .asMutations(NucleotideSequence.ALPHABET)
        val firstResult = firstMutations.intersection(secondMutations)
        val secondResult = secondMutations.intersection(firstMutations)
        if (print) {
            println("    original: ${original.encode(",")}")
            println("       first: ${firstMutations.encode(",")}")
            println("      second: ${secondMutations.encode(",")}")
            println("intersection: ${intersection.encode(",")}")
            println(" firstResult: ${firstResult.encode(",")}")
            println("secondResult: ${secondResult.encode(",")}")
        }
        if (intersection.asSequence().any { Mutation.isInsertion(it) }) {
            //in case of subsequent insertions there are different 'right' results
            intersection shouldBeIn arrayOf(firstResult, secondResult)
        } else {
            firstResult shouldBe intersection
            secondResult shouldBe intersection
        }
    }

    private fun Random.sampleOfIndexes(original: Mutations<NucleotideSequence>): List<Int> =
        (0 until nextInt(original.size()))
            .map { nextInt(original.size()) }
            .distinct()
            .groupBy { Mutation.getPosition(original.getMutation(it)) }
            .values
            .asSequence()
            .map { it.sorted() }
            .map { mutationIndexes ->
                var lastPosition = -1
                mutationIndexes.map { index ->
                    val firstTheSameMutationIndex = original.asSequence()
                        .withIndex()
                        .first { (i, mutation) -> i > lastPosition && original.getMutation(index) == mutation }
                        .index
                    lastPosition = firstTheSameMutationIndex
                    firstTheSameMutationIndex
                }
            }
            .flatten()
            .distinct()
            .sorted()
            .toList()

    private fun mutations(mutations: String) = Mutations(NucleotideSequence.ALPHABET, mutations)
}
