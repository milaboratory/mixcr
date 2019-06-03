/*
 * Copyright (c) 2014-2019, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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
 * commercial purposes should contact MiLaboratory LLC, which owns exclusive
 * rights for distribution of this program for commercial purposes, using the
 * following email address: licensing@milaboratory.com.
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
