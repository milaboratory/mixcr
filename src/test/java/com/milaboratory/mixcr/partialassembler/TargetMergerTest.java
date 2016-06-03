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