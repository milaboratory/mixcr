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

import io.repseq.core.GeneType;
import io.repseq.core.VDJCGeneId;
import io.repseq.core.VDJCLibraryId;

import java.util.Objects;

public final class VJCSignature {
    /**
     * Special marker GeneID used to make matchHits procedure to ignore V, J or C genes during matchHits procedure
     */
    public static final VDJCGeneId DO_NOT_CHECK = new VDJCGeneId(new VDJCLibraryId("NO_LIBRARY", 0), "DO_NOT_CHECK");
    private final VDJCGeneId vGene, jGene, cGene;

    /**
     * null for absent hits, DO_NOT_CHECK to ignore corresponding gene
     */
    VJCSignature(VDJCGeneId vGene, VDJCGeneId jGene, VDJCGeneId cGene) {
        this.vGene = vGene;
        this.jGene = jGene;
        this.cGene = cGene;
    }

    boolean matchHits(CloneAccumulator acc) {
        if (vGene != DO_NOT_CHECK) {
            if (vGene == null && acc.hasInfoFor(GeneType.Variable))
                return false;
            if (vGene != null && acc.hasInfoFor(GeneType.Variable, vGene))
                return false;
        }

        if (jGene != DO_NOT_CHECK) {
            if (jGene == null && acc.hasInfoFor(GeneType.Joining))
                return false;
            if (jGene != null && acc.hasInfoFor(GeneType.Joining, jGene))
                return false;
        }

        if (cGene != DO_NOT_CHECK) {
            if (cGene == null && acc.hasInfoFor(GeneType.Constant))
                return false;
            if (cGene != null && acc.hasInfoFor(GeneType.Constant, cGene))
                return false;
        }

        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        VJCSignature that = (VJCSignature) o;

        if (!Objects.equals(vGene, that.vGene)) return false;
        if (!Objects.equals(jGene, that.jGene)) return false;
        return Objects.equals(cGene, that.cGene);

    }

    @Override
    public int hashCode() {
        int result = vGene != null ? vGene.hashCode() : 0;
        result = 31 * result + (jGene != null ? jGene.hashCode() : 0);
        result = 31 * result + (cGene != null ? cGene.hashCode() : 0);
        return result;
    }
}
