@file:Suppress("LocalVariableName")

package com.milaboratory.mixcr.trees

import com.google.common.primitives.Bytes
import com.milaboratory.core.Range
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.trees.MutationsGenerator.generateMutations
import com.milaboratory.mixcr.util.RangeInfo
import org.junit.Assert
import org.junit.Ignore
import org.junit.Test
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.stream.Collectors
import java.util.stream.IntStream

class CalculationOfMutationsDifferenceTest {
    @Test
    fun dontIncludeInsertionsFirstInsertionsInParent() {
        val grand_ = NucleotideSequence("TTTT")
        val parent = NucleotideSequence("TTTTG")
        val child_ = NucleotideSequence("ATTTTA")
        val fromGrandToParent = MutationsWithRange(
            grand_,
            Mutations(NucleotideSequence.ALPHABET, "I0G,I4G"),
            RangeInfo(Range(0, grand_.size()), false)
        )
        Assert.assertEquals(parent, fromGrandToParent.buildSequence())
        val fromGrandToChild = MutationsWithRange(
            grand_,
            Mutations(NucleotideSequence.ALPHABET, "I0A,I4A"),
            RangeInfo(Range(0, grand_.size()), true)
        )
        Assert.assertEquals(child_, fromGrandToChild.buildSequence())
        val result = fromGrandToParent.differenceWith(fromGrandToChild)
        Assert.assertEquals(child_, result.buildSequence())
        Assert.assertEquals(child_, fromGrandToParent.combineWith(result).buildSequence())
    }

    @Test
    fun dontIncludeFirstInsertionsInChild() {
        val grand_ = NucleotideSequence("TTTT")
        val parent = NucleotideSequence("GTTTTG")
        val child_ = NucleotideSequence("TTTTA")
        val fromGrandToParent = MutationsWithRange(
            grand_,
            Mutations(NucleotideSequence.ALPHABET, "I0G,I4G"),
            RangeInfo(Range(0, grand_.size()), true)
        )
        Assert.assertEquals(parent, fromGrandToParent.buildSequence())
        val fromGrandToChild = MutationsWithRange(
            grand_,
            Mutations(NucleotideSequence.ALPHABET, "I0A,I4A"),
            RangeInfo(Range(0, grand_.size()), false)
        )
        Assert.assertEquals(child_, fromGrandToChild.buildSequence())
        val result = fromGrandToParent.differenceWith(fromGrandToChild)
        Assert.assertEquals(child_, result.buildSequence())
        Assert.assertEquals(child_, fromGrandToParent.combineWith(result).buildSequence())
    }

    @Test
    fun deletionOfFirstLetterInChildAndIncludeFirst() {
        val grand_ = NucleotideSequence("TTTT")
        val parent = NucleotideSequence("TTTG")
        val child_ = NucleotideSequence("TTT")
        val fromGrandToParent = MutationsWithRange(
            grand_,
            Mutations(NucleotideSequence.ALPHABET, "ST3G"),
            RangeInfo(Range(0, grand_.size()), false)
        )
        Assert.assertEquals(parent, fromGrandToParent.buildSequence())
        val fromGrandToChild = MutationsWithRange(
            grand_,
            Mutations(NucleotideSequence.ALPHABET, "DT0"),
            RangeInfo(Range(0, grand_.size()), false)
        )
        Assert.assertEquals(child_, fromGrandToChild.buildSequence())
        val result = fromGrandToParent.differenceWith(fromGrandToChild)
        Assert.assertEquals(child_, result.buildSequence())
        Assert.assertEquals(child_, fromGrandToParent.combineWith(result).buildSequence())
    }

    @Test
    fun deletionOfFirstLetterInChildAndDontIncludeFirstInsertions() {
        val grand_ = NucleotideSequence("TTTT")
        val parent = NucleotideSequence("TTTG")
        val child_ = NucleotideSequence("TTT")
        val fromGrandToParent = MutationsWithRange(
            grand_,
            Mutations(NucleotideSequence.ALPHABET, "ST3G"),
            RangeInfo(Range(0, grand_.size()), false)
        )
        Assert.assertEquals(parent, fromGrandToParent.buildSequence())
        val fromGrandToChild = MutationsWithRange(
            grand_,
            Mutations(NucleotideSequence.ALPHABET, "DT0"),
            RangeInfo(Range(0, grand_.size()), false)
        )
        Assert.assertEquals(child_, fromGrandToChild.buildSequence())
        val result = fromGrandToParent.differenceWith(fromGrandToChild)
        Assert.assertEquals(child_, result.buildSequence())
        Assert.assertEquals(child_, fromGrandToParent.combineWith(result).buildSequence())
    }

