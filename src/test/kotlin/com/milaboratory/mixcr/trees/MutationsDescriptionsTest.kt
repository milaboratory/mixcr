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

import com.milaboratory.core.Range
import com.milaboratory.core.alignment.Alignment
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.mutations.MutationsUtil
import com.milaboratory.core.sequence.AminoAcidAlphabet.INCOMPLETE_CODON
import com.milaboratory.core.sequence.AminoAcidSequence
import com.milaboratory.core.sequence.AminoAcidSequence.translate
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.core.sequence.Sequence
import com.milaboratory.core.sequence.TranslationParameters.FromLeftWithIncompleteCodon
import com.milaboratory.core.sequence.TranslationParameters.FromLeftWithoutIncompleteCodon
import com.milaboratory.core.sequence.TranslationParameters.FromRightWithIncompleteCodon
import com.milaboratory.mixcr.util.RandomizedTest
import com.milaboratory.mixcr.util.extractAbsoluteMutations
import com.milaboratory.mixcr.util.plus
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.repseq.core.GeneFeature
import io.repseq.core.GeneFeature.CDR2
import io.repseq.core.GeneFeature.CDR3
import io.repseq.core.GeneFeature.FR3
import io.repseq.core.GeneFeature.FR4
import io.repseq.core.GeneFeature.JCDR3Part
import io.repseq.core.GeneFeature.VCDR3Part
import io.repseq.core.GeneFeature.VJJunction
import io.repseq.core.ReferencePoint.CDR2Begin
import io.repseq.core.ReferencePoint.CDR3Begin
import io.repseq.core.ReferencePoint.CDR3End
import io.repseq.core.ReferencePoint.FR3Begin
import io.repseq.core.ReferencePoint.FR4End
import io.repseq.core.ReferencePoint.JBegin
import io.repseq.core.ReferencePoint.JBeginTrimmed
import io.repseq.core.ReferencePoint.VEnd
import io.repseq.core.ReferencePoint.VEndTrimmed
import io.repseq.core.ReferencePoints
import io.repseq.core.ReferenceUtil
import org.junit.Ignore
import org.junit.Test
import kotlin.random.Random

