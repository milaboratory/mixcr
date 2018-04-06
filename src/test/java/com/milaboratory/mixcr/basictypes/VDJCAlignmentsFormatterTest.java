/*
 * Copyright (c) 2014-2018, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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