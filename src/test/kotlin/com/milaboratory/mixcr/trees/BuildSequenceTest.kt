package com.milaboratory.mixcr.trees

import com.milaboratory.core.Range
import com.milaboratory.core.alignment.Aligner
import com.milaboratory.core.alignment.LinearGapAlignmentScoring
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.trees.MutationsUtils.buildSequence
import com.milaboratory.mixcr.util.extractAbsoluteMutations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Ignore
import org.junit.Test
import java.util.concurrent.ThreadLocalRandom
import java.util.stream.Collectors
import java.util.stream.IntStream
import kotlin.random.Random

class BuildSequenceTest {
    @Ignore
    @Test
    fun randomizedTestOfBuildingSequence() {
        val numberOfRuns = 1000000
        val failedSeeds = IntStream.range(0, numberOfRuns)
            .mapToObj { ThreadLocalRandom.current().nextLong() }
            .parallel()
            .filter { seed: Long -> testBuildSequence(seed, false) }
            .collect(Collectors.toList())
        assertEquals(emptyList<Any>(), failedSeeds)
    }

    @Test
    fun reproduceRandomTestOfBuildingSequence() {
        assertFalse(testBuildSequence(3301598077971287922L, true))
    }

    @Ignore
    @Test
    fun randomizedTestBuildSequencesByCuttingMutations() {
        val numberOfRuns = 100000
        val failedSeeds = IntStream.range(0, numberOfRuns)
            .mapToObj { ThreadLocalRandom.current().nextLong() }
            .parallel()
            .filter { seed: Long -> testBuildSequencesByCuttingMutations(seed, false) }
            .collect(Collectors.toList())
        println("failed: " + failedSeeds.size)
        assertEquals(emptyList<Any>(), failedSeeds)
    }

    @Test
    fun reproduceBuildSequencesByCuttingMutations() {
        assertFalse(testBuildSequencesByCuttingMutations(4441662389111061139L, true))
        assertFalse(testBuildSequencesByCuttingMutations(4865476048882002489L, true))
    }

    @Test
    fun mutationsForEmptyRangeWithInsertion() {
        val parent = NucleotideSequence("CCCTTT")
        val mutations = Mutations(NucleotideSequence.ALPHABET, "I3A")
        val child = mutations.mutate(parent)
        assertEquals("CCCATTT", child.toString())
        assertEquals("[I3:A]", mutations.extractAbsoluteMutations(Range(3, 3), true).toString())
        assertEquals("[]", mutations.extractAbsoluteMutations(Range(3, 3), false).toString())
    }

    @Test
    fun mutationsWithInsertionAtTheEndOfRange() {
        val parent = NucleotideSequence("CCCTTT")
        val mutations = Mutations(NucleotideSequence.ALPHABET, "I3A")
        val child = mutations.mutate(parent)
        assertEquals("CCCATTT", child.toString())
        assertEquals("[I3:A]", mutations.extractAbsoluteMutations(Range(0, 3), true).toString())
        assertEquals("[I3:A]", mutations.extractAbsoluteMutations(Range(0, 3), false).toString())
    }

    @Test
    fun insertionOnBoundary() {
        val parent = NucleotideSequence("CCCTTT")
        val mutations = Mutations(NucleotideSequence.ALPHABET, "I3A")
        val child = mutations.mutate(parent)
        assertEquals("CCCATTT", child.toString())
        assertEquals("CCCA", buildSequence(parent, mutations, Range(0, 3), false).toString())
        assertEquals("TTT", buildSequence(parent, mutations, Range(3, 6), false).toString())
        assertEquals("ATTT", buildSequence(parent, mutations, Range(3, 6), true).toString())
    }

    @Test
    fun insertionInTheEnd() {
        val parent = NucleotideSequence("CCCTTT")
        val mutations = Mutations(NucleotideSequence.ALPHABET, "I6A")
        val child = mutations.mutate(parent)
        assertEquals("CCCTTTA", child.toString())
        assertEquals("CCC", buildSequence(parent, mutations, Range(0, 3), false).toString())
        assertEquals("TTTA", buildSequence(parent, mutations, Range(3, 6), false).toString())
    }

    @Test
    fun insertionInTheBeginning() {
        val parent = NucleotideSequence("CCCTTT")
        val mutations = Mutations(NucleotideSequence.ALPHABET, "I0A")
        val child = mutations.mutate(parent)
        assertEquals("ACCCTTT", child.toString())
        assertEquals("CCC", buildSequence(parent, mutations, Range(0, 3), false).toString())
        assertEquals("ACCC", buildSequence(parent, mutations, Range(0, 3), true).toString())
        assertEquals("TTT", buildSequence(parent, mutations, Range(3, 6), false).toString())
    }