class MutationsDescriptionsTest {
    @Test
    fun `get alignments with NDN bound mod == 3`() {
        val VSequence1 = NucleotideSequence("AAAAAAGGGCCCCCCCCC")
        val VSequence1A = AminoAcidSequence("KKGPPP")
        val VMutations = Mutations(NucleotideSequence.ALPHABET, "SA0GSG7T")
        val baseNDN = NucleotideSequence("AAAAAA")
        val NDNMutations = Mutations(NucleotideSequence.ALPHABET, "SA1TSA4T")
        val JSequence1 = NucleotideSequence("CCCCCCGGGAAAAAAAAA")
        val JSequence1A = AminoAcidSequence("PPGKKK")
        val JMutations = Mutations(NucleotideSequence.ALPHABET, "SG7TSA17T")
        val mutationsDescription = MutationsDescription(
            sortedMapOf(GeneFeature(FR3Begin, VEndTrimmed) to VMutations),
            VSequence1,
            ReferencePoints(ReferenceUtil.getReferencePointIndex(FR3Begin), intArrayOf(0, 6, 18))
                .withVCDR3PartLength(6),
            baseNDN,
            NDNMutations,
            sortedMapOf(GeneFeature(JBeginTrimmed, FR4End) to JMutations),
            JSequence1,
            ReferencePoints(ReferenceUtil.getReferencePointIndex(JBegin), intArrayOf(0, 9, 18))
                .withJCDR3PartLength(6)
        )
        VMutations.mutate(VSequence1) shouldBe NucleotideSequence("GAAAAAGTGCCCCCCCCC")
        JMutations.mutate(JSequence1) shouldBe NucleotideSequence("CCCCCCGTGAAAAAAAAT")
        translate(VMutations.mutate(VSequence1), FromLeftWithIncompleteCodon) shouldBe
                AminoAcidSequence("EKVPPP")
        translate(JMutations.mutate(JSequence1), FromLeftWithIncompleteCodon) shouldBe
                AminoAcidSequence("PPVKKN")
        translate(VSequence1, FromLeftWithIncompleteCodon) shouldBe VSequence1A
        translate(JSequence1, FromLeftWithIncompleteCodon) shouldBe JSequence1A

        mutationsDescription.nAlignment(FR3, GeneFeature(FR3Begin, VEnd)) should {
            requireNotNull(it)
            it.sequence1 shouldBe VSequence1
            it.sequence1.getRange(it.sequence1Range) shouldBe NucleotideSequence("AAAAAA")
            it.absoluteMutations shouldBe Mutations(NucleotideSequence.ALPHABET, "SA0G")
            it.relativeMutations.mutate(it.sequence1.getRange(it.sequence1Range)) shouldBe NucleotideSequence("GAAAAA")
        }
        mutationsDescription.aaAlignment(FR3, GeneFeature(FR3Begin, VEnd)) should {
            requireNotNull(it)
            it.sequence1 shouldBe VSequence1A
            it.sequence1.getRange(it.sequence1Range) shouldBe AminoAcidSequence("KK")
            it.absoluteMutations shouldBe Mutations(AminoAcidSequence.ALPHABET, "SK0E")
            it.relativeMutations.mutate(it.sequence1.getRange(it.sequence1Range)) shouldBe AminoAcidSequence("EK")
        }
        mutationsDescription.nAlignment(FR3) should {
            requireNotNull(it)
            it.sequence1 shouldBe NucleotideSequence("AAAAAA")
            it.sequence1.getRange(it.sequence1Range) shouldBe NucleotideSequence("AAAAAA")
            it.absoluteMutations shouldBe Mutations(NucleotideSequence.ALPHABET, "SA0G")
            it.relativeMutations.mutate(it.sequence1.getRange(it.sequence1Range)) shouldBe NucleotideSequence("GAAAAA")
        }
        mutationsDescription.aaAlignment(FR3) should {
            requireNotNull(it)
            it.sequence1 shouldBe AminoAcidSequence("KK")
            it.sequence1.getRange(it.sequence1Range) shouldBe AminoAcidSequence("KK")
            it.absoluteMutations shouldBe Mutations(AminoAcidSequence.ALPHABET, "SK0E")
            it.relativeMutations.mutate(it.sequence1.getRange(it.sequence1Range)) shouldBe AminoAcidSequence("EK")
        }

        mutationsDescription.nAlignment(VCDR3Part, GeneFeature(FR3Begin, VEnd)) should {
            requireNotNull(it)
            it.sequence1 shouldBe VSequence1
            it.sequence1.getRange(it.sequence1Range) shouldBe NucleotideSequence("GGGCCC")
            it.absoluteMutations shouldBe Mutations(NucleotideSequence.ALPHABET, "SG7T")
            it.relativeMutations.mutate(it.sequence1.getRange(it.sequence1Range)) shouldBe NucleotideSequence("GTGCCC")
        }
        mutationsDescription.nAlignment(VCDR3Part) should {
            requireNotNull(it)
            it.sequence1 shouldBe NucleotideSequence("GGGCCC")
            it.sequence1.getRange(it.sequence1Range) shouldBe NucleotideSequence("GGGCCC")
            it.absoluteMutations shouldBe Mutations(NucleotideSequence.ALPHABET, "SG1T")
            it.relativeMutations.mutate(it.sequence1.getRange(it.sequence1Range)) shouldBe NucleotideSequence("GTGCCC")
        }



        mutationsDescription.nAlignment(CDR3) should {
            requireNotNull(it)
            it.sequence1 shouldBe NucleotideSequence("GGGCCCAAAAAACCCGGG")
            it.sequence1.getRange(it.sequence1Range) shouldBe NucleotideSequence("GGGCCCAAAAAACCCGGG")
            it.absoluteMutations shouldBe Mutations(NucleotideSequence.ALPHABET, "SG1TSA7TSA10TSG16T")
            it.relativeMutations.mutate(it.sequence1.getRange(it.sequence1Range)) shouldBe NucleotideSequence("GTGCCCATAATACCCGTG")
        }
        mutationsDescription.aaAlignment(CDR3) should {
            requireNotNull(it)
            it.sequence1 shouldBe AminoAcidSequence("GPKKPG")
            it.sequence1.getRange(it.sequence1Range) shouldBe AminoAcidSequence("GPKKPG")
            it.absoluteMutations shouldBe Mutations(AminoAcidSequence.ALPHABET, "SG0VSK2ISK3ISG5V")
            it.relativeMutations.mutate(it.sequence1.getRange(it.sequence1Range)) shouldBe AminoAcidSequence("VPIIPV")
        }
        mutationsDescription.nAlignment(VJJunction) should {
            requireNotNull(it)
            it.sequence1 shouldBe baseNDN
            it.sequence1.getRange(it.sequence1Range) shouldBe baseNDN
            it.absoluteMutations shouldBe NDNMutations
            it.relativeMutations.mutate(it.sequence1.getRange(it.sequence1Range)) shouldBe NucleotideSequence("ATAATA")
        }


        mutationsDescription.nAlignment(JCDR3Part, GeneFeature(JBegin, FR4End)) should {
            requireNotNull(it)
            it.sequence1 shouldBe JSequence1
            it.sequence1.getRange(it.sequence1Range) shouldBe NucleotideSequence("CCCGGG")
            it.absoluteMutations shouldBe Mutations(NucleotideSequence.ALPHABET, "SG7T")
            it.relativeMutations.mutate(it.sequence1.getRange(it.sequence1Range)) shouldBe NucleotideSequence("CCCGTG")
        }
        mutationsDescription.nAlignment(JCDR3Part) should {
            requireNotNull(it)
            it.sequence1 shouldBe NucleotideSequence("CCCGGG")
            it.sequence1.getRange(it.sequence1Range) shouldBe NucleotideSequence("CCCGGG")
            it.absoluteMutations shouldBe Mutations(NucleotideSequence.ALPHABET, "SG4T")
            it.relativeMutations.mutate(it.sequence1.getRange(it.sequence1Range)) shouldBe NucleotideSequence("CCCGTG")
        }

        mutationsDescription.nAlignment(FR4, GeneFeature(JBegin, FR4End)) should {
            requireNotNull(it)
            it.sequence1 shouldBe JSequence1
            it.sequence1.getRange(it.sequence1Range) shouldBe NucleotideSequence("AAAAAAAAA")
            it.absoluteMutations shouldBe Mutations(NucleotideSequence.ALPHABET, "SA17T")
            it.relativeMutations.mutate(it.sequence1.getRange(it.sequence1Range)) shouldBe NucleotideSequence("AAAAAAAAT")
        }
        mutationsDescription.aaAlignment(FR4, GeneFeature(JBegin, FR4End)) should {
            requireNotNull(it)
            it.sequence1 shouldBe JSequence1A
            it.sequence1.getRange(it.sequence1Range) shouldBe AminoAcidSequence("KKK")
            it.absoluteMutations shouldBe Mutations(AminoAcidSequence.ALPHABET, "SK5N")
            it.relativeMutations.mutate(it.sequence1.getRange(it.sequence1Range)) shouldBe AminoAcidSequence("KKN")
        }
        mutationsDescription.nAlignment(FR4, GeneFeature(JBeginTrimmed, FR4End)) should {
            requireNotNull(it)
            it.sequence1 shouldBe NucleotideSequence("CCCGGGAAAAAAAAA")
            it.sequence1.getRange(it.sequence1Range) shouldBe NucleotideSequence("AAAAAAAAA")
            it.absoluteMutations shouldBe Mutations(NucleotideSequence.ALPHABET, "SA14T")
            it.relativeMutations.mutate(it.sequence1.getRange(it.sequence1Range)) shouldBe NucleotideSequence("AAAAAAAAT")
        }
        mutationsDescription.aaAlignment(FR4, GeneFeature(JBeginTrimmed, FR4End)) should {
            requireNotNull(it)
            it.sequence1 shouldBe AminoAcidSequence("PGKKK")
            it.sequence1.getRange(it.sequence1Range) shouldBe AminoAcidSequence("KKK")
            it.absoluteMutations shouldBe Mutations(AminoAcidSequence.ALPHABET, "SK4N")
            it.relativeMutations.mutate(it.sequence1.getRange(it.sequence1Range)) shouldBe AminoAcidSequence("KKN")
        }
        mutationsDescription.nAlignment(FR4) should {
            requireNotNull(it)
            it.sequence1 shouldBe NucleotideSequence("AAAAAAAAA")
            it.sequence1.getRange(it.sequence1Range) shouldBe NucleotideSequence("AAAAAAAAA")
            it.absoluteMutations shouldBe Mutations(NucleotideSequence.ALPHABET, "SA8T")
            it.relativeMutations.mutate(it.sequence1.getRange(it.sequence1Range)) shouldBe NucleotideSequence("AAAAAAAAT")
        }
        mutationsDescription.aaAlignment(FR4) should {
            requireNotNull(it)
            it.sequence1 shouldBe AminoAcidSequence("KKK")
            it.sequence1.getRange(it.sequence1Range) shouldBe AminoAcidSequence("KKK")
            it.absoluteMutations shouldBe Mutations(AminoAcidSequence.ALPHABET, "SK2N")
            it.relativeMutations.mutate(it.sequence1.getRange(it.sequence1Range)) shouldBe AminoAcidSequence("KKN")
        }



        mutationsDescription.nAlignment(GeneFeature(FR3Begin, FR4End)) should {
            requireNotNull(it)
            it.sequence1 shouldBe NucleotideSequence("AAAAAAGGGCCCAAAAAACCCGGGAAAAAAAAA")
            it.sequence1.getRange(it.sequence1Range) shouldBe NucleotideSequence("AAAAAAGGGCCCAAAAAACCCGGGAAAAAAAAA")
            it.absoluteMutations shouldBe Mutations(NucleotideSequence.ALPHABET, "SA0GSG7TSA13TSA16TSG22TSA32T")
            it.relativeMutations.mutate(it.sequence1.getRange(it.sequence1Range)) shouldBe
                    NucleotideSequence("GAAAAAGTGCCCATAATACCCGTGAAAAAAAAT")
        }
        mutationsDescription.aaAlignment(GeneFeature(FR3Begin, FR4End)) should {
            requireNotNull(it)
            it.sequence1 shouldBe AminoAcidSequence("KKGPKKPGKKK")
            it.sequence1.getRange(it.sequence1Range) shouldBe AminoAcidSequence("KKGPKKPGKKK")
            it.absoluteMutations shouldBe Mutations(AminoAcidSequence.ALPHABET, "SK0ESG2VSK4ISK5ISG7VSK10N")
            it.relativeMutations.mutate(it.sequence1.getRange(it.sequence1Range)) shouldBe AminoAcidSequence("EKVPIIPVKKN")
        }
    }

