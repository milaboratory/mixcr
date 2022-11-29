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
package com.milaboratory.mixcr.basictypes

import com.milaboratory.core.Range
import com.milaboratory.core.alignment.AffineGapAlignmentScoring
import com.milaboratory.core.alignment.Aligner
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.core.sequence.SequenceQuality
import io.kotest.matchers.collections.shouldContainInOrder
import org.junit.Test

/**
 * Created by dbolotin on 03/09/15.
 */
class MultiAlignmentHelperTest {
    @Test
    fun test1() {
        val seq0qual = SequenceQuality("GIHGIIHIHFHGHGIIIGKHK")
        val seq0 = NucleotideSequence("GATACATTAGACACAGATACA")
        val seq1 = NucleotideSequence("AGACACATATACACAG")
        val seq2 = NucleotideSequence("GATACGATACATTAGAGACCACAGATACA")
        val inputs = listOf(
            MultiAlignmentHelper.AlignmentInput(
                "Query0",
                Aligner.alignLocalAffine(AffineGapAlignmentScoring.getNucleotideBLASTScoring(), seq0, seq1),
                10,
                20
            ),
            MultiAlignmentHelper.AlignmentInput(
                "Query1",
                Aligner.alignGlobalAffine(AffineGapAlignmentScoring.getNucleotideBLASTScoring(), seq0, seq1),
                12,
                30
            ),
            MultiAlignmentHelper.AlignmentInput(
                "Query2",
                Aligner.alignLocalAffine(AffineGapAlignmentScoring.getNucleotideBLASTScoring(), seq0, seq2),
                14,
                30
            ),
            MultiAlignmentHelper.AlignmentInput(
                "Query3",
                Aligner.alignGlobalAffine(AffineGapAlignmentScoring.getNucleotideBLASTScoring(), seq0, seq2),
                16,
                30
            )
        )
        for (input in inputs) {
            println(input.alignment.alignmentHelper)
            println()
        }
        val helper = MultiAlignmentHelper.build(
            MultiAlignmentHelper.DEFAULT_SETTINGS, Range(0, seq0.size()),
            name = "Subject",
            inputs.first().alignment.sequence1,
            inputs,
            listOf(MultiAlignmentHelper.QualityInput(seq0qual))
        )

        helper.format().also { println(it) }.lines()
            .map { it.trimEnd() } shouldContainInOrder """
|Quality   78778     878777   7778887878      
|Subject 0 GATAC-----ATTAGA---CACAGATACA--- 20  Score 
| Query0 0              aga---cacaTataca    12  10
| Query1 0 -------------aga---cacaTatacaCAG 15  12
| Query2 5 gatac-----attagaGACcacagataca    28  14
| Query3 0 gatacGATACattagaGACcacagataca    28  16
        """.trimMargin().lines().map { it.trimEnd() }

        val expectedSplit = listOf(
            """
|Quality   78778  
|Subject 0 GATAC 4  Score
| Query1 0 ----- 0  12
| Query2 5 gatac 9  14
| Query3 0 gatac 4  16
        """.trimMargin(),
            """
|Quality            
|Subject  5 ----- 5   Score
| Query1  0 ----- 0   12
| Query2 10 ----- 10  14
| Query3  5 GATAC 9   16
            """.trimMargin(),
            """
|Quality    87877   
|Subject  5 ATTAG 9   Score
| Query0  0    ag 1   10 
| Query1  0 ---ag 1   12
| Query2 10 attag 14  14
| Query3 10 attag 14  16
""".trimMargin(),
            """
|Quality    7   7   
|Subject 10 A---C 11  Score
| Query0  2 a---c 3   10
| Query1  2 a---c 3   12
| Query2 15 aGACc 19  14
| Query3 15 aGACc 19  16
            """.trimMargin(),
            """
|Quality    77888   
|Subject 12 ACAGA 16  Score
| Query0  4 acaTa 8   10
| Query1  4 acaTa 8   12
| Query2 20 acaga 24  14
| Query3 20 acaga 24  16
            """.trimMargin(),
            """
|Quality    7878    
|Subject 17 TACA- 20  Score
| Query0  9 taca  12  10
| Query1  9 tacaC 13  12
| Query2 25 taca  28  14
| Query3 25 taca  28  16
            """.trimMargin(),
            """
|Quality         
|Subject 21 -- 21  Score
| Query1 14 AG 15  12
            """.trimMargin()
        )

        helper.split(5).forEachIndexed { index, spl ->
            spl.format().also { println(it) }.lines()
                .map { it.trimEnd() } shouldContainInOrder
                    expectedSplit[index].lines().map { it.trimEnd() }
        }

        MultiAlignmentHelper.build(
            MultiAlignmentHelper.DOT_MATCH_SETTINGS, Range(0, seq0.size()),
            name = "",
            inputs.first().alignment.sequence1,
            inputs,
            emptyList()
        ).format().also { println(it) }.lines()
            .map { it.trimEnd() } shouldContainInOrder """
|       0 GATAC-----ATTAGA---CACAGATACA--- 20  Score 
|Query0 0              ...---....T.....    12  10
|Query1 0 -------------...---....T.....CAG 15  12
|Query2 5 .....-----......GAC..........    28  14
|Query3 0 .....GATAC......GAC..........    28  16
        """.trimMargin().lines().map { it.trimEnd() }

        val build = MultiAlignmentHelper.build(
            MultiAlignmentHelper.DOT_MATCH_SETTINGS, Range(0, seq0.size()),
            name = "",
            seq0,
            emptyList(),
            listOf(MultiAlignmentHelper.QualityInput(seq0qual))
        )
        build
            .format().also { println(it) }.lines()
            .map { it.trimEnd() } shouldContainInOrder """
|Quality   787788787777778887878   
|        0 GATACATTAGACACAGATACA 20  Score
            """.trimMargin().lines().map { it.trimEnd() }
    }

