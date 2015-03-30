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
package com.milaboratory.mixcr.reference;

import com.milaboratory.core.Range;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.sequence.NucleotideSequence;
import org.junit.Assert;
import org.junit.Test;

import static com.milaboratory.mixcr.reference.GeneFeature.*;

public class ReferencePointsTest {
    @Test
    public void test1() throws Exception {
        new ReferencePoints(0, new int[]{1, 3, 5});
        new ReferencePoints(0, new int[]{1, -1, 5, -1, 7});
    }

    @Test(expected = IllegalArgumentException.class)
    public void test2() throws Exception {
        new ReferencePoints(0, new int[]{1, 3, 2});
    }

    @Test(expected = IllegalArgumentException.class)
    public void test3() throws Exception {
        new ReferencePoints(0, new int[]{1, -1, 5, -1, 3});
    }

    @Test(expected = IllegalArgumentException.class)
    public void test5() throws Exception {
        new ReferencePoints(0, new int[]{1, -1, 5, -1, -3});
    }

    @Test(expected = IllegalArgumentException.class)
    public void test6() throws Exception {
        new ReferencePoints(0, new int[]{1, -5, 6, 8});
    }

    @Test(expected = IllegalArgumentException.class)
    public void test7() throws Exception {
        new ReferencePoints(0, new int[]{-3, 5, 6, 8});
        new ReferencePoints(0, new int[]{-3, 5, 6, -10});
    }

    @Test
    public void test4() throws Exception {
        ReferencePoints rp = new ReferencePoints(3, new int[]{1, 2, 4, -1, 5, -1, 7});
        Assert.assertEquals(new Range(2, 4), rp.getRange(FR1));
        Assert.assertNull(rp.getRange(CDR1));
        Assert.assertNull(rp.getRange(FR2));
    }

    @Test
    public void test8() throws Exception {
        ReferencePoints points = new ReferencePoints(0, new int[]{2, 52, 63, 84, 155, 455, 645, 1255, 2142});
        GeneFeature feature = GeneFeatureTest.create(2, 4);
        Assert.assertEquals(21, points.getRelativePosition(feature,
                new ReferencePoint(BasicReferencePoint.getByIndex(3))));

        feature = GeneFeatureTest.create(2, 4, 5, 7);
        Assert.assertEquals(155 - 63 + 645 - 455, points.getRelativePosition(feature,
                new ReferencePoint(BasicReferencePoint.getByIndex(6))));
    }

    @Test
    public void test9() throws Exception {
        ReferencePoints points = new ReferencePoints(0, new int[]{2, 52, 63, 84, 155, 455, 645, 1255, 2142});
        GeneFeature feature = GeneFeatureTest.create(2, 5);
        GeneFeature minor = GeneFeatureTest.create(3, 4);
        Assert.assertEquals(new Range(84 - 63, 155 - 63), points.getRelativeRange(feature, minor));

        feature = GeneFeatureTest.create(2, 5, 6, 8);
        minor = GeneFeatureTest.create(6, 8);
        Assert.assertEquals(new Range(455 - 63, 2142 - 645 + 455 - 63), points.getRelativeRange(feature, minor));
    }


    @Test
    public void test10() throws Exception {
        ReferencePoints points = new ReferencePoints(0, new int[]{-1, 4, 15, 20, 27, 29, 48, 71, 83});
        GeneFeature feature = GeneFeatureTest.create(2, 3);
        Assert.assertEquals(
                new ReferencePoints(2, new int[]{0, 5}),
                points.getRelativeReferencePoints(feature));

        feature = GeneFeatureTest.create(2, 4, 6, 8);
        Assert.assertEquals(
                new ReferencePoints(2, new int[]{0, 5, 12, -1, 12, 35, 47}),
                points.getRelativeReferencePoints(feature));
    }

    @Test
    public void test11() throws Exception {
        ReferencePoints points = new ReferencePoints(0, new int[]{2, 52, 63, 84, 155, 455, 645, 1255, 2142, 12342, 24234234, 234423424});
        GeneFeature feature = GeneFeatureTest.create(3, 5, 7, 9);
        Assert.assertEquals(
                new ReferencePoints(0, new int[]{-1, -1, -1, 0, 71, 371, -1, 371, 1258, 11458, -1, -1, -1, -1, -1, -1, -1, -1, -1}),
                points.getRelativeReferencePoints(feature));
    }

    @Test
    public void test12() throws Exception {
        ReferencePoints points = new ReferencePoints(0, new int[]{-1, 96, 85, 80, 73, 71, 52, 29, 17});
        GeneFeature feature = GeneFeatureTest.create(2, 3);
        Assert.assertEquals(
                new ReferencePoints(2, new int[]{0, 5}),
                points.getRelativeReferencePoints(feature));

        feature = GeneFeatureTest.create(2, 4, 6, 8);
        Assert.assertEquals(
                new ReferencePoints(2, new int[]{0, 5, 12, -1, 12, 35, 47}),
                points.getRelativeReferencePoints(feature));
    }

    @Test
    public void test13() throws Exception {
        ReferencePoints points = new ReferencePoints(0, new int[]{-1, 96, 85, 80, 73, 71, 52, 29, 17});
        Mutations<NucleotideSequence> mutations = Mutations.decode("DA7I15C", NucleotideSequence.ALPHABET);
        GeneFeature feature = GeneFeatureTest.create(2, 4, 6, 8);
        Assert.assertEquals(
                new ReferencePoints(2, new int[]{0, 5, 11, -1, 11, 35, 47}),
                points.getRelativeReferencePoints(feature).applyMutations(mutations));
    }
}