    @Test
    fun `get alignments with NDN bound mod != 3`() {
        val VSequence1 = NucleotideSequence("AAAAAAGGGCCCCCCCCC")
        val VSequence1A = AminoAcidSequence("KKGPPP")
        val VMutations = Mutations(NucleotideSequence.ALPHABET, "SA0GSG7T")
        val baseNDN = NucleotideSequence("AAAAAAA")
        val NDNMutations = Mutations(NucleotideSequence.ALPHABET, "SA0TSA6T")
        val JSequence1 = NucleotideSequence("CCCCCCGGGAAAAAAAAA")
        val JSequence1A = AminoAcidSequence("PPGKKK")
        val JMutations = Mutations(NucleotideSequence.ALPHABET, "SG7TSA17T")
        val mutationsDescription = MutationsDescription(
            sortedMapOf(GeneFeature(FR3Begin, VEndTrimmed) to VMutations),
            VSequence1,
            ReferencePoints(ReferenceUtil.getReferencePointIndex(FR3Begin), intArrayOf(0, 6, 18))
                .withVCDR3PartLength(7),
            baseNDN,
            NDNMutations,
            sortedMapOf(GeneFeature(JBeginTrimmed, FR4End) to JMutations),
            JSequence1,
            ReferencePoints(ReferenceUtil.getReferencePointIndex(JBegin), intArrayOf(0, 9, 18))
                .withJCDR3PartLength(7)
        )
        VMutations.mutate(VSequence1) shouldBe NucleotideSequence("GAAAAAGTGCCCCCCCCC")
        JMutations.mutate(JSequence1) shouldBe NucleotideSequence("CCCCCCGTGAAAAAAAAT")
        translate(VMutations.mutate(VSequence1), FromLeftWithIncompleteCodon) shouldBe
                AminoAcidSequence("EKVPPP")
        translate(JMutations.mutate(JSequence1), FromLeftWithIncompleteCodon) shouldBe
                AminoAcidSequence("PPVKKN")
        translate(VSequence1, FromLeftWithIncompleteCodon) shouldBe VSequence1A
        translate(JSequence1, FromLeftWithIncompleteCodon) shouldBe JSequence1A

        mutationsDescription.nAlignment(VCDR3Part, GeneFeature(FR3Begin, VEnd)) should {
            requireNotNull(it)
            it.sequence1 shouldBe VSequence1
            it.sequence1.getRange(it.sequence1Range) shouldBe NucleotideSequence("GGGCCCC")
            it.absoluteMutations shouldBe Mutations(NucleotideSequence.ALPHABET, "SG7T")
            it.relativeMutations.mutate(it.sequence1.getRange(it.sequence1Range)) shouldBe NucleotideSequence("GTGCCCC")
        }
        mutationsDescription.nAlignment(VCDR3Part) should {
            requireNotNull(it)
            it.sequence1 shouldBe NucleotideSequence("GGGCCCC")
            it.sequence1.getRange(it.sequence1Range) shouldBe NucleotideSequence("GGGCCCC")
            it.absoluteMutations shouldBe Mutations(NucleotideSequence.ALPHABET, "SG1T")
            it.relativeMutations.mutate(it.sequence1.getRange(it.sequence1Range)) shouldBe NucleotideSequence("GTGCCCC")
        }



        mutationsDescription.nAlignment(CDR3) should {
            requireNotNull(it)
            it.sequence1 shouldBe NucleotideSequence("GGGCCCCAAAAAAACCCCGGG")
            it.sequence1.getRange(it.sequence1Range) shouldBe NucleotideSequence("GGGCCCCAAAAAAACCCCGGG")
            it.absoluteMutations shouldBe Mutations(NucleotideSequence.ALPHABET, "SG1TSA7TSA13TSG19T")
            it.relativeMutations.mutate(it.sequence1.getRange(it.sequence1Range)) shouldBe NucleotideSequence("GTGCCCCTAAAAATCCCCGTG")
        }
        mutationsDescription.aaAlignment(CDR3) should {
            requireNotNull(it)
            it.sequence1 shouldBe AminoAcidSequence("GPQKNPG")
            it.sequence1.getRange(it.sequence1Range) shouldBe AminoAcidSequence("GPQKNPG")
            it.absoluteMutations shouldBe Mutations(AminoAcidSequence.ALPHABET, "SG0VSQ2LSN4ISG6V")
            it.relativeMutations.mutate(it.sequence1.getRange(it.sequence1Range)) shouldBe AminoAcidSequence("VPLKIPV")
        }
        mutationsDescription.nAlignment(GeneFeature(CDR3Begin, JBeginTrimmed)) should {
            requireNotNull(it)
            it.sequence1 shouldBe NucleotideSequence("GGGCCCCAAAAAAA")
            it.sequence1.getRange(it.sequence1Range) shouldBe NucleotideSequence("GGGCCCCAAAAAAA")
            it.absoluteMutations shouldBe Mutations(NucleotideSequence.ALPHABET, "SG1TSA7TSA13T")
            it.relativeMutations.mutate(it.sequence1.getRange(it.sequence1Range)) shouldBe NucleotideSequence("GTGCCCCTAAAAAT")
        }
        mutationsDescription.nAlignment(VJJunction) should {
            requireNotNull(it)
            it.sequence1 shouldBe baseNDN
            it.sequence1.getRange(it.sequence1Range) shouldBe baseNDN
            it.absoluteMutations shouldBe NDNMutations
            it.relativeMutations.mutate(it.sequence1.getRange(it.sequence1Range)) shouldBe NucleotideSequence("TAAAAAT")
        }
        mutationsDescription.nAlignment(GeneFeature(VEndTrimmed, CDR3End)) should {
            requireNotNull(it)
            it.sequence1 shouldBe NucleotideSequence("AAAAAAACCCCGGG")
            it.sequence1.getRange(it.sequence1Range) shouldBe NucleotideSequence("AAAAAAACCCCGGG")
            it.absoluteMutations shouldBe Mutations(NucleotideSequence.ALPHABET, "SA0TSA6TSG12T")
            it.relativeMutations.mutate(it.sequence1.getRange(it.sequence1Range)) shouldBe NucleotideSequence("TAAAAATCCCCGTG")
        }



        mutationsDescription.nAlignment(JCDR3Part, GeneFeature(JBegin, FR4End)) should {
            requireNotNull(it)
            it.sequence1 shouldBe JSequence1
            it.sequence1.getRange(it.sequence1Range) shouldBe NucleotideSequence("CCCCGGG")
            it.absoluteMutations shouldBe Mutations(NucleotideSequence.ALPHABET, "SG7T")
            it.relativeMutations.mutate(it.sequence1.getRange(it.sequence1Range)) shouldBe NucleotideSequence("CCCCGTG")
        }
        mutationsDescription.nAlignment(JCDR3Part) should {
            requireNotNull(it)
            it.sequence1 shouldBe NucleotideSequence("CCCCGGG")
            it.sequence1.getRange(it.sequence1Range) shouldBe NucleotideSequence("CCCCGGG")
            it.absoluteMutations shouldBe Mutations(NucleotideSequence.ALPHABET, "SG5T")
            it.relativeMutations.mutate(it.sequence1.getRange(it.sequence1Range)) shouldBe NucleotideSequence("CCCCGTG")
        }



        mutationsDescription.nAlignment(GeneFeature(FR3Begin, FR4End)) should {
            requireNotNull(it)
            it.sequence1 shouldBe NucleotideSequence("AAAAAAGGGCCCCAAAAAAACCCCGGGAAAAAAAAA")
            it.sequence1.getRange(it.sequence1Range) shouldBe NucleotideSequence("AAAAAAGGGCCCCAAAAAAACCCCGGGAAAAAAAAA")
            it.absoluteMutations shouldBe Mutations(NucleotideSequence.ALPHABET, "SA0GSG7TSA13TSA19TSG25TSA35T")
            it.relativeMutations.mutate(it.sequence1.getRange(it.sequence1Range)) shouldBe
                    NucleotideSequence("GAAAAAGTGCCCCTAAAAATCCCCGTGAAAAAAAAT")
        }
        mutationsDescription.aaAlignment(GeneFeature(FR3Begin, FR4End)) should {
            requireNotNull(it)
            it.sequence1 shouldBe AminoAcidSequence("KKGPQKNPGKKK")
            it.sequence1.getRange(it.sequence1Range) shouldBe AminoAcidSequence("KKGPQKNPGKKK")
            it.absoluteMutations shouldBe Mutations(AminoAcidSequence.ALPHABET, "SK0ESG2VSQ4LSN6ISG8VSK11N")
            it.relativeMutations.mutate(it.sequence1.getRange(it.sequence1Range)) shouldBe AminoAcidSequence("EKVPLKIPVKKN")
        }
    }

