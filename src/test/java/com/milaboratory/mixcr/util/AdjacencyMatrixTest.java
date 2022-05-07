/*
 * Copyright (c) 2014-2020, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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
package com.milaboratory.mixcr.util;

import com.google.common.collect.Lists;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import org.apache.commons.math3.random.Well19937c;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 *
 */
public class AdjacencyMatrixTest {
    @Test
    public void test1() throws Exception {
        AdjacencyMatrix matrix = new AdjacencyMatrix(6);
        matrix.setConnected(0, 4);
        matrix.setConnected(0, 1);
        matrix.setConnected(1, 4);
        matrix.setConnected(1, 2);
        matrix.setConnected(2, 3);
        matrix.setConnected(3, 4);
        matrix.setConnected(3, 5);
        List<BitArrayInt> cliques = Lists.newArrayList(matrix.calculateMaximalCliques().iterator());
        HashSet<BitArrayInt> set = new HashSet<>(cliques);

        Assert.assertEquals(cliques.size(), set.size());
        Assert.assertEquals(5, set.size());

        Assert.assertTrue(set.contains(createSet(6, 0, 1, 4)));
        Assert.assertTrue(set.contains(createSet(6, 1, 2)));
        Assert.assertTrue(set.contains(createSet(6, 2, 3)));
        Assert.assertTrue(set.contains(createSet(6, 3, 4)));
        Assert.assertTrue(set.contains(createSet(6, 3, 5)));
    }

    @Test
    public void test4() throws Exception {
        AdjacencyMatrix matrix = new AdjacencyMatrix(6);
        matrix.setConnected(0, 4);
        matrix.setConnected(0, 1);
        matrix.setConnected(1, 4);
        matrix.setConnected(1, 2);
        matrix.setConnected(2, 3);
        matrix.setConnected(3, 4);
        matrix.setConnected(3, 5);
        for (int i = 0; i < 6; i++)
            matrix.setConnected(i, i);

        List<BitArrayInt> cliques = Lists.newArrayList(matrix.calculateMaximalCliques().iterator());
        HashSet<BitArrayInt> set = new HashSet<>(cliques);

        Assert.assertEquals(cliques.size(), set.size());
        Assert.assertEquals(5, set.size());

        Assert.assertTrue(set.contains(createSet(6, 0, 1, 4)));
        Assert.assertTrue(set.contains(createSet(6, 1, 2)));
        Assert.assertTrue(set.contains(createSet(6, 2, 3)));
        Assert.assertTrue(set.contains(createSet(6, 3, 4)));
        Assert.assertTrue(set.contains(createSet(6, 3, 5)));
    }

    @Test
    public void test5() throws Exception {
        AdjacencyMatrix matrix = new AdjacencyMatrix(6);
        matrix.setConnected(0, 4);
        matrix.setConnected(0, 1);
        matrix.setConnected(1, 4);
        matrix.setConnected(1, 2);
        matrix.setConnected(2, 3);
        matrix.setConnected(3, 4);
        for (int i = 0; i < 6; i++)
            matrix.setConnected(i, i);

        List<BitArrayInt> cliques = Lists.newArrayList(matrix.calculateMaximalCliques().iterator());
        HashSet<BitArrayInt> set = new HashSet<>(cliques);

        Assert.assertEquals(cliques.size(), set.size());
        Assert.assertEquals(5, set.size());

        Assert.assertTrue(set.contains(createSet(6, 0, 1, 4)));
        Assert.assertTrue(set.contains(createSet(6, 1, 2)));
        Assert.assertTrue(set.contains(createSet(6, 2, 3)));
        Assert.assertTrue(set.contains(createSet(6, 3, 4)));
        Assert.assertTrue(set.contains(createSet(6, 5)));
    }

    @Test
    public void test2() throws Exception {
        TestData testData = generateTestData(130, 5, 5, 10, 10);
        AdjacencyMatrix matrix = testData.matrix;
        for (BitArrayInt bitArrayInt : Lists.newArrayList(matrix.calculateMaximalCliques().iterator())) {
            if (bitArrayInt.bitCount() >= 5)
                System.out.println(bitArrayInt);
        }
//        System.out.println(matrix);

    }

    @Test
    public void test3() throws Exception {
        for (int i = 0; i < 100; i++)
            assertTestData(generateTestData(150, 10, 10, 15, 30), 10);
    }

    static void assertTestData(TestData testData, int minCliqueSize) {
        HashSet<BitArrayInt> actual = new HashSet<>();
        List<BitArrayInt> cliques = Lists.newArrayList(testData.matrix.calculateMaximalCliques().iterator());
        for (BitArrayInt clique : cliques)
            if (clique.bitCount() >= minCliqueSize)
                actual.add(clique);

        HashSet<BitArrayInt> expected = new HashSet<>(Arrays.asList(testData.cliques));
        Assert.assertEquals(expected, actual);
    }

    static TestData generateTestData(int size, int nCliques,
                                     int minCliqueSize, int maxCliqueSize,
                                     int noisePoints) {
        Well19937c random = new Well19937c(System.currentTimeMillis());
        BitArrayInt[] cliques = new BitArrayInt[nCliques];

        AdjacencyMatrix matrix = new AdjacencyMatrix(size);
        int generated = 0;
        final int[] mask = new int[size];
        for (int i = 0; i < nCliques; i++) {
            int cSize = minCliqueSize + random.nextInt(maxCliqueSize - minCliqueSize);
            TIntSet tClique = new TIntHashSet();
            int maskSet = 0;
            while (tClique.size() < cSize) {
//                if (generated == size)
//                    break;
                int v = random.nextInt(size);
                if (mask[v] != 0) {
                    ++maskSet;
                    if (maskSet >= 3)
                        continue;
                }

                mask[v] = 1;
                ++generated;
                tClique.add(v);
            }

            cliques[i] = new BitArrayInt(size);
            int[] vs = tClique.toArray();
            for (int j = 0; j < vs.length; j++)
                cliques[i].set(vs[j]);

            for (int j = 0; j < vs.length; j++)
                for (int k = j + 1; k < vs.length; k++)
                    matrix.setConnected(vs[j], vs[k]);
        }

        //introduce noise

        for (int i = 0; i < noisePoints; i++) {
            int a = random.nextInt(size), b = random.nextInt(size);
            if (a == b) {
                --i;
                continue;
            }
            matrix.setConnected(a, b);
        }

        return new TestData(matrix, cliques);
    }

    static class TestData {
        final AdjacencyMatrix matrix;
        final BitArrayInt[] cliques;

        public TestData(AdjacencyMatrix matrix, BitArrayInt[] cliques) {
            this.matrix = matrix;
            this.cliques = cliques;
        }
    }

    public static BitArrayInt createSet(int size, int... elements) {
        BitArrayInt ret = new BitArrayInt(size);
        for (int element : elements)
            ret.set(element);
        return ret;
    }
}
