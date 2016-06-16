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

import com.milaboratory.util.IntArrayList;
import junit.framework.Assert;
import org.apache.commons.math3.random.Well44497a;
import org.junit.Ignore;
import org.junit.Test;

import java.util.*;

import static com.milaboratory.mixcr.reference.ReferencePoint.*;
import static com.milaboratory.mixcr.reference.GeneFeature.*;
import static org.junit.Assert.*;

/**
 * @author Dmitry Bolotin
 * @author Stanislav Poslavsky
 */
public class GeneFeatureTest {

    @Test
    public void test1() throws Exception {
        GeneFeature f1, f2, f3, expected, actual;

        f1 = create(1, 3);
        f2 = create(4, 5);
        expected = create(new int[]{1, 3, 4, 5});
        actual = new GeneFeature(f1, f2);
        assertEquals(expected, actual);


        f1 = create(1, 5);
        f2 = create(5, 6);
        f3 = create(6, 9);
        expected = create(1, 9);
        actual = new GeneFeature(f1, f2, f3);
        assertEquals(expected, actual);


        f1 = create(1, 5);
        f2 = create(6, 7);
        f3 = create(7, 9);
        expected = create(1, 5, 6, 9);
        actual = new GeneFeature(f1, f2, f3);
        assertEquals(expected, actual);

        f1 = create(1, 5);
        f2 = create(8, 10);
        f3 = create(11, 12);
        expected = create(1, 5, 8, 10, 11, 12);
        actual = new GeneFeature(f1, f2, f3);
        assertEquals(expected, actual);
    }

    @Test(expected = IllegalArgumentException.class)
    public void test2() throws Exception {
        GeneFeature f1 = create(1, 5),
                f2 = create(3, 7);
        new GeneFeature(f1, f2);
    }

    @Test
    public void test3() throws Exception {
        GeneFeature f1, f2, f3, expected, actual;

        f1 = createWithOffsets(1, 3, -2, 0);
        f2 = createWithOffsets(3, 5, 1, 1);
        f3 = createWithOffsets(5, 7, 1, 5);
        expected = create(new int[]{1, 3, 3, 7}, new int[]{-2, 0, 1, 5});
        actual = new GeneFeature(f2, f1, f3);
        assertEquals(expected, actual);


        f1 = createWithOffsets(1, 3, -2, 0);
        f2 = createWithOffsets(3, 5, 1, -1);
        f3 = createWithOffsets(5, 7, -2, 5);
        expected = create(new int[]{1, 3, 3, 7}, new int[]{-2, 0, 1, 5});
        actual = new GeneFeature(f2, f1, f3);
        assertEquals(expected, actual);


        f1 = createWithOffsets(1, 3, -2, 0);
        f2 = createWithOffsets(3, 5, 1, -3);
        f3 = createWithOffsets(5, 7, -2, 5);
        expected = create(new int[]{1, 3, 3, 5, 5, 7}, new int[]{-2, 0, 1, -3, -2, 5});
        actual = new GeneFeature(f2, f1, f3);
        assertEquals(expected, actual);
        assertEquals(3, actual.regions.length);
    }

    @Test
    public void test3_1() throws Exception {
        GeneFeature f1 = createWithOffsets(1, 3, -2, 0);
        assertEquals(createWithOffsets(1, 3, -3, 2), new GeneFeature(f1, -1, 2));
    }

    @Test
    public void test3_2() throws Exception {
        GeneFeature f1 = new GeneFeature(
                createWithOffsets(1, 3, -2, 0),
                createWithOffsets(4, 5, -2, 4)
        );

        GeneFeature f2 = new GeneFeature(
                createWithOffsets(1, 3, -6, 0),
                createWithOffsets(4, 5, -2, 2)
        );

        assertEquals(f2, new GeneFeature(f1, -4, -2));
    }