    @Test
    fun `randomized test of broken AA`() {
        RandomizedTest.randomized(::testBrokenAA, numberOfRuns = 100000)
    }

    @Test
    fun `reproduce test of broken AA`() {
        RandomizedTest.reproduce(
            ::testBrokenAA,
            -3966494014459193821L, -1558052645563470479L
        )
    }

    private fun testBrokenAA(random: Random, print: Boolean) {
        val VSequence1 = NucleotideSequence("AAAAAAGGGCCCCCCCCC")
        val VMutations = Mutations.EMPTY_NUCLEOTIDE_MUTATIONS
        val baseNDN = NucleotideSequence("AAAAAA")
        val NDNMutations = Mutations.EMPTY_NUCLEOTIDE_MUTATIONS
        val JSequence1 = random.generateSequence(18 + random.nextInt(4))
        val JMutations = random.generateMutations(JSequence1)
        val mutationsDescription = MutationsDescription(
            sortedMapOf(GeneFeature(FR3Begin, VEndTrimmed) to VMutations),
            VSequence1,
            ReferencePoints(ReferenceUtil.getReferencePointIndex(FR3Begin), intArrayOf(0, 6, 18))
                .withVCDR3PartLength(6),
            baseNDN,
            NDNMutations,
            sortedMapOf(GeneFeature(JBeginTrimmed, FR4End) to JMutations),
            JSequence1,
            ReferencePoints(ReferenceUtil.getReferencePointIndex(JBegin), intArrayOf(0, 9, 18))
                .withJCDR3PartLength(6)
        )
        if (print) {
            println(translate(JSequence1, FromRightWithIncompleteCodon))
            println(translate(JMutations.mutate(JSequence1), FromRightWithIncompleteCodon))
        }
        mutationsDescription.aaAlignment(CDR3)
        mutationsDescription.aaAlignment(FR4)
        mutationsDescription.aaAlignment(FR4, GeneFeature(JBegin, FR4End))
        mutationsDescription.aaMutationsDetailed(CDR3)
        mutationsDescription.aaMutationsDetailed(FR4)
        mutationsDescription.aaMutationsDetailed(FR4, GeneFeature(JBegin, FR4End))
    }

