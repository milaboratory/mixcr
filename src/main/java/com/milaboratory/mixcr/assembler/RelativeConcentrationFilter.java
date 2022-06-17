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
