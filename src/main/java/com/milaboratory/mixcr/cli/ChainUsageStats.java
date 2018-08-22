/*
 * Copyright (c) 2014-2018, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
 * (here and after addressed as Inventors)
 * All Rights Reserved
 *
 * Permission to use, copy, modify and distribute any part of this program for
 * educational, research and non-profit purposes, by non-profit institutions
 * only, without fee, and without a written agreement is hereby granted,
 * provided that the above copyright notice, this paragraph and the following
 * three paragraphs appear in all copies.
 *
 * Those desiring to incorporate this work into commercial products or use for
 * commercial purposes should contact the Inventors using the following email
 * addresses: licensing@milaboratory.com
 *
 * IN NO EVENT SHALL THE INVENTORS BE LIABLE TO ANY PARTY FOR DIRECT, INDIRECT,
 * SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING LOST PROFITS,
 * ARISING OUT OF THE USE OF THIS SOFTWARE, EVEN IF THE INVENTORS HAS BEEN
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * THE SOFTWARE PROVIDED HEREIN IS ON AN "AS IS" BASIS, AND THE INVENTORS HAS
 * NO OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR
 * MODIFICATIONS. THE INVENTORS MAKES NO REPRESENTATIONS AND EXTENDS NO
 * WARRANTIES OF ANY KIND, EITHER IMPLIED OR EXPRESS, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY OR FITNESS FOR A
 * PARTICULAR PURPOSE, OR THAT THE USE OF THE SOFTWARE WILL NOT INFRINGE ANY
 * PATENT, TRADEMARK OR OTHER RIGHTS.
 */
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
