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
package com.milaboratory.mixcr.alleles

import com.milaboratory.mixcr.util.geneName
import io.repseq.core.VDJCGeneId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.LongAdder

class OverallAllelesStatistics {
    private val genesTotalCount: MutableMap<String, LongAdder> = ConcurrentHashMap()
    private val alleles: MutableMap<VDJCGeneId, AlleleStatistics> = ConcurrentHashMap()

    fun baseGeneCount(geneId: VDJCGeneId): Long = genesTotalCount[geneId.geneName]?.toLong() ?: 0L

    fun registerBaseGene(geneId: VDJCGeneId) =
        genesTotalCount.computeIfAbsent(geneId.geneName) { LongAdder() }.increment()

    fun stats(geneId: VDJCGeneId): AlleleStatistics =
        alleles.computeIfAbsent(geneId) { AlleleStatistics() }

    class AlleleStatistics {
        val naives = AtomicInteger(0)
        val count = AtomicInteger(0)
        val diversity: MutableSet<Pair<VDJCGeneId, Int>> = ConcurrentHashMap.newKeySet()
    }
}
