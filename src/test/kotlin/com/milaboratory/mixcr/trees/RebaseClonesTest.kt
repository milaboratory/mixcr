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
@file:Suppress("LocalVariableName")

package com.milaboratory.mixcr.trees

import com.google.common.collect.Maps
import com.milaboratory.core.Range
import com.milaboratory.core.alignment.AffineGapAlignmentScoring
import com.milaboratory.core.alignment.Aligner
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.mutations.Mutations.EMPTY_NUCLEOTIDE_MUTATIONS
import com.milaboratory.core.sequence.NucleotideAlphabet.*
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.core.sequence.Seq
import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.trees.MutationsUtils.NDNScoring
import com.milaboratory.mixcr.trees.MutationsUtils.buildSequence
import com.milaboratory.mixcr.util.RandomizedTest
import com.milaboratory.mixcr.util.extractAbsoluteMutations
import com.milaboratory.mixcr.util.plus
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.repseq.core.GeneFeature.FR3
import io.repseq.core.GeneFeature.FR4
import io.repseq.core.GeneType
import io.repseq.core.ReferencePoint.*
import io.repseq.core.ReferencePointsBuilder
import io.repseq.core.VDJCGeneId
import io.repseq.core.VDJCLibraryId
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.Integer.min
import kotlin.random.Random

class RebaseClonesTest {
    private val scoringSet = ScoringSet(
        AffineGapAlignmentScoring.getNucleotideBLASTScoring(),
        NDNScoring(),
        AffineGapAlignmentScoring.getNucleotideBLASTScoring()
    )

    private val vdjcLibraryId = VDJCLibraryId("human", 0)

    @Test
    fun randomizedTestForRebaseMutations() {
        RandomizedTest.randomized(::testRebaseMutations, numberOfRuns = 100000)
    }

    @Test
    fun reproduceRebaseMutations() {
        RandomizedTest.reproduce(
            ::testRebaseMutations,
            -3024114589288036124L,
            1573488352840504194L,
            2717362330381213098L,
            -7736026003531838642L,
            -2276640640846890955L,
            -4625731613403327929L
        )
    }

    @Test
    fun randomizedTestForRebaseMutationsWithOverlappingNDN() {
        RandomizedTest.randomized(::testRebaseMutationsWithOverlappingNDN, numberOfRuns = 100000)
    }

    @Test
    fun reproduceRebaseMutationsWithOverlappingNDN() {
        RandomizedTest.reproduce(
            ::testRebaseMutationsWithOverlappingNDN,
            -457834585042503844L,
            1409405702313405375L,
            -819744160537745066L,
            -8563146941277040334L,
            2821340284741722462L,
            1899181973510814170L,
        )
    }

