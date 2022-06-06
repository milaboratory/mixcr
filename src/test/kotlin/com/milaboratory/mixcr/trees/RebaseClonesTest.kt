@file:Suppress("LocalVariableName")

package com.milaboratory.mixcr.trees

import com.google.common.collect.Maps
import com.milaboratory.core.Range
import com.milaboratory.core.alignment.AffineGapAlignmentScoring
import com.milaboratory.core.alignment.Aligner
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.mutations.Mutations.EMPTY_NUCLEOTIDE_MUTATIONS
import com.milaboratory.core.sequence.NucleotideAlphabet.C
import com.milaboratory.core.sequence.NucleotideAlphabet.G
import com.milaboratory.core.sequence.NucleotideAlphabet.N
import com.milaboratory.core.sequence.NucleotideAlphabet.T
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.core.sequence.Seq
import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.trees.MutationsUtils.NDNScoring
import com.milaboratory.mixcr.trees.MutationsUtils.buildSequence
import com.milaboratory.mixcr.util.extractAbsoluteMutations
import io.kotest.matchers.shouldBe
import io.repseq.core.GeneType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Ignore
import org.junit.Test
import java.lang.Integer.min
import java.util.concurrent.ThreadLocalRandom
import java.util.stream.Collectors
import java.util.stream.IntStream
import kotlin.random.Random

class RebaseClonesTest {
    @Ignore
    @Test
    fun randomizedTestForRebaseMutations() {
        val numberOfRuns = 100000
        val failedSeeds = IntStream.range(0, numberOfRuns)
            .mapToObj { ThreadLocalRandom.current().nextLong() }
            .parallel()
            .filter { seed: Long -> testRebaseMutations(seed, false) }
            .collect(Collectors.toList())
        println("failed: " + failedSeeds.size)
        failedSeeds shouldBe emptyList()
    }

    @Test
    fun reproduceRebaseMutations() {
        assertFalse(testRebaseMutations(-3024114589288036124L, true))
        assertFalse(testRebaseMutations(1573488352840504194L, true))
        assertFalse(testRebaseMutations(2717362330381213098L, true))
        assertFalse(testRebaseMutations(-7736026003531838642L, true))
        assertFalse(testRebaseMutations(-2276640640846890955L, true))
        assertFalse(testRebaseMutations(-4625731613403327929L, true))
    }

    @Ignore
    @Test
    fun randomizedTestForRebaseMutationsWithOverlappingNDN() {
        val numberOfRuns = 100000
        val failedSeeds = IntStream.range(0, numberOfRuns)
            .mapToObj { ThreadLocalRandom.current().nextLong() }
            .parallel()
            .filter { seed: Long -> testRebaseMutationsWithOverlappingNDN(seed, false) }
            .collect(Collectors.toList())
        println("failed: " + failedSeeds.size)
        failedSeeds shouldBe emptyList()
    }

    @Test
    fun reproduceRebaseMutationsWithOverlappingNDN() {
        assertFalse(testRebaseMutationsWithOverlappingNDN(-457834585042503844L, true))
        assertFalse(testRebaseMutationsWithOverlappingNDN(1409405702313405375L, true))
        assertFalse(testRebaseMutationsWithOverlappingNDN(-819744160537745066L, true))
        assertFalse(testRebaseMutationsWithOverlappingNDN(-8563146941277040334L, true))
        assertFalse(testRebaseMutationsWithOverlappingNDN(2821340284741722462L, true))
        assertFalse(testRebaseMutationsWithOverlappingNDN(1899181973510814170L, true))
    }

