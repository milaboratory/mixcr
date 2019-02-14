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
package com.milaboratory.mixcr.assembler;

import com.milaboratory.util.IntArrayList;

import static org.apache.commons.math3.util.ArithmeticUtils.pow;
import static org.apache.commons.math3.util.CombinatoricsUtils.binomialCoefficient;

public class AssemblerUtils {
    public static long mappingVariantsCount(int numberOfBadPoints, int maxAllowedMutations) {
        long variants = 1;
        for (int i = 1; i <= maxAllowedMutations; ++i)
            variants += binomialCoefficient(numberOfBadPoints, i) * pow(3L, i);
        return variants;
    }

    /**
     * Calculates maximal number of registered mismatches for the number of possible mismatch positions, to control the
     * probability of non-specific mapping in cases with big number of low quality positions. Maximal probability of
     * random mapping is defined through the numberOfVariants parameter.
     *
     * Basically, if the target read has only one low quality position and you found the clonotype with the different
     * nucleotide in exactly this position, it is not the same as if the sequence had all low quality positions and you
     * found the clonotype differing in one of them. In the first case you had only 3 possible sequences to match with,
     * and in the second case - 3*L, where L is the length of the clonal sequence. To address for this effect, MiXCR
     * controls the number of possible matching sequence, instead of a raw number of allowed mismatches.
     */
    public static class MappingThresholdCalculator {
        final int[] values;
        final int oneThreshold;

        public MappingThresholdCalculator(long numberOfVariants, int maxN) {
            int n = -1;
            this.oneThreshold = (int) (numberOfVariants / 3);
            IntArrayList thresholds = new IntArrayList();
            for (int i = 0; i < maxN; ++i) {
                for (n = 0; n < i; ++n)
                    if (mappingVariantsCount(i, n + 1) > numberOfVariants)
                        break;
                if (n == 1 && i > 1)
                    break;
                thresholds.add(n);
            }
            this.values = thresholds.toArray();
        }

        /**
         * Returns maximal number of allowed mismatches to keep the number of possible mapping variants lower then the
         * value of numberOfVariants parameter, provided in constructor.
         *
         * @param N number of low quality positions
         * @return
         */
        public int getThreshold(int N) {
            if (N < values.length)
                return values[N];
            if (N <= oneThreshold)
                return 1;
            return 0;
        }
    }
}