    @Test
    fun rebaseMutationsOnRootWithNDNFarInTheRight() {
        val VSequence = oneLetterSequence(T, 50)
        val JSequence = oneLetterSequence(G, 50)
        val clonesRebase = ClonesRebase(
            VSequence,
            JSequence,
            scoringSet
        )
        val originalNode = MutationsSet(
            VGeneMutations(
                mapOf(FR3 to EMPTY_NUCLEOTIDE_MUTATIONS),
                PartInCDR3(Range(10, 15), EMPTY_NUCLEOTIDE_MUTATIONS)
            ),
            NDNMutations(
                Mutations(NucleotideSequence.ALPHABET, "SN0C,SN1C,SN2C,SN3C,SN4C")
            ),
            JGeneMutations(
                PartInCDR3(Range(10, 30), EMPTY_NUCLEOTIDE_MUTATIONS),
                mapOf(FR4 to EMPTY_NUCLEOTIDE_MUTATIONS)
            )
        )
        val VPartitioning = ReferencePointsBuilder().apply {
            setPosition(FR3Begin, 0)
            setPosition(FR3End, 10)
            setPosition(VEnd, 50)
        }.build()
        val JPartitioning = ReferencePointsBuilder().apply {
            setPosition(JBegin, 0)
            setPosition(FR4Begin, 30)
            setPosition(FR4End, 40)
        }.build()
        val originalRoot = RootInfo(
            VSequence,
            VPartitioning,
            listOf(FR3),
            Range(10, 15),
            oneLetterSequence(N, 5),
            JSequence,
            JPartitioning,
            listOf(FR4),
            Range(10, 30),
            VJBase(VDJCGeneId(vdjcLibraryId, "VSome"), VDJCGeneId(vdjcLibraryId, "JSome"), 20)
        )
        originalNode.VMutations.buildPartInCDR3(originalRoot) shouldBe oneLetterSequence(T, 5)
        originalNode.NDNMutations.buildSequence(originalRoot) shouldBe oneLetterSequence(C, 5)
        originalNode.JMutations.buildPartInCDR3(originalRoot) shouldBe oneLetterSequence(G, 20)
        val rebaseTo = RootInfo(
            VSequence,
            VPartitioning,
            listOf(FR3),
            Range(10, 25),
            oneLetterSequence(N, 10),
            JSequence,
            JPartitioning,
            listOf(FR4),
            Range(25, 30),
            VJBase(VDJCGeneId(vdjcLibraryId, "VSome"), VDJCGeneId(vdjcLibraryId, "JSome"), 20)
        )
        val result = clonesRebase.rebaseMutations(
            originalNode,
            originalRoot,
            rebaseTo
        )
        result.VMutations.buildPartInCDR3(rebaseTo) shouldBe oneLetterSequence(T, 5)
            .concatenate(oneLetterSequence(C, 5))
            .concatenate(oneLetterSequence(G, 5))

        result.NDNMutations.buildSequence(rebaseTo) shouldBe oneLetterSequence(G, 10)
        result.JMutations.buildPartInCDR3(rebaseTo) shouldBe oneLetterSequence(G, 5)
    }

