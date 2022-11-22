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
package com.milaboratory.mixcr.basictypes;

import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.AffineGapAlignmentScoring;
import com.milaboratory.core.alignment.Aligner;
import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.SequenceQuality;
import org.junit.Test;

/**
 * Created by dbolotin on 03/09/15.
 */
public class MultiAlignmentHelperTest {
    @Test
    public void test1() throws Exception {
        SequenceQuality seq0qual = new SequenceQuality("GIHGIIHIHFHGHGIIIGKHK");
        NucleotideSequence seq0 = new NucleotideSequence("GATACATTAGACACAGATACA");
        NucleotideSequence seq1 = new NucleotideSequence("AGACACATATACACAG");
        NucleotideSequence seq2 = new NucleotideSequence("GATACGATACATTAGAGACCACAGATACA");

        Alignment<NucleotideSequence>[] alignments = new Alignment[]{
                Aligner.alignLocalAffine(AffineGapAlignmentScoring.getNucleotideBLASTScoring(), seq0, seq1),
                Aligner.alignGlobalAffine(AffineGapAlignmentScoring.getNucleotideBLASTScoring(), seq0, seq1),
                Aligner.alignLocalAffine(AffineGapAlignmentScoring.getNucleotideBLASTScoring(), seq0, seq2),
                Aligner.alignGlobalAffine(AffineGapAlignmentScoring.getNucleotideBLASTScoring(), seq0, seq2),
        };

        for (Alignment<NucleotideSequence> alignment : alignments) {
            System.out.println(alignment.getAlignmentHelper());
            System.out.println();
        }

        MultiAlignmentHelper helper = MultiAlignmentHelper.build(MultiAlignmentHelper.DEFAULT_SETTINGS, new Range(0, seq0.size()),
                alignments);
        helper.addSubjectQuality("Quality", seq0qual);
        helper.setSubjectLeftTitle("Subject");
        for (int i = 0; i < 4; i++)
            helper.setQueryLeftTitle(i, "Query" + i);

        System.out.println(helper);

        for (MultiAlignmentHelper spl : helper.split(5)) {
            System.out.println();
            System.out.println(spl);
        }

        System.out.println();
        System.out.println(MultiAlignmentHelper.build(MultiAlignmentHelper.DOT_MATCH_SETTINGS, new Range(0, seq0.size()),
                alignments));

        System.out.println();
        System.out.println(MultiAlignmentHelper.build(MultiAlignmentHelper.DOT_MATCH_SETTINGS, new Range(0, seq0.size()),
                seq0,
                new Alignment[0]).addSubjectQuality("", seq0qual));
    }

    @Test
    public void test2() {
        // AACGATGGGCGCAAATATAGGGAGAACTCCGATCGACATCGGGTATCGCCCTGGTACGATCC--CGGTGACAAAGCGTTCGGACCTGTCTGGACGCTAGAACGC
        //                TATAGGGAG--CTCCGATCTACATCG
        //                                                         CGATCCTTCGGTGACAAAGCGTTCTGACC
        //                                     CATCAGGTATCGCCCTGGTACG
        NucleotideSequence seq0 = new NucleotideSequence("AACGATGGGCGCAAATATAGGGAGAACTCCGATCGACATCGGGTATCGCCCTGGTACGATCCCGGTGACAAAGCGTTCGGACCTGTCTGGACGCTAGAACGC");
        NucleotideSequence seq1 = new NucleotideSequence("TATAGGGAGCTCCGATCGACATCG");
        NucleotideSequence seq2 = new NucleotideSequence("CGATCCTTCGGTGACAAAGCGTTCGGACC");
        NucleotideSequence seq3 = new NucleotideSequence("CATCAGGTATCGCCCTGGTACG");

        Alignment<NucleotideSequence>[] alignments = new Alignment[]{
                Aligner.alignLocalAffine(AffineGapAlignmentScoring.getNucleotideBLASTScoring(), seq0, seq1),
                Aligner.alignLocalAffine(AffineGapAlignmentScoring.getNucleotideBLASTScoring(), seq0, seq2),
                Aligner.alignLocalAffine(AffineGapAlignmentScoring.getNucleotideBLASTScoring(), seq0, seq3),
        };

        for (Alignment<NucleotideSequence> alignment : alignments) {
            System.out.println(alignment.getAlignmentHelper());
            System.out.println();
        }

        MultiAlignmentHelper helper = MultiAlignmentHelper.build(MultiAlignmentHelper.DEFAULT_SETTINGS,
                new Range(0, seq0.size()),
                alignments);

        System.out.println(helper);
    }
}