    @Test
    fun rebaseMutationsOnRootWithNDNFarInTheRight() {
        val VSequence = oneLetterSequence(T, 50)
        val JSequence = oneLetterSequence(G, 50)
        val clonesRebase = ClonesRebase(
            VSequence,
            AffineGapAlignmentScoring.getNucleotideBLASTScoring(),
            NDNScoring(),
            JSequence,
            AffineGapAlignmentScoring.getNucleotideBLASTScoring()
        )
        val originalNode = MutationsSet(
            VGeneMutations(
                VSequence,
                mapOf(Range(0, 10) to EMPTY_NUCLEOTIDE_MUTATIONS),
                PartInCDR3(Range(10, 15), EMPTY_NUCLEOTIDE_MUTATIONS)
            ),
            NDNMutations(
                oneLetterSequence(N, 5),
                Mutations(NucleotideSequence.ALPHABET, "SN0C,SN1C,SN2C,SN3C,SN4C")
            ),
            JGeneMutations(
                JSequence,
                PartInCDR3(Range(10, 30), EMPTY_NUCLEOTIDE_MUTATIONS),
                mapOf(Range(30, 40) to EMPTY_NUCLEOTIDE_MUTATIONS)
            )
        )
        originalNode.VMutations.buildPartInCDR3() shouldBe oneLetterSequence(T, 5)
        originalNode.NDNMutations.buildSequence() shouldBe oneLetterSequence(C, 5)
        originalNode.JMutations.buildPartInCDR3() shouldBe oneLetterSequence(G, 20)
        val originalRoot = RootInfo(
            Range(10, 15),
            oneLetterSequence(N, 5),
            Range(10, 30),
            VJBase("VSome", "JSome", 20)
        )
        val rebaseTo = RootInfo(
            Range(10, 25),
            oneLetterSequence(N, 10),
            Range(25, 30),
            VJBase("VSome", "JSome", 20)
        )
        val result = clonesRebase.rebaseMutations(
            originalNode,
            originalRoot,
            rebaseTo
        )
        result.VMutations.buildPartInCDR3() shouldBe oneLetterSequence(T, 5)
            .concatenate(oneLetterSequence(C, 5))
            .concatenate(oneLetterSequence(G, 5))

        result.NDNMutations.buildSequence() shouldBe oneLetterSequence(G, 10)
        result.JMutations.buildPartInCDR3() shouldBe oneLetterSequence(G, 5)
    }

    @Test
    fun rebaseMutationsOnRootWithNDNFarInTheLeft() {
        val VSequence = oneLetterSequence(T, 50)
        val JSequence = oneLetterSequence(G, 50)
        val clonesRebase = ClonesRebase(
            VSequence,
            AffineGapAlignmentScoring.getNucleotideBLASTScoring(),
            NDNScoring(),
            JSequence,
            AffineGapAlignmentScoring.getNucleotideBLASTScoring()
        )
        val originalNode = MutationsSet(
            VGeneMutations(
                VSequence,
                mapOf(Range(0, 10) to EMPTY_NUCLEOTIDE_MUTATIONS),
                PartInCDR3(Range(10, 25), EMPTY_NUCLEOTIDE_MUTATIONS)
            ),
            NDNMutations(
                oneLetterSequence(N, 10),
                Mutations(NucleotideSequence.ALPHABET, "SN0C,SN1C,SN2C,SN3C,SN4C,SN5C,SN6C,SN7C,SN8C,SN9C")
            ),
            JGeneMutations(
                JSequence,
                PartInCDR3(Range(25, 30), EMPTY_NUCLEOTIDE_MUTATIONS),
                mapOf(Range(30, 40) to EMPTY_NUCLEOTIDE_MUTATIONS)
            )
        )
        originalNode.VMutations.buildPartInCDR3() shouldBe oneLetterSequence(T, 15)
        originalNode.NDNMutations.buildSequence() shouldBe oneLetterSequence(C, 10)
        originalNode.JMutations.buildPartInCDR3() shouldBe oneLetterSequence(G, 5)
        val originalRoot = RootInfo(
            Range(10, 25),
            oneLetterSequence(N, 10),
            Range(25, 30),
            VJBase("VSome", "JSome", 20)
        )
        val rebaseTo = RootInfo(
            Range(10, 15),
            oneLetterSequence(N, 5),
            Range(10, 30),
            VJBase("VSome", "JSome", 20)
        )
        val result = clonesRebase.rebaseMutations(
            originalNode,
            originalRoot,
            rebaseTo
        )
        result.VMutations.buildPartInCDR3() shouldBe oneLetterSequence(T, 5)
        result.NDNMutations.buildSequence() shouldBe oneLetterSequence(T, 5)
        result.JMutations.buildPartInCDR3() shouldBe oneLetterSequence(T, 5)
            .concatenate(oneLetterSequence(C, 10))
            .concatenate(oneLetterSequence(G, 5))
    }

