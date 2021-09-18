/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 * 
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 * 
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */
package com.milaboratory.mixcr.assembler;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.ClonalSequence;

public final class RelativeConcentrationFilter implements ClusteringFilter {
    final double specificMutationProbability;

    @JsonCreator
    public RelativeConcentrationFilter(
            @JsonProperty("specificMutationProbability") double specificMutationProbability) {
        this.specificMutationProbability = specificMutationProbability;
    }

    @Override
    public boolean allow(Mutations<NucleotideSequence> mutations,
                         long majorClusterCount,
                         long minorClusterCount,
                         ClonalSequence majorSequence) {
        double expected = majorClusterCount * Math.pow(
                specificMutationProbability * majorSequence.getConcatenated().size(),
                mutations.size());
        return minorClusterCount <= expected;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RelativeConcentrationFilter)) return false;

        RelativeConcentrationFilter that = (RelativeConcentrationFilter) o;

        if (Double.compare(that.specificMutationProbability, specificMutationProbability) != 0) return false;

        return true;
    }

    @Override
    public int hashCode() {
        long temp = Double.doubleToLongBits(specificMutationProbability);
        return (int) (temp ^ (temp >>> 32));
    }
}
