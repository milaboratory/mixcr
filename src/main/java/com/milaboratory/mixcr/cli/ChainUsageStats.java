/*
 *
 * Copyright (c) 2022, MiLaboratories Inc. All Rights Reserved
 *
 * Before downloading or accessing the software, please read carefully the
 * License Agreement available at:
 * https://github.com/milaboratory/miplots/blob/main/LICENSE
 *
 * By downloading or accessing the software, you accept and agree to be bound
 * by the terms of the License Agreement. If you do not want to agree to the terms
 * of the Licensing Agreement, you must not download or access the software.
 */
package com.milaboratory.mixcr.cli;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.milaboratory.util.ReportHelper;
import io.repseq.core.Chains;
import io.repseq.core.Chains.NamedChains;

import java.util.Map;
import java.util.Objects;

public class ChainUsageStats implements MiXCRReport {
    @JsonProperty("chimeras")
    public final long chimeras;
    @JsonProperty("total")
    public final long total;
    @JsonProperty("chains")
    @JsonDeserialize(keyUsing = Chains.KnownChainsKeyDeserializer.class)
//    @JsonSerialize(keyUsing = Chains.KnownChainsSerializer.class)
    public final Map<NamedChains, Long> chains;

    @JsonCreator
    public ChainUsageStats(@JsonProperty("chimeras") long chimeras,
                           @JsonProperty("total") long total,
                           @JsonProperty("chains") Map<NamedChains, Long> chains) {
        this.chimeras = chimeras;
        this.total = total;
        this.chains = chains;
    }

    @Override
    public void writeReport(ReportHelper helper) {
        long total = this.total;
        for (Map.Entry<NamedChains, Long> ch : chains.entrySet())
            helper.writePercentAndAbsoluteField(ch.getKey().name + " chains", ch.getValue(), total);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChainUsageStats that = (ChainUsageStats) o;
        return chimeras == that.chimeras && total == that.total && Objects.equals(chains, that.chains);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chimeras, total, chains);
    }
}
