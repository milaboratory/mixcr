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
import cc.redberry.pipe.OutputPortCloseable;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.postanalysis.Dataset;
import com.milaboratory.mixcr.postanalysis.SetPreprocessorFactory;
import com.milaboratory.mixcr.postanalysis.WeightFunctions;
import com.milaboratory.mixcr.postanalysis.preproc.ElementPredicate;
import com.milaboratory.mixcr.postanalysis.preproc.NoPreprocessing;
import com.milaboratory.mixcr.postanalysis.preproc.SelectTop;
import io.repseq.core.GeneFeature;
import org.apache.commons.math3.random.RandomGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToLongFunction;
import java.util.stream.LongStream;

public final class DownsamplingUtil {
    private DownsamplingUtil() {
    }

    public static <T> long total(ToLongFunction<T> getCount, Dataset<T> set) {
        long total = 0;
        try (OutputPortCloseable<T> port = set.mkElementsPort()) {
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

    public static SetPreprocessorFactory<Clone> parseDownsampling(String downsampling, boolean dropOutliers) {
        if (downsampling.equalsIgnoreCase("none")) {
            return new NoPreprocessing.Factory<>();
        } else if (downsampling.startsWith("umi-count")) {
            if (downsampling.endsWith("auto"))
                return new ClonesDownsamplingPreprocessorFactory(new DownsampleValueChooser.Auto(), dropOutliers);
            else if (downsampling.endsWith("min"))
                return new ClonesDownsamplingPreprocessorFactory(new DownsampleValueChooser.Minimal(), dropOutliers);
            else {
                return new ClonesDownsamplingPreprocessorFactory(new DownsampleValueChooser.Fixed(downsamplingValue(downsampling)), dropOutliers);
            }
        } else {
            int value = downsamplingValue(downsampling);
            if (downsampling.startsWith("cumulative-top")) {
                return new SelectTop.Factory<>(WeightFunctions.Count, 1.0 * value / 100.0);
            } else if (downsampling.startsWith("top")) {
                return new SelectTop.Factory<>(WeightFunctions.Count, value);
            } else {
                throw new IllegalArgumentException("Illegal downsampling string: " + downsampling);
            }
        }
    }

    public static SetPreprocessorFactory<Clone> filterOnlyProductive(SetPreprocessorFactory<Clone> p) {
        List<ElementPredicate<Clone>> filters = new ArrayList<>();
        filters.add(new ElementPredicate.NoStops(GeneFeature.CDR3));
        filters.add(new ElementPredicate.NoOutOfFrames(GeneFeature.CDR3));
        return p.filterFirst(filters);
    }

    private static int downsamplingValue(String downsampling) {
        return Integer.parseInt(downsampling.substring(downsampling.lastIndexOf("-") + 1));
    }
}