    @Test
    fun deletionBeforeBeginOfRangeWithIncludedFirstInserts() {
        val parent = NucleotideSequence("AGTT")
        val mutations = Mutations(NucleotideSequence.ALPHABET, "DG1")
        val child = mutations.mutate(parent)
        assertEquals("ATT", child.toString())
        assertEquals("T", buildSequence(parent, mutations, Range(2, 3), true).toString())
    }

    @Test
    fun deletionOfFirstLetterWithIncludedFirstInsertsSubsetStartsInTheBeginning() {
        val parent = NucleotideSequence("ATTT")
        val mutations = Mutations(NucleotideSequence.ALPHABET, "DA0")
        val child = mutations.mutate(parent)
        assertEquals("TTT", child.toString())
        assertEquals("TTT", buildSequence(parent, mutations, Range(0, 4), true).toString())
    }

    @Test
    fun deletionOfSeveralFirstLettersWithIncludedFirstInsertsSubsetStartsInTheBeginning() {
        val parent = NucleotideSequence("AATTT")
        val mutations = Mutations(NucleotideSequence.ALPHABET, "DA0,DA1")
        val child = mutations.mutate(parent)
        assertEquals("TTT", child.toString())
        assertEquals("TTT", buildSequence(parent, mutations, Range(0, 5), true).toString())
    }

    @Test
    fun deletionOfFirstLetterWithIncludedFirstInsertsSubsetStartsInAMiddle() {
        val parent = NucleotideSequence("GGATTT")
        val mutations = Mutations(NucleotideSequence.ALPHABET, "DA2")
        val child = mutations.mutate(parent)
        assertEquals("GGTTT", child.toString())
        assertEquals("TTT", buildSequence(parent, mutations, Range(2, 6), true).toString())
    }

    @Test
    fun insertionOfSeveralLettersOnBoundary() {
        val parent = NucleotideSequence("CCCTTT")
        val mutations = Mutations(NucleotideSequence.ALPHABET, "I3A,I3A,I3A")
        val child = mutations.mutate(parent)
        assertEquals("CCCAAATTT", child.toString())
        assertEquals("CCCAAA", buildSequence(parent, mutations, Range(0, 3), false).toString())
        assertEquals("TTT", buildSequence(parent, mutations, Range(3, 6), false).toString())
    }

    @Test
    fun deletionToTheLeftFromBoundary() {
        val parent = NucleotideSequence("CCATTT")
        val mutations = Mutations(NucleotideSequence.ALPHABET, "DA2")
        val child = mutations.mutate(parent)
        assertEquals("CCTTT", child.toString())
        assertEquals("CC", buildSequence(parent, mutations, Range(0, 3), false).toString())
        assertEquals("TTT", buildSequence(parent, mutations, Range(3, 6), false).toString())
    }

    @Test
    fun deletionToTheRightFromBoundary() {
        val parent = NucleotideSequence("CCCATT")
        val mutations = Mutations(NucleotideSequence.ALPHABET, "DA3")
        val child = mutations.mutate(parent)
        assertEquals("CCCTT", child.toString())
        assertEquals("CCC", buildSequence(parent, mutations, Range(0, 3), false).toString())
        assertEquals("TT", buildSequence(parent, mutations, Range(3, 6), false).toString())
    }

    @Test
    fun deletionToTheRightAndToTheLeftFromBoundary() {
        val parent = NucleotideSequence("CCAATT")
        val mutations = Mutations(NucleotideSequence.ALPHABET, "DA2,DA3")
        val child = mutations.mutate(parent)
        assertEquals("CCTT", child.toString())
        assertEquals("CC", buildSequence(parent, mutations, Range(0, 3), false).toString())
        assertEquals("TT", buildSequence(parent, mutations, Range(3, 6), false).toString())
    }

    @Test
    fun deletionToTheLeftFromBoundaryAndInsertionOnBoundary() {
        val parent = NucleotideSequence("CCATTT")
        val mutations = Mutations(NucleotideSequence.ALPHABET, "DA2,I3G")
        val child = mutations.mutate(parent)
        assertEquals("CCGTTT", child.toString())
        assertEquals("CCG", buildSequence(parent, mutations, Range(0, 3), false).toString())
        assertEquals("TTT", buildSequence(parent, mutations, Range(3, 6), false).toString())
        assertEquals("GTTT", buildSequence(parent, mutations, Range(3, 6), true).toString())
    }

