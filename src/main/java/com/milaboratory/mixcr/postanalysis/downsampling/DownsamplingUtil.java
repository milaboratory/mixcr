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
package com.milaboratory.mixcr.postanalysis.downsampling;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPort;
import com.milaboratory.mixcr.postanalysis.Dataset;
import org.apache.commons.math3.random.RandomGenerator;

import java.util.function.ToLongFunction;
import java.util.stream.LongStream;

public final class DownsamplingUtil {
    private DownsamplingUtil() {
    }

    public static <T> long total(ToLongFunction<T> getCount, Dataset<T> set) {
        long total = 0;
        try (OutputPort<T> port = set.mkElementsPort()) {
            for (T t : CUtils.it(port))
                total += getCount.applyAsLong(t);
            return total;
        }
    }

    public static long[] downsample_mvhg(long[] counts, long downSampleSize, RandomGenerator rnd) {
        long total = LongStream.of(counts).sum();
        return downsample_mvhg(counts, total, downSampleSize, rnd);
    }

    static long[] downsample_mvhg(long[] counts, long total, long downSampleSize, RandomGenerator rnd) {
        long[] result = new long[counts.length];
        RandomMvhgMarginals.random_multivariate_hypergeometric_marginals(rnd, total, counts, downSampleSize, result);
        return result;
    }

    public static long[] downsample_counts(long[] counts, long downSampleSize, RandomGenerator rnd) {
        long total = LongStream.of(counts).sum();
        long[] result = new long[counts.length];
        RandomMvhgCounts.random_multivariate_hypergeometric_count(rnd, total, counts, downSampleSize, result);
        return result;
    }
}
