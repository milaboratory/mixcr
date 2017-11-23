package com.milaboratory.mixcr.cli;

import com.milaboratory.mixcr.basictypes.VDJCObject;
import io.repseq.core.Chains;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by poslavsky on 08/11/2016.
 */
final class ChainUsageStats implements Report {
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

    @Override
    public void writeReport(ReportHelper helper) {
        long total = this.total.get();
        for (Map.Entry<Chains, AtomicLong> ch : counters.entrySet())
            helper.writePercentAndAbsoluteField(ch.getKey().toString() + " chains", ch.getValue(), total);
    }
}
