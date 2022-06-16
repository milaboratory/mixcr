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

import gnu.trove.list.array.TIntArrayList

class AdjacencyMatrix(val size: Int) {
    val data: Array<BitArrayInt?>

    init {
        data = arrayOfNulls(size)
        for (i in 0 until size) data[i] = BitArrayInt(size)
    }

    fun setConnected(i: Int, j: Int) {
        setConnected(i, j, true)
    }

    fun isConnected(i: Int, j: Int): Boolean {
        return data[i]!![j]
    }

    fun setConnected(i: Int, j: Int, connected: Boolean) {
        if (connected) {
            data[i]!!.set(j)
            data[j]!!.set(i)
        } else {
            data[i]!!.clear(j)
            data[j]!!.clear(i)
        }
    }

    fun clear(i: Int) {
        for (j in 0 until size) setConnected(i, j, false)
    }

    /**
     * Extract maximal cliques using Bron–Kerbosch algorithm.
     *
     * @return list of maximal cliques
     */
    @JvmOverloads
    fun calculateMaximalCliques(maxCliques: Int = 0): Sequence<BitArrayInt> = sequence {
        var totalCount = 0;
        val stack = BStack()
        stack.currentP().setAll()
        stack.currentPi().setAll()
        val tmp = BitArrayInt(size)
        while (true) {
            val v = stack.nextVertex()
            if (v == -1) if (stack.pop()) continue else break
            stack.currentP().clear(v)
            stack.loadAndGetNextR().set(v)
            stack.loadAndGetNextP().and(data[v])
            stack.loadAndGetNextX().and(data[v])
            stack.currentX().set(v)
            if (stack.nextP().isEmpty && stack.nextX().isEmpty) {
                yield(stack.nextR().clone())
                totalCount++
                if (maxCliques != 0 && totalCount >= maxCliques) return@sequence
                continue
            }
            tmp.loadValueFrom(stack.nextP())
            tmp.or(stack.nextX())
            var u = 0
            var bestU = -1
            var count = -1
            while (tmp.nextBit(u).also { u = it } != -1) {
                val c = tmp.numberOfCommonBits(data[u])
                if (bestU == -1 || c > count) {
                    bestU = u
                    count = c
                }
                ++u
            }
            stack.nextPi().loadValueFrom(data[bestU])
            stack.nextPi().clear(bestU)
            stack.nextPi().xor(stack.nextP())
            stack.nextPi().and(stack.nextP())
            stack.push()
        }
    }

    override fun toString(): String {
        val builder = StringBuilder()
        for (i in 0 until size) builder.append(data[i]).append('\n')
        builder.deleteCharAt(builder.length - 1)
        return builder.toString()
    }

    internal inner class BStack {
        val R = BList()
        val X = BList()
        val P = BList()

        /**
         * P for vertices enumeration = P / N(v)
         */
        val Pi = BList()
        val lastVertex = TIntArrayList()
        var currentLevel = 0

        init {
            lastVertex.add(0)
        }

        fun currentR(): BitArrayInt {
            return R[currentLevel]
        }

        fun currentX(): BitArrayInt {
            return X[currentLevel]
        }

        fun currentP(): BitArrayInt {
            return P[currentLevel]
        }

        fun currentPi(): BitArrayInt {
            return Pi[currentLevel]
        }

        fun nextVertex(): Int {
            val nextVertex = currentPi().nextBit(lastVertex[lastVertex.size() - 1])
            lastVertex[currentLevel] = nextVertex + 1
            return nextVertex
        }

        fun loadAndGetNextR(): BitArrayInt {
            R[currentLevel + 1].loadValueFrom(R[currentLevel])
            return R[currentLevel + 1]
        }

        fun loadAndGetNextX(): BitArrayInt {
            X[currentLevel + 1].loadValueFrom(X[currentLevel])
            return X[currentLevel + 1]
        }

        fun loadAndGetNextP(): BitArrayInt {
            P[currentLevel + 1].loadValueFrom(P[currentLevel])
            return P[currentLevel + 1]
        }

        fun nextX(): BitArrayInt {
            return X[currentLevel + 1]
        }

        fun nextP(): BitArrayInt {
            return P[currentLevel + 1]
        }

        fun nextR(): BitArrayInt {
            return R[currentLevel + 1]
        }

        fun nextPi(): BitArrayInt {
            return Pi[currentLevel + 1]
        }

        fun push() {
            currentLevel++
            lastVertex.add(0)
        }

        fun pop(): Boolean {
            currentLevel--
            lastVertex.removeAt(lastVertex.size() - 1)
            return currentLevel != -1
        }
    }

    internal inner class BList {
        val list: MutableList<BitArrayInt> = ArrayList()
        operator fun get(i: Int): BitArrayInt {
            if (i >= list.size) for (j in list.size..i) list.add(BitArrayInt(size))
            return list[i]
        }
    }
}
