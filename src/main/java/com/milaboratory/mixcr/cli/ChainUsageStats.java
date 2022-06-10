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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.milaboratory.mixcr.basictypes.VDJCObject;
import com.milaboratory.util.Report;
import com.milaboratory.util.ReportHelper;
import io.repseq.core.Chains;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by poslavsky on 08/11/2016.
 */
@JsonSerialize(using = ChainUsageStats.Serializer.class)
public final class ChainUsageStats implements Report {
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

    public static final class Serializer extends JsonSerializer<ChainUsageStats> {
        @Override
        public void serialize(ChainUsageStats value, JsonGenerator jgen, SerializerProvider provider) throws IOException, JsonProcessingException {
            jgen.writeStartObject();
            jgen.writeNumberField("total", value.total.longValue());
            jgen.writeNumberField("chimeras", value.chimeras.longValue());
            jgen.writeObjectFieldStart("chains");
            for (Map.Entry<Chains, AtomicLong> entry : value.counters.entrySet()) {
                String chains = entry.getKey().toString();
                if (chains.isEmpty())
                    chains = "X";
                jgen.writeNumberField(chains, entry.getValue().longValue());
            }
            jgen.writeEndObject();
            jgen.writeEndObject();
        }
    }
}
