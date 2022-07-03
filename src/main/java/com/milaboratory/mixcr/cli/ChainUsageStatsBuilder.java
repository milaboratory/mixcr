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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static io.repseq.core.Chains.*;

public final class ChainUsageStatsBuilder implements ReportBuilder {
    final AtomicLong chimeras = new AtomicLong(0);
    final AtomicLong total = new AtomicLong(0);
    final ConcurrentHashMap<Chains, AtomicLong> counters = new ConcurrentHashMap<>();

    AtomicLong getCounter(Chains chains) {
        AtomicLong counter;
        if ((counter = counters.get(chains)) != null)
            return counter;
        else {
            AtomicLong newCounter = new AtomicLong(0);
            counter = counters.putIfAbsent(chains, newCounter);
            if (counter == null)
                return newCounter;
            else
                return counter;
        }
    }

    void increment(VDJCObject obj) {
        total.incrementAndGet();
        if (obj.isChimera())
            chimeras.incrementAndGet();
        else
            getCounter(obj.commonTopChains()).incrementAndGet();
    }

    void decrement(VDJCObject obj) {
        total.decrementAndGet();
        if (obj.isChimera())
            chimeras.decrementAndGet();
        else
            getCounter(obj.commonTopChains()).decrementAndGet();
    }

    Map<Chains.NamedChains, Long> getMap() {
        Map<Chains.NamedChains, Long> r = new HashMap<>();
        for (Map.Entry<Chains, AtomicLong> e : counters.entrySet())
            for (Chains.NamedChains knownChains : Arrays.asList(
                    TRAD_NAMED,
                    TRB_NAMED, TRG_NAMED,
                    IGH_NAMED, IGKL_NAMED))
                if (knownChains.chains.intersects(e.getKey()))
                    r.put(knownChains, r.getOrDefault(knownChains, 0L) + e.getValue().get());
        return r;
    }

    @Override
    public ChainUsageStats buildReport() {
        return new ChainUsageStats(
                chimeras.get(),
                total.get(),
                getMap()
        );
    }
}
