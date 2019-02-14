/*
 * Copyright (c) 2014-2019, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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
 * commercial purposes should contact MiLaboratory LLC, which owns exclusive
 * rights for distribution of this program for commercial purposes, using the
 * following email address: licensing@milaboratory.com.
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
package com.milaboratory.mixcr.partialassembler;

import com.milaboratory.core.alignment.AffineGapAlignmentScoring;
import com.milaboratory.core.alignment.Aligner;
import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.alignment.AlignmentUtils;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.test.TestUtil;
import org.junit.Assert;
import org.junit.Test;

/**
 * Created by poslavsky on 18/05/16.
 */
public class TargetMergerTest {

    @Test
    public void test1() throws Exception {


        //acgatgggcgcaa     atatagggagctccgatcgacatcgagTTTTTtatcgccctggtacgatcccggtgacaaagcgttc   ggacctgtctggacgctagaacgcag
        //acgatgggcgcaa                          atcgggtatcgccctggtacgatAccggtga aaagcgttc   ggacctgtctggacgctagaacgcag
        //acgatgggcgcaa     atatagg agctAcgatcgacatcgggtatcgccc                              ggacctgtctggacgctagaacgcag
        final NucleotideSequence seq1 = new NucleotideSequence("atatagg agctAcgatcgacatcgAgtatcgccc                           ".replace(" ",""));
        final NucleotideSequence seq2 = new NucleotideSequence("                     atcgggtatcgccctggtacgatAccggtga aaagcgttc".replace(" ",""));
        final NucleotideSequence merg = new NucleotideSequence("atatagg agctAcgatcgacatcgggtatcgccctggtacgatAccggtga aaagcgttc".replace(" ",""));


        final NucleotideSequence reference = new NucleotideSequence("acgatgggcgcaa     atatagggagctccgatcgacatcgagTTTTTtatcgccctggtacgatcccggtgacaaagcgttc   ggacctgtctggacgctagaacgcag".replace(" ", ""));

        AffineGapAlignmentScoring<NucleotideSequence> scoring = new AffineGapAlignmentScoring<>(
                NucleotideSequence.ALPHABET, 8, -5, -8, -1);
        final Alignment<NucleotideSequence> al1 = Aligner.alignLocal(scoring, reference, seq1);
        final Alignment<NucleotideSequence> al2 = Aligner.alignLocal(scoring, reference, seq2);
        final Alignment<NucleotideSequence> merge = TargetMerger.merge(scoring, 10, merg, 20, al1, al2);

        System.out.println(al1 + "\n\n" + al2 + "\n\n" + merge);

        Assert.assertEquals(merg.getRange(merge.getSequence2Range()), AlignmentUtils.getAlignedSequence2Part(merge));
    }

    @Test
    public void test2() throws Exception {

        System.out.println(TestUtil.randomSequence(NucleotideSequence.ALPHABET, 100, 130).toString().toLowerCase());
//        System.out.println(TestUtil.randomSequence(NucleotideSequence.ALPHABET, 20, 30).toString().toLowerCase());
    }
}