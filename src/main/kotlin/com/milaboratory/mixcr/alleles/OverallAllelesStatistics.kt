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
import org.apache.commons.math3.stat.descriptive.SummaryStatistics
import org.apache.commons.math3.stat.descriptive.SynchronizedSummaryStatistics
import java.util.concurrent.ConcurrentHashMap
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
        val naives = LongAdder()
        private val count = LongAdder()
        private val diversity: MutableSet<Pair<VDJCGeneId, Int>> = ConcurrentHashMap.newKeySet()
        val scoreNotChanged: LongAdder = LongAdder()
        val withNegativeScoreChange: LongAdder = LongAdder()
        val scoreDelta: SummaryStatistics = SynchronizedSummaryStatistics()
        fun scoreDelta(delta: Float) {
            if (delta == 0.0F) {
                scoreNotChanged.increment()
            } else {
                if (delta < 0.0F) {
                    withNegativeScoreChange.increment()
                }
                scoreDelta.addValue(delta.toDouble())
            }
        }

        fun register(CDR3Length: Int, complementaryGeneId: VDJCGeneId) {
            count.increment()
            diversity += complementaryGeneId to CDR3Length
        }

        fun diversity() = diversity.size
        fun count() = count.sum()
    }
}
