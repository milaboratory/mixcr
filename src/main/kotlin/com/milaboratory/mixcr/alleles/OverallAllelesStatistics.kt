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

import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.util.geneName
import io.repseq.core.GeneFeature
import io.repseq.core.VDJCGeneId
import org.apache.commons.math3.stat.descriptive.SummaryStatistics
import org.apache.commons.math3.stat.descriptive.SynchronizedSummaryStatistics
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.LongAdder

class OverallAllelesStatistics(
    private val useClonesWithCountGreaterThen: Int
) {
    private val genesTotalCount: MutableMap<String, LongAdder> = ConcurrentHashMap()
    private val alleles: MutableMap<VDJCGeneId, AlleleStatistics> = ConcurrentHashMap()

    fun baseGeneCount(geneId: VDJCGeneId): Long = genesTotalCount[geneId.geneName]?.toLong() ?: 0L

    fun registerBaseGene(geneId: VDJCGeneId) =
        genesTotalCount.computeIfAbsent(geneId.geneName) { LongAdder() }.increment()

    fun stats(geneId: VDJCGeneId): AlleleStatistics =
        alleles.computeIfAbsent(geneId) { AlleleStatistics() }

    inner class AlleleStatistics {
        private val naives = LongAdder()
        private val naivesFilteredByCount = LongAdder()
        private val count = LongAdder()
        private val countFilteredByCount = LongAdder()
        private val diversity: MutableSet<Pair<VDJCGeneId, Int>> = ConcurrentHashMap.newKeySet()
        val scoreNotChanged: LongAdder = LongAdder()
        private val withNegativeScoreChange: LongAdder = LongAdder()
        private val withNegativeScoreChangeFilteredByCount: LongAdder = LongAdder()
        val scoreDelta: SummaryStatistics = SynchronizedSummaryStatistics()
        fun scoreDelta(clone: Clone, delta: Float) {
            if (delta == 0.0F) {
                scoreNotChanged.increment()
            } else {
                if (delta < 0.0F) {
                    withNegativeScoreChange.increment()
                    if (clone.count > useClonesWithCountGreaterThen) {
                        withNegativeScoreChangeFilteredByCount.increment()
                    }
                }
                scoreDelta.addValue(delta.toDouble())
            }
        }

        fun withNegativeScoreChange(filteredByCount: Boolean) = when {
            filteredByCount -> withNegativeScoreChangeFilteredByCount
            else -> withNegativeScoreChange
        }.toLong()

        fun naives(filteredByCount: Boolean) = when {
            filteredByCount -> naivesFilteredByCount
            else -> naives
        }.toLong()

        fun naive(clone: Clone) {
            naives.increment()
            if (clone.count > useClonesWithCountGreaterThen) {
                naivesFilteredByCount.increment()
            }
        }

        fun register(clone: Clone, complementaryGeneId: VDJCGeneId) {
            count.increment()
            if (clone.count > useClonesWithCountGreaterThen) {
                countFilteredByCount.increment()
            }
            diversity += complementaryGeneId to clone.ntLengthOf(GeneFeature.CDR3)
        }

        fun diversity() = diversity.size
        fun count(filteredByCount: Boolean) = when {
            filteredByCount -> countFilteredByCount
            else -> count
        }.toLong()
    }
}