    @Test
    fun insertionsInDifferentPlaces() {
        val grand_ = NucleotideSequence("CCCC")
        val parent = NucleotideSequence("CTCCC")
        val child_ = NucleotideSequence("CCCAC")
        val fromGrandToParent = MutationsWithRange(
            grand_,
            Mutations(NucleotideSequence.ALPHABET, "I1T"),
            RangeInfo(Range(0, grand_.size()), false)
        )
        Assert.assertEquals(parent, fromGrandToParent.buildSequence())
        val fromGrandToChild = MutationsWithRange(
            grand_,
            Mutations(NucleotideSequence.ALPHABET, "I3A"),
            RangeInfo(Range(0, grand_.size()), false)
        )
        Assert.assertEquals(child_, fromGrandToChild.buildSequence())
        val result = fromGrandToParent.differenceWith(fromGrandToChild)
        Assert.assertEquals(child_, result.buildSequence())
        Assert.assertEquals(child_, fromGrandToParent.combineWith(result).buildSequence())
    }

    @Test
    fun substitutionsWithSubRangeFromOne() {
        val grand_ = NucleotideSequence("CCGCCG")
        val parent = NucleotideSequence("CACCG")
        val child_ = NucleotideSequence("CGCCA")
        val fromGrandToParent = MutationsWithRange(
            grand_,
            Mutations(NucleotideSequence.ALPHABET, "SG2A"),
            RangeInfo(Range(1, grand_.size()), false)
        )
        Assert.assertEquals(parent, fromGrandToParent.buildSequence())
        val fromGrandToChild = MutationsWithRange(
            grand_,
            Mutations(NucleotideSequence.ALPHABET, "SG5A"),
            RangeInfo(Range(1, grand_.size()), false)
        )
        Assert.assertEquals(child_, fromGrandToChild.buildSequence())
        val result = fromGrandToParent.differenceWith(fromGrandToChild)
        Assert.assertEquals(child_, result.buildSequence())
        Assert.assertEquals(child_, fromGrandToParent.combineWith(result).buildSequence())
    }

    @Test
    fun deletionInTheStartOfSubRangeFromOneAndDontIncludeFirstInsertions() {
        val grand_ = NucleotideSequence("GTTGT")
        val parent = NucleotideSequence("ATG")
        val child_ = NucleotideSequence("TG")
        val fromGrandToParent = MutationsWithRange(
            grand_,
            Mutations(NucleotideSequence.ALPHABET, "ST1A"),
            RangeInfo(Range(1, 4), false)
        )
        Assert.assertEquals(parent, fromGrandToParent.buildSequence())
        val fromGrandToChild = MutationsWithRange(
            grand_,
            Mutations(NucleotideSequence.ALPHABET, "DT1"),
            RangeInfo(Range(1, 4), false)
        )
        Assert.assertEquals(child_, fromGrandToChild.buildSequence())
        val result = fromGrandToParent.differenceWith(fromGrandToChild)
        Assert.assertEquals(child_, result.buildSequence())
        Assert.assertEquals(child_, fromGrandToParent.combineWith(result).buildSequence())
    }

    @Test
    fun reproduceRandom() {
        Assert.assertFalse(testRandom(5657551124671488951L, true))
    }

    @Ignore
    @Test
    fun randomizedTest() {
        val numberOfRuns = 1000000
        val failedSeeds = IntStream.range(0, numberOfRuns)
            .mapToObj { ThreadLocalRandom.current().nextLong() }
            .parallel()
            .filter { seed: Long -> testRandom(seed, false) }
            .collect(Collectors.toList())
        println("failed: " + failedSeeds.size)
        Assert.assertEquals(emptyList<Any>(), failedSeeds)
    }

    private fun testRandom(seed: Long, print: Boolean): Boolean {
        return try {
            val random = Random(seed)
            val grand = generate(random)
            val subRange = Range(random.nextInt(2), grand.size() - random.nextInt(2))
            val fromGrandToParent = MutationsWithRange(
                grand,
                generateMutations(grand, random),
                RangeInfo(subRange, random.nextBoolean())
            )
            val fromGrandToChild = MutationsWithRange(
                grand,
                generateMutations(grand, random),
                RangeInfo(subRange, random.nextBoolean())
            )
            val child = fromGrandToChild.buildSequence()
            if (print) {
                println("grand: $grand")
                println("parent: " + fromGrandToParent.buildSequence())
                println("child: $child")
                println()
                println("from grand to parent: " + fromGrandToParent.mutations)
                println("from grand to parent range info: " + fromGrandToParent.rangeInfo)
                println()
                println("from grand to child: " + fromGrandToChild.mutations)
                println("from grand to child range info: " + fromGrandToChild.rangeInfo)
                println()
            }
            val result = fromGrandToParent.differenceWith(fromGrandToChild)
            if (print) {
                println("result: " + result.mutations)
                println("result range info: " + result.rangeInfo)
                println()
            }
            Assert.assertEquals(child, result.buildSequence())
            Assert.assertEquals(child, fromGrandToParent.combineWith(result).buildSequence())
            false
        } catch (e: Throwable) {
            if (print) {
                e.printStackTrace()
            }
            true
        }
    }

    private fun generate(random: Random): NucleotideSequence {
        val chars = IntStream.range(0, 5 + random.nextInt(5))
            .mapToObj { random.nextInt(4).toByte() }
            .collect(Collectors.toList())
        return NucleotideSequence(Bytes.toArray(chars))
    }
}