    @Test
    fun test2() {
        // AACGATGGGCGCAAATATAGGGAGAACTCCGATCGACATCGGGTATCGCCCTGGTACGATCC--CGGTGACAAAGCGTTCGGACCTGTCTGGACGCTAGAACGC
        //                TATAGGGAG--CTCCGATCTACATCG
        //                                                         CGATCCTTCGGTGACAAAGCGTTCTGACC
        //                                     CATCAGGTATCGCCCTGGTACG
        val seq0 =
            NucleotideSequence("AACGATGGGCGCAAATATAGGGAGAACTCCGATCGACATCGGGTATCGCCCTGGTACGATCCCGGTGACAAAGCGTTCGGACCTGTCTGGACGCTAGAACGC")
        val seq1 = NucleotideSequence("TATAGGGAGCTCCGATCGACATCG")
        val seq2 = NucleotideSequence("CGATCCTTCGGTGACAAAGCGTTCGGACC")
        val seq3 = NucleotideSequence("CATCAGGTATCGCCCTGGTACG")
        val inputs = listOf(
            MultiAlignmentHelper.AlignmentInput(
                "",
                Aligner.alignLocalAffine(AffineGapAlignmentScoring.getNucleotideBLASTScoring(), seq0, seq1),
                10,
                20
            ),
            MultiAlignmentHelper.AlignmentInput(
                "",
                Aligner.alignLocalAffine(AffineGapAlignmentScoring.getNucleotideBLASTScoring(), seq0, seq2),
                12,
                30
            ),
            MultiAlignmentHelper.AlignmentInput(
                "",
                Aligner.alignLocalAffine(AffineGapAlignmentScoring.getNucleotideBLASTScoring(), seq0, seq3),
                14,
                30
            )
        )
        for (input in inputs) {
            println(input.alignment.alignmentHelper)
            println()
        }
        MultiAlignmentHelper.build(
            MultiAlignmentHelper.DEFAULT_SETTINGS,
            Range(0, seq0.size()),
            name = "",
            inputs.first().alignment.sequence1,
            inputs,
            emptyList()
        ).format().also { println(it) }.lines()
            .map { it.trimEnd() } shouldContainInOrder """
| 0 AACGATGGGCGCAAATATAGGGAGAACTCCGATCGACATCGGGTATCGCCCTGGTACGATCC--CGGTGACAAAGCGTTCGGACCTGTCTGGACGCTAGAACGC 101  Score
| 0                tatagggag--ctccgatcgacatcg                                                                23   10
| 0                                                         cgatccTTcggtgacaaagcgttcggacc                    28   12
| 0                                     catcAggtatcgccctggtacg                                               21   14
        """.trimMargin().lines().map { it.trimEnd() }
    }
}
