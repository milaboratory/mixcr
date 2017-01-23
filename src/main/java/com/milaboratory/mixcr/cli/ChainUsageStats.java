package com.milaboratory.mixcr.cli;

import com.milaboratory.mixcr.basictypes.VDJCObject;
import io.repseq.core.Chains;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by poslavsky on 08/11/2016.
 */
final class ChainUsageStats implements ReportWriter {
    final AtomicLong chimeras = new AtomicLong(0);
    final AtomicLong total = new AtomicLong(0);
    final ConcurrentHashMap<Chains, AtomicLong> chains = new ConcurrentHashMap<>();

    void put(VDJCObject obj) {
        total.incrementAndGet();
        if (obj.isChimera())
            chimeras.incrementAndGet();
        else {
            Chains chains = obj.commonTopChains();
            AtomicLong at = new AtomicLong(0);
            AtomicLong p = this.chains.putIfAbsent(chains, at);
            if (p == null)
                p = at;
            p.incrementAndGet();
        }
    }

    @Override
    public void writeReport(ReportHelper helper) {
        long total = this.total.get();
        for (Map.Entry<Chains, AtomicLong> ch : chains.entrySet()) {
            helper.writePercentAndAbsoluteField(ch.getKey().toString() + " chains", ch.getValue(), total);
        }
    }
}
