package com.milaboratory.mixcr.trees

import com.milaboratory.core.Range
import com.milaboratory.core.sequence.TranslationParameters
import com.milaboratory.mixcr.util.RandomizedTest
import io.kotest.matchers.shouldBe
import org.junit.Ignore
import org.junit.Test
import kotlin.random.Random

class MutationsDescriptionsTest {
    @Ignore
    @Test
    fun `randomized test of difference`() {
        RandomizedTest.randomized(::testDifference, numberOfRuns = 100000)
    }

    @Test
    fun `reproduce test of difference`() {
        RandomizedTest.reproduce(
            ::testDifference,
            176390778668665483L,
            -503051901091347058L
                - 1915251203683321547L,
            -9151767725362997486L,
            0L
        )
    }

    private fun testDifference(random: Random, print: Boolean) {
        val sequence1 = random.generateSequence(20)

        val commonMutations = random.generateMutations(sequence1)
        val mutationsOfBase = commonMutations.combineWith(random.generateMutations(commonMutations.mutate(sequence1)))
        val mutationsOfComparison =
            commonMutations.combineWith(random.generateMutations(commonMutations.mutate(sequence1)))


        val firstPoint = random.nextInt(10)
        val secondPoint = random.nextInt(10, 20)
        val range1 = Range(0, firstPoint)
        val base1 = MutationsDescription(
            sequence1,
            mutationsOfBase,
            range1,
            TranslationParameters.FromLeftWithoutIncompleteCodon,
            true
        )
        val comparison1 = MutationsDescription(
            sequence1,
            mutationsOfComparison,
            range1,
            TranslationParameters.FromLeftWithoutIncompleteCodon,
            true
        )
        val difference1 = base1.differenceWith(comparison1)

        val range2 = Range(firstPoint, secondPoint)
        val base2 = MutationsDescription(
            sequence1,
            mutationsOfBase,
            range2,
            TranslationParameters.FromLeftWithoutIncompleteCodon,
            false
        )
        val comparison2 = MutationsDescription(
            sequence1,
            mutationsOfComparison,
            range2,
            TranslationParameters.FromLeftWithoutIncompleteCodon,
            false
        )
        val difference2 = base2.differenceWith(comparison2)

        val range3 = Range(secondPoint, 20)
        val base3 = MutationsDescription(
            sequence1,
            mutationsOfBase,
            range3,
            TranslationParameters.FromLeftWithoutIncompleteCodon,
            false
        )
        val comparison3 = MutationsDescription(
            sequence1,
            mutationsOfComparison,
            range3,
            TranslationParameters.FromLeftWithoutIncompleteCodon,
            false
        )
        val difference3 = base3.differenceWith(comparison3)
        if (print) {
            println("               sequence1: $sequence1")
            println("      all base mutations: $mutationsOfBase")
            println("all comparison mutations: $mutationsOfComparison")
            println()
            println("             first range:")
            println("                   range: $range1")
            println("          base mutations: ${base1.mutations}")
            println("    comparison mutations: ${comparison1.mutations}")
            println("    difference mutations: ${difference1.mutations}")
            println()
            println("            second range:")
            println("                   range: $range2")
            println("          base mutations: ${base2.mutations}")
            println("    comparison mutations: ${comparison2.mutations}")
            println("    difference mutations: ${difference2.mutations}")
            println()
            println("             third range:")
            println("                   range: $range3")
            println("          base mutations: ${base3.mutations}")
            println("    comparison mutations: ${comparison3.mutations}")
            println("    difference mutations: ${difference3.mutations}")
            println()
            println("       comparison target: ${comparison1.targetNSequence} ${comparison2.targetNSequence} ${comparison3.targetNSequence}")
            println("       difference target: ${difference1.targetNSequence} ${difference2.targetNSequence} ${difference3.targetNSequence}")
        }
        comparison1.targetNSequence.concatenate(comparison2.targetNSequence)
            .concatenate(comparison3.targetNSequence) shouldBe
            difference1.targetNSequence.concatenate(difference2.targetNSequence)
                .concatenate(difference3.targetNSequence)
    }
}
