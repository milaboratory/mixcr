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
import com.milaboratory.mixcr.basictypes.MultiAlignmentHelper.Input
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsFormatter.Companion.makeQualityLine
import io.kotest.matchers.collections.shouldContainExactly
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
        val inputs = arrayOf(
            Input(
                "Query0",
                Aligner.alignLocalAffine(AffineGapAlignmentScoring.getNucleotideBLASTScoring(), seq0, seq1),
                null
            ),
            Input(
                "Query1",
                Aligner.alignGlobalAffine(AffineGapAlignmentScoring.getNucleotideBLASTScoring(), seq0, seq1),
                null
            ),
            Input(
                "Query2",
                Aligner.alignLocalAffine(AffineGapAlignmentScoring.getNucleotideBLASTScoring(), seq0, seq2),
                null
            ),
            Input(
                "Query3",
                Aligner.alignGlobalAffine(AffineGapAlignmentScoring.getNucleotideBLASTScoring(), seq0, seq2),
                null
            )
        )
        for (input in inputs) {
            println(input.alignment.alignmentHelper)
            println()
        }
        val helper = MultiAlignmentHelper.build(
            MultiAlignmentHelper.DEFAULT_SETTINGS, Range(0, seq0.size()),
            leftTitle = "Subject",
            rightTitle = "",
            *inputs
        )
        helper.addAnnotation(helper.makeQualityLine("Quality", seq0qual))

        helper.formatLines().also { println(it) }.lines().map { it.trimEnd() } shouldContainExactly """
|Quality   78778     878777   7778887878      
|Subject 0 GATAC-----ATTAGA---CACAGATACA--- 20 
| Query0 0              aga---cacaTataca    12
| Query1 0 -------------aga---cacaTatacaCAG 15
| Query2 5 gatac-----attagaGACcacagataca    28
| Query3 0 gatacGATACattagaGACcacagataca    28
        """.trimMargin().lines().map { it.trimEnd() }

        val expectedSplit = listOf(
            """
|Quality   78778  
|Subject 0 GATAC 4 
| Query1 0 ----- 0
| Query2 5 gatac 9
| Query3 0 gatac 4
        """.trimMargin(),
            """
|Quality            
|Subject  5 ----- 5  
| Query1  0 ----- 0 
| Query2 10 ----- 10
| Query3  5 GATAC 9 
            """.trimMargin(),
            """
|Quality    87877   
|Subject  5 ATTAG 9  
| Query0  0    ag 1 
| Query1  0 ---ag 1 
| Query2 10 attag 14
| Query3 10 attag 14
""".trimMargin(),
            """
|Quality    7   7   
|Subject 10 A---C 11 
| Query0  2 a---c 3 
| Query1  2 a---c 3 
| Query2 15 aGACc 19
| Query3 15 aGACc 19
            """.trimMargin(),
            """
|Quality    77888   
|Subject 12 ACAGA 16 
| Query0  4 acaTa 8 
| Query1  4 acaTa 8 
| Query2 20 acaga 24
| Query3 20 acaga 24
            """.trimMargin(),
            """
|Quality    7878    
|Subject 17 TACA- 20 
| Query0  9 taca  12
| Query1  9 tacaC 13
| Query2 25 taca  28
| Query3 25 taca  28
            """.trimMargin(),
            """
|Quality         
|Subject 21 -- 21 
| Query1 14 AG 15
            """.trimMargin()
        )

        helper.split(5).forEachIndexed { index, spl ->
            spl.formatLines().also { println(it) }.lines().map { it.trimEnd() } shouldContainExactly
                    expectedSplit[index].lines().map { it.trimEnd() }
        }

        MultiAlignmentHelper.build(
            MultiAlignmentHelper.DOT_MATCH_SETTINGS, Range(0, seq0.size()),
            leftTitle = "",
            rightTitle = "",
            *inputs
        ).formatLines().also { println(it) }.lines().map { it.trimEnd() } shouldContainExactly """
|       0 GATAC-----ATTAGA---CACAGATACA--- 20 
|Query0 0              ...---....T.....    12
|Query1 0 -------------...---....T.....CAG 15
|Query2 5 .....-----......GAC..........    28
|Query3 0 .....GATAC......GAC..........    28
        """.trimMargin().lines().map { it.trimEnd() }

        run {
            val build = MultiAlignmentHelper.build(
                MultiAlignmentHelper.DOT_MATCH_SETTINGS, Range(0, seq0.size()),
                leftTitle = "",
                rightTitle = "",
                seq0
            )
            build.addAnnotation(build.makeQualityLine("", seq0qual))
                .formatLines().also { println(it) }.lines().map { it.trimEnd() }
        } shouldContainExactly """
|   787788787777778887878   
| 0 GATACATTAGACACAGATACA 20 
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
        val inputs = arrayOf(
            Input(
                "",
                Aligner.alignLocalAffine(AffineGapAlignmentScoring.getNucleotideBLASTScoring(), seq0, seq1),
                null
            ),
            Input(
                "",
                Aligner.alignLocalAffine(AffineGapAlignmentScoring.getNucleotideBLASTScoring(), seq0, seq2),
                null
            ),
            Input(
                "",
                Aligner.alignLocalAffine(AffineGapAlignmentScoring.getNucleotideBLASTScoring(), seq0, seq3),
                null
            )
        )
        for (input in inputs) {
            println(input.alignment.alignmentHelper)
            println()
        }
        MultiAlignmentHelper.build(
            MultiAlignmentHelper.DEFAULT_SETTINGS,
            Range(0, seq0.size()),
            leftTitle = "",
            rightTitle = "",
            *inputs
        ).formatLines().lines().also { println(it) }.map { it.trimEnd() } shouldContainExactly """
| 0 AACGATGGGCGCAAATATAGGGAGAACTCCGATCGACATCGGGTATCGCCCTGGTACGATCC--CGGTGACAAAGCGTTCGGACCTGTCTGGACGCTAGAACGC 101 
| 0                tatagggag--ctccgatcgacatcg                                                                23 
| 0                                                         cgatccTTcggtgacaaagcgttcggacc                    28 
| 0                                     catcAggtatcgccctggtacg                                               21 
        """.trimMargin().lines().map { it.trimEnd() }
    }
}