    private fun oneLetterSequence(letter: Byte, size: Int): NucleotideSequence =
        NucleotideSequence.ALPHABET.createBuilder()
            .also { builder ->
                repeat(size) {
                    builder.append(letter)
                }
            }.createAndDestroy()

    @Ignore
    @Test
    fun randomizedTestForRebaseClone() {
        val numberOfRuns = 100000
        val failedSeeds = IntStream.range(0, numberOfRuns)
            .mapToObj { ThreadLocalRandom.current().nextLong() }
            .parallel()
            .filter { seed: Long -> testRebaseClone(seed, false) }
            .collect(Collectors.toList())
        println("failed: " + failedSeeds.size)
        assertEquals(emptyList<Any>(), failedSeeds)
    }

    @Test
    fun reproduceRebaseClone() {
        assertFalse(testRebaseClone(7238690851096249903L, true))
        assertFalse(testRebaseClone(-6528292659028221478L, true))
        assertFalse(testRebaseClone(-1959168467592812968L, true))
        assertFalse(testRebaseClone(4887507527711339190L, true))
        assertFalse(testRebaseClone(2049978999466120864L, true))
        assertFalse(testRebaseClone(-7534105378312308262L, true))
        assertFalse(testRebaseClone(4510972677298188920L, true))
        assertFalse(testRebaseClone(1729663315728681110L, true))
        assertFalse(testRebaseClone(4608235439778868248L, true))
        assertFalse(testRebaseClone(7155779204574879033L, true))
        assertFalse(testRebaseClone(-4567604316340909864L, true))
        assertFalse(testRebaseClone(7360045022198406917L, true))
        assertFalse(testRebaseClone(8861605449460417460L, true))
        assertFalse(testRebaseClone(3361027404503237374L, true))
        assertFalse(testRebaseClone(5633311090069099492L, true))
        assertFalse(testRebaseClone(-140150437646008446L, true))
        assertFalse(testRebaseClone(-3721882169827128329L, true))
        assertFalse(testRebaseClone(-6639724146754084784L, true))
        assertFalse(testRebaseClone(-154711501619107070L, true))
        assertFalse(testRebaseClone(3991336578395308109L, true))
        assertFalse(testRebaseClone(-5047483764046740699L, true))
        assertFalse(testRebaseClone(49156566332349046L, true))
        assertFalse(testRebaseClone(-6877842382590389599L, true))
        assertFalse(testRebaseClone(5739929328149910349L, true))
        assertFalse(testRebaseClone(7581006658967416418L, true))
        assertFalse(testRebaseClone(5492150036748141135L, true))
        assertFalse(testRebaseClone(8053975088522559753L, true))
        assertFalse(testRebaseClone(8812578697731451467L, true))
    }