    @Test
    fun `randomized test of mutations projection`() {
        RandomizedTest.randomized(::testMutationsProjection, numberOfRuns = 100000)
    }

    @Test
    fun `reproduce test of mutations projection`() {
        RandomizedTest.reproduce(
            ::testMutationsProjection,
            -3410513706783932092L,
            -4806524422783115806L,
            -7280270285052261045L,
            3725096791126160884L,
            -2250080923051716147L,
            -5872928166188935892L,
            -3461743546929671603L,
            929553915027118555L,
            -7791725873056070880L,
            -1102062497004849929L
        )
    }

    private fun testMutationsProjection(random: Random, print: Boolean) {
        val VSequence1 = random.generateSequence(18)
        val baseNDN = random.generateSequence(9)
        val JSequence1 = random.generateSequence(12)
        val VMutations = random.generateMutations(VSequence1, Range(0, 15))
        val VAASequence = translate(VSequence1, FromLeftWithoutIncompleteCodon)
        val NDNMutations = random.generateMutations(baseNDN)
        val JMutations = random.generateMutations(JSequence1, Range(3, 12))
        val expectedNTarget = VMutations.mutate(VSequence1.getRange(0, 15))
        val expectedFullAATarget = translate(
            VMutations.mutate(VSequence1.getRange(0, 15)) +
                    NDNMutations.mutate(baseNDN) +
                    buildSequence(JSequence1, JMutations, Range(3, 12), true),
            FromLeftWithIncompleteCodon
        )
        if (!isValid(VSequence1, VMutations, baseNDN, NDNMutations, JSequence1, JMutations)) {
            return
        }
        val mutationsDescription = MutationsDescription(
            sortedMapOf(GeneFeature(CDR2Begin, VEndTrimmed) to VMutations),
            VSequence1,
            ReferencePoints(ReferenceUtil.getReferencePointIndex(CDR2Begin), intArrayOf(0, 6, 12, 18))
                .withVCDR3PartLength(3),
            baseNDN,
            NDNMutations,
            sortedMapOf(GeneFeature(JBeginTrimmed, FR4End) to JMutations),
            JSequence1,
            ReferencePoints(ReferenceUtil.getReferencePointIndex(JBegin), intArrayOf(0, 6, 12))
                .withJCDR3PartLength(3)
        )
        val fullVNAlignment = mutationsDescription.nAlignment(GeneFeature(CDR2Begin, VEndTrimmed))
        val CDR2NAlignment = mutationsDescription.nAlignment(CDR2)
        val FR3NAlignment = mutationsDescription.nAlignment(FR3)
        val VCDR3PartNAlignment = mutationsDescription.nAlignment(VCDR3Part)
        checkNotNull(fullVNAlignment)
        checkNotNull(CDR2NAlignment)
        checkNotNull(FR3NAlignment)
        checkNotNull(VCDR3PartNAlignment)
        if (print) {
            println("                  VNSequence1: $VSequence1")
            println("                  VNMutations: $VMutations")
            println("expected fullMutatedVNSegment: $expectedNTarget")
            println("  actual fullMutatedVNSegment: ${fullVNAlignment.target}")
            println("composed fullMutatedVNSegment: ${CDR2NAlignment.target} ${FR3NAlignment.target} ${VCDR3PartNAlignment.target}")
            println("     [CDR2Begin, VEndTrimmed]:")
            println("$fullVNAlignment")
            println("                         CDR2:")
            println("$CDR2NAlignment")
            println("                          FR3:")
            println("$FR3NAlignment")
            println("                    VCDR3Part:")
            println("$VCDR3PartNAlignment")
        }
        val fullAAAlignment = mutationsDescription.aaAlignment(GeneFeature(CDR2Begin, FR4End))
        val CDR2AAAlignment = mutationsDescription.aaAlignment(CDR2)
        val FR3AAAlignment = mutationsDescription.aaAlignment(FR3)
        val CDR3AAAlignment = mutationsDescription.aaAlignment(CDR3)
        val FR4AAAlignment = mutationsDescription.aaAlignment(FR4)
        checkNotNull(fullAAAlignment)
        checkNotNull(CDR2AAAlignment)
        checkNotNull(FR3AAAlignment)
        checkNotNull(CDR3AAAlignment)
        checkNotNull(FR4AAAlignment)
        if (print) {
            println("                  VAASequence1: $VAASequence")
            println(
                "                  VAAMutations: ${
                    MutationsUtil.nt2aa(
                        VSequence1,
                        VMutations,
                        FromLeftWithoutIncompleteCodon
                    )
                }"
            )
            println("expected fullMutatedAASegment: $expectedFullAATarget")
            println("  actual fullMutatedAASegment: ${fullAAAlignment.target}")
            println("composed fullMutatedAASegment: ${CDR2AAAlignment.target} ${FR3AAAlignment.target} ${CDR3AAAlignment.target} ${FR4AAAlignment.target}")
            println("          [CDR2Begin, FR4End]:")
            println("$fullAAAlignment")
            println("                         CDR2:")
            println("$CDR2AAAlignment")
            println("                          FR3:")
            println("$FR3AAAlignment")
            println("                         CDR3:")
            println("$CDR3AAAlignment")
            println("                          FR4:")
            println("$FR4AAAlignment")
        }
        fullVNAlignment.target shouldBe expectedNTarget
        (CDR2NAlignment.target + FR3NAlignment.target + VCDR3PartNAlignment.target) shouldBe expectedNTarget
        fullAAAlignment.target shouldBe expectedFullAATarget
        (CDR2AAAlignment.target + FR3AAAlignment.target + CDR3AAAlignment.target + FR4AAAlignment.target) shouldBe expectedFullAATarget
    }

    @Ignore("There is no way to build consistent difference. We can just try our best")
    @Test
    fun `randomized test of difference`() {
        RandomizedTest.randomized(::testDifference, numberOfRuns = 100000)
    }

    @Test
    fun `reproduce test of difference`() {
        RandomizedTest.reproduce(
            ::testDifference,
            -2699654147603327555L,
            -1974766567385910414L,
            -4370699181734706309L,
            4400567774691311589L,
            5963091239703462218L,
            3752231082547469194L,
            -3714570875977080625L,
            56464468226461700L,
            8549077566522360280L,
            -7360294260727700912L,
            -1840253156580636563L,
            3916467960646430943L,
        )
    }

    private fun testDifference(random: Random, print: Boolean) {
        val VSequence1 = random.generateSequence(18)
        val baseNDN = random.generateSequence(9)
        val JSequence1 = random.generateSequence(12)

        val firstVMutations = random.generateMutations(VSequence1, Range(0, 15))
        val firstNDNMutations = random.generateMutations(baseNDN)
        val firstJMutations = random.generateMutations(JSequence1, Range(3, 12))
        val expectedNTargetOfFirst = firstVMutations.mutate(VSequence1.getRange(0, 15))
        val expectedAATargetOfFirst = translate(
            buildSequence(VSequence1, firstVMutations, Range(0, 15), true) +
                    firstNDNMutations.mutate(baseNDN) +
                    buildSequence(JSequence1, firstJMutations, Range(3, 12), true),
            FromLeftWithIncompleteCodon
        )
        if (!isValid(VSequence1, firstVMutations, baseNDN, firstNDNMutations, JSequence1, firstJMutations)) {
            return
        }
        val secondVMutations = random.generateMutations(VSequence1, Range(0, 15))
        val secondNDNMutations = random.generateMutations(baseNDN)
        val secondJMutations = random.generateMutations(JSequence1, Range(3, 12))
        val expectedNTargetOfSecond = secondVMutations.mutate(VSequence1.getRange(0, 15))
        val expectedAATargetOfSecond = translate(
            buildSequence(VSequence1, secondVMutations, Range(0, 15), true) +
                    secondNDNMutations.mutate(baseNDN) +
                    buildSequence(JSequence1, secondJMutations, Range(3, 12), true),
            FromLeftWithIncompleteCodon
        )

        if (!isValid(VSequence1, secondVMutations, baseNDN, secondNDNMutations, JSequence1, secondJMutations)) {
            return
        }
        val firstMutationsDescription = MutationsDescription(
            sortedMapOf(GeneFeature(CDR2Begin, VEndTrimmed) to firstVMutations),
            VSequence1,
            ReferencePoints(ReferenceUtil.getReferencePointIndex(CDR2Begin), intArrayOf(0, 6, 12, 18))
                .withVCDR3PartLength(3),
            baseNDN,
            firstNDNMutations,
            sortedMapOf(GeneFeature(JBeginTrimmed, FR4End) to firstJMutations),
            JSequence1,
            ReferencePoints(ReferenceUtil.getReferencePointIndex(JBegin), intArrayOf(0, 6, 12))
                .withJCDR3PartLength(3)
        )
        val secondMutationsDescription = MutationsDescription(
            sortedMapOf(GeneFeature(CDR2Begin, VEndTrimmed) to secondVMutations),
            VSequence1,
            ReferencePoints(ReferenceUtil.getReferencePointIndex(CDR2Begin), intArrayOf(0, 6, 12, 18))
                .withVCDR3PartLength(3),
            baseNDN,
            secondNDNMutations,
            sortedMapOf(GeneFeature(JBeginTrimmed, FR4End) to secondJMutations),
            JSequence1,
            ReferencePoints(ReferenceUtil.getReferencePointIndex(JBegin), intArrayOf(0, 6, 12))
                .withJCDR3PartLength(3)
        )

        val fromFirstToSecond = firstMutationsDescription.differenceWith(secondMutationsDescription)


        val fullVNAlignmentOfFirst = firstMutationsDescription.nAlignment(GeneFeature(CDR2Begin, VEndTrimmed))
        val CDR2NAlignmentOfFirst = firstMutationsDescription.nAlignment(CDR2)
        val FR3NAlignmentOfFirst = firstMutationsDescription.nAlignment(FR3)
        val VCDR3PartNAlignmentOfFirst = firstMutationsDescription.nAlignment(VCDR3Part)
        checkNotNull(fullVNAlignmentOfFirst)
        checkNotNull(CDR2NAlignmentOfFirst)
        checkNotNull(FR3NAlignmentOfFirst)
        checkNotNull(VCDR3PartNAlignmentOfFirst)
        val fullVNAlignmentOfSecond = secondMutationsDescription.nAlignment(GeneFeature(CDR2Begin, VEndTrimmed))
        val CDR2NAlignmentOfSecond = secondMutationsDescription.nAlignment(CDR2)
        val FR3NAlignmentOfSecond = secondMutationsDescription.nAlignment(FR3)
        val VCDR3PartNAlignmentOfSecond = secondMutationsDescription.nAlignment(VCDR3Part)
        checkNotNull(fullVNAlignmentOfSecond)
        checkNotNull(CDR2NAlignmentOfSecond)
        checkNotNull(FR3NAlignmentOfSecond)
        checkNotNull(VCDR3PartNAlignmentOfSecond)
        val fullVNAlignmentFromFirstToSecond = fromFirstToSecond.nAlignment(GeneFeature(CDR2Begin, VEndTrimmed))
        val CDR2NAlignmentFromFirstToSecond = fromFirstToSecond.nAlignment(CDR2)
        val FR3NAlignmentFromFirstToSecond = fromFirstToSecond.nAlignment(FR3)
        val VCDR3PartNAlignmentFromFirstToSecond = fromFirstToSecond.nAlignment(VCDR3Part)
        checkNotNull(fullVNAlignmentFromFirstToSecond)
        checkNotNull(CDR2NAlignmentFromFirstToSecond)
        checkNotNull(FR3NAlignmentFromFirstToSecond)
        checkNotNull(VCDR3PartNAlignmentFromFirstToSecond)
        if (print) {
            println("                            VNSequence1: $VSequence1")
            println("                            VNMutations: $firstVMutations")
            println()
            println(" expected fullMutatedVNSegment of first: $expectedNTargetOfFirst")
            println("   actual fullMutatedVNSegment of first: ${fullVNAlignmentFromFirstToSecond.subsequence1}")
            println(" composed fullMutatedVNSegment of first: ${CDR2NAlignmentFromFirstToSecond.subsequence1} ${FR3NAlignmentFromFirstToSecond.subsequence1} ${VCDR3PartNAlignmentFromFirstToSecond.subsequence1}")
            println()
            println("expected fullMutatedVNSegment of second: $expectedNTargetOfSecond")
            println("  actual fullMutatedVNSegment of second: ${fullVNAlignmentFromFirstToSecond.target}")
            println("composed fullMutatedVNSegment of second: ${CDR2NAlignmentFromFirstToSecond.target} ${FR3NAlignmentFromFirstToSecond.target} ${VCDR3PartNAlignmentFromFirstToSecond.target}")
        }
        fullVNAlignmentFromFirstToSecond.subsequence1 shouldBe expectedNTargetOfFirst
        (CDR2NAlignmentFromFirstToSecond.subsequence1 + FR3NAlignmentFromFirstToSecond.subsequence1 + VCDR3PartNAlignmentFromFirstToSecond.subsequence1) shouldBe expectedNTargetOfFirst

        fullVNAlignmentFromFirstToSecond.target shouldBe expectedNTargetOfSecond
        (CDR2NAlignmentFromFirstToSecond.target + FR3NAlignmentFromFirstToSecond.target + VCDR3PartNAlignmentFromFirstToSecond.target) shouldBe expectedNTargetOfSecond


        val fullVAAAlignmentOfFirst = firstMutationsDescription.aaAlignment(GeneFeature(CDR2Begin, FR4End))
        val CDR2AAAlignmentOfFirst = firstMutationsDescription.aaAlignment(CDR2)
        val FR3AAAlignmentOfFirst = firstMutationsDescription.aaAlignment(FR3)
        val CDR3AAAlignmentOfFirst = firstMutationsDescription.aaAlignment(CDR3)
        val FR4AAAlignmentOfFirst = firstMutationsDescription.aaAlignment(FR4)
        checkNotNull(fullVAAAlignmentOfFirst)
        checkNotNull(CDR2AAAlignmentOfFirst)
        checkNotNull(FR3AAAlignmentOfFirst)
        checkNotNull(CDR3AAAlignmentOfFirst)
        checkNotNull(FR4AAAlignmentOfFirst)
        val fullVAAAlignmentOfSecond = secondMutationsDescription.aaAlignment(GeneFeature(CDR2Begin, FR4End))
        val CDR2AAAlignmentOfSecond = secondMutationsDescription.aaAlignment(CDR2)
        val FR3AAAlignmentOfSecond = secondMutationsDescription.aaAlignment(FR3)
        val CDR3AAAlignmentOfSecond = secondMutationsDescription.aaAlignment(CDR3)
        val FR4AAAlignmentOfSecond = secondMutationsDescription.aaAlignment(FR4)
        checkNotNull(fullVAAAlignmentOfSecond)
        checkNotNull(CDR2AAAlignmentOfSecond)
        checkNotNull(FR3AAAlignmentOfSecond)
        checkNotNull(CDR3AAAlignmentOfSecond)
        checkNotNull(FR4AAAlignmentOfSecond)
        val fullVAAAlignmentFromFirstToSecond = fromFirstToSecond.aaAlignment(GeneFeature(CDR2Begin, FR4End))
        val CDR2AAAlignmentFromFirstToSecond = fromFirstToSecond.aaAlignment(CDR2)
        val FR3AAAlignmentFromFirstToSecond = fromFirstToSecond.aaAlignment(FR3)
        val CDR3AAAlignmentFromFirstToSecond = fromFirstToSecond.aaAlignment(CDR3)
        val FR4AAAlignmentFromFirstToSecond = fromFirstToSecond.aaAlignment(FR4)
        checkNotNull(fullVAAAlignmentFromFirstToSecond)
        checkNotNull(CDR2AAAlignmentFromFirstToSecond)
        checkNotNull(FR3AAAlignmentFromFirstToSecond)
        checkNotNull(CDR3AAAlignmentFromFirstToSecond)
        checkNotNull(FR4AAAlignmentFromFirstToSecond)
        if (print) {
            println()
            println(" expected fullMutatedVAASegment of first: $expectedAATargetOfFirst")
            println("   actual fullMutatedVAASegment of first: ${fullVAAAlignmentFromFirstToSecond.subsequence1}")
            println(" composed fullMutatedVAASegment of first: ${CDR2AAAlignmentFromFirstToSecond.subsequence1} ${FR3AAAlignmentFromFirstToSecond.subsequence1} ${CDR3AAAlignmentFromFirstToSecond.subsequence1} ${FR4AAAlignmentFromFirstToSecond.subsequence1}")
            println()
            println("expected fullMutatedVAASegment of second: $expectedAATargetOfSecond")
            println("  actual fullMutatedVAASegment of second: ${fullVAAAlignmentFromFirstToSecond.target}")
            println("composed fullMutatedVAASegment of second: ${CDR2AAAlignmentFromFirstToSecond.target} ${FR3AAAlignmentFromFirstToSecond.target} ${CDR3AAAlignmentFromFirstToSecond.target} ${FR4AAAlignmentFromFirstToSecond.target}")
        }
        fullVAAAlignmentFromFirstToSecond.subsequence1 shouldBe expectedAATargetOfFirst
        (CDR2AAAlignmentFromFirstToSecond.subsequence1 + FR3AAAlignmentFromFirstToSecond.subsequence1 + CDR3AAAlignmentFromFirstToSecond.subsequence1 + FR4AAAlignmentFromFirstToSecond.subsequence1) shouldBe expectedAATargetOfFirst

        fullVAAAlignmentFromFirstToSecond.target shouldBe expectedAATargetOfSecond
        (CDR2AAAlignmentFromFirstToSecond.target + FR3AAAlignmentFromFirstToSecond.target + CDR3AAAlignmentFromFirstToSecond.target + FR4AAAlignmentFromFirstToSecond.target) shouldBe expectedAATargetOfSecond
    }

    private fun isValid(
        VSequence1: NucleotideSequence,
        VMutations: Mutations<NucleotideSequence>,
        baseNDN: NucleotideSequence,
        NDNMutations: Mutations<NucleotideSequence>,
        JSequence1: NucleotideSequence,
        JMutations: Mutations<NucleotideSequence>
    ): Boolean {
        if (VMutations.extractAbsoluteMutations(Range(0, 6), true).lengthDelta % 3 != 0) {
            return false
        }
        if (VMutations.extractAbsoluteMutations(Range(6, 12), false).lengthDelta % 3 != 0) {
            return false
        }
        if (!translate(VMutations.mutate(VSequence1.getRange(0, 15)), FromLeftWithIncompleteCodon).isValid()) {
            return false
        }
        if (!translate(VSequence1.getRange(0, 15), FromLeftWithIncompleteCodon).isValid()) {
            return false
        }
        if (NDNMutations.lengthDelta % 3 != 0) {
            return false
        }
        if (!translate(NDNMutations.mutate(baseNDN), FromLeftWithIncompleteCodon).isValid()) {
            return false
        }
        if (!translate(baseNDN, FromLeftWithIncompleteCodon).isValid()) {
            return false
        }
        if (JMutations.extractAbsoluteMutations(Range(3, 6), true).lengthDelta % 3 != 0) {
            return false
        }
        if (JMutations.extractAbsoluteMutations(Range(6, 12), false).lengthDelta % 3 != 0) {
            return false
        }
        if (!translate(
                buildSequence(JSequence1, JMutations, Range(3, 12), true),
                FromRightWithIncompleteCodon
            ).isValid()
        ) {
            return false
        }
        if (!translate(JSequence1.getRange(3, 12), FromRightWithIncompleteCodon).isValid()) {
            return false
        }
        return true
    }

    private fun AminoAcidSequence.isValid() =
        !containStops() && INCOMPLETE_CODON !in asArray() && INCOMPLETE_CODON !in asArray()

    private val <S : Sequence<S>> Alignment<S>.target
        get() = relativeMutations.mutate(subsequence1)

    private val <S : Sequence<S>> Alignment<S>.subsequence1
        get() = sequence1.getRange(sequence1Range)
}
