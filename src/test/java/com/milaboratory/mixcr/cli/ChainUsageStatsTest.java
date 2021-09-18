/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 *
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 *
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */
package com.milaboratory.mixcr.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.milaboratory.util.GlobalObjectMappers;
import io.repseq.core.Chains;
import org.junit.Test;

public class ChainUsageStatsTest {
    @Test
    public void serializationTest() throws JsonProcessingException {
        ChainUsageStats stats = new ChainUsageStats();
        stats.total.incrementAndGet();
        stats.total.incrementAndGet();
        stats.chimeras.incrementAndGet();
        stats.getCounter(Chains.TRB).incrementAndGet();
        System.out.println(GlobalObjectMappers.PRETTY.writeValueAsString(stats));
    }
}
