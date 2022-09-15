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
package com.milaboratory.mixcr.cli

import com.milaboratory.mixcr.basictypes.VDJCObject
import com.milaboratory.util.ReportBuilder
import io.repseq.core.Chains
import io.repseq.core.GeneFeature
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class ChainUsageStatsBuilder : ReportBuilder {
    internal val chimeras = AtomicLong(0)
    internal val total = AtomicLong(0)
    private val records = ConcurrentHashMap<Chains, RecordBuilder>()

    internal fun getRecordBuilder(chains: Chains): RecordBuilder? {
        var rec: RecordBuilder?
        return if (records[chains].also { rec = it } != null) rec else {
            val newRec = RecordBuilder()
            rec = records.putIfAbsent(chains, newRec)
            if (rec == null) newRec else rec
        }
    }

    fun increment(obj: VDJCObject) {
        total.incrementAndGet()
        if (obj.isChimera)
            chimeras.incrementAndGet()
        else
            getRecordBuilder(obj.commonTopChains())!!.increment(obj)
    }

    fun decrement(obj: VDJCObject) {
        total.decrementAndGet()
        if (obj.isChimera)
            chimeras.decrementAndGet()
        else
            getRecordBuilder(obj.commonTopChains())!!.increment(obj)
    }

    override fun buildReport(): ChainUsageStats {
        return ChainUsageStats(
            chimeras.get(), total.get(), records.mapValues { (_, v) -> v.build() }
        )
    }

    internal class RecordBuilder {
        val total = AtomicLong(0L)
        val nf = AtomicLong(0L)
        val oof = AtomicLong(0L)
        val stops = AtomicLong(0L)
        fun increment(obj: VDJCObject): RecordBuilder {
            total.incrementAndGet()
            if (!obj.isAvailable(GeneFeature.CDR3)) return this
            val hasStops = obj.containsStopsOrAbsent(GeneFeature.CDR3)
            val isOOf = obj.isOutOfFrameOrAbsent(GeneFeature.CDR3)
            if (isOOf) // if oof, do not check stops
                oof.incrementAndGet() else if (hasStops) stops.incrementAndGet()
            if (hasStops || isOOf) nf.incrementAndGet()
            return this
        }

        fun add(oth: RecordBuilder): RecordBuilder {
            total.addAndGet(oth.total.get())
            nf.addAndGet(oth.nf.get())
            oof.addAndGet(oth.oof.get())
            stops.addAndGet(oth.stops.get())
            return this
        }

        fun build(): ChainUsageStatsRecord {
            return ChainUsageStatsRecord(
                total.get(), nf.get(), oof.get(), stops.get()
            )
        }
    }
}