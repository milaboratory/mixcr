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

import java.util.*
import kotlin.math.sqrt

/**
 * copy of picocli.CommandLine.CosineSimilarity
 */
object CosineSimilarity {
    fun mostSimilar(pattern: String, candidates: Iterable<String>, threshold: Double = 0.0): List<String> {
        val sorted: SortedMap<Double, String> = TreeMap()
        candidates.forEach { candidate ->
            val score = similarity(pattern.lowercase(Locale.getDefault()), candidate.lowercase(Locale.getDefault()), 2)
            if (score > threshold) {
                sorted[score] = candidate
            }
        }
        return sorted.values.reversed()
    }

    private fun similarity(sequence1: String, sequence2: String, degree: Int): Double {
        val m1 = countNgramFrequency(sequence1, degree)
        val m2 = countNgramFrequency(sequence2, degree)
        return dotProduct(m1, m2) / sqrt(dotProduct(m1, m1) * dotProduct(m2, m2))
    }

    private fun countNgramFrequency(sequence: String, degree: Int): Map<String, Int> {
        val m = hashMapOf<String, Int>()
        var i = 0
        while (i + degree <= sequence.length) {
            val gram = sequence.substring(i, i + degree)
            m[gram] = 1 + (m[gram] ?: 0)
            i++
        }
        return m
    }

    private fun dotProduct(m1: Map<String, Int>, m2: Map<String, Int>): Double =
        m1.keys.sumOf { key ->
            (m1[key]!! * (m2[key] ?: 0)).toDouble()
        }
}
