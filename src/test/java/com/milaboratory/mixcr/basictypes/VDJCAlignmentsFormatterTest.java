/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 * 
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 * 
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */
package com.milaboratory.mixcr.basictypes;

import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.alignment.MultiAlignmentHelper;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.sequence.NucleotideSequence;
import io.repseq.core.ExtendedReferencePointsBuilder;
import io.repseq.core.ReferencePoint;
import org.junit.Assert;
import org.junit.Test;

import static com.milaboratory.core.alignment.MultiAlignmentHelper.DEFAULT_SETTINGS;

public class VDJCAlignmentsFormatterTest {
    boolean show = true;

    @Test
    public void test0() {
        Assert.assertTrue(testWithoutLeftover("GGTTCGGGGACCAGGTTAACCGTTGTAGGG",
                ReferencePoint.CDR3Begin, ReferencePoint.CDR3End).endsWith(" G "));
        Assert.assertTrue(testWithoutLeftover("GGTTCGGGGACCAGGTAACCGTTGTAGGG",
                ReferencePoint.CDR3Begin, ReferencePoint.CDR3End).endsWith(" G "));
        Assert.assertTrue(testWithoutLeftover("GGTTCGGGGACCAGGAACCGTTGTAGGG",
                ReferencePoint.CDR3Begin, ReferencePoint.CDR3End).endsWith(" G "));
    }

    @Test
    public void test1() {
        Assert.assertTrue(testWithoutLeftover("GGTTCGGGGACCAGGTTAACCGTTGTAGGG",
                ReferencePoint.CDR3Begin, ReferencePoint.VEndTrimmed).endsWith(" G "));
        Assert.assertTrue(testWithoutLeftover("GGTTCGGGGACCAGGTTAACCGTTGTAGG",
                ReferencePoint.CDR3Begin, ReferencePoint.VEndTrimmed).endsWith("_ "));
        Assert.assertTrue(testWithoutLeftover("GGTTCGGGGACCAGGTTAACCGTTGTAG",
                ReferencePoint.CDR3Begin, ReferencePoint.VEndTrimmed).endsWith("_"));
    }

    @Test
    public void test2() {
        Assert.assertTrue(testWithoutLeftover("GGTTCGGGGACCAGGTTAACCGTTGTAGGG",
                ReferencePoint.JBeginTrimmed, ReferencePoint.CDR3End).startsWith(" G "));
        Assert.assertTrue(testWithoutLeftover("GTTCGGGGACCAGGTTAACCGTTGTAGGG",
                ReferencePoint.JBeginTrimmed, ReferencePoint.CDR3End).startsWith("_ "));
        Assert.assertTrue(testWithoutLeftover("TTCGGGGACCAGGTTAACCGTTGTAGGG",
                ReferencePoint.JBeginTrimmed, ReferencePoint.CDR3End).startsWith("_"));
    }

    @Test
    public void test3() {
        testWithLeftover("GGTTCGGGGACCAGGTTAACCGTTGTAGG", "G",
                ReferencePoint.L1Begin, ReferencePoint.L1End,  ReferencePoint.L2Begin, ReferencePoint.L2End);
        // Assert.assertTrue(testWithoutLeftover("GGTTCGGGGACCAGGTTAACCGTTGTAGGG",
        //         ReferencePoint.JBeginTrimmed, ReferencePoint.CDR3End).startsWith(" G "));
        // Assert.assertTrue(testWithoutLeftover("GGTTCGGGGACCAGGTTAACCGTTGTAGGG",
        //         ReferencePoint.JBeginTrimmed, ReferencePoint.CDR3End).startsWith(" G "));
    }

    public String testWithoutLeftover(String seqStr, ReferencePoint rp1, ReferencePoint rp2) {
        NucleotideSequence seq = new NucleotideSequence(seqStr);
        Alignment<NucleotideSequence> al = new Alignment<>(seq, Mutations.EMPTY_NUCLEOTIDE_MUTATIONS,
                new Range(0, seq.size()), new Range(0, seq.size()), 100.0f);
        MultiAlignmentHelper ml = MultiAlignmentHelper.build(DEFAULT_SETTINGS, new Range(0, seq.size()), al);
        ExtendedReferencePointsBuilder b = new ExtendedReferencePointsBuilder();
        b.setPosition(rp1, 0);
        b.setPosition(rp2, seq.size());
        VDJCAlignmentsFormatter.drawAASequence(ml, b.build(), seq);
        if (show)
            System.out.println(ml);
        return ml.getAnnotationString(0);
    }

    public String testWithLeftover(String seqStr1, String seqStr2,
                                      ReferencePoint rp1, ReferencePoint rp2,
                                      ReferencePoint rp3, ReferencePoint rp4) {
        NucleotideSequence seq = new NucleotideSequence(seqStr1 + "AAAAAAAAAA" + seqStr2);
        Alignment<NucleotideSequence> al = new Alignment<>(seq, Mutations.EMPTY_NUCLEOTIDE_MUTATIONS,
                new Range(0, seq.size()), new Range(0, seq.size()), 100.0f);
        MultiAlignmentHelper ml = MultiAlignmentHelper.build(DEFAULT_SETTINGS, new Range(0, seq.size()), al);
        ExtendedReferencePointsBuilder b = new ExtendedReferencePointsBuilder();
        b.setPosition(rp1, 0);
        b.setPosition(rp2, seqStr1.length());
        b.setPosition(rp3, seqStr1.length() + 10);
        b.setPosition(rp4, seqStr1.length() + 10 + seqStr2.length());
        VDJCAlignmentsFormatter.drawAASequence(ml, b.build(), seq);
        if (show)
            System.out.println(ml);
        return ml.getAnnotationString(0);
    }
}