    @Test
    public void testIntersection1() throws Exception {
        GeneFeature f1, f2;
        f1 = create(1, 5, 7, 9);
        f2 = create(8, 9);
        assertEquals(create(8, 9), intersection(f1, f2));

        f1 = create(1, 5, 7, 10);
        f2 = create(8, 9);
        assertEquals(create(8, 9), intersection(f1, f2));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIntersection2() throws Exception {
        GeneFeature f1, f2;
        f1 = create(1, 5, 7, 9);
        f2 = create(6, 9);
        intersection(f1, f2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIntersection3() throws Exception {
        GeneFeature f1, f2;
        f1 = create(1, 5, 7, 9);
        f2 = create(2, 5, 6, 9);
        intersection(f1, f2);
    }

    @Test
    public void testIntersection5() throws Exception {
        GeneFeature f1, f2;
        f1 = create(1, 5, 7, 9, 10, 12);
        f2 = create(2, 5, 7, 9, 10, 11);
        assertEquals(create(2, 5, 7, 9, 10, 11), intersection(f1, f2));
        assertEquals(create(2, 5, 7, 9, 10, 11), intersection(f2, f1));

        f1 = create(2, 5, 7, 9, 10, 12);
        f2 = create(1, 5, 7, 9, 10, 11);
        assertEquals(create(2, 5, 7, 9, 10, 11), intersection(f1, f2));
        assertEquals(create(2, 5, 7, 9, 10, 11), intersection(f2, f1));

        f1 = create(8, 9, 10, 11);
        f2 = create(1, 5, 7, 9, 10, 12);
        assertEquals(create(8, 9, 10, 11), intersection(f1, f2));
        assertEquals(create(8, 9, 10, 11), intersection(f2, f1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIntersection6() throws Exception {
        GeneFeature f1, f2;
        f1 = create(6, 9, 10, 11);
        f2 = create(1, 5, 7, 9, 10, 12);
        intersection(f1, f2);
    }

    @Test
    public void testIntersection7() throws Exception {
        GeneFeature f1, f2;
        f1 = create(new int[]{1, 5, 7, 9, 10, 12}, new int[]{-2, 0, 1, -3, -2, 5});
        f2 = create(new int[]{1, 5, 7, 9, 10, 12}, new int[]{-3, 0, 1, -3, -2, 4});
        assertEquals(create(new int[]{1, 5, 7, 9, 10, 12}, new int[]{-2, 0, 1, -3, -2, 4}),
                intersection(f2, f1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIntersection8() throws Exception {
        GeneFeature f1, f2;
        f1 = create(new int[]{1, 5, 7, 9, 10, 12}, new int[]{-2, 0, 1, -3, -2, 5});
        f2 = create(new int[]{1, 5, 7, 9, 10, 12}, new int[]{-3, 0, 2, -3, -2, 4});
        intersection(f2, f1);
    }

    @Test
    public void testIntersection9() throws Exception {
        GeneFeature f1, f2;
        f1 = create(new int[]{7, 9}, new int[]{2, -4});
        f2 = create(new int[]{1, 5, 7, 9, 10, 12}, new int[]{-3, 0, 1, -4, -2, 4});
        assertEquals(create(new int[]{7, 9}, new int[]{2, -4}),
                intersection(f2, f1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIntersection10() throws Exception {
        GeneFeature f1, f2;
        f1 = create(new int[]{7, 9}, new int[]{0, -3});
        f2 = create(new int[]{1, 5, 7, 9, 10, 12}, new int[]{-3, 0, 1, -3, -2, 4});
        intersection(f2, f1);
    }

    @Test
    public void testIntersection11() throws Exception {
        GeneFeature f1, f2;
        f1 = create(7, 9);
        f2 = create(1, 5, 7, 9, 10, 12);
        assertEquals(create(7, 9),
                intersection(f2, f1));
    }

    @Test
    public void testIntersection12() throws Exception {
        assertNull(intersection(CDR3, CExon1));
        assertNull(intersection(CExon1, CDR3));
    }

    @Test(expected = IllegalArgumentException.class)
    public void test4() throws Exception {
        new GeneFeature(create(1, 5), create(1, 7));
    }

    @Test(expected = IllegalArgumentException.class)
    public void test5() throws Exception {
        new GeneFeature(create(1, 7), create(1, 5));
    }

    @Test
    public void testRandom1() {
        Well44497a rand = new Well44497a();
        int tn = BasicReferencePoint.TOTAL_NUMBER_OF_REFERENCE_POINTS;
        for (int baseBlock = 2; baseBlock < 5; ++baseBlock)
            for (int t = 0; t < 1000; ++t) {
                int[] all = new int[tn];
                ArrayList<GeneFeature> features = new ArrayList<>();
                int begin, end = 0;
                do {
                    begin = end + rand.nextInt(baseBlock);
                    end = begin + 1 + rand.nextInt(baseBlock);
                    if (end >= tn)
                        break;
                    Arrays.fill(all, begin, end, 1);
                    features.add(create(begin, end));
                } while (end < tn);

                IntArrayList expectedPoints = new IntArrayList();
                if (all[0] == 1)
                    expectedPoints.add(0);
                for (int i = 1; i < tn; ++i)
                    if (all[i] != all[i - 1])
                        expectedPoints.add(i);
                if (all[tn - 1] == 1)
                    expectedPoints.add(tn);

                GeneFeature actual = new GeneFeature(features.toArray(new GeneFeature[features.size()]));
                assertEquals(create(expectedPoints.toArray()), actual);
                assertEquals(expectedPoints.size() / 2, actual.regions.length);
            }
    }

    @Test
    public void testStatic() throws Exception {
        assertEquals(JRegion, GeneFeature.parse("JRegion"));
    }

    static final GeneFeature create(int... indexes) {
        assert indexes.length % 2 == 0;
        GeneFeature[] res = new GeneFeature[indexes.length / 2];
        for (int i = 0; i < indexes.length; ) {
            res[i / 2] = new GeneFeature(
                    new ReferencePoint(BasicReferencePoint.getByIndex(indexes[i])),
                    new ReferencePoint(BasicReferencePoint.getByIndex(indexes[i + 1])));
            i += 2;
        }
        return new GeneFeature(res);
    }

    static final GeneFeature createWithOffsets(int index1, int index2, int offset1, int offset2) {
        return create(new int[]{index1, index2}, new int[]{offset1, offset2});
    }

    static final GeneFeature create(int[] indexes, int[] offsets) {
        GeneFeature[] res = new GeneFeature[indexes.length / 2];
        for (int i = 0; i < indexes.length; ) {
            res[i / 2] = new GeneFeature(
                    new ReferencePoint(
                            new ReferencePoint(BasicReferencePoint.getByIndex(indexes[i])), offsets[i]),
                    new ReferencePoint(
                            new ReferencePoint(BasicReferencePoint.getByIndex(indexes[i + 1])), offsets[i + 1]));
            i += 2;
        }
        return new GeneFeature(res);
    }

    @Test
    public void testParse1() throws Exception {
        assertEncode("CDR3");
        assertEncode("CDR3(1, -2)");
        assertEncode("CDR3(-31,-2)");
        assertEncode("CDR1(3, 2)+CDR3(-31,-2)");
    }

    @Test
    public void testParse2() throws Exception {
        assertEncode("{FR1Begin:FR3End}");
        assertEncode("{FR1Begin:FR3End}+JRegion");
        assertEncode("{FR1Begin:FR3End}+JRegion+CExon1(-3,12)");
        assertEncode("{FR1Begin(-33):FR3End(3)}+JRegion+CExon1(-3,12)");
    }

    @Test
    public void testParse3() throws Exception {
        assertEncode("CDR3Begin(0, 10)");
        assertEncode("V5UTRBeginTrimmed(0, 10)");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParse4() throws Exception {
        GeneFeature.parse("CDR3Begin");
    }

    @Test
    public void testContains1() throws Exception {
        assertTrue(CDR3.contains(VJJunction));
        assertTrue(CDR3.contains(VDJunction));
        assertTrue(CDR3.contains(DJJunction));
        assertTrue(CDR3.contains(CDR3));
        assertFalse(CDR3.contains(FR2));
        assertFalse(CDR3.contains(CDR1));
        assertFalse(CDR3.contains(V5UTRGermline));
    }

    @Test
    public void testEncode1() throws Exception {
        Collection<GeneFeature> features = GeneFeature.getFeaturesByName().values();
        for (GeneFeature feature : features)
            assertEquals(feature, GeneFeature.parse(encode(feature)));
    }

    private static void assertEncode(String str) {
        Assert.assertEquals(str.replace(" ", ""), encode(GeneFeature.parse(str)).replace(" ", ""));
    }

    @Ignore
    @Test
    public void testListForDocumentation() throws Exception {
        getFeatureByName("sd");
        List<GFT> gfts = new ArrayList<>();
        int withh1 = 25, withh2 = 35;
        String sep = "+" + chars(withh1 + 2, '-') + "+" + chars(withh2 + 2, '-') + "+";
        for (Map.Entry<GeneFeature, String> entry : nameByFeature.entrySet()) {
            String name = entry.getValue();
            String value = encode(entry.getKey(), false);
            gfts.add(new GFT(entry.getKey(),
                    "| " + fixed(name, withh1) + " | " + fixed(value, withh2) + " |"));
        }
        Collections.sort(gfts);
        System.out.println(sep);
        System.out.println("| " + fixed("Gene Feature Name", withh1) + " | " + fixed("Gene feature decomposition", withh2) + " |");
        String sep1 = "+" + chars(withh1 + 2, '=') + "+" + chars(withh2 + 2, '=') + "+";
        System.out.println(sep1);
        for (GFT gft : gfts) {
            System.out.println(gft.text);
            System.out.println(sep);
        }
    }

    private static String fixed(String str, int length) {
        return str + chars(length - str.length(), ' ');
    }

    private static String chars(int n, char cc) {
        char[] c = new char[n];
        Arrays.fill(c, cc);
        return String.valueOf(c);
    }

    private static final class GFT implements Comparable<GFT> {
        final GeneFeature feature;
        final String text;

        private GFT(GeneFeature feature, String text) {
            this.feature = feature;
            this.text = text;
        }

        @Override
        public int compareTo(GFT o) {
            return feature.getFirstPoint().compareTo(o.feature.getFirstPoint());
        }
    }

    @Test
    public void testIntersection15() throws Exception {
        assertEquals(intersection(VTranscript, VGene), VTranscript);
    }

    @Test
    public void testIntersection16() throws Exception {
        assertEquals(
                intersection(VTranscript, new GeneFeature(UTR5Begin.move(1), VEnd)),
                new GeneFeature(new GeneFeature(UTR5Begin.move(1), L1End), new GeneFeature(L2Begin, VEnd)));
    }

    @Test
    public void testIntersection17() throws Exception {
        assertEquals(
                intersection(new GeneFeature(new GeneFeature(UTR5Begin, L1End.move(-1)), new GeneFeature(L2Begin.move(-5), VEnd.move(1))), new GeneFeature(UTR5Begin.move(1), VEnd)),
                new GeneFeature(new GeneFeature(UTR5Begin.move(1), L1End.move(-1)), new GeneFeature(L2Begin.move(-5), VEnd)));
    }
}