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
import com.milaboratory.core.mutations.Mutation
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
import com.milaboratory.core.sequence.TranslationParameters.withIncompleteCodon
import com.milaboratory.mixcr.util.asSequence
import com.milaboratory.mixcr.util.extractAbsoluteMutations
import com.milaboratory.mixcr.util.plus
import com.milaboratory.test.RandomizedTest
import com.milaboratory.test.generateSequence
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.repseq.core.GeneFeature
import io.repseq.core.GeneFeature.CDR1
import io.repseq.core.GeneFeature.CDR2
import io.repseq.core.GeneFeature.CDR3
import io.repseq.core.GeneFeature.FR3
import io.repseq.core.GeneFeature.FR4
import io.repseq.core.GeneFeature.JCDR3Part
import io.repseq.core.GeneFeature.VCDR3Part
import io.repseq.core.GeneFeature.VDJRegion
import io.repseq.core.GeneFeature.VJJunction
import io.repseq.core.ReferencePoint.CDR1Begin
import io.repseq.core.ReferencePoint.CDR2Begin
import io.repseq.core.ReferencePoint.CDR3Begin
import io.repseq.core.ReferencePoint.CDR3End
import io.repseq.core.ReferencePoint.FR1Begin
import io.repseq.core.ReferencePoint.FR3Begin
import io.repseq.core.ReferencePoint.FR4End
import io.repseq.core.ReferencePoint.JBegin
import io.repseq.core.ReferencePoint.JBeginTrimmed
import io.repseq.core.ReferencePoint.VEnd
import io.repseq.core.ReferencePoint.VEndTrimmed
import io.repseq.core.ReferencePoints
import io.repseq.core.ReferencePointsBuilder
import io.repseq.core.ReferenceUtil
import org.junit.Test
import kotlin.random.Random