    private fun testRebaseMutations(seed: Long, print: Boolean): Boolean {
        return try {
            val random = Random(seed)
            val VSequence = random.generateSequence(50 + random.nextInt(50))
            val VRangeBeforeCDR3Begin = Range(0, 10 + random.nextInt(10)).move(10 + random.nextInt(5))
            val VRangeAfterCDR3Begin = Range(0, random.nextInt(5)).move(VRangeBeforeCDR3Begin.upper)
            val JSequence = random.generateSequence(50 + random.nextInt(50))
            val JRangeBeforeCDR3End = Range(0, random.nextInt(5)).move(10 + random.nextInt(5))
            val JRangeAfterCDR3End = Range(0, 10 + random.nextInt(10)).move(JRangeBeforeCDR3End.upper)
            val VRange = Range(VRangeBeforeCDR3Begin.lower, VRangeAfterCDR3Begin.upper)
            val VMutations = random.generateMutations(VSequence, VRange)
            val JRange = Range(JRangeBeforeCDR3End.lower, JRangeAfterCDR3End.upper)
            val JMutations = random.generateMutations(JSequence, JRange)
            val NDN = random.generateSequence(10 + random.nextInt(10))
            val originalRootInfo = RootInfo(
                VRangeAfterCDR3Begin,
                random.generateSequence(NDN.size() - 3 + random.nextInt(6)),
                JRangeAfterCDR3End,
                VJBase("VSome", "JSome", 20)
            )
            val original = MutationsSet(
                VGeneMutations(
                    VSequence,
                    mapOf(VRangeBeforeCDR3Begin to VMutations.extractAbsoluteMutations(VRangeBeforeCDR3Begin, true)),
                    PartInCDR3(
                        originalRootInfo.VRangeInCDR3,
                        VMutations.extractAbsoluteMutations(originalRootInfo.VRangeInCDR3, false)
                    )
                ),
                NDNMutations(
                    originalRootInfo.reconstructedNDN,
                    Aligner.alignGlobal(
                        AffineGapAlignmentScoring.getNucleotideBLASTScoring(),
                        originalRootInfo.reconstructedNDN,
                        NDN
                    ).absoluteMutations
                ),
                JGeneMutations(
                    JSequence,
                    PartInCDR3(
                        originalRootInfo.JRangeInCDR3,
                        JMutations.extractAbsoluteMutations(originalRootInfo.JRangeInCDR3, false)
                    ),
                    mapOf(JRangeAfterCDR3End to JMutations.extractAbsoluteMutations(JRangeAfterCDR3End, true))
                )
            )
            var VBorderExpand = -2 + random.nextInt(4)
            if (originalRootInfo.VRangeInCDR3.length() + VBorderExpand < 0) {
                VBorderExpand = originalRootInfo.VRangeInCDR3.length()
            }
            var JBorderExpand = -2 + random.nextInt(4)
            if (originalRootInfo.JRangeInCDR3.length() + JBorderExpand < 0) {
                JBorderExpand = originalRootInfo.JRangeInCDR3.length()
            }
            val rebaseToRootInfo = RootInfo(
                originalRootInfo.VRangeInCDR3.expand(0, VBorderExpand),
                random.generateSequence(originalRootInfo.reconstructedNDN.size() - VBorderExpand - JBorderExpand),
                originalRootInfo.JRangeInCDR3.expand(JBorderExpand, 0),
                VJBase("VSome", "JSome", 20)
            )
            val clonesRebase = ClonesRebase(
                VSequence,
                AffineGapAlignmentScoring.getNucleotideBLASTScoring(),
                NDNScoring(),
                JSequence,
                AffineGapAlignmentScoring.getNucleotideBLASTScoring()
            )
            val result = clonesRebase.rebaseMutations(original, originalRootInfo, rebaseToRootInfo)
            if (print) {
                println(" original rootInfo: $originalRootInfo")
                println("rebase to rootInfo: $rebaseToRootInfo")
                println(
                    "original CDR3: "
                        + original.VMutations.buildPartInCDR3()
                        + " " + original.NDNMutations.buildSequence()
                        + " " + original.JMutations.buildPartInCDR3()
                )
                println(
                    "  result CDR3: "
                        + result.VMutations.buildPartInCDR3()
                        + " " + result.NDNMutations.buildSequence()
                        + " " + result.JMutations.buildPartInCDR3()
                )
            }
            assertEquals(
                original.VMutations.mutations.values,
                result.VMutations.mutations.values
            )
            assertEquals(buildCDR3(original), buildCDR3(result))
            assertEquals(
                original.JMutations.mutations.values,
                result.JMutations.mutations.values
            )
            assertEquals(rebaseToRootInfo.VRangeInCDR3, result.VMutations.partInCDR3.range)
            assertEquals(rebaseToRootInfo.reconstructedNDN, result.NDNMutations.base)
            assertEquals(rebaseToRootInfo.JRangeInCDR3, result.JMutations.partInCDR3.range)
            false
        } catch (e: Throwable) {
            if (print) {
                e.printStackTrace()
            }
            true
        }
    }

