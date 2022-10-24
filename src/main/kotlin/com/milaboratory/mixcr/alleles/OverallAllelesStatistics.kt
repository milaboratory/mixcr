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
import com.milaboratory.mixcr.basictypes.tag.TagsInfo
import com.milaboratory.mixcr.util.geneName
import io.repseq.core.GeneFeature
import io.repseq.core.VDJCGeneId
import org.apache.commons.math3.stat.descriptive.SummaryStatistics
import org.apache.commons.math3.stat.descriptive.SynchronizedSummaryStatistics
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.LongAdder

class OverallAllelesStatistics(
    private val clonesFilter: AllelesBuilder.ClonesFilter
) {
    private val genesTotalCount: MutableMap<String, LongAdder> = ConcurrentHashMap()
    private val alleles: MutableMap<VDJCGeneId, AlleleStatistics> = ConcurrentHashMap()

    fun baseGeneCount(geneId: VDJCGeneId): Long = genesTotalCount[geneId.geneName]?.toLong() ?: 0L

    fun registerBaseGene(geneId: VDJCGeneId) =
        genesTotalCount.computeIfAbsent(geneId.geneName) { LongAdder() }.increment()

    fun stats(geneId: VDJCGeneId): AlleleStatistics =
        alleles.computeIfAbsent(geneId) { AlleleStatistics() }

    val stats: Map<VDJCGeneId, AlleleStatistics> = alleles

    inner class AlleleStatistics {
        private val naives = LongAdder()
        private val naivesFiltered = LongAdder()
        private val count = LongAdder()
        private val countFiltered = LongAdder()
        private val diversity: MutableSet<Pair<VDJCGeneId, Int>> = ConcurrentHashMap.newKeySet()
        private val withNegativeScoreChange: LongAdder = LongAdder()
        private val withNegativeScoreChangeFiltered: LongAdder = LongAdder()
        val scoreDelta: SummaryStatistics = SynchronizedSummaryStatistics()
        fun scoreDelta(clone: Clone, delta: Float, tagsInfo: TagsInfo) {
            if (delta < 0.0F) {
                withNegativeScoreChange.increment()
                if (clonesFilter.match(clone, tagsInfo)) {
                    withNegativeScoreChangeFiltered.increment()
                }
            }
            if (delta != 0.0F) {
                scoreDelta.addValue(delta.toDouble())
            }
        }

        fun withNegativeScoreChange(filtered: Boolean) = when {
            filtered -> withNegativeScoreChangeFiltered
            else -> withNegativeScoreChange
        }.toLong()

        fun naives(filtered: Boolean) = when {
            filtered -> naivesFiltered
            else -> naives
        }.toLong()

        fun naive(clone: Clone, tagsInfo: TagsInfo) {
            naives.increment()
            if (clonesFilter.match(clone, tagsInfo)) {
                naivesFiltered.increment()
            }
        }

        fun register(clone: Clone, complementaryGeneId: VDJCGeneId, tagsInfo: TagsInfo) {
            count.increment()
            if (clonesFilter.match(clone, tagsInfo)) {
                countFiltered.increment()
            }
            diversity += complementaryGeneId to clone.ntLengthOf(GeneFeature.CDR3)
        }

        fun diversity() = diversity.size
        fun count(filtered: Boolean) = when {
            filtered -> countFiltered
            else -> count
        }.toLong()
    }
}
