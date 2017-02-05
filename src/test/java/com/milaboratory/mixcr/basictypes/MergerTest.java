/*
 * Copyright (c) 2014-2015, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
 * (here and after addressed as Inventors)
 * All Rights Reserved
 *
 * Permission to use, copy, modify and distribute any part of this program for
 * educational, research and non-profit purposes, by non-profit institutions
 * only, without fee, and without a written agreement is hereby granted,
 * provided that the above copyright notice, this paragraph and the following
 * three paragraphs appear in all copies.
 *
 * Those desiring to incorporate this work into commercial products or use for
 * commercial purposes should contact the Inventors using one of the following
 * email addresses: chudakovdm@mail.ru, chudakovdm@gmail.com
 *
 * IN NO EVENT SHALL THE INVENTORS BE LIABLE TO ANY PARTY FOR DIRECT, INDIRECT,
 * SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING LOST PROFITS,
 * ARISING OUT OF THE USE OF THIS SOFTWARE, EVEN IF THE INVENTORS HAS BEEN
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * THE SOFTWARE PROVIDED HEREIN IS ON AN "AS IS" BASIS, AND THE INVENTORS HAS
 * NO OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR
 * MODIFICATIONS. THE INVENTORS MAKES NO REPRESENTATIONS AND EXTENDS NO
 * WARRANTIES OF ANY KIND, EITHER IMPLIED OR EXPRESS, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY OR FITNESS FOR A
 * PARTICULAR PURPOSE, OR THAT THE USE OF THE SOFTWARE WILL NOT INFRINGE ANY
 * PATENT, TRADEMARK OR OTHER RIGHTS.
 */
package com.milaboratory.mixcr.basictypes;

import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.Aligner;
import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.alignment.LinearGapAlignmentScoring;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public class MergerTest {
    //@Test
    //public void test1() throws Exception {
    //    NucleotideSequence sequence = new NucleotideSequence("AGTCTAGTCGTAGACGCGCTGATTAGCGTAGGTCGGTCGTATT");
    //    NSequenceWithQuality lTarget = new NSequenceWithQuality(
    //            "attgcAGTCTAGTCGTAGCGCGACGATTAGCGT",
    //            "JJJJJJJJJJJJJJJJJHJJJJBJJJJJJJJJJ");
    //    NSequenceWithQuality rTarget = new NSequenceWithQuality(
    //            "TAGACGCGTTCCGATTAGCGTAGGTCGGTCGTATTaggta",
    //            "JJJJJJJJHJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJ");
    //    Alignment<NucleotideSequence> lAlignment = Aligner.alignLocalLinear(new LinearGapAlignmentScoring<>(NucleotideSequence.ALPHABET, 4, -5, -9), sequence, lTarget.getSequence());
    //    Alignment<NucleotideSequence> rAlignment = Aligner.alignLocalLinear(new LinearGapAlignmentScoring<>(NucleotideSequence.ALPHABET, 4, -5, -9), sequence, rTarget.getSequence());
    //
    //    System.out.println(lAlignment.getAlignmentHelper().toStringWithSeq2Quality(lTarget.getQuality()));
    //    System.out.println();
    //    System.out.println(rAlignment.getAlignmentHelper().toStringWithSeq2Quality(rTarget.getQuality()));
    //    System.out.println();
    //    System.out.println(Merger.merge(new Range(0, sequence.size()), new Alignment[]{lAlignment, rAlignment}, new NSequenceWithQuality[]{lTarget, rTarget}).toPrettyString());
    //}

    @Test
    public void test2() throws Exception {
        mAssert("CGCACAGTGTTGTCAAAGAAAACGCGTACGACATTGAGAAGACCGGCCGTTCTCCTTTGACATGATTGGATCGGTTGCTGCCGGCCCAGAATCCTAGCAG",
                "CGCACAGTGTTGTCAAAGAAAACGCGTACGACATTGAGAAGACCGGCC",
                "CGACATTGAGAAGACCGGCCGTTCTCCTTTGACATGATTGGATCGGTTGCTGCCGGCCCAGAATCCTAGCAG",
                "CGCACAGTGTTGTCAAAGAAAACGCGTACGACATTGAGAAGACCGGCCGTTCTCCTTTGACATGATTGGATCGGTTGCTGCCGGCCCAGAATCCTAGCAG",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAANNNNNNNNNNNNNNNNNNNNBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB");
        mAssert("CGCACAGTGTTGTCAAAGAAAACGCGTACGACATTGAGAAGACCGGCCGTTCTCCTTTGACATGATTGGATCGGTTGCTGCCGGCCCAGAATCCTAGCAG",
                "CGCACAGTGTTGTCAAAGAAAACGCGTACGACATTGAGAAGACCGGCC",
                /*                        */"CGACATCGAGAAGACCGGCCGTTCTCCTTTGACATGATTGGATCGGTTGCTGCCGGCCCAGAATCCTAGCAG",
                "CGCACAGTGTTGTCAAAGAAAACGCGTACGACATCGAGAAGACCGGCCGTTCTCCTTTGACATGATTGGATCGGTTGCTGCCGGCCCAGAATCCTAGCAG",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAANNNNNNBNNNNNNNNNNNNNBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB");
        mAssert("CGCACAGTGTTGTCAAAGAAAACGCGTACGACATTGAGAAGACCGGCCGTTCTCCTTTGACATGATTGGATCGGTTGCTGCCGGCCCAGAATCCTAGCAG",
                "CGCACAGTGTTGTCAAAGAAAACGCGTACGACATCGAGAAGACCGGCC",
                /*                        */"CGACATTGAGAAGACCGGCCGTTCTCCTTTGACATGATTGGATCGGTTGCTGCCGGCCCAGAATCCTAGCAG",
                "CGCACAGTGTTGTCAAAGAAAACGCGTACGACATTGAGAAGACCGGCCGTTCTCCTTTGACATGATTGGATCGGTTGCTGCCGGCCCAGAATCCTAGCAG",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAANNNNNNBNNNNNNNNNNNNNBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB");
    }

    public static void mAssert(String originalSeq,
                               String seq1, String seq2,
                               String expectedSequence, String expectedQuality) {
        NucleotideSequence originalSequence = new NucleotideSequence(originalSeq);
        NSequenceWithQuality target1 = new NSequenceWithQuality(seq1, lets('A', seq1.length()));
        NSequenceWithQuality target2 = new NSequenceWithQuality(seq2, lets('B', seq2.length()));
        LinearGapAlignmentScoring<NucleotideSequence> scoring = new LinearGapAlignmentScoring<>(NucleotideSequence.ALPHABET, 4, -5, -9);
        Alignment<NucleotideSequence> alignment1 = Aligner.alignLocalLinear(scoring, originalSequence, target1.getSequence());
        Alignment<NucleotideSequence> alignment2 = Aligner.alignLocalLinear(scoring, originalSequence, target2.getSequence());
        NSequenceWithQuality result = Merger.merge(new Range(0, originalSequence.size()), new Alignment[]{alignment1, alignment2}, new NSequenceWithQuality[]{target1, target2});
        Assert.assertEquals(expectedSequence, result.getSequence().toString());
        Assert.assertEquals(expectedQuality, result.getQuality().toString());
    }

    public static String lets(char letter, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, letter);
        return new String(chars);
    }
}