    private fun testBuildSequence(seed: Long, print: Boolean): Boolean = try {
        val random = Random(seed)
        val part1 = random.generateSequence(5 + random.nextInt(5))
        val part2 = random.generateSequence(5 + random.nextInt(5))
        val part3 = random.generateSequence(5 + random.nextInt(5))
        val mutatedPart1: NucleotideSequence = random.generateMutations(part1).mutate(part1)
        val mutatedPart2: NucleotideSequence = random.generateMutations(part2).mutate(part2)
        val mutatedPart3: NucleotideSequence = random.generateMutations(part3).mutate(part3)
        val parent = NucleotideSequence.ALPHABET.createBuilder()
            .append(part1)
            .append(part2)
            .append(part3)
            .createAndDestroy()
        val child = NucleotideSequence.ALPHABET.createBuilder()
            .append(mutatedPart1)
            .append(mutatedPart2)
            .append(mutatedPart3)
            .createAndDestroy()
        val mutations = Aligner.alignGlobal(
            LinearGapAlignmentScoring.getNucleotideBLASTScoring(),
            parent,
            child
        ).absoluteMutations
        if (print) {
            println("parent(" + parent.size() + "):")
            println(parent)
            println("child(" + child.size() + "):")
            println(child)
            println("mutations:")
            println(mutations)
            println("part1:")
            println("$part1 => $mutatedPart1")
            println("part2:")
            println("$part2 => $mutatedPart2")
            println("part3:")
            println("$part3 => $mutatedPart3")
        }
        val sequence1Range1 = Range(0, part1.size())
        val resultPart1: NucleotideSequence = buildSequence(
            parent,
            mutations,
            sequence1Range1,
            true
        )
        val sequence1Range2 = Range(part1.size(), part1.size() + part2.size())
        val resultPart2: NucleotideSequence = buildSequence(
            parent,
            mutations,
            sequence1Range2,
            false
        )
        val sequence1Range3 = Range(part1.size() + part2.size(), parent.size())
        val resultPart3: NucleotideSequence = buildSequence(
            parent,
            mutations,
            sequence1Range3,
            false
        )
        val result = NucleotideSequence.ALPHABET.createBuilder()
            .append(resultPart1)
            .append(resultPart2)
            .append(resultPart3)
            .createAndDestroy()
        if (print) {
            println("resultPart1:")
            println("$resultPart1 ($mutatedPart1)")
            println("resultPart2:")
            println("$resultPart2 ($mutatedPart2)")
            println("resultPart3:")
            println("$resultPart3 ($mutatedPart3)")
            println("result:")
            println(result)
            println("expected:")
            println(child)
        }
        assertEquals(result, child)
        false
    } catch (e: Exception) {
        if (print) {
            e.printStackTrace()
        }
        true
    }

    private fun testBuildSequencesByCuttingMutations(seed: Long, print: Boolean): Boolean = try {
        val random = Random(seed)
        val parent = random.generateSequence(15)
        val range1 = Range(0, 5)
        val range2 = Range(5, 10)
        val range3 = Range(10, 15)
        val mutations: Mutations<NucleotideSequence> = random.generateMutations(parent)
        val child = mutations.mutate(parent)
        val mutatedPart1 = buildSequence(parent, mutations.extractAbsoluteMutations(range1, true), range1)
        val mutatedPart2 = buildSequence(parent, mutations.extractAbsoluteMutations(range2, false), range2)
        val mutatedPart3 = buildSequence(parent, mutations.extractAbsoluteMutations(range3, false), range3)
        val result = mutatedPart1.concatenate(mutatedPart2).concatenate(mutatedPart3)
        if (print) {
            println("parent: $parent")
            println(" child: $child")
            println("result: $result")
            println()
            println("mutations: $mutations")
            println()
            println("range1: $range1")
            println("range2: $range2")
            println("range3: $range3")
            println()
            println("parent: ${parent.getRange(range1)} ${parent.getRange(range2)} ${parent.getRange(range3)}")
            println(" child: $child")
            println("result: $mutatedPart1 $mutatedPart2 $mutatedPart3")
            println()
        }
        assertEquals(result, child)
        false
    } catch (e: Throwable) {
        if (print) {
            e.printStackTrace()
        }
        true
    }
}