    @Test
    fun rebaseMutationsOnRootWithNDNFarInTheLeft() {
        val VSequence = oneLetterSequence(T, 50)
        val JSequence = oneLetterSequence(G, 50)
        val clonesRebase = ClonesRebase(
            VSequence,
            JSequence,
            scoringSet
        )
        val originalNode = MutationsSet(
            VGeneMutations(
                mapOf(FR3 to EMPTY_NUCLEOTIDE_MUTATIONS),
                PartInCDR3(Range(10, 25), EMPTY_NUCLEOTIDE_MUTATIONS)
            ),
            NDNMutations(
                Mutations(NucleotideSequence.ALPHABET, "SN0C,SN1C,SN2C,SN3C,SN4C,SN5C,SN6C,SN7C,SN8C,SN9C")
            ),
            JGeneMutations(
                PartInCDR3(Range(25, 30), EMPTY_NUCLEOTIDE_MUTATIONS),
                mapOf(FR4 to EMPTY_NUCLEOTIDE_MUTATIONS)
            )
        )
        val VPartitioning = ReferencePointsBuilder().apply {
            setPosition(FR3Begin, 0)
            setPosition(FR3End, 10)
            setPosition(VEnd, 50)
        }.build()
        val JPartitioning = ReferencePointsBuilder().apply {
            setPosition(JBegin, 0)
            setPosition(FR4Begin, 30)
            setPosition(FR4End, 40)
        }.build()

        val originalRoot = RootInfo(
            VSequence,
            VPartitioning,
            listOf(FR3),
            Range(10, 25),
            oneLetterSequence(N, 10),
            JSequence,
            JPartitioning,
            listOf(FR4),
            Range(25, 30),
            VJBase(VDJCGeneId(vdjcLibraryId, "VSome"), VDJCGeneId(vdjcLibraryId, "JSome"), 20)
        )
        originalNode.VMutations.buildPartInCDR3(originalRoot) shouldBe oneLetterSequence(T, 15)
        originalNode.NDNMutations.buildSequence(originalRoot) shouldBe oneLetterSequence(C, 10)
        originalNode.JMutations.buildPartInCDR3(originalRoot) shouldBe oneLetterSequence(G, 5)
        val rebaseTo = RootInfo(
            VSequence,
            VPartitioning,
            listOf(FR3),
            Range(10, 15),
            oneLetterSequence(N, 5),
            JSequence,
            JPartitioning,
            listOf(FR4),
            Range(10, 30),
            VJBase(VDJCGeneId(vdjcLibraryId, "VSome"), VDJCGeneId(vdjcLibraryId, "JSome"), 20)
        )
        val result = clonesRebase.rebaseMutations(
            originalNode,
            originalRoot,
            rebaseTo
        )
        result.VMutations.buildPartInCDR3(rebaseTo) shouldBe oneLetterSequence(T, 5)
        result.NDNMutations.buildSequence(rebaseTo) shouldBe oneLetterSequence(T, 5)
        result.JMutations.buildPartInCDR3(rebaseTo) shouldBe oneLetterSequence(T, 5)
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

    @Test
    fun randomizedTestForRebaseClone() {
        RandomizedTest.randomized(::testRebaseClone, numberOfRuns = 100000)
    }

    @Test
    fun reproduceRebaseClone() {
        RandomizedTest.reproduce(
            ::testRebaseClone,
            7238690851096249903L,
            -6528292659028221478L,
            -1959168467592812968L,
            4887507527711339190L,
            2049978999466120864L,
            -7534105378312308262L,
            4510972677298188920L,
            1729663315728681110L,
            4608235439778868248L,
            7155779204574879033L,
            -4567604316340909864L,
            7360045022198406917L,
            8861605449460417460L,
            3361027404503237374L,
            5633311090069099492L,
            -140150437646008446L,
            -3721882169827128329L,
            -6639724146754084784L,
            -154711501619107070L,
            3991336578395308109L,
            -5047483764046740699L,
            49156566332349046L,
            -6877842382590389599L,
            5739929328149910349L,
            7581006658967416418L,
            5492150036748141135L,
            8053975088522559753L,
            8812578697731451467L,
        )
    }

    private fun testRebaseMutations(random: Random, print: Boolean) {
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
        val VPartitioning = ReferencePointsBuilder().apply {
            setPosition(FR3Begin, VRangeBeforeCDR3Begin.lower)
            setPosition(FR3End, VRangeBeforeCDR3Begin.upper)
            setPosition(VEnd, VRangeAfterCDR3Begin.upper)
        }.build()
        val JPartitioning = ReferencePointsBuilder().apply {
            setPosition(JBegin, JRangeBeforeCDR3End.lower)
            setPosition(FR4Begin, JRangeAfterCDR3End.lower)
            setPosition(FR4End, JRangeAfterCDR3End.upper)
        }.build()
        val originalRootInfo = RootInfo(
            VSequence,
            VPartitioning,
            listOf(FR3),
            VRangeAfterCDR3Begin,
            random.generateSequence(NDN.size() - 3 + random.nextInt(6)),
            JSequence,
            JPartitioning,
            listOf(FR4),
            JRangeBeforeCDR3End,
            VJBase(VDJCGeneId(vdjcLibraryId, "VSome"), VDJCGeneId(vdjcLibraryId, "JSome"), 20)
        )
        val original = MutationsSet(
            VGeneMutations(
                mapOf(FR3 to VMutations.extractAbsoluteMutations(VRangeBeforeCDR3Begin, true)),
                PartInCDR3(
                    originalRootInfo.VRangeInCDR3,
                    VMutations.extractAbsoluteMutations(originalRootInfo.VRangeInCDR3, false)
                )
            ),
            NDNMutations(
                Aligner.alignGlobal(
                    AffineGapAlignmentScoring.getNucleotideBLASTScoring(),
                    originalRootInfo.reconstructedNDN,
                    NDN
                ).absoluteMutations
            ),
            JGeneMutations(
                PartInCDR3(
                    originalRootInfo.JRangeInCDR3,
                    JMutations.extractAbsoluteMutations(originalRootInfo.JRangeInCDR3, false)
                ),
                mapOf(FR4 to JMutations.extractAbsoluteMutations(JRangeAfterCDR3End, true))
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
            VSequence,
            VPartitioning,
            listOf(FR3),
            originalRootInfo.VRangeInCDR3.expand(0, VBorderExpand),
            random.generateSequence(originalRootInfo.reconstructedNDN.size() - VBorderExpand - JBorderExpand),
            JSequence,
            JPartitioning,
            listOf(FR4),
            originalRootInfo.JRangeInCDR3.expand(JBorderExpand, 0),
            VJBase(VDJCGeneId(vdjcLibraryId, "VSome"), VDJCGeneId(vdjcLibraryId, "JSome"), 20)
        )
        val clonesRebase = ClonesRebase(
            VSequence,
            JSequence,
            scoringSet
        )
        val result = clonesRebase.rebaseMutations(original, originalRootInfo, rebaseToRootInfo)
        if (print) {
            println(" original rootInfo: $originalRootInfo")
            println("rebase to rootInfo: $rebaseToRootInfo")
            println(
                "original CDR3: "
                        + original.VMutations.buildPartInCDR3(originalRootInfo)
                        + " " + original.NDNMutations.buildSequence(originalRootInfo)
                        + " " + original.JMutations.buildPartInCDR3(originalRootInfo)
            )
            println(
                "  result CDR3: "
                        + result.VMutations.buildPartInCDR3(rebaseToRootInfo)
                        + " " + result.NDNMutations.buildSequence(rebaseToRootInfo)
                        + " " + result.JMutations.buildPartInCDR3(rebaseToRootInfo)
            )
        }
        original.VMutations.mutations.values shouldContainExactly result.VMutations.mutations.values
        original.buildCDR3(originalRootInfo) shouldBe result.buildCDR3(rebaseToRootInfo)
        original.JMutations.mutations.values shouldContainExactly result.JMutations.mutations.values
        rebaseToRootInfo.VRangeInCDR3 shouldBe result.VMutations.partInCDR3.range
        rebaseToRootInfo.JRangeInCDR3 shouldBe result.JMutations.partInCDR3.range
    }

    private fun testRebaseMutationsWithOverlappingNDN(random: Random, print: Boolean) {
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
        val VPartitioning = ReferencePointsBuilder().apply {
            setPosition(FR3Begin, VRangeBeforeCDR3Begin.lower)
            setPosition(FR3End, VRangeBeforeCDR3Begin.upper)
            setPosition(VEnd, 50)
        }.build()
        val JPartitioning = ReferencePointsBuilder().apply {
            setPosition(JBegin, 0)
            setPosition(FR4Begin, JRangeAfterCDR3End.lower)
            setPosition(FR4End, JRangeAfterCDR3End.upper)
        }.build()
        val originalRootInfo = RootInfo(
            VSequence,
            VPartitioning,
            listOf(FR3),
            Range(10, 10 + NDNRangeInCDR3Before.lower),
            oneLetterSequence(N, NDNRangeInCDR3Before.length()),
            JSequence,
            JPartitioning,
            listOf(FR4),
            Range(JRangeAfterCDR3End.lower - CDR3Length + NDNRangeInCDR3Before.upper, JRangeAfterCDR3End.lower),
            VJBase(VDJCGeneId(vdjcLibraryId, "VSome"), VDJCGeneId(vdjcLibraryId, "JSome"), CDR3Length)
        )
        val original = MutationsSet(
            VGeneMutations(
                mapOf(FR3 to VMutations.extractAbsoluteMutations(VRangeBeforeCDR3Begin, true)),
                PartInCDR3(
                    originalRootInfo.VRangeInCDR3,
                    VMutations.extractAbsoluteMutations(originalRootInfo.VRangeInCDR3, false)
                )
            ),
            NDNMutations(
                Aligner.alignGlobal(
                    AffineGapAlignmentScoring.getNucleotideBLASTScoring(),
                    originalRootInfo.reconstructedNDN,
                    random.generateSequence(NDNRangeInCDR3Before.length())
                ).absoluteMutations
            ),
            JGeneMutations(
                PartInCDR3(
                    originalRootInfo.JRangeInCDR3,
                    JMutations.extractAbsoluteMutations(originalRootInfo.JRangeInCDR3, false)
                ),
                mapOf(FR4 to JMutations.extractAbsoluteMutations(JRangeAfterCDR3End, true))
            )
        )

        val NDNRangeInCDR3After = (random.nextInt(CDR3Length - offset)).let { left ->
            Range(min(offset, left), random.nextInt(left, CDR3Length))
        }
        val rebaseToRootInfo = RootInfo(
            VSequence,
            VPartitioning,
            listOf(FR3),
            Range(10, 10 + NDNRangeInCDR3After.lower),
            oneLetterSequence(N, NDNRangeInCDR3After.length()),
            JSequence,
            JPartitioning,
            listOf(FR4),
            Range(JRangeAfterCDR3End.lower - CDR3Length + NDNRangeInCDR3After.upper, JRangeAfterCDR3End.lower),
            VJBase(VDJCGeneId(vdjcLibraryId, "VSome"), VDJCGeneId(vdjcLibraryId, "JSome"), CDR3Length)
        )
        if (print) {
            println(" original rootInfo: $originalRootInfo")
            println("rebase to rootInfo: $rebaseToRootInfo")
            println(
                "original CDR3: "
                        + original.VMutations.buildPartInCDR3(originalRootInfo)
                        + " " + original.NDNMutations.buildSequence(originalRootInfo)
                        + " " + original.JMutations.buildPartInCDR3(originalRootInfo)
            )
        }
        val clonesRebase = ClonesRebase(
            VSequence,
            JSequence,
            scoringSet
        )
        val result = clonesRebase.rebaseMutations(original, originalRootInfo, rebaseToRootInfo)
        if (print) {
            println(
                "  result CDR3: "
                        + result.VMutations.buildPartInCDR3(rebaseToRootInfo)
                        + " " + result.NDNMutations.buildSequence(rebaseToRootInfo)
                        + " " + result.JMutations.buildPartInCDR3(rebaseToRootInfo)
            )
        }
        original.VMutations.mutations.values shouldContainExactly result.VMutations.mutations.values
        original.buildCDR3(originalRootInfo) shouldBe result.buildCDR3(rebaseToRootInfo)
        original.JMutations.mutations.values shouldContainExactly result.JMutations.mutations.values
        rebaseToRootInfo.VRangeInCDR3 shouldBe result.VMutations.partInCDR3.range
        rebaseToRootInfo.JRangeInCDR3 shouldBe result.JMutations.partInCDR3.range
    }

    private fun MutationsSet.buildCDR3(rootInfo: RootInfo): NucleotideSequence = VMutations.buildPartInCDR3(rootInfo)
        .concatenate(NDNMutations.buildSequence(rootInfo))
        .concatenate(JMutations.buildPartInCDR3(rootInfo))

    private fun testRebaseClone(random: Random, print: Boolean) {
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
        val VPartitioning = ReferencePointsBuilder().apply {
            setPosition(FR3Begin, VRangeBeforeCDR3Begin.lower)
            setPosition(FR3End, VRangeBeforeCDR3Begin.upper)
            setPosition(VEnd, VRangeAfterCDR3Begin.upper)
        }.build()
        val JPartitioning = ReferencePointsBuilder().apply {
            setPosition(JBegin, JRangeBeforeCDR3End.lower)
            setPosition(FR4Begin, JRangeAfterCDR3End.lower)
            setPosition(FR4End, JRangeAfterCDR3End.upper)
        }.build()

        val NDNSubsetBeforeMutation = CDR3.getRange(NDNSubsetRangeBeforeMutation)
        val mutationsOfNDN = random.generateMutations(NDNSubsetBeforeMutation)
        val rootInfo = RootInfo(
            VSequence,
            VPartitioning,
            listOf(FR3),
            VRangeInCDR3,
            mutationsOfNDN.mutate(NDNSubsetBeforeMutation),
            JSequence,
            JPartitioning,
            listOf(FR4),
            JRangeInCDR3,
            VJBase(VDJCGeneId(vdjcLibraryId, "VSome"), VDJCGeneId(vdjcLibraryId, "JSome"), 20)
        )
        val builtClone: NucleotideSequence = NucleotideSequence.ALPHABET.createBuilder()
            .append(buildSequence(VSequence, VMutations, VRangeBeforeCDR3Begin, true))
            .append(VSequenceInCDR3)
            .append(NDN)
            .append(JSequenceInCDR3)
            .append(buildSequence(JSequence, JMutations, JRangeAfterCDR3End, false))
            .createAndDestroy()
        val VGeneMutations = VGeneMutations(
            mapOf(FR3 to VMutations.extractAbsoluteMutations(VRangeBeforeCDR3Begin, true)),
            PartInCDR3(
                commonVRangeInCDR3,
                VMutations.extractAbsoluteMutations(commonVRangeInCDR3, false)
            )
        )
        val JGeneMutations = JGeneMutations(
            PartInCDR3(
                commonJRangeInCDR3,
                JMutations.extractAbsoluteMutations(commonJRangeInCDR3, true)
            ),
            mapOf(FR4 to JMutations.extractAbsoluteMutations(JRangeAfterCDR3End, false))
        )
        val mutationsFromVJGermline = MutationsFromVJGermline(
            VGeneMutations,
            VMutations.extractAbsoluteMutations(VRangeAfterCDR3Begin, false) to VRangeAfterCDR3Begin,
            buildSequence(
                VSequence,
                VMutations,
                VRangeAfterCDR3Begin,
                false
            ) +
                    NDN +
                    buildSequence(
                        JSequence,
                        JMutations,
                        JRangeBeforeCDR3End,
                        true
                    ),
            JMutations.extractAbsoluteMutations(JRangeBeforeCDR3End, true) to JRangeBeforeCDR3End,
            JGeneMutations
        )
        val VJBase = VJBase(VDJCGeneId(vdjcLibraryId, "VSome"), VDJCGeneId(vdjcLibraryId, "JSome"), 20)
        val rebasedClone = ClonesRebase(
            VSequence,
            JSequence,
            scoringSet
        ).rebaseClone(
            rootInfo,
            mutationsFromVJGermline,
            CloneWrapper(
                Clone(emptyArray(), Maps.newEnumMap(GeneType::class.java), null, 0.0, 0, 0),
                0,
                VJBase,
                listOf(VJBase)
            )
        )
        val resultedNDN = rebasedClone.mutationsSet.NDNMutations.buildSequence(rootInfo)

        val resultSequenceBuilder = NucleotideSequence.ALPHABET.createBuilder()
        rebasedClone.mutationsSet.VMutations.mutations.map { (geneFeature, mutations) ->
            buildSequence(
                VSequence,
                mutations,
                VPartitioning.getRange(geneFeature)
            )
        }
            .forEach { resultSequenceBuilder.append(it) }
        val CDR3Begin = resultSequenceBuilder.size()
        resultSequenceBuilder.append(rebasedClone.mutationsSet.VMutations.buildPartInCDR3(rootInfo))
        resultSequenceBuilder.append(resultedNDN)
        resultSequenceBuilder.append(rebasedClone.mutationsSet.JMutations.buildPartInCDR3(rootInfo))
        val CDR3End = resultSequenceBuilder.size()
        rebasedClone.mutationsSet.JMutations.mutations.map { (geneFeature, mutations) ->
            buildSequence(
                JSequence,
                mutations,
                JPartitioning.getRange(geneFeature)
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
                .joinToString(" ") { (geneFeature, mutations) ->
                    buildSequence(
                        VSequence,
                        mutations,
                        VPartitioning.getRange(geneFeature)
                    ).toString()
                }
                    + " "
                    + rebasedClone.mutationsSet.VMutations.buildPartInCDR3(rootInfo)
                    + " "
                    + resultedNDN
                    + " "
                    + rebasedClone.mutationsSet.JMutations.buildPartInCDR3(rootInfo)
                    + " "
                    + rebasedClone.mutationsSet.JMutations.mutations.entries
                .joinToString(" ") { (geneFeature, mutations) ->
                    buildSequence(
                        JSequence,
                        mutations,
                        JPartitioning.getRange(geneFeature)
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
        assertEquals(rootInfo.VRangeInCDR3, rebasedClone.mutationsSet.VMutations.partInCDR3.range)
        assertEquals(rootInfo.JRangeInCDR3, rebasedClone.mutationsSet.JMutations.partInCDR3.range)
        assertEquals(CDR3, resultSequence.getRange(CDR3Begin, CDR3End))
        assertEquals(builtClone, resultSequence)
        assertEquals(
            VPartLeftInRoot.concatenate(VPartGotFromNDN),
            rebasedClone.mutationsSet.VMutations.buildPartInCDR3(rootInfo)
        )
        assertEquals(NDNSubsetBeforeMutation, resultedNDN)
        assertEquals(
            JPartGotFromNDN.concatenate(JPartLeftInRoot),
            rebasedClone.mutationsSet.JMutations.buildPartInCDR3(rootInfo)
        )
    }
}
