package com.milaboratory.mixcr.postanalysis.downsampling;


import cc.redberry.pipe.InputPort;
import com.fasterxml.jackson.annotation.JsonProperty;
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

import static com.milaboratory.mixcr.postanalysis.downsampling.DownsamplingUtil.downsample_mvhg;

/**
 *
 */
public class DownsamplingPreprocessor<T> implements SetPreprocessor<T> {
    public final ToLongFunction<T> getCount;
    public final BiFunction<T, Long, T> setCount;
    @JsonProperty("downsampleValueChooser")
    public final DownsampleValueChooser downsampleValueChooser;
    @JsonProperty("seed")
    public final long seed;
    final String id;
    private final SetPreprocessorStat.Builder<T> stats;

    public DownsamplingPreprocessor(ToLongFunction<T> getCount,
                                    BiFunction<T, Long, T> setCount,
                                    DownsampleValueChooser downsampleValueChooser,
                                    long seed,
                                    String id) {
        this.getCount = getCount;
        this.setCount = setCount;
        this.downsampleValueChooser = downsampleValueChooser;
        this.seed = seed;
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
        if (downsampling == -1)
            downsampling = downsampleValueChooser.compute(setup.counts);

        if (downsampling > setup.counts[iDataset]) {
            stats.drop(iDataset);
            return t -> null;
        }

        long[] counts = setup.countLists[iDataset].toArray();
        RandomGenerator rnd = new Well19937c(seed);
        long[] countsDownsampled = downsample_mvhg(counts, downsampling, rnd);

        AtomicInteger idx = new AtomicInteger(0);
        stats.clear(iDataset);
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
