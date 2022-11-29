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
import com.milaboratory.core.alignment.Alignment
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.basictypes.MultiAlignmentHelper.Builder.build
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldStartWith
import io.repseq.core.ExtendedReferencePointsBuilder
import io.repseq.core.ReferencePoint
import org.junit.Test

class VDJCAlignmentsFormatterTest {
    var show = true

    @Test
    fun test0() {
        testWithoutLeftover(
            "GGTTCGGGGACCAGGTTAACCGTTGTAGGG",
            ReferencePoint.CDR3Begin, ReferencePoint.CDR3End
        ) shouldEndWith " G "
        testWithoutLeftover(
            "GGTTCGGGGACCAGGTAACCGTTGTAGGG",
            ReferencePoint.CDR3Begin, ReferencePoint.CDR3End
        ) shouldEndWith " G "
        testWithoutLeftover(
            "GGTTCGGGGACCAGGAACCGTTGTAGGG",
            ReferencePoint.CDR3Begin, ReferencePoint.CDR3End
        ) shouldEndWith " G "
    }

    @Test
    fun test1() {
        testWithoutLeftover(
            "GGTTCGGGGACCAGGTTAACCGTTGTAGGG",
            ReferencePoint.CDR3Begin, ReferencePoint.VEndTrimmed
        ) shouldEndWith " G "
        testWithoutLeftover(
            "GGTTCGGGGACCAGGTTAACCGTTGTAGG",
            ReferencePoint.CDR3Begin, ReferencePoint.VEndTrimmed
        ) shouldEndWith "_ "
        testWithoutLeftover(
            "GGTTCGGGGACCAGGTTAACCGTTGTAG",
            ReferencePoint.CDR3Begin, ReferencePoint.VEndTrimmed
        ) shouldEndWith "_"
    }

    @Test
    fun test2() {
        testWithoutLeftover(
            "GGTTCGGGGACCAGGTTAACCGTTGTAGGG",
            ReferencePoint.JBeginTrimmed, ReferencePoint.CDR3End
        ) shouldStartWith " G "
        testWithoutLeftover(
            "GTTCGGGGACCAGGTTAACCGTTGTAGGG",
            ReferencePoint.JBeginTrimmed, ReferencePoint.CDR3End
        ) shouldStartWith "_ "
        testWithoutLeftover(
            "TTCGGGGACCAGGTTAACCGTTGTAGGG",
            ReferencePoint.JBeginTrimmed, ReferencePoint.CDR3End
        ) shouldStartWith "_"
    }

    @Test
    fun test3() {
        testWithLeftover(
            "GGTTCGGGGACCAGGTTAACCGTTGTAGG", "G",
            ReferencePoint.L1Begin, ReferencePoint.L1End, ReferencePoint.L2Begin, ReferencePoint.L2End
        )
        // Assert.assertTrue(testWithoutLeftover("GGTTCGGGGACCAGGTTAACCGTTGTAGGG",
        //         ReferencePoint.JBeginTrimmed, ReferencePoint.CDR3End).startsWith(" G "));
        // Assert.assertTrue(testWithoutLeftover("GGTTCGGGGACCAGGTTAACCGTTGTAGGG",
        //         ReferencePoint.JBeginTrimmed, ReferencePoint.CDR3End).startsWith(" G "));
    }

    fun testWithoutLeftover(seqStr: String?, rp1: ReferencePoint?, rp2: ReferencePoint?): String {
        val seq = NucleotideSequence(seqStr)
        val al = Alignment(
            seq, Mutations.EMPTY_NUCLEOTIDE_MUTATIONS,
            Range(0, seq.size()), Range(0, seq.size()), 100.0f
        )
        val b = ExtendedReferencePointsBuilder()
        b.setPosition(rp1, 0)
        b.setPosition(rp2, seq.size())
        val partitioning = b.build()
        val ml = build(
            MultiAlignmentHelper.DEFAULT_SETTINGS,
            Range(0, seq.size()),
            "",
            al.sequence1.sequence,
            listOf(MultiAlignmentHelper.AlignmentInput("", al, 10, 20)),
            listOf(MultiAlignmentHelper.AminoAcidInput(partitioning))
        )
        if (show) println(ml)
        return MultiAlignmentFormatter.LinesFormatter().buildAnnotationLines(ml)[0].content
    }

    private fun testWithLeftover(
        seqStr1: String, seqStr2: String,
        rp1: ReferencePoint?, rp2: ReferencePoint?,
        rp3: ReferencePoint?, rp4: ReferencePoint?
    ): String {
        val seq = NucleotideSequence(seqStr1 + "AAAAAAAAAA" + seqStr2)
        val al = Alignment(
            seq, Mutations.EMPTY_NUCLEOTIDE_MUTATIONS,
            Range(0, seq.size()), Range(0, seq.size()), 100.0f
        )
        val b = ExtendedReferencePointsBuilder()
        b.setPosition(rp1, 0)
        b.setPosition(rp2, seqStr1.length)
        b.setPosition(rp3, seqStr1.length + 10)
        b.setPosition(rp4, seqStr1.length + 10 + seqStr2.length)
        val partitioning = b.build()
        val ml = build(
            MultiAlignmentHelper.DEFAULT_SETTINGS,
            Range(0, seq.size()),
            "",
            al.sequence1,
            listOf(MultiAlignmentHelper.AlignmentInput("", al, 20, 30)),
            listOf(MultiAlignmentHelper.AminoAcidInput(partitioning))
        )
        if (show) println(ml)
        return MultiAlignmentFormatter.LinesFormatter().buildAnnotationLines(ml)[0].content
    }
}