class MutationsDescriptionsTest {
    @Test
    fun `get alignments with sequence 1 started from FR1Begin`() {
        val VSequence1 = NucleotideSequence("TTTTTTAAAAAAGGGCCCCCCCCC")
        val VSequence1A = AminoAcidSequence("FFKKGPPP")
        val VMutations = Mutations(NucleotideSequence.ALPHABET, "SA6GSG13T")
        val baseNDN = NucleotideSequence("AAAAAA")
        val NDNMutations = Mutations(NucleotideSequence.ALPHABET, "SA1TSA4T")
        val JSequence1 = NucleotideSequence("CCCCCCGGGAAAAAAAAA")
        val JSequence1A = AminoAcidSequence("PPGKKK")
        val JMutations = Mutations(NucleotideSequence.ALPHABET, "SG7TSA17T")
        val mutationsDescription = MutationsDescription(
            sortedMapOf(GeneFeature(FR3Begin, VEndTrimmed) to VMutations),
            VSequence1,
            ReferencePointsBuilder().apply {
                setPosition(FR1Begin, 0)
                setPosition(FR3Begin, 6)
                setPosition(CDR3Begin, 12)
                setPosition(VEnd, 24)
            }.build().withVCDR3PartLength(6),
            baseNDN,
            NDNMutations,
            sortedMapOf(GeneFeature(JBeginTrimmed, FR4End) to JMutations),
            JSequence1,
            ReferencePoints(ReferenceUtil.getReferencePointIndex(JBegin), intArrayOf(0, 9, 18))
                .withJCDR3PartLength(6)
        )
        VMutations.mutate(VSequence1) shouldBe NucleotideSequence("TTTTTTGAAAAAGTGCCCCCCCCC")
        JMutations.mutate(JSequence1) shouldBe NucleotideSequence("CCCCCCGTGAAAAAAAAT")
        translate(VMutations.mutate(VSequence1), FromLeftWithoutIncompleteCodon) shouldBe
                AminoAcidSequence("FFEKVPPP")
        translate(JMutations.mutate(JSequence1), FromLeftWithoutIncompleteCodon) shouldBe
                AminoAcidSequence("PPVKKN")
        translate(VSequence1, FromLeftWithoutIncompleteCodon) shouldBe VSequence1A
        translate(JSequence1, FromLeftWithoutIncompleteCodon) shouldBe JSequence1A

        mutationsDescription.nAlignment(FR3, GeneFeature(FR1Begin, VEnd)) should {
            requireNotNull(it)
            it.sequence1 shouldBe VSequence1
            it.sequence1.getRange(it.sequence1Range) shouldBe NucleotideSequence("AAAAAA")
            it.absoluteMutations shouldBe Mutations(NucleotideSequence.ALPHABET, "SA6G")
            it.relativeMutations.mutate(it.sequence1.getRange(it.sequence1Range)) shouldBe NucleotideSequence("GAAAAA")
        }
        mutationsDescription.aaAlignment(FR3, GeneFeature(FR1Begin, VEnd)) should {
            requireNotNull(it)
            it.sequence1 shouldBe VSequence1A
            it.sequence1.getRange(it.sequence1Range) shouldBe AminoAcidSequence("KK")
            it.absoluteMutations shouldBe Mutations(AminoAcidSequence.ALPHABET, "SK2E")
            it.relativeMutations.mutate(it.sequence1.getRange(it.sequence1Range)) shouldBe AminoAcidSequence("EK")
        }
        mutationsDescription.nAlignment(FR3, GeneFeature(FR3Begin, VEnd)) should {
            requireNotNull(it)
            it.sequence1 shouldBe NucleotideSequence("AAAAAAGGGCCCCCCCCC")
            it.sequence1.getRange(it.sequence1Range) shouldBe NucleotideSequence("AAAAAA")
            it.absoluteMutations shouldBe Mutations(NucleotideSequence.ALPHABET, "SA0G")
            it.relativeMutations.mutate(it.sequence1.getRange(it.sequence1Range)) shouldBe NucleotideSequence("GAAAAA")
        }
        mutationsDescription.aaAlignment(FR3, GeneFeature(FR3Begin, VEnd)) should {
            requireNotNull(it)
            it.sequence1 shouldBe AminoAcidSequence("KKGPPP")
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
            it.sequence1 shouldBe NucleotideSequence("AAAAAAGGGCCCCCCCCC")
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

        mutationsDescription.nAlignment(GeneFeature(FR3Begin, FR4End), GeneFeature(FR1Begin, FR4End)) should {
            requireNotNull(it)
            it.sequence1 shouldBe NucleotideSequence("TTTTTTAAAAAAGGGCCCAAAAAACCCGGGAAAAAAAAA")
            it.sequence1.getRange(it.sequence1Range) shouldBe NucleotideSequence("AAAAAAGGGCCCAAAAAACCCGGGAAAAAAAAA")
            it.absoluteMutations shouldBe Mutations(NucleotideSequence.ALPHABET, "SA6GSG13TSA19TSA22TSG28TSA38T")
            it.relativeMutations.mutate(it.sequence1.getRange(it.sequence1Range)) shouldBe
                    NucleotideSequence("GAAAAAGTGCCCATAATACCCGTGAAAAAAAAT")
        }
        mutationsDescription.aaAlignment(GeneFeature(FR3Begin, FR4End), GeneFeature(FR1Begin, FR4End)) should {
            requireNotNull(it)
            it.sequence1 shouldBe AminoAcidSequence("FFKKGPKKPGKKK")
            it.sequence1.getRange(it.sequence1Range) shouldBe AminoAcidSequence("KKGPKKPGKKK")
            it.absoluteMutations shouldBe Mutations(AminoAcidSequence.ALPHABET, "SK2ESG4VSK6ISK7ISG9VSK12N")
            it.relativeMutations.mutate(it.sequence1.getRange(it.sequence1Range)) shouldBe AminoAcidSequence("EKVPIIPVKKN")
        }
    }

    @Test
    fun `get alignments with sequence 1 started from FR1Begin with incomplete triplets`() {
        val VSequence1 = NucleotideSequence("TTTTTAAAAAAGGGCCCCCCCC")
        val VSequence1A = AminoAcidSequence("_FKKGPP_")
        val VMutations = Mutations(NucleotideSequence.ALPHABET, "SA5GSG12T")
        val baseNDN = NucleotideSequence("AAAAAA")
        val NDNMutations = Mutations(NucleotideSequence.ALPHABET, "SA1TSA4T")
        val JSequence1 = NucleotideSequence("CCCCCGGGAAAAAAAAA")
        val JSequence1A = AminoAcidSequence("_PGKKK")
        val JMutations = Mutations(NucleotideSequence.ALPHABET, "SG6TSA16T")
        val mutationsDescription = MutationsDescription(
            sortedMapOf(GeneFeature(FR3Begin, VEndTrimmed) to VMutations),
            VSequence1,
            ReferencePointsBuilder().apply {
                setPosition(FR1Begin, 0)
                setPosition(FR3Begin, 5)
                setPosition(CDR3Begin, 11)
                setPosition(VEnd, 22)
            }.build().withVCDR3PartLength(6),
            baseNDN,
            NDNMutations,
            sortedMapOf(GeneFeature(JBeginTrimmed, FR4End) to JMutations),
            JSequence1,
            ReferencePoints(ReferenceUtil.getReferencePointIndex(JBegin), intArrayOf(0, 8, 17))
                .withJCDR3PartLength(6)
        )
        VMutations.mutate(VSequence1) shouldBe NucleotideSequence("TTTTTGAAAAAGTGCCCCCCCC")
        JMutations.mutate(JSequence1) shouldBe NucleotideSequence("CCCCCGTGAAAAAAAAT")
        translate(VMutations.mutate(VSequence1), withIncompleteCodon(2)) shouldBe
                AminoAcidSequence("_FEKVPP_")
        translate(JMutations.mutate(JSequence1), withIncompleteCodon(2)) shouldBe
                AminoAcidSequence("_PVKKN")
        translate(VSequence1, withIncompleteCodon(2)) shouldBe VSequence1A
        translate(JSequence1, withIncompleteCodon(2)) shouldBe JSequence1A

        mutationsDescription.nAlignment(FR3, GeneFeature(FR1Begin, VEnd)) should {
            requireNotNull(it)
            it.sequence1 shouldBe VSequence1
            it.sequence1.getRange(it.sequence1Range) shouldBe NucleotideSequence("AAAAAA")
            it.absoluteMutations shouldBe Mutations(NucleotideSequence.ALPHABET, "SA5G")
            it.relativeMutations.mutate(it.sequence1.getRange(it.sequence1Range)) shouldBe NucleotideSequence("GAAAAA")
        }
        mutationsDescription.aaAlignment(FR3, GeneFeature(FR1Begin, VEnd)) should {
            requireNotNull(it)
            it.sequence1 shouldBe AminoAcidSequence("_FKKGPP")
            it.sequence1.getRange(it.sequence1Range) shouldBe AminoAcidSequence("KK")
            it.absoluteMutations shouldBe Mutations(AminoAcidSequence.ALPHABET, "SK2E")
            it.relativeMutations.mutate(it.sequence1.getRange(it.sequence1Range)) shouldBe AminoAcidSequence("EK")
        }
        mutationsDescription.nAlignment(FR3, GeneFeature(FR3Begin, VEnd)) should {
            requireNotNull(it)
            it.sequence1 shouldBe NucleotideSequence("AAAAAAGGGCCCCCCCC")
            it.sequence1.getRange(it.sequence1Range) shouldBe NucleotideSequence("AAAAAA")
            it.absoluteMutations shouldBe Mutations(NucleotideSequence.ALPHABET, "SA0G")
            it.relativeMutations.mutate(it.sequence1.getRange(it.sequence1Range)) shouldBe NucleotideSequence("GAAAAA")
        }
        mutationsDescription.aaAlignment(FR3, GeneFeature(FR3Begin, VEnd)) should {
            requireNotNull(it)
            it.sequence1 shouldBe AminoAcidSequence("KKGPP")
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
            it.sequence1 shouldBe NucleotideSequence("AAAAAAGGGCCCCCCCC")
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
        mutationsDescription.nAlignment(GeneFeature(FR3Begin, FR4End), GeneFeature(FR1Begin, FR4End)) should {
            requireNotNull(it)
            it.sequence1 shouldBe NucleotideSequence("TTTTTAAAAAAGGGCCCAAAAAACCCGGGAAAAAAAAA")
            it.sequence1.getRange(it.sequence1Range) shouldBe NucleotideSequence("AAAAAAGGGCCCAAAAAACCCGGGAAAAAAAAA")
            it.absoluteMutations shouldBe Mutations(NucleotideSequence.ALPHABET, "SA5GSG12TSA18TSA21TSG27TSA37T")
            it.relativeMutations.mutate(it.sequence1.getRange(it.sequence1Range)) shouldBe
                    NucleotideSequence("GAAAAAGTGCCCATAATACCCGTGAAAAAAAAT")
        }
        mutationsDescription.aaAlignment(GeneFeature(FR3Begin, FR4End), GeneFeature(FR1Begin, FR4End)) should {
            requireNotNull(it)
            it.sequence1 shouldBe AminoAcidSequence("_FKKGPKKPGKKK")
            it.sequence1.getRange(it.sequence1Range) shouldBe AminoAcidSequence("KKGPKKPGKKK")
            it.absoluteMutations shouldBe Mutations(AminoAcidSequence.ALPHABET, "SK2ESG4VSK6ISK7ISG9VSK12N")
            it.relativeMutations.mutate(it.sequence1.getRange(it.sequence1Range)) shouldBe AminoAcidSequence("EKVPIIPVKKN")
        }
    }

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
    fun `export not covered feature`() {
        val VSequence1 = NucleotideSequence("AAAAAACCCCCCAAAAAAGGGCCCCCCCCC")
        val VMutations = Mutations(NucleotideSequence.ALPHABET, "SA12GSG19T")
        val baseNDN = NucleotideSequence("AAAAAA")
        val NDNMutations = Mutations(NucleotideSequence.ALPHABET, "SA1TSA4T")
        val JSequence1 = NucleotideSequence("CCCCCCGGGAAAAAAAAA")
        val JMutations = Mutations(NucleotideSequence.ALPHABET, "SG7TSA17T")
        val mutationsDescription = MutationsDescription(
            sortedMapOf(GeneFeature(FR3Begin, VEndTrimmed) to VMutations),
            VSequence1,
            ReferencePoints(ReferenceUtil.getReferencePointIndex(CDR2Begin), intArrayOf(0, 6, 12, 18, 30))
                .withVCDR3PartLength(6),
            baseNDN,
            NDNMutations,
            sortedMapOf(GeneFeature(JBeginTrimmed, FR4End) to JMutations),
            JSequence1,
            ReferencePoints(ReferenceUtil.getReferencePointIndex(JBegin), intArrayOf(0, 9, 18))
                .withJCDR3PartLength(6)
        )
        VMutations.mutate(VSequence1) shouldBe NucleotideSequence("AAAAAACCCCCCGAAAAAGTGCCCCCCCCC")
        JMutations.mutate(JSequence1) shouldBe NucleotideSequence("CCCCCCGTGAAAAAAAAT")

        mutationsDescription.nAlignment(CDR2) shouldBe null
        mutationsDescription.aaAlignment(CDR2) shouldBe null
        mutationsDescription.nAlignment(GeneFeature(CDR2Begin, CDR3Begin)) shouldBe null
        mutationsDescription.aaAlignment(GeneFeature(CDR2Begin, CDR3Begin)) shouldBe null

        mutationsDescription.nAlignment(CDR1) shouldBe null
        mutationsDescription.aaAlignment(CDR1) shouldBe null
        mutationsDescription.nAlignment(GeneFeature(CDR1Begin, CDR3Begin)) shouldBe null
        mutationsDescription.aaAlignment(GeneFeature(CDR1Begin, CDR3Begin)) shouldBe null

        mutationsDescription.nAlignment(VDJRegion) shouldBe null
        mutationsDescription.aaAlignment(VDJRegion) shouldBe null
        mutationsDescription.nAlignment(VDJRegion) shouldBe null
        mutationsDescription.aaAlignment(VDJRegion) shouldBe null
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
    fun `randomized test of broken Aa`() {
        RandomizedTest.randomized(::testBrokenAa, numberOfRuns = 100000)
    }

    @Test
    fun `reproduce test of broken Aa`() {
        RandomizedTest.reproduce(
            ::testBrokenAa,
            -3966494014459193821L, -1558052645563470479L
        )
    }

    private fun testBrokenAa(random: Random, print: Boolean) {
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
        RandomizedTest.randomized(::testMutationsProjection, numberOfRuns = 100_000)
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
        val VAaSequence = translate(VSequence1, FromLeftWithoutIncompleteCodon)
        val NDNMutations = random.generateMutations(baseNDN)
        val JMutations = random.generateMutations(JSequence1, Range(3, 12))
        val expectedNTarget = VMutations.mutate(VSequence1.getRange(0, 15))
        val expectedFullAaTarget = translate(
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
        val fullAaAlignment = mutationsDescription.aaAlignment(GeneFeature(CDR2Begin, FR4End))
        val CDR2AaAlignment = mutationsDescription.aaAlignment(CDR2)
        val FR3AaAlignment = mutationsDescription.aaAlignment(FR3)
        val CDR3AaAlignment = mutationsDescription.aaAlignment(CDR3)
        val FR4AaAlignment = mutationsDescription.aaAlignment(FR4)
        checkNotNull(fullAaAlignment)
        checkNotNull(CDR2AaAlignment)
        checkNotNull(FR3AaAlignment)
        checkNotNull(CDR3AaAlignment)
        checkNotNull(FR4AaAlignment)
        if (print) {
            println("                  VAaSequence1: $VAaSequence")
            println(
                "                  VAaMutations: ${
                    MutationsUtil.nt2aa(
                        VSequence1,
                        VMutations,
                        FromLeftWithoutIncompleteCodon
                    )
                }"
            )
            println("expected fullMutatedAaSegment: $expectedFullAaTarget")
            println("  actual fullMutatedAaSegment: ${fullAaAlignment.target}")
            println("composed fullMutatedAaSegment: ${CDR2AaAlignment.target} ${FR3AaAlignment.target} ${CDR3AaAlignment.target} ${FR4AaAlignment.target}")
            println("          [CDR2Begin, FR4End]:")
            println("$fullAaAlignment")
            println("                         CDR2:")
            println("$CDR2AaAlignment")
            println("                          FR3:")
            println("$FR3AaAlignment")
            println("                         CDR3:")
            println("$CDR3AaAlignment")
            println("                          FR4:")
            println("$FR4AaAlignment")
        }
        fullVNAlignment.target shouldBe expectedNTarget
        (CDR2NAlignment.target + FR3NAlignment.target + VCDR3PartNAlignment.target) shouldBe expectedNTarget
        fullAaAlignment.target shouldBe expectedFullAaTarget
        (CDR2AaAlignment.target + FR3AaAlignment.target + CDR3AaAlignment.target + FR4AaAlignment.target) shouldBe expectedFullAaTarget
    }

    @Test
    fun `randomized test of difference`() {
        RandomizedTest.randomized(::testDifference, numberOfRuns = 100_000)
    }

    @Test
    fun `reproduce test of difference`() {
        RandomizedTest.reproduce(
            ::testDifference,
            6188717389695233857L,
            -1498261129221652450L,
            -1391451482407814944L,
            -806612386108641232L,
            7885745501533895298L,
            3918346117227262916L,
            -9080740558421995995L,
            -8984092378964770538L,
            -7911449512021046160L,
            -1011466467301499251L,
            -8258866350102008211L,
            -5108916171340241338L,
            -5514327963004606929L,
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
        if (!isValid(VSequence1, firstVMutations, baseNDN, firstNDNMutations, JSequence1, firstJMutations)) {
            return
        }
        val secondVMutations = random.generateMutations(VSequence1, Range(0, 15))
        val secondNDNMutations = random.generateMutations(baseNDN)
        val secondJMutations = random.generateMutations(JSequence1, Range(3, 12))

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


        val fromFirstToSecond = firstMutationsDescription.differenceWith(secondMutationsDescription)
        val fullVNAlignmentFromFirstToSecond = fromFirstToSecond.nAlignment(GeneFeature(CDR2Begin, VEndTrimmed))
        val CDR2NAlignmentFromFirstToSecond = fromFirstToSecond.nAlignment(CDR2)
        val FR3NAlignmentFromFirstToSecond = fromFirstToSecond.nAlignment(FR3)
        val VCDR3PartNAlignmentFromFirstToSecond = fromFirstToSecond.nAlignment(VCDR3Part)
        checkNotNull(fullVNAlignmentFromFirstToSecond)
        checkNotNull(CDR2NAlignmentFromFirstToSecond)
        checkNotNull(FR3NAlignmentFromFirstToSecond)
        checkNotNull(VCDR3PartNAlignmentFromFirstToSecond)

        val expectedVNTargetOfFirst = firstVMutations.mutate(VSequence1.getRange(0, 15))
        val expectedVNTargetOfSecond = secondVMutations.mutate(VSequence1.getRange(0, 15))
        if (print) {
            println("                            VNSequence1: $VSequence1")
            println("                      first VNMutations: $firstVMutations")
            println("                     second VNMutations: $secondVMutations")
            println()
            println(" expected fullMutatedVNSegment of first: $expectedVNTargetOfFirst")
            println("   actual fullMutatedVNSegment of first: ${fullVNAlignmentFromFirstToSecond.subsequence1}")
            println(" composed fullMutatedVNSegment of first: ${CDR2NAlignmentFromFirstToSecond.subsequence1} ${FR3NAlignmentFromFirstToSecond.subsequence1} ${VCDR3PartNAlignmentFromFirstToSecond.subsequence1}")
            println()
            println("expected fullMutatedVNSegment of second: $expectedVNTargetOfSecond")
            println("  actual fullMutatedVNSegment of second: ${fullVNAlignmentFromFirstToSecond.target}")
            println("composed fullMutatedVNSegment of second: ${CDR2NAlignmentFromFirstToSecond.target} ${FR3NAlignmentFromFirstToSecond.target} ${VCDR3PartNAlignmentFromFirstToSecond.target}")
        }
        fullVNAlignmentFromFirstToSecond.subsequence1 shouldBe expectedVNTargetOfFirst
        (CDR2NAlignmentFromFirstToSecond.subsequence1 + FR3NAlignmentFromFirstToSecond.subsequence1 + VCDR3PartNAlignmentFromFirstToSecond.subsequence1) shouldBe expectedVNTargetOfFirst

        fullVNAlignmentFromFirstToSecond.target shouldBe expectedVNTargetOfSecond
        (CDR2NAlignmentFromFirstToSecond.target + FR3NAlignmentFromFirstToSecond.target + VCDR3PartNAlignmentFromFirstToSecond.target) shouldBe expectedVNTargetOfSecond


        val fullJNAlignmentFromFirstToSecond = fromFirstToSecond.nAlignment(GeneFeature(JBeginTrimmed, FR4End))
        val JCDR3PartNAlignmentFromFirstToSecond = fromFirstToSecond.nAlignment(JCDR3Part)
        val FR4NAlignmentFromFirstToSecond = fromFirstToSecond.nAlignment(FR4)
        checkNotNull(fullJNAlignmentFromFirstToSecond)
        checkNotNull(JCDR3PartNAlignmentFromFirstToSecond)
        checkNotNull(FR4NAlignmentFromFirstToSecond)

        val expectedJNTargetOfFirst = buildSequence(JSequence1, firstJMutations, Range(3, 12), true)
        val expectedJNTargetOfSecond = buildSequence(JSequence1, secondJMutations, Range(3, 12), true)
        if (print) {
            println()
            println("                            JNSequence1: $JSequence1")
            println("                      first JNMutations: $firstJMutations")
            println("                     second JNMutations: $secondJMutations")
            println()
            println(" expected fullMutatedJNSegment of first: $expectedJNTargetOfFirst")
            println("   actual fullMutatedJNSegment of first: ${fullJNAlignmentFromFirstToSecond.subsequence1}")
            println(" composed fullMutatedJNSegment of first: ${JCDR3PartNAlignmentFromFirstToSecond.subsequence1} ${FR4NAlignmentFromFirstToSecond.subsequence1}")
            println()
            println("expected fullMutatedJNSegment of second: $expectedJNTargetOfSecond")
            println("  actual fullMutatedJNSegment of second: ${fullJNAlignmentFromFirstToSecond.target}")
            println("composed fullMutatedJNSegment of second: ${JCDR3PartNAlignmentFromFirstToSecond.target} ${FR4NAlignmentFromFirstToSecond.target}")
        }
        fullJNAlignmentFromFirstToSecond.subsequence1 shouldBe expectedJNTargetOfFirst
        (JCDR3PartNAlignmentFromFirstToSecond.subsequence1 + FR4NAlignmentFromFirstToSecond.subsequence1) shouldBe expectedJNTargetOfFirst

        fullJNAlignmentFromFirstToSecond.target shouldBe expectedJNTargetOfSecond
        (JCDR3PartNAlignmentFromFirstToSecond.target + FR4NAlignmentFromFirstToSecond.target) shouldBe expectedJNTargetOfSecond


        val fullVAaAlignmentOfFirst = firstMutationsDescription.aaAlignment(GeneFeature(CDR2Begin, FR4End))
        val CDR2AaAlignmentOfFirst = firstMutationsDescription.aaAlignment(CDR2)
        val FR3AaAlignmentOfFirst = firstMutationsDescription.aaAlignment(FR3)
        val CDR3AaAlignmentOfFirst = firstMutationsDescription.aaAlignment(CDR3)
        val FR4AaAlignmentOfFirst = firstMutationsDescription.aaAlignment(FR4)
        checkNotNull(fullVAaAlignmentOfFirst)
        checkNotNull(CDR2AaAlignmentOfFirst)
        checkNotNull(FR3AaAlignmentOfFirst)
        checkNotNull(CDR3AaAlignmentOfFirst)
        checkNotNull(FR4AaAlignmentOfFirst)
        val fullVAaAlignmentOfSecond = secondMutationsDescription.aaAlignment(GeneFeature(CDR2Begin, FR4End))
        val CDR2AaAlignmentOfSecond = secondMutationsDescription.aaAlignment(CDR2)
        val FR3AaAlignmentOfSecond = secondMutationsDescription.aaAlignment(FR3)
        val CDR3AaAlignmentOfSecond = secondMutationsDescription.aaAlignment(CDR3)
        val FR4AaAlignmentOfSecond = secondMutationsDescription.aaAlignment(FR4)
        checkNotNull(fullVAaAlignmentOfSecond)
        checkNotNull(CDR2AaAlignmentOfSecond)
        checkNotNull(FR3AaAlignmentOfSecond)
        checkNotNull(CDR3AaAlignmentOfSecond)
        checkNotNull(FR4AaAlignmentOfSecond)
        val CDR2AaAlignmentFromFirstToSecond = fromFirstToSecond.aaAlignment(CDR2)
        val FR3AaAlignmentFromFirstToSecond = fromFirstToSecond.aaAlignment(FR3)
        val CDR3AaAlignmentFromFirstToSecond = fromFirstToSecond.aaAlignment(CDR3)
        val FR4AaAlignmentFromFirstToSecond = fromFirstToSecond.aaAlignment(FR4)
        checkNotNull(CDR2AaAlignmentFromFirstToSecond)
        checkNotNull(FR3AaAlignmentFromFirstToSecond)
        checkNotNull(CDR3AaAlignmentFromFirstToSecond)
        checkNotNull(FR4AaAlignmentFromFirstToSecond)

        val CDR2_FR3_AaAlignmentFromFirstToSecond = fromFirstToSecond.aaAlignment(GeneFeature(CDR2Begin, CDR3Begin))
        checkNotNull(CDR2_FR3_AaAlignmentFromFirstToSecond)
        val firstCDR2_FR3_AaMutations = MutationsUtil.nt2aa(
            VSequence1,
            firstVMutations.extractAbsoluteMutations(Range(0, 12), isIncludeFirstInserts = true),
            FromLeftWithIncompleteCodon
        )
        val secondCDR2_FR3_AaMutations = MutationsUtil.nt2aa(
            VSequence1,
            secondVMutations.extractAbsoluteMutations(Range(0, 12), isIncludeFirstInserts = true),
            FromLeftWithIncompleteCodon
        )
        val expectedCDR3_FR3_AaTargetOfFirst = firstCDR2_FR3_AaMutations.mutate(translate(VSequence1.getRange(0, 12)))
        val expectedCDR3_FR3_AaTargetOfSecond = secondCDR2_FR3_AaMutations.mutate(translate(VSequence1.getRange(0, 12)))
        if (print) {
            println()
            println("                            VAaSequence1: ${translate(VSequence1.getRange(0, 12))}")
            println("                      first VAaMutations: $firstCDR2_FR3_AaMutations")
            println("                     second VAaMutations: $secondCDR2_FR3_AaMutations")
            println()
            println(" expected fullMutatedVAaSegment of first: $expectedCDR3_FR3_AaTargetOfFirst")
            println("   actual fullMutatedVAaSegment of first: ${CDR2_FR3_AaAlignmentFromFirstToSecond.subsequence1}")
            println(" composed fullMutatedVAaSegment of first: ${CDR2AaAlignmentFromFirstToSecond.subsequence1} ${FR3AaAlignmentFromFirstToSecond.subsequence1}")
            println()
            println("expected fullMutatedVAaSegment of second: $expectedCDR3_FR3_AaTargetOfSecond")
            println("  actual fullMutatedVAaSegment of second: ${CDR2_FR3_AaAlignmentFromFirstToSecond.target}")
            println("composed fullMutatedVAaSegment of second: ${CDR2AaAlignmentFromFirstToSecond.target} ${FR3AaAlignmentFromFirstToSecond.target}")
        }
        CDR2_FR3_AaAlignmentFromFirstToSecond.subsequence1 shouldBe expectedCDR3_FR3_AaTargetOfFirst
        (CDR2AaAlignmentFromFirstToSecond.subsequence1 + FR3AaAlignmentFromFirstToSecond.subsequence1) shouldBe expectedCDR3_FR3_AaTargetOfFirst

        CDR2_FR3_AaAlignmentFromFirstToSecond.target shouldBe expectedCDR3_FR3_AaTargetOfSecond
        (CDR2AaAlignmentFromFirstToSecond.target + FR3AaAlignmentFromFirstToSecond.target) shouldBe expectedCDR3_FR3_AaTargetOfSecond

        val firstFR4_AaMutations = MutationsUtil.nt2aa(
            JSequence1.getRange(6, 12),
            firstJMutations.extractAbsoluteMutations(Range(6, 12), isIncludeFirstInserts = false).move(-6),
            FromLeftWithIncompleteCodon
        )
        val secondFR4_AaMutations = MutationsUtil.nt2aa(
            JSequence1.getRange(6, 12),
            secondJMutations.extractAbsoluteMutations(Range(6, 12), isIncludeFirstInserts = false).move(-6),
            FromLeftWithIncompleteCodon
        )
        val expectedFR4_AaTargetOfFirst = firstFR4_AaMutations.mutate(translate(JSequence1.getRange(6, 12)))
        val expectedFR4_AaTargetOfSecond = secondFR4_AaMutations.mutate(translate(JSequence1.getRange(6, 12)))
        if (print) {
            println()
            println("                            JAaSequence1: ${translate(JSequence1.getRange(6, 12))}")
            println("                      first JAaMutations: $firstFR4_AaMutations")
            println("                     second JAaMutations: $secondFR4_AaMutations")
            println()
            println(" expected fullMutatedJAaSegment of first: $expectedFR4_AaTargetOfFirst")
            println("   actual fullMutatedJAaSegment of first: ${FR4AaAlignmentFromFirstToSecond.subsequence1}")
            println()
            println("expected fullMutatedJAaSegment of second: $expectedFR4_AaTargetOfSecond")
            println("  actual fullMutatedJAaSegment of second: ${FR4AaAlignmentFromFirstToSecond.target}")
        }
        FR4AaAlignmentFromFirstToSecond.subsequence1 shouldBe expectedFR4_AaTargetOfFirst

        FR4AaAlignmentFromFirstToSecond.target shouldBe expectedFR4_AaTargetOfSecond

        val expectedNTargetOfFirst = buildSequence(VSequence1, firstVMutations, Range(0, 15), true) +
                firstNDNMutations.mutate(baseNDN) +
                buildSequence(JSequence1, firstJMutations, Range(3, 12), true)
        val expectedNTargetOfSecond = buildSequence(VSequence1, secondVMutations, Range(0, 15), true) +
                secondNDNMutations.mutate(baseNDN) +
                buildSequence(JSequence1, secondJMutations, Range(3, 12), true)
        val fullNAlignmentFromFirstToSecond = fromFirstToSecond.nAlignment(GeneFeature(CDR2Begin, FR4End))
        val CDR3NAlignmentFromFirstToSecond = fromFirstToSecond.nAlignment(CDR3)
        checkNotNull(fullNAlignmentFromFirstToSecond)
        checkNotNull(CDR3NAlignmentFromFirstToSecond)

        if (print) {
            println()
            println(" expected fullMutatedNSegment of first: $expectedNTargetOfFirst")
            println("   actual fullMutatedNSegment of first: ${fullNAlignmentFromFirstToSecond.subsequence1}")
            println(" composed fullMutatedNSegment of first: ${CDR2NAlignmentFromFirstToSecond.subsequence1} ${FR3NAlignmentFromFirstToSecond.subsequence1} ${CDR3NAlignmentFromFirstToSecond.subsequence1} ${FR4NAlignmentFromFirstToSecond.subsequence1}")
            println()
            println("expected fullMutatedNSegment of second: $expectedNTargetOfSecond")
            println("  actual fullMutatedNSegment of second: ${fullNAlignmentFromFirstToSecond.target}")
            println("composed fullMutatedNSegment of second: ${CDR2NAlignmentFromFirstToSecond.target} ${FR3NAlignmentFromFirstToSecond.target} ${CDR3NAlignmentFromFirstToSecond.target} ${FR4NAlignmentFromFirstToSecond.target}")
        }
        fullNAlignmentFromFirstToSecond.subsequence1 shouldBe expectedNTargetOfFirst
        (CDR2NAlignmentFromFirstToSecond.subsequence1 + FR3NAlignmentFromFirstToSecond.subsequence1 + CDR3NAlignmentFromFirstToSecond.subsequence1 + FR4NAlignmentFromFirstToSecond.subsequence1) shouldBe expectedNTargetOfFirst

        fullNAlignmentFromFirstToSecond.target shouldBe expectedNTargetOfSecond
        (CDR2NAlignmentFromFirstToSecond.target + FR3NAlignmentFromFirstToSecond.target + CDR3NAlignmentFromFirstToSecond.target + FR4NAlignmentFromFirstToSecond.target) shouldBe expectedNTargetOfSecond


        val fullAaAlignmentFromFirstToSecond = fromFirstToSecond.aaAlignment(GeneFeature(CDR2Begin, FR4End))
        checkNotNull(fullAaAlignmentFromFirstToSecond)

        val expectedAaTargetOfFirst = translate(expectedNTargetOfFirst, FromLeftWithIncompleteCodon)
        val expectedAaTargetOfSecond = translate(expectedNTargetOfSecond, FromLeftWithIncompleteCodon)
        if (print) {
            println()
            println(" expected fullMutatedAaSegment of first: $expectedAaTargetOfFirst")
            println("   actual fullMutatedAaSegment of first: ${fullAaAlignmentFromFirstToSecond.subsequence1}")
            println(" composed fullMutatedAaSegment of first: ${CDR2AaAlignmentFromFirstToSecond.subsequence1} ${FR3AaAlignmentFromFirstToSecond.subsequence1} ${CDR3AaAlignmentFromFirstToSecond.subsequence1} ${FR4AaAlignmentFromFirstToSecond.subsequence1}")
            println()
            println("expected fullMutatedAaSegment of second: $expectedAaTargetOfSecond")
            println("  actual fullMutatedAaSegment of second: ${fullAaAlignmentFromFirstToSecond.target}")
            println("composed fullMutatedAaSegment of second: ${CDR2AaAlignmentFromFirstToSecond.target} ${FR3AaAlignmentFromFirstToSecond.target} ${CDR3AaAlignmentFromFirstToSecond.target} ${FR4AaAlignmentFromFirstToSecond.target}")
        }
        fullAaAlignmentFromFirstToSecond.subsequence1 shouldBe expectedAaTargetOfFirst
        (CDR2AaAlignmentFromFirstToSecond.subsequence1 + FR3AaAlignmentFromFirstToSecond.subsequence1 + CDR3AaAlignmentFromFirstToSecond.subsequence1 + FR4AaAlignmentFromFirstToSecond.subsequence1) shouldBe expectedAaTargetOfFirst

        fullAaAlignmentFromFirstToSecond.target shouldBe expectedAaTargetOfSecond
        (CDR2AaAlignmentFromFirstToSecond.target + FR3AaAlignmentFromFirstToSecond.target + CDR3AaAlignmentFromFirstToSecond.target + FR4AaAlignmentFromFirstToSecond.target) shouldBe expectedAaTargetOfSecond
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
        if (VMutations.asSequence().count { Mutation.getPosition(it) == 15 && Mutation.isInDel(it) } > 0) {
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
        if (JMutations.extractAbsoluteMutations(Range(0, 6), true).lengthDelta % 3 != 0) {
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
        if (JMutations.asSequence().count { Mutation.getPosition(it) == 3 && Mutation.isInDel(it) } > 0) {
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
