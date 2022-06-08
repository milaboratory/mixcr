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
package com.milaboratory.mixcr.assembler.fullseq;

import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import gnu.trove.map.hash.TLongIntHashMap;

import java.util.Arrays;
import java.util.DoubleSummaryStatistics;

public final class CoverageAccumulator {
    public final VDJCHit hit;
    private final long[] coverage;

    public CoverageAccumulator(VDJCHit hit) {
        this.hit = hit;
        final int geneLength = hit.getGene().getPartitioning().getLength(hit.getAlignedFeature());
        this.coverage = new long[geneLength];
    }

    public void accumulate(VDJCHit hit) {
        for (int targetId = 0; targetId < hit.numberOfTargets(); targetId++) {
            Alignment<NucleotideSequence> al = hit.getAlignment(targetId);
            if (al == null)
                continue;
            Range coveredRange = al.getSequence1Range();
            for (int i = coveredRange.getLower(); i < coveredRange.getUpper(); i++)
                coverage[i]++;
        }
    }

    public DoubleSummaryStatistics getStat() {
        return Arrays.stream(coverage).mapToDouble(i -> i).summaryStatistics();
    }

    private final TLongIntHashMap nocCache = new TLongIntHashMap();

    public int getNumberOfCoveredPoints(long threshold) {
        if (nocCache.containsKey(threshold))
            return nocCache.get(threshold);
        else {
            int c = (int) Arrays.stream(coverage).filter(i -> i >= threshold).count();
            nocCache.put(threshold, c);
            return c;
        }
    }

    public double getFractionOfCoveredPoints(long threshold) {
        return 1.0 * getNumberOfCoveredPoints(threshold) / coverage.length;
    }
}