    private fun testRebaseMutationsWithOverlappingNDN(seed: Long, print: Boolean): Boolean {
        return try {
            val random = Random(seed)
            val VSequence = random.generateSequence(50)
            val VMutations = random.generateMutations(VSequence)
            val VRangeBeforeCDR3Begin = Range(0, 10)
            val JSequence = random.generateSequence(50)
            val JMutations = random.generateMutations(JSequence)
            val JRangeAfterCDR3End = Range(40, 50)

            val CDR3Length = 20
            val offset = 5
            val NDNRangeInCDR3Before = (random.nextInt(CDR3Length - offset)).let { left ->
                Range(min(offset, left), random.nextInt(left, CDR3Length))
            }
            val originalRootInfo = RootInfo(
                Range(10, 10 + NDNRangeInCDR3Before.lower),
                oneLetterSequence(N, NDNRangeInCDR3Before.length()),
                Range(JRangeAfterCDR3End.lower - CDR3Length + NDNRangeInCDR3Before.upper, JRangeAfterCDR3End.lower),
                VJBase("VSome", "JSome", CDR3Length)
            )
            val original = MutationsSet(
                VGeneMutations(
                    VSequence,
                    mapOf(VRangeBeforeCDR3Begin to VMutations.extractAbsoluteMutations(VRangeBeforeCDR3Begin, true)),
                    PartInCDR3(
                        originalRootInfo.VRangeInCDR3,
                        VMutations.extractAbsoluteMutations(originalRootInfo.VRangeInCDR3, false)
                    )
                ),
                NDNMutations(
                    originalRootInfo.reconstructedNDN,
                    Aligner.alignGlobal(
                        AffineGapAlignmentScoring.getNucleotideBLASTScoring(),
                        originalRootInfo.reconstructedNDN,
                        random.generateSequence(NDNRangeInCDR3Before.length())
                    ).absoluteMutations
                ),
                JGeneMutations(
                    JSequence,
                    PartInCDR3(
                        originalRootInfo.JRangeInCDR3,
                        JMutations.extractAbsoluteMutations(originalRootInfo.JRangeInCDR3, false)
                    ),
                    mapOf(JRangeAfterCDR3End to JMutations.extractAbsoluteMutations(JRangeAfterCDR3End, true))
                )
            )

            val NDNRangeInCDR3After = (random.nextInt(CDR3Length - offset)).let { left ->
                Range(min(offset, left), random.nextInt(left, CDR3Length))
            }
            val rebaseToRootInfo = RootInfo(
                Range(10, 10 + NDNRangeInCDR3After.lower),
                oneLetterSequence(N, NDNRangeInCDR3After.length()),
                Range(JRangeAfterCDR3End.lower - CDR3Length + NDNRangeInCDR3After.upper, JRangeAfterCDR3End.lower),
                VJBase("VSome", "JSome", CDR3Length)
            )
            if (print) {
                println(" original rootInfo: $originalRootInfo")
                println("rebase to rootInfo: $rebaseToRootInfo")
                println(
                    "original CDR3: "
                        + original.VMutations.buildPartInCDR3()
                        + " " + original.NDNMutations.buildSequence()
                        + " " + original.JMutations.buildPartInCDR3()
                )
            }
            val clonesRebase = ClonesRebase(
                VSequence,
                AffineGapAlignmentScoring.getNucleotideBLASTScoring(),
                NDNScoring(),
                JSequence,
                AffineGapAlignmentScoring.getNucleotideBLASTScoring()
            )
            val result = clonesRebase.rebaseMutations(original, originalRootInfo, rebaseToRootInfo)
            if (print) {
                println(
                    "  result CDR3: "
                        + result.VMutations.buildPartInCDR3()
                        + " " + result.NDNMutations.buildSequence()
                        + " " + result.JMutations.buildPartInCDR3()
                )
            }
            assertEquals(
                original.VMutations.mutations.values,
                result.VMutations.mutations.values
            )
            assertEquals(buildCDR3(original), buildCDR3(result))
            assertEquals(
                original.JMutations.mutations.values,
                result.JMutations.mutations.values
            )
            assertEquals(rebaseToRootInfo.VRangeInCDR3, result.VMutations.partInCDR3.range)
            assertEquals(rebaseToRootInfo.reconstructedNDN, result.NDNMutations.base)
            assertEquals(rebaseToRootInfo.JRangeInCDR3, result.JMutations.partInCDR3.range)
            false
        } catch (e: Throwable) {
            if (print) {
                e.printStackTrace()
            }
            true
        }
    }

    private fun buildCDR3(original: MutationsSet): NucleotideSequence = original.VMutations.buildPartInCDR3()
        .concatenate(original.NDNMutations.buildSequence())
        .concatenate(original.JMutations.buildPartInCDR3())

