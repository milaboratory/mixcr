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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public class ChainUsageStatsRecord {
    public static final ChainUsageStatsRecord EMPTY = new ChainUsageStatsRecord(0, 0, 0, 0);
    @JsonProperty("total")
    public final long total;
    @JsonProperty("nonFunctional")
    public final long nonFunctional;
    @JsonProperty("isOOF")
    public final long isOOF;
    @JsonProperty("hasStops")
    public final long hasStops;

    @JsonCreator
    public ChainUsageStatsRecord(@JsonProperty("total") long total,
                                 @JsonProperty("nonFunctional") long nonFunctional,
                                 @JsonProperty("isOOF") long isOOF,
                                 @JsonProperty("hasStops") long hasStops) {
        this.total = total;
        this.nonFunctional = nonFunctional;
        this.isOOF = isOOF;
        this.hasStops = hasStops;
        assert isOOF + hasStops == nonFunctional;
    }

    public long productive() {
        return total - nonFunctional;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChainUsageStatsRecord that = (ChainUsageStatsRecord) o;
        return total == that.total && nonFunctional == that.nonFunctional && isOOF == that.isOOF && hasStops == that.hasStops;
    }

    @Override
    public int hashCode() {
        return Objects.hash(total, nonFunctional, isOOF, hasStops);
    }
}
