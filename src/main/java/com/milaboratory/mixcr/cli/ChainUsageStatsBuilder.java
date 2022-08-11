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
package com.milaboratory.mixcr.cli;

import com.milaboratory.mixcr.basictypes.VDJCObject;
import com.milaboratory.util.ReportBuilder;
import io.repseq.core.Chains;
import io.repseq.core.GeneFeature;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static io.repseq.core.Chains.*;

public final class ChainUsageStatsBuilder implements ReportBuilder {
    final AtomicLong chimeras = new AtomicLong(0);
    final AtomicLong total = new AtomicLong(0);
    final ConcurrentHashMap<Chains, RecordBuilder> records = new ConcurrentHashMap<>();

    RecordBuilder getRecordBuilder(Chains chains) {
        RecordBuilder rec;
        if ((rec = records.get(chains)) != null)
            return rec;
        else {
            RecordBuilder newRec = new RecordBuilder();
            rec = records.putIfAbsent(chains, newRec);
            if (rec == null)
                return newRec;
            else
                return rec;
        }
    }

    void increment(VDJCObject obj) {
        total.incrementAndGet();
        if (obj.isChimera())
            chimeras.incrementAndGet();
        else
            getRecordBuilder(obj.commonTopChains()).increment(obj);
    }

    void decrement(VDJCObject obj) {
        total.decrementAndGet();
        if (obj.isChimera())
            chimeras.decrementAndGet();
        else
            getRecordBuilder(obj.commonTopChains()).increment(obj);
    }

    Map<NamedChains, ChainUsageStatsRecord> getMap() {
        Map<NamedChains, RecordBuilder> r = new HashMap<>();
        for (Map.Entry<Chains, RecordBuilder> e : records.entrySet())
            for (NamedChains knownChains : Arrays.asList(
                    TRAD_NAMED, TRB_NAMED, TRG_NAMED,
                    IGH_NAMED, IGK_NAMED, IGL_NAMED))
                if (knownChains.chains.intersects(e.getKey()))
                    r.computeIfAbsent(knownChains, __ -> new RecordBuilder()).add(e.getValue());
        return r.entrySet().stream().collect(
                Collectors.toMap(Map.Entry::getKey, e -> e.getValue().build()));
    }

    @Override
    public ChainUsageStats buildReport() {
        return new ChainUsageStats(
                chimeras.get(),
                total.get(),
                getMap()
        );
    }

    static class RecordBuilder {
        public final AtomicLong total = new AtomicLong(0L);
        public final AtomicLong nf = new AtomicLong(0L);
        public final AtomicLong oof = new AtomicLong(0L);
        public final AtomicLong stops = new AtomicLong(0L);

        RecordBuilder increment(VDJCObject obj) {
            total.incrementAndGet();
            if (!obj.isAvailable(GeneFeature.CDR3))
                return this;

            boolean hasStops = obj.containsStopsOrAbsent(GeneFeature.CDR3);
            boolean isOOf = obj.isOutOfFrameOrAbsent(GeneFeature.CDR3);
            if (isOOf) // if oof, do not check stops
                this.oof.incrementAndGet();
            else if (hasStops)
                stops.incrementAndGet();
            if (hasStops || isOOf)
                nf.incrementAndGet();
            return this;
        }

        RecordBuilder add(RecordBuilder oth) {
            total.addAndGet(oth.total.get());
            nf.addAndGet(oth.nf.get());
            oof.addAndGet(oth.oof.get());
            stops.addAndGet(oth.stops.get());
            return this;
        }

        ChainUsageStatsRecord build() {
            return new ChainUsageStatsRecord(
                    total.get(),
                    nf.get(),
                    oof.get(),
                    stops.get()
            );
        }
    }
}
