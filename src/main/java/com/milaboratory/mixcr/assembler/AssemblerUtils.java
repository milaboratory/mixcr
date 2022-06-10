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