    private fun testRebaseClone(seed: Long, print: Boolean): Boolean = try {
        val random = Random(seed)
        val VSequence = random.generateSequence(50 + random.nextInt(50))
        val VRangeBeforeCDR3Begin = Range(0, 10 + random.nextInt(10)).move(10 + random.nextInt(5))
        val VRangeAfterCDR3Begin = Range(0, random.nextInt(5)).move(VRangeBeforeCDR3Begin.upper)
        val NDN = random.generateSequence(10 + random.nextInt(15))
        val JSequence = random.generateSequence(50 + random.nextInt(50))
        val JRangeBeforeCDR3End = Range(0, random.nextInt(5)).move(10 + random.nextInt(5))
        val JRangeAfterCDR3End = Range(0, 10 + random.nextInt(10)).move(JRangeBeforeCDR3End.upper)
        val VRange = Range(VRangeBeforeCDR3Begin.lower, VRangeAfterCDR3Begin.upper)
        val VMutations = random.generateMutations(VSequence, VRange)
        val JRange = Range(JRangeBeforeCDR3End.lower, JRangeAfterCDR3End.upper)
        val JMutations = random.generateMutations(JSequence, JRange)
        val VSequenceInCDR3 = buildSequence(VSequence, VMutations, VRangeAfterCDR3Begin, false)
        val JSequenceInCDR3 = buildSequence(JSequence, JMutations, JRangeBeforeCDR3End, true)
        val commonVRangeInCDR3 = Range(
            0,
            if (VRangeAfterCDR3Begin.length() == 0) 0 else random.nextInt(VRangeAfterCDR3Begin.length())
        ).move(VRangeAfterCDR3Begin.lower)
        val commonJRangeInCDR3 = Range(
            if (JRangeBeforeCDR3End.length() == 0) 0 else -random.nextInt(JRangeBeforeCDR3End.length()),
            0
        ).move(JRangeBeforeCDR3End.upper)
        val CDR3 = NucleotideSequence.ALPHABET.createBuilder()
            .append(VSequenceInCDR3)
            .append(NDN)
            .append(JSequenceInCDR3)
            .createAndDestroy()
        val VRangeInCDR3 = Range(0, commonVRangeInCDR3.length() + random.nextInt(5))
            .move(VRangeBeforeCDR3Begin.upper)
        val JRangeInCDR3 = Range(-(commonJRangeInCDR3.length() + random.nextInt(5)), 0)
            .move(JRangeBeforeCDR3End.upper)
        val VPartInCDR3 = buildSequence(VSequence, VMutations, VRangeInCDR3, false)
        val JPartInCDR3 = buildSequence(JSequence, JMutations, JRangeInCDR3, true)
        val NDNSubsetRangeBeforeMutation = Range(
            VPartInCDR3.size(),
            CDR3.size() - JPartInCDR3.size()
        )
        val NDNSubsetBeforeMutation = CDR3.getRange(NDNSubsetRangeBeforeMutation)
        val mutationsOfNDN: Mutations<NucleotideSequence> = random.generateMutations(NDNSubsetBeforeMutation)
        val rootInfo = RootInfo(
            VRangeInCDR3,
            mutationsOfNDN.mutate(NDNSubsetBeforeMutation),
            JRangeInCDR3,
            VJBase("VSome", "JSome", 20)
        )
        val builtClone: NucleotideSequence = NucleotideSequence.ALPHABET.createBuilder()
            .append(buildSequence(VSequence, VMutations, VRangeBeforeCDR3Begin, true))
            .append(VSequenceInCDR3)
            .append(NDN)
            .append(JSequenceInCDR3)
            .append(buildSequence(JSequence, JMutations, JRangeAfterCDR3End, false))
            .createAndDestroy()
        val VGeneMutations = VGeneMutations(
            VSequence,
            mapOf(VRangeBeforeCDR3Begin to VMutations.extractAbsoluteMutations(VRangeBeforeCDR3Begin, true)),
            PartInCDR3(
                commonVRangeInCDR3,
                VMutations.extractAbsoluteMutations(commonVRangeInCDR3, false)
            )
        )
        val JGeneMutations = JGeneMutations(
            JSequence,
            PartInCDR3(
                commonJRangeInCDR3,
                JMutations.extractAbsoluteMutations(commonJRangeInCDR3, true)
            ),
            mapOf(JRangeAfterCDR3End to JMutations.extractAbsoluteMutations(JRangeAfterCDR3End, false))
        )
        val mutationsFromVJGermline = MutationsFromVJGermline(
            VGeneMutations,
            Range(commonVRangeInCDR3.upper, VRangeAfterCDR3Begin.upper).let { range ->
                VMutations.extractAbsoluteMutations(range, false) to range
            },
            NucleotideSequence.ALPHABET.createBuilder()
                .append(
                    buildSequence(
                        VSequence,
                        VMutations,
                        Range(commonVRangeInCDR3.upper, VRangeAfterCDR3Begin.upper),
                        false
                    )
                )
                .append(NDN)
                .append(
                    buildSequence(
                        JSequence,
                        JMutations,
                        Range(JRangeBeforeCDR3End.lower, commonJRangeInCDR3.lower),
                        true
                    )
                )
                .createAndDestroy(),
            Range(JRangeBeforeCDR3End.lower, commonJRangeInCDR3.lower).let { range ->
                JMutations.extractAbsoluteMutations(range, true) to range
            },
            JGeneMutations
        )
        val rebasedClone = ClonesRebase(
            VSequence,
            AffineGapAlignmentScoring.getNucleotideBLASTScoring(),
            AffineGapAlignmentScoring.getNucleotideBLASTScoring(),
            JSequence,
            AffineGapAlignmentScoring.getNucleotideBLASTScoring()
        ).rebaseClone(
            rootInfo,
            mutationsFromVJGermline,
            CloneWrapper(
                Clone(emptyArray(), Maps.newEnumMap(GeneType::class.java), null, 0.0, 0, 0),
                0,
                VJBase("BGeneName", "JGeneName", 20)
            )
        )
        val resultedNDN = rebasedClone.mutationsSet.NDNMutations.buildSequence()

        val resultSequenceBuilder = NucleotideSequence.ALPHABET.createBuilder()
        rebasedClone.mutationsSet.VMutations.mutations.map { (range, mutations) ->
            buildSequence(
                rebasedClone.mutationsSet.VMutations.sequence1,
                mutations,
                range
            )
        }
            .forEach { resultSequenceBuilder.append(it) }
        val CDR3Begin = resultSequenceBuilder.size()
        resultSequenceBuilder.append(rebasedClone.mutationsSet.VMutations.buildPartInCDR3())
        resultSequenceBuilder.append(resultedNDN)
        resultSequenceBuilder.append(rebasedClone.mutationsSet.JMutations.buildPartInCDR3())
        val CDR3End = resultSequenceBuilder.size()
        rebasedClone.mutationsSet.JMutations.mutations.map { (range, mutations) ->
            buildSequence(
                rebasedClone.mutationsSet.JMutations.sequence1,
                mutations,
                range
            )
        }
            .forEach { resultSequenceBuilder.append(it) }
        val resultSequence = resultSequenceBuilder.createAndDestroy()
        var VPartLeftInRootRange = VRangeAfterCDR3Begin.intersection(rootInfo.VRangeInCDR3)
        VPartLeftInRootRange = VPartLeftInRootRange ?: Range(VRangeAfterCDR3Begin.lower, VRangeAfterCDR3Begin.lower)
        val VPartLeftInRoot = buildSequence(VSequence, VMutations, VPartLeftInRootRange, false)
        var JPartLeftInRootRange = JRangeBeforeCDR3End.intersection(rootInfo.JRangeInCDR3)
        JPartLeftInRootRange = JPartLeftInRootRange ?: Range(JRangeBeforeCDR3End.upper, JRangeBeforeCDR3End.upper)
        val JPartLeftInRoot = buildSequence(JSequence, JMutations, JPartLeftInRootRange, true)
        val VPartGotFromNDNRange = Range(
            VPartLeftInRootRange.length(),
            rootInfo.VRangeInCDR3.length()
        ).move(VPartLeftInRoot.size() - VPartLeftInRootRange.length())
        val JPartGotFromNDNRange = Range(
            CDR3.size() - rootInfo.JRangeInCDR3.length(),
            CDR3.size() - JPartLeftInRootRange.length()
        ).move(JPartLeftInRootRange.length() - JPartLeftInRoot.size())
        val VPartGotFromNDN =
            if (VPartGotFromNDNRange.isReverse) NucleotideSequence("") else CDR3.getRange(VPartGotFromNDNRange)
        val JPartGotFromNDN =
            if (JPartGotFromNDNRange.isReverse) NucleotideSequence("") else CDR3.getRange(JPartGotFromNDNRange)
        if (print) {
            println("original: $builtClone")
            println("  result: $resultSequence")
            println()
            println(
                "   original with marking: "
                    + buildSequence(VSequence, VMutations, VRangeBeforeCDR3Begin, true)
                    + " "
                    + VSequenceInCDR3
                    + " "
                    + NDN
                    + " "
                    + JSequenceInCDR3
                    + " "
                    + buildSequence(JSequence, JMutations, JRangeAfterCDR3End, false)
            )
            println("     result with marking: "
                + rebasedClone.mutationsSet.VMutations.mutations.entries
                .joinToString(" ") { (range, mutations) ->
                    buildSequence(
                        rebasedClone.mutationsSet.VMutations.sequence1,
                        mutations,
                        range
                    ).toString()
                }
                + " "
                + rebasedClone.mutationsSet.VMutations.buildPartInCDR3()
                + " "
                + resultedNDN
                + " "
                + rebasedClone.mutationsSet.JMutations.buildPartInCDR3()
                + " "
                + rebasedClone.mutationsSet.JMutations.mutations.entries
                .joinToString(" ") { (range, mutations) ->
                    buildSequence(
                        rebasedClone.mutationsSet.JMutations.sequence1,
                        mutations,
                        range
                    ).toString()
                }
            )
            println(
                "root mutated in germline: "
                    + buildSequence(VSequence, VMutations, VRangeBeforeCDR3Begin, true)
                    + " "
                    + VPartLeftInRoot
                    + " "
                    + VPartGotFromNDN
                    + " "
                    + rootInfo.reconstructedNDN
                    + " "
                    + JPartGotFromNDN
                    + " "
                    + JPartLeftInRoot
                    + " "
                    + buildSequence(JSequence, JMutations, JRangeAfterCDR3End, false)
            )
            println(
                "          rebase on root: "
                    + buildSequence(
                    VSequence,
                    EMPTY_NUCLEOTIDE_MUTATIONS,
                    VRangeBeforeCDR3Begin,
                    true
                )
                    + " "
                    + buildSequence(
                    VSequence,
                    EMPTY_NUCLEOTIDE_MUTATIONS,
                    rootInfo.VRangeInCDR3,
                    false
                )
                    + " "
                    + rootInfo.reconstructedNDN
                    + " "
                    + buildSequence(
                    JSequence,
                    EMPTY_NUCLEOTIDE_MUTATIONS,
                    rootInfo.JRangeInCDR3,
                    true
                )
                    + " "
                    + buildSequence(
                    JSequence,
                    EMPTY_NUCLEOTIDE_MUTATIONS,
                    JRangeAfterCDR3End,
                    false
                )
            )
            println()
            println("original CDR3: $CDR3")
            println(
                "  result CDR3: " + (resultSequence as Seq<NucleotideSequence>).getRange(CDR3Begin, CDR3End)
            )
            println()
            println("     original NDN: $NDN")
            println("      mutated NDN: ${rootInfo.reconstructedNDN}")
            println("    subset of NDN: $NDNSubsetBeforeMutation")
            println("mutated from root: $resultedNDN")
            println()
        }
        assertEquals(
            rootInfo.VRangeInCDR3.lower,
            rebasedClone.mutationsSet.VMutations.mutations.keys.maxOf { it.upper })
        assertEquals(rootInfo.VRangeInCDR3, rebasedClone.mutationsSet.VMutations.partInCDR3.range)
        assertEquals(
            rootInfo.JRangeInCDR3.upper,
            rebasedClone.mutationsSet.JMutations.mutations.keys.minOf { it.lower })
        assertEquals(rootInfo.JRangeInCDR3, rebasedClone.mutationsSet.JMutations.partInCDR3.range)
        assertEquals(CDR3, resultSequence.getRange(CDR3Begin, CDR3End))
        assertEquals(builtClone, resultSequence)
        assertEquals(
            VPartLeftInRoot.concatenate(VPartGotFromNDN),
            rebasedClone.mutationsSet.VMutations.buildPartInCDR3()
        )
        assertEquals(NDNSubsetBeforeMutation, resultedNDN)
        assertEquals(
            JPartGotFromNDN.concatenate(JPartLeftInRoot),
            rebasedClone.mutationsSet.JMutations.buildPartInCDR3()
        )
        false
    } catch (e: Throwable) {
        if (print) {
            e.printStackTrace()
        }
        true
    }
}
