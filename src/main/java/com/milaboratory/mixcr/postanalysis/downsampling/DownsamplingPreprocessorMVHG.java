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


import cc.redberry.pipe.InputPort;
import com.milaboratory.mixcr.postanalysis.MappingFunction;
import com.milaboratory.mixcr.postanalysis.SetPreprocessor;
import com.milaboratory.mixcr.postanalysis.SetPreprocessorSetup;
import com.milaboratory.mixcr.postanalysis.SetPreprocessorStat;
import gnu.trove.list.array.TLongArrayList;
import gnu.trove.map.hash.TIntObjectHashMap;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.random.Well19937c;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.ToLongFunction;
import java.util.stream.LongStream;

import static com.milaboratory.mixcr.postanalysis.downsampling.DownsamplingUtil.downsample_mvhg;

/**
 * Downsampling based on multivariate hypergeometric distributions
 */
public class DownsamplingPreprocessorMVHG<T> implements SetPreprocessor<T> {
    public final ToLongFunction<T> getCount;
    public final BiFunction<T, Long, T> setCount;
    public final DownsampleValueChooser downsampleValueChooser;
    public final boolean dropOutliers;
    final String id;
    private final SetPreprocessorStat.Builder<T> stats;

    public DownsamplingPreprocessorMVHG(DownsampleValueChooser downsampleValueChooser,
                                        ToLongFunction<T> getCount,
                                        BiFunction<T, Long, T> setCount,
                                        boolean dropOutliers,
                                        String id) {
        this.getCount = getCount;
        this.setCount = setCount;
        this.downsampleValueChooser = downsampleValueChooser;
        this.dropOutliers = dropOutliers;
        this.id = id;
        this.stats = new SetPreprocessorStat.Builder<>(id, getCount::applyAsLong);
    }

    public String[] description() {
        String ch = downsampleValueChooser.id();
        if (ch == null || ch.isEmpty())
            return new String[0];
        return new String[]{ch};
    }

    private ComputeCountsStep setup = null;
    private long downsampling = -1;

    @Override
    public SetPreprocessorSetup<T> nextSetupStep() {
        if (setup == null)
            return setup = new ComputeCountsStep();

        return null;
    }

    @Override
    public MappingFunction<T> getMapper(int iDataset) {
        stats.clear(iDataset);

        if (downsampling == -1)
            downsampling = downsampleValueChooser.compute(setup.counts);

        if (downsampling > setup.counts[iDataset]) {
            if (dropOutliers) {
                stats.drop(iDataset);
                return t -> null;
            } else
                return t -> {
                    stats.asis(iDataset, t);
                    return t;
                };
        }

        long[] counts = setup.countLists[iDataset].toArray();
        long total = LongStream.of(counts).sum();
        RandomGenerator rnd = new Well19937c(total);
        long[] countsDownsampled = downsample_mvhg(counts, total, downsampling, rnd);

        AtomicInteger idx = new AtomicInteger(0);
        return t -> {
            stats.before(iDataset, t);
            int i = idx.getAndIncrement();
            if (countsDownsampled[i] == 0)
                return null;
            T tNew = setCount.apply(t, countsDownsampled[i]);
            stats.after(iDataset, tNew);
            return tNew;
        };
    }

    @Override
    public TIntObjectHashMap<List<SetPreprocessorStat>> getStat() {
        return stats.getStatMap();
    }

    @Override
    public String id() {
        return id;
    }

    class ComputeCountsStep implements SetPreprocessorSetup<T> {
        long[] counts;
        TLongArrayList[] countLists;

        boolean initialized = false;

        @Override
        public void initialize(int nDatasets) {
            if (initialized)
                throw new IllegalStateException();
            initialized = true;
            counts = new long[nDatasets];
            countLists = new TLongArrayList[nDatasets];
            for (int i = 0; i < nDatasets; i++) {
                countLists[i] = new TLongArrayList();
            }
        }

        @Override
        public InputPort<T> consumer(int i) {
            if (!initialized)
                throw new IllegalStateException();
            return object -> {
                if (object == null)
                    return;
                long c = getCount.applyAsLong(object);
                counts[i] += c;
                countLists[i].add(c);
            };
        }
    }
}
