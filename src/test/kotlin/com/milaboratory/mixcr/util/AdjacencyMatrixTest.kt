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
package com.milaboratory.mixcr.util

import gnu.trove.set.hash.TIntHashSet
import org.apache.commons.math3.random.Well19937c
import org.junit.Assert
import org.junit.Test
import java.util.*

/**
 *
 */
class AdjacencyMatrixTest {
    @Test
    fun test1() {
        val matrix = AdjacencyMatrix(6)
        matrix.setConnected(0, 4)
        matrix.setConnected(0, 1)
        matrix.setConnected(1, 4)
        matrix.setConnected(1, 2)
        matrix.setConnected(2, 3)
        matrix.setConnected(3, 4)
        matrix.setConnected(3, 5)
        val cliques: List<BitArrayInt> = matrix.calculateMaximalCliques().toList()
        val set = HashSet(cliques)
        Assert.assertEquals(cliques.size.toLong(), set.size.toLong())
        Assert.assertEquals(5, set.size.toLong())
        Assert.assertTrue(set.contains(createSet(6, 0, 1, 4)))
        Assert.assertTrue(set.contains(createSet(6, 1, 2)))
        Assert.assertTrue(set.contains(createSet(6, 2, 3)))
        Assert.assertTrue(set.contains(createSet(6, 3, 4)))
        Assert.assertTrue(set.contains(createSet(6, 3, 5)))
    }

    @Test
    fun test4() {
        val matrix = AdjacencyMatrix(6)
        matrix.setConnected(0, 4)
        matrix.setConnected(0, 1)
        matrix.setConnected(1, 4)
        matrix.setConnected(1, 2)
        matrix.setConnected(2, 3)
        matrix.setConnected(3, 4)
        matrix.setConnected(3, 5)
        for (i in 0..5) matrix.setConnected(i, i)
        val cliques: List<BitArrayInt> = matrix.calculateMaximalCliques().toList()
        val set = HashSet(cliques)
        Assert.assertEquals(cliques.size.toLong(), set.size.toLong())
        Assert.assertEquals(5, set.size.toLong())
        Assert.assertTrue(set.contains(createSet(6, 0, 1, 4)))
        Assert.assertTrue(set.contains(createSet(6, 1, 2)))
        Assert.assertTrue(set.contains(createSet(6, 2, 3)))
        Assert.assertTrue(set.contains(createSet(6, 3, 4)))
        Assert.assertTrue(set.contains(createSet(6, 3, 5)))
    }

    @Test
    @Throws(Exception::class)
    fun test5() {
        val matrix = AdjacencyMatrix(6)
        matrix.setConnected(0, 4)
        matrix.setConnected(0, 1)
        matrix.setConnected(1, 4)
        matrix.setConnected(1, 2)
        matrix.setConnected(2, 3)
        matrix.setConnected(3, 4)
        for (i in 0..5) matrix.setConnected(i, i)
        val cliques: List<BitArrayInt> = matrix.calculateMaximalCliques().toList()
        val set = HashSet(cliques)
        Assert.assertEquals(cliques.size.toLong(), set.size.toLong())
        Assert.assertEquals(5, set.size.toLong())
        Assert.assertTrue(set.contains(createSet(6, 0, 1, 4)))
        Assert.assertTrue(set.contains(createSet(6, 1, 2)))
        Assert.assertTrue(set.contains(createSet(6, 2, 3)))
        Assert.assertTrue(set.contains(createSet(6, 3, 4)))
        Assert.assertTrue(set.contains(createSet(6, 5)))
    }

    @Test
    @Throws(Exception::class)
    fun test2() {
        val testData = generateTestData(130, 5, 5, 10, 10)
        val matrix = testData.matrix
        for (bitArrayInt in matrix.calculateMaximalCliques()) {
            if (bitArrayInt.bitCount() >= 5) println(bitArrayInt)
        }
        //        System.out.println(matrix);
    }

    @Test
    @Throws(Exception::class)
    fun test3() {
        for (i in 0..99) assertTestData(generateTestData(150, 10, 10, 15, 30), 10)
    }

    class TestData(val matrix: AdjacencyMatrix, val cliques: Array<BitArrayInt?>)
    companion object {
        fun assertTestData(testData: TestData, minCliqueSize: Int) {
            val actual = HashSet<BitArrayInt>()
            val cliques: List<BitArrayInt> = testData.matrix.calculateMaximalCliques().toList()
            for (clique in cliques) if (clique.bitCount() >= minCliqueSize) actual.add(clique)
            val expected = HashSet(testData.cliques.toList())
            Assert.assertEquals(expected, actual)
        }

        fun generateTestData(
            size: Int, nCliques: Int,
            minCliqueSize: Int, maxCliqueSize: Int,
            noisePoints: Int
        ): TestData {
            val random = Well19937c(System.currentTimeMillis())
            val cliques = arrayOfNulls<BitArrayInt>(nCliques)
            val matrix = AdjacencyMatrix(size)
            var generated = 0
            val mask = IntArray(size)
            for (i in 0 until nCliques) {
                val cSize = minCliqueSize + random.nextInt(maxCliqueSize - minCliqueSize)
                val tClique = TIntHashSet()
                var maskSet = 0
                while (tClique.size() < cSize) {
//                if (generated == size)
//                    break;
                    val v = random.nextInt(size)
                    if (mask[v] != 0) {
                        ++maskSet
                        if (maskSet >= 3) continue
                    }
                    mask[v] = 1
                    ++generated
                    tClique.add(v)
                }
                cliques[i] = BitArrayInt(size)
                val vs: IntArray = tClique.toArray()
                for (j in vs.indices) cliques[i]!!.set(vs[j])
                for (j in vs.indices) for (k in j + 1 until vs.size) matrix.setConnected(vs[j], vs[k])
            }

            // introduce noise
            var i = 0
            while (i < noisePoints) {
                val a = random.nextInt(size)
                val b = random.nextInt(size)
                if (a == b) {
                    --i
                    i++
                    continue
                }
                matrix.setConnected(a, b)
                i++
            }
            return TestData(matrix, cliques)
        }

        fun createSet(size: Int, vararg elements: Int): BitArrayInt {
            val ret = BitArrayInt(size)
            for (element in elements) ret.set(element)
            return ret
        }
    }
